# v6.2.0 真机验收记录（ColorOS 实机，2026-11-12）

## 结论

v6.1.0/v6.2.0 两版改动里**只有模拟器能证的部分**（8 语言 × 2 主题 × 大字、96 个动态弹窗、JVM 单测、lint）此前已经跑绿。本轮补的是它们覆盖不到的那一层：**一台真的在放视频的 ColorOS 手机上，「请求暂停，保持黑幕」到底会不会亮起来**。

结果：会。优酷播放时该单选 `enabled=true`，来源标题实读为「优酷视频」，能力行实读为「播放中 · 可请求暂停」，且不再出现「去授权」；黑幕内三枚播控键全部可点，暂停注入让优酷 `PLAYING → PAUSED`，再点回到 `PLAYING`。报障用户那句「开了通知还是不行」在本机不复现，因为他那台机器上「通知使用权」一直是开着的——真正缺的是**入口**和**实时读取**，两样都已生效。

同时抓到两处**只有真机能抓到的**情况：入口排序里「演示」这一组提升后首页看不出变化（该机首次成功引导已完成，按钮本身是 GONE）；以及验收结束时 `accelerometer_rotation` 由 0 变成 1（视频 App 改写，我没有执行过任何 `settings put`，详见「系统设置核对」）。

## 环境

- 机型 `PME110`，Android 16（sdk 36），分辨率 1080x2344，序列号 `3B164H006DY00000`。
- 安装路径是**升级安装**而非全新：`v6.0.0/code99` → `v6.2.0/code101`，命令 `adb install -r`（**故意不带 `-g`**，不给他任何新授权，用来验签名连续性）。安装成功即证明两版用的是同一把 release key（`cd40e01d…`，与 v6.0.0/v6.1.0 同）。
- 设备侧读到的包信息：`versionCode=101`、`versionName=6.2.0`、`lastUpdateTime=2026-11-12 20:51:27`、`firstInstallTime=2026-10-02 02:54:43`（装机历史未断）。
- 安装包为 GitHub Release 产物：1,316,304 字节，sha256 `c17cf4f499f07bcbd5b476485e2b0574a5a1d0b012632f3ae2897f1cac002ba6`。
- 系统语言中文、主题浅色、`font_scale=1.0`，全部保持用户原状，未做任何为验收而改的设置。
- 助手在验收全程处于「运行中」，悬浮球在场（`Window #10 com.lujinyu.xiting`）——播放联动的前置条件按项目约束是先起息屏听剧、确认服务与悬浮球，再动播放器。

## 检查矩阵

