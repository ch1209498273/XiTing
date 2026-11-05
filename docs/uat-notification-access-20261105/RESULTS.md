# 通知使用权引导改造验收记录（2026-11-05）

## 结论

用户报障的形态是「提示要开通知权限，我开了还是不行，于是挨个换浏览器/腾讯/优酷/爱奇艺」。本轮把 App 内通向系统「通知使用权」的路从 **0 条**补到 **3 条**（首页权限行、定时弹窗内按钮、自检面板修复按钮），并把定时弹窗的会话与权限读取改成实时，从系统页授权回来不再需要关掉重开。

改动全部属于引导与状态刷新：没有新增功能开关，没有改定时的确认契约（确认时仍重查 token 并比对内容签名），没有新增任何字符串资源。

## 起因与代码侧根因

- 报障附件为 4 张截图（ColorOS 真机）：定时弹窗里「请求暂停，保持黑幕」整行发灰；红字「未找到可暂停的播放会话。请检查通知使用权和播放器支持」；首页权限区只有「悬浮窗权限 / 电池后台加白 / 通知权限」三行且都显示已开启。
- 根因四条，均已定位到行号：首页无「通知使用权」行（`page_home.xml`、`strings.xml` 只有三个 `perm_*`）；定时弹窗的灰色单选无出口（`TimerDialogs.kt` 只设 `isEnabled=false`）；已有自检面板 `SupportDiagnostics.show()` 与 `finishThisEpisode()`、`ListeningScenes.show()` 在 src/main 内无调用点；`auto_playback_unavailable` 把「未授权」与「无会话」合并成同一句话，真正区分未授权的 `player_media_access_missing` 因 `sources` 恒空而永远走不到。

## 本轮改动

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `app/src/main/res/layout/page_home.xml` | 权限区新增 `row_media`/`pill_media` 第四行，标签用 `support_media_access`，状态位用 `pill_on`/`pill_off` | 用户把「通知权限 已开启」当成了报错里要的那个开关 |
| `app/src/main/res/values/colors.xml` + `drawable/bg_icon_purple.xml` | 新增紫色图标底色 | 与蓝色铃铛区分，避免两行看起来是同一件事 |
| `MainActivity.kt` | 新行点击跳 `ACTION_NOTIFICATION_LISTENER_SETTINGS`；`setPill(pill_media, hasMediaAccess(), …)` | 与电池行同构：不做条件分支、不加新文案 |
| `TimerDialogs.kt` | 未授权时提示改说 `player_media_access_missing`，并出现「去授权」按钮；`sources` 由 `val` 改 `var`，`updateCapability()` 每次重读会话与来源标题；弹窗窗口重获焦点时自动刷新 | 消除死胡同；授权/换播放器回来不必关掉重开 |
| `ReliabilityUi.kt` | 设置页「数据」组新增「诊断与自检」一行，接现成的 `SupportDiagnostics.show()` | 该面板早就写好但生产界面进不去 |
| `values-en/strings.xml` | `pill_battery_off` 由 "Exempt" 改 "Not exempted" | 见下方「一次判断修正」 |

## 一次判断修正（记录过程，不只记结论）

上一轮我判断英文电池状态位「翻译反了」。横向核对 8 个语言包后撤回该结论：`on` 一律是过去分词（Exempted / 등록됨 / Inscrit / Eingetragen / 已加白），`off` 一律是动词原形（Exempt / 등록 / Inscrire / Eintragen / 去加白），与通用 `pill_on`/`pill_off`（On/Enable、已开启/去开启）同构，属全库约定而非错误。真正的问题只有英文 `Exempt` 形容词味过强、动作读不出来，因此只改 EN 一处为状态式 "Not exempted"，其余七包未动。

## 环境

- AVD `api35`，Android 15，ABI x86_64，1080x2340，系统语言英文（`environment.log`）。
- 安装用 `adb install -r -g`（`install.log`）。证据运行时安装的 APK sha256 `5829630737f6cf11d8028492251dbe8a163b88b929bf19bc853c6439a9fac836`；记录完成后重跑的 `assembleDebug` 产物 sha256 `b6206123b177cb1acc2fffc59363632788d9bf3d55c6f146bdc363bc20810c6a`。两次之间源码无改动，debug 包非字节可复现，差异不代表内容变化。
- 版本 `6.0.0` / code 99，分支 `codex/v43-achievement-medals`。
- 权限状态用 `cmd notification allow_listener` / `disallow_listener` 切换，逐次读 `Settings.Secure enabled_notification_listeners` 原文留证（`permission-state.log`）。注意该值与系统服务的实际绑定可能有延迟，脚本在切换后统一 sleep 2 再取界面。