| # | 检查项 | 结果 | 证据 |
| --- | --- | --- | --- |
| 1 | 升级安装不重置权限：`通知使用权` 仍在 `enabled_notification_listeners` 里 | PASS | `environment.log`（本机）＋`02-home-permissions.xml` |
| 2 | 首页权限区四行齐且状态正确：悬浮窗 已开启 / 电池后台加白 已加白 / 通知权限 已开启 / 通知使用权 已开启 | PASS | `02-home-permissions.xml` / `.png` |
| 3 | 新增的第四行图标底色与铃铛行区分开（紫底播放三角），胶囊不溢出 | PASS | `02-home-permissions.png` |
| 4 | 已授权 + 无会话时，定时弹窗单选仍 `enabled=false`，提示为「暂时无法确定播放来源，可用黑幕和定时返回」，**不出现**「去授权」 | PASS | `03-timer-dialog-granted.xml` / `.png` |
| 5 | 设置页「语言」组下方出现「常用入口顺序」一行 | PASS | `04-settings-entry-order.xml` |
| 6 | 排序弹窗默认序为 定时 → 播放器 → 演示 → 权限 | PASS | `05-entry-order-dialog.xml` / `.png` |
| 7 | 点「演示」后弹窗列表序变为 演示 → 定时 → 播放器 → 权限（`promote()` 确实落盘） | PASS | `06-entry-order-after-promote-demo.xml` |
| 8 | 但**首页看不出变化**：该机 `first_success_confirmed_v1` 已为真，`btn_first_use` 为 GONE，被提升的是一个不可见节点 | 记录为已知限制 | 同上，配合 `MainActivity.refreshStates()` |
| 9 | 改提升「权限」→ 首页真实重排：「服务与权限」整组上移到「最近听过」之下，「睡前定时/防杀保活」被挤到「节能统计」之后 | PASS | `07-home-order-permissions-top.xml` / `.png` |
| 10 | 未参与排序的节点（黑幕卡、最近听过、节能统计）原地不动，符合「只在参与槽位之间重排」的设计 | PASS | 同上，逐节点 bounds 比对 |
| 11 | 点「恢复默认顺序」后首页与改动前**逐坐标一致**（睡前定时 y=594、服务与权限 y=1274） | PASS | `08-home-order-restored.xml` / `.png` |
| 12 | 真实第三方播放器（优酷 `com.youku.phone`）后台出声时，会话被读到：`state=PLAYING(3)`、`actions=550`（0x226，含 ACTION_PAUSE） | PASS | `media-session.log`（本机） |
| 13 | 此时定时弹窗：单选 `enabled=true` 且已勾选、来源标题「优酷视频」、能力行「播放中 · 可请求暂停」、无「去授权」 | PASS | `09-timer-dialog-youku.xml` / `.png` |
| 14 | 悬浮球短按进黑幕时，优酷保持 `PLAYING`（声音继续，这是本 App 的立身之本） | PASS | `black-overlay.log`（本机） |
| 15 | 黑幕解锁后控件齐且可用：`上一集` / `播放暂停` / `下一集` / `返回播放器`，来源名显示「优酷视频」 | PASS | `10-black-overlay-youku-controls.xml` / `.png` |
| 16 | 播放暂停注入有效：点一次优酷 `PLAYING → PAUSED`，再点一次 `→ PLAYING`（往返都成） | PASS | 同上＋`media-session.log` |
| 17 | 「下一集」注入后会话未丢、position 由 0 变 90000 ms；但**是否真的换集无法判定**（优酷 `metadata: null`，会话侧读不到集号） | 部分通过，判据不足 | `black-overlay.log` |
| 18 | 首页「播放来源卡片」在会话存在时显示（图标＋「优酷视频」＋「播放中 · 可请求暂停」） | PASS | `11-home-source-card-youku.xml` / `.png` |
| 19 | 同一卡片在会话消失后自动隐藏（force-stop 优酷后卡片节点不再出现在界面树里） | PASS | `12-home-source-card-hidden.xml` / `.png` |
| 20 | 设置页「诊断与自检」入口在真机可进，11 条逐条实读：悬浮窗权限 是 / 通知使用权 是 / 通知已开启 是 / 电池优化已豁免 是 / 可读取媒体会话 否 / 媒体正在播放 否 / 会话声明支持暂停 否 / 支持快进后退 否 / 支持切集 否 / 助手服务运行中 是 / 黑幕正在显示 否（后四条为当时 force-stop 优酷后的真实状态） | PASS | `13-diagnostics-panel.xml` / `.png` |
| 21 | 自检面板那句免责说明（「媒体能力是播放器的声明，不保证第三方会响应」）在真机上被验证为**必要**而非冗余：见第 23、24 条 | PASS | 同上 |
| 22 | 设置页「检查更新」一行显示「当前 v6.2.0」，与 `dumpsys package` 读到的 `versionName=6.2.0` 一致 | PASS | `13-diagnostics-panel.png` 背景行 |
| 23 | 爱奇艺 `com.qiyi.video`：会话能读到（`MediaSessionController`、`actions=566`=0x236，含 ACTION_PAUSE），但本机停在详情页 `state=NONE(0)`，点播放器区/点选集 1 都不出声，疑似 VIP/广告门槛 | **未覆盖** | `player-probe.log`（本机） |
| 24 | 抖音 `com.ss.android.ugc.aweme`：会话能读到（`FeedPlayerSession`、`active=true`、`state=STOPPED(1)`、`actions=566`），但卡在 `SplashActivity`，a11y 树 186 节点零文本，`KEYCODE_MEDIA_PLAY` 不改变状态 | **未覆盖** | `player-probe.log` |
| 25 | B 站：本机未安装（`pm list packages` 无 `bilibili`） | 不适用 | `environment.log` |
| 26 | 全程未 arm 真实定时（弹窗一律点「取消」），验收结束时首页「睡前定时」仍显示「未设置」 | PASS | `08`、`12` 两份首页 dump |

## 一次口径更正

上一轮我在计划里写过「B站/爱奇艺/优酷，抖音未装」。实际 `pm list packages` 结果是**相反的**：抖音装着（`com.ss.android.ugc.aweme`），B 站没装。覆盖名单据此改为 爱奇艺 / 优酷 / 抖音，结论也**不覆盖 B 站**——不是「B 站不行」，是这台机器上没这个 App。