## 检查矩阵

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 撤权后首页出现第四行「Notification access」且状态位为动作式 "Enable" | PASS | `home-permissions-off.xml` / `.png` |
| 电池行英文状态位读作 "Not exempted"，胶囊不溢出 | PASS | 同上，`pill_battery` 字段 |
| 撤权时定时弹窗提示改口为「Media control access not granted · …」并出现「GRANT」 | PASS | `timer-dialog-no-access.xml` / `.png` |
| 撤权时「请求暂停，保持黑幕」仍为 enabled=false（没有因加出口而放开） | PASS | 同上 |
| 点弹窗内「GRANT」前台窗口为 `Settings$NotificationAccessSettingsActivity` | PASS | `grant-jump-focus.log` |
| 设置页「数据」组内出现「Diagnostics」一行 | PASS | `settings-diagnostics-row.xml` |
| 自检面板逐条列出且撤权时为「Notification access: No」 | PASS | `diagnostics-no-access.xml` / `.png` |
| 自检面板主按钮变为「ALLOW NOTIFICATION ACCESS」并落到同一系统页 | PASS | `diagnostics-fix-focus.log` |
| 弹窗开着的状态下授权，按返回后不碰任何控件即自愈：GRANT 消失、提示回到已授权分支 | PASS | `timer-dialog-selfheal.xml` / `.png` |
| 自愈后单选仍为 enabled=false（当时无可暂停会话，判据正确） | PASS | 同上 |
| 授权后首页 `pill_media` 由 "Enable" 翻转为 "On" | PASS | `home-permissions-on.xml` / `.png` |
| 回归：选「撤掉黑幕」点 OK 仍能写入定时 | PASS | `home-timer-set.xml` / `.png`（首页显示 "30 min left · Remove black curtain"） |
| assembleDebug 通过 | PASS | `build.log` |
| JVM 单元测试：39 个测试类 / 244 个用例 / 失败 0 / 错误 0 | PASS | `unit-tests.log`（`tools/run_tests.ps1` 在 ASCII 镜像目录执行，逐类表格全 PASS） |
| `lintDebug`：错误 0，警告 76，且无一条指向本轮改动的文件 | PASS | `lint.log`、`app/build/reports/lint-results-debug.txt` |

## 未验到与边界

1. **会话刷新的后半条未能在模拟器证明**：「新播放器出现 → 单选变亮」。`devtools/SessionTest` 夹具在 `onStop` 里故意自动暂停（模拟真实视频 App），Android 15 又禁止后台起音频，`cmd media_session dispatch play` 也没能唤醒它，因此造不出「后台仍在 PLAYING 的会话」。权限与会话走的是同一次重读，权限半条已实测通过。真机补验方法：腾讯视频/优酷/爱奇艺开「听视频/后台播放」让声音继续 → 打开睡前定时 → 从别处切回来或按一下时长下拉，单选应变亮。
2. **自愈依赖窗口焦点回调**。若某些 ROM 返回时不触发焦点变化，兜底刷新点是「改时长下拉」（`refreshSummary` 内已接 `updateCapability()`）；再兜底是关掉重开弹窗，行为与改造前一致。
3. **弹窗内会话列表的语义未改**：只认「正在播放」或「已被选为黑幕目标的暂停会话」，暂停过的历史会话不提升——这是 `CurrentPlaybackPolicy.chooseCurrentPlayback` 的既有设计，本轮不动。
4. 模拟器系统语言为英文，中文界面未截图；本轮中文侧未改动任何文案（只改 EN 一处）。
5. 未连真机、未做耗电实验、未发布版本；`acceptance.json` 中 `release` 记为 `not_published`。

## 给报障用户的口径

「通知权限」是本 App 能否弹通知（已开启，正常）；「通知使用权」是本 App 能否读取正在播放的视频（暂停、剧名走的是它）。新版首页会并排显示这两行，点「通知使用权」那行直接跳到系统开关；定时弹窗里也会就地给出「去授权」。授权后无需重开弹窗。若开关开对仍发灰，是播放器在切出时自己暂停了：请开启播放器的后台播放，或在黑幕内设置定时。

## 证据索引

- `run-sequence.sh`：本轮全部操作的脚本，坐标从 dump 反查，可重放。
- `sequence.log`：执行时序与每步点击结果（含一次预期内的 `MISS CANCEL`，因返回键已顺带关掉自检面板）。
- `permission-state.log`：两次 `enabled_notification_listeners` 原文与本App是否在其中。
- `home-permissions-off.xml/.png`、`home-permissions-on.xml/.png`、`home-timer-set.xml/.png`
- `timer-dialog-no-access.xml/.png`、`timer-dialog-selfheal.xml/.png`
- `diagnostics-no-access.xml/.png`、`settings-diagnostics-row.xml`
- `grant-jump-focus.log`、`diagnostics-fix-focus.log`：两处跳转后的前台窗口
- `environment.log`、`install.log`、`apk-sha256.txt`、`build.log`、`unit-tests.log`