## 系统设置核对（约束要求操作前后各读一次）

| 项 | 操作前 | 操作后 | 我是否写过 |
| --- | --- | --- | --- |
| `system/accelerometer_rotation` | `0` | **`1`** | 否。全程没有执行过 `settings put system accelerometer_rotation`、`user_rotation`、`font_scale` 或 `wm user-rotation` |
| `system/user_rotation` | `0` | `0` | 否 |
| `system/font_scale` | `1.0` | `1.0` | 否 |
| `secure/enabled_notification_listeners` | 含本 App | 含本 App（原文未变） | 否 |

`accelerometer_rotation` 这一项是本机已知行为：视频 App 为了横屏播放会把它临时改成 1，正常退出时自己改回 0。这次我用 `am force-stop com.youku.phone` 收尾，App 没机会走自己的还原逻辑，值就停在 1。**我没有自行写回**——按项目约束，原始值虽然在本轮有记录，但恢复动作要他指定才做。一键还原：下拉控制中心里的「自动旋转」关掉；或让我按 `0` 写回并读回验证。

## 未验与限制

1. **爱奇艺、抖音的播放联动未覆盖**。两家的会话都能被读到，但都进不到 `PLAYING`：爱奇艺卡在详情页（`state=NONE`，播放器区与选集点击都不出声），抖音卡在闪屏（a11y 树空、媒体键无响应）。这不是息屏听剧的失败——它连会话都建好了，是**没有真实播放内容可联动**。要补这一档，需要他本人在爱奇艺里播一集能播的（VIP 或免费集都行），我这边只做只读取证。
2. **「切集」只验到注入，没验到结果**。优酷的 `metadata` 恒为 `null`，会话侧读不到剧名与集号，所以「点下一集之后真的换集了吗」无法从机器侧判定；能判定的只有「请求已发出、会话存活、播放位置变化」。抖音/爱奇艺的 actions 里同样缺 `ACTION_PLAY`，切集走的是 `SKIP_TO_ITEM_WITH_ID`，这条路的真机结果仍是未知。
3. **「演示」组排序不可见**（第 8 条）。逻辑上 `promote()` 落盘、`applyHomeEntryOrder()` 消费，但被移动的是 GONE 节点，肉眼无法验收。这一档要真验，得在一台**首次成功引导未完成**的机器上跑（模拟器靶场即可）。
4. **黑幕的「±30 秒」与剧名显示**没在真机取到：它们同样依赖 `metadata`，优酷不给。模拟器侧用合成会话验过三档 actions，真机侧这一格是空的。
5. 本轮**只动了优酷一个 App 的播放状态**，且收尾 force-stop；未清理他的播放历史、未登录任何账号、未点过任何「购买/开通会员」。
6. 截图 `02-home-permissions.png`、`07`、`08`、`11`、`12` 含他本人的使用总量（今日 5时15分 / 近7天 33时32分 / 累计 94时40分）；`09`、`11` 角落里有优酷小窗的一帧公开剧集画面。这些目录是**公开仓库**，是否入库由他定，未定之前我没有提交。

## 证据索引

`drive.py` 是可重放的驱动脚本：坐标一律从 `uiautomator dump` 反查，不硬编码；`adb` 绝对路径写死在脚本里，因为这台机器的 PATH 里没有 adb。

已随本目录保存（待他决定是否入库）：

- `01-home-top`、`02-home-permissions`、`03-timer-dialog-granted`：装机后的首页与无会话态定时弹窗。
- `04-settings-entry-order`、`05-entry-order-dialog`、`06-entry-order-after-promote-demo`、`07-home-order-permissions-top`、`08-home-order-restored`：入口排序全链，含默认序、提升后、恢复后。
- `09-timer-dialog-youku`：真机播放态的定时弹窗（本轮最关键的一张）。
- `10-black-overlay-youku-controls`：黑幕内播控面板。
- `11-home-source-card-youku`、`12-home-source-card-hidden`：来源卡片显示/隐藏双向。
- `13-diagnostics-panel`：新接线的自检面板。

仅本地（`.gitignore` 全局排除 `*.log`）：`environment.log`、`install.log`、`media-session.log`、`black-overlay.log`、`player-probe.log`、`settings-check.log`。
