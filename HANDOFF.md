# 交接说明（HANDOFF）

给下一个接手此项目的 agent / 开发者。读完这一份就能接着干，不用翻整段对话。

> 当前版本：2026-11-06 · **v6.1.0 / versionCode 100**。当前能力与验收范围见 [v6.1.0](docs/releases/v6.1.0.md)。以下 v4.x 内容作为历史交接资料保留。
> v4.0 主体（10 项功能/优化 + 全量聚合数据架构）见下方清单。
> v4.0：成就图标换肤（五枚徽章图标，ColorOS 验证）、听剧月报/年报海报 + 跨月本地推送、
> 全量历史聚合（明细裁剪 60 天，派生数据不进备份）、摸头互动、黑幕剧名加播放 App 名、
> 关于页诊断反馈、CI/发版 lint+i18n 门禁、息屏建议通知（双路径）、targetSdk 36 预研文档。
> v3.8.0：MainActivity 三步瘦身、海报 1:1、黑幕播控（剧名+±30s）。
> 另：**「暂停检测/播放中断提醒」已拍板不做**（主场景听到睡着，无声是正常收尾），见第七节。
> v3.6.2：深色模式（跟随系统）+ 成就弹窗联动高亮。
> v3.6.1：多语言热刷新与布局修复、成就口径改「连续 7 天」、崩溃自捕获、
> CI 与一键发版/巡检脚本；当晚 7 语言真机巡检通过。详见 git log。

---

## 一、项目是什么

`D:\AI任务\zcode\息屏听剧` —— OPPO/ColorOS 的「息屏听剧」App。

核心功能：在任意视频 App 播放时，点悬浮球让屏幕全黑（**黑幕遮罩并降低窗口亮度，非系统真正关屏**）而声音继续。
纯 Kotlin + 纯 Android SDK，**零第三方依赖、无 INTERNET 权限**，v6.1.0 正式安装包 1,316,376 bytes。

- 包名 `com.lujinyu.xiting`，versionCode 100 / versionName 6.1.0
- `minSdk 26` / **`targetSdk 35`（刻意不升，见第五节 + docs/targetsdk36_黑幕兼容预研.md）**
- 主源码 **12,631 行** Kotlin（`wc -l app/src/main/java/**/*.kt` 实测，2026-10-18 校正。此前本行写「约 6800 行」，已过期一倍），单模块
- **38 个 JVM 单测文件 / 232 项**，另有 **26 个 androidTest 设备检查文件**（见第八节口径）
- **路径含中文是既定事实**：用户 2026-10-11 明确拍板**不迁移**纯 ASCII 路径，
  测试继续走 `tools/run_tests.ps1` 镜像方案，别再提搬迁

---

## 二、当前状态

**2026-11-06 v6.1.0 / code100 公开发布**：本版累计 v6.0.0 之后 12 个提交，主体是「通知使用权引导改造」——起因是用户报障「提示需要开启通知权限，但已经开启通知了，还是不行」，挨个换浏览器/腾讯/优酷/爱奇艺。定位不是功能 bug 而是引导缺失：报错要的是「通知使用权」（读别的 App 在放什么），首页显示的却是「通知权限」（本 App 能否弹通知），且改造前 App 内没有任何一条调用点能打开那个系统开关（`SupportDiagnostics.show`、`finishThisEpisode`、`ListeningScenes.show` 都只在 androidTest 里被调用）。改后入口 0 条→3 条：首页权限区第四行、定时弹窗内「去授权」、设置页「诊断与自检」；定时弹窗的会话与权限改实时重读并在窗口重获焦点时自动刷新。验收见 `docs/uat-notification-access-20261105/RESULTS.md`（api35 英文+中文两轮，13 项检查，244 项单测，lint 错误 0，i18n 392/392）。**未验**：真机（报障用户是 ColorOS）；「新播放器出现→单选变亮」这半条模拟器造不出条件（夹具 onStop 自动暂停 + Android 15 禁后台起音频），需真机开播放器后台播放补验。

发布符号（逐项外部核对，不采信脚本 stdout）：标签 `v6.1.0` → `f701a37`（`git ls-remote` 与本地 HEAD 一致，符合"标签打在 release 提交"的惯例）；GitHub Release id 436500792，非 draft、非 prerelease、latest=true，资产 `XiTing-6.1.0.apk` **1,316,376 bytes**，sha256 `225e290b21e8…`；签名者 `CN=XiTing Release, O=ch1209498273`，证书摘要与 v6.0.0 相同（同一把发布键，非 debug）；蒲公英 build 22 / 6.1.0，公开页已核。release 包内直接 `aapt dump` 检索到 `row_media`/`pill_media` 与「未授权读取媒体控制」「通知使用权」——即新入口确实在发出去的包里，而不只在源码里。CI run 42221354903 completed/success。全部字段见 `docs/releases/v6.1.0-publication.json`。

**2026-11-06 接上「常用入口排序」与「播放来源」卡片（工作区，未发布，属 v6.1.0 之后）**：起因是报障用户那条链暴露的同类缺陷——代码写了、测试调了、生产界面进不去。用户知情后仍选最重档，故本轮接线。

- **先纠正一处误判**：`playback_source_card` 被无条件 GONE **不是漏接**。它当时的文案是「请选择播放器」「选择配合使用的应用」，服务的是 v5.0.1 已撤掉的播放器选择流程；留着就是指向不存在界面的死入口。所以本轮不是把可见性翻过来，而是把内容改成不承担"选择"语义：`np!=null` 显示来源与能力，`hasSelection` 显示"所选来源现在怎么了"，两者都不成立仍 GONE。`PlaybackSelection` 是 memory-only 且 `select()` 无生产调用点，故 `hasSelection` 分支目前只由测试与黑幕自动目标路径触发，属防御分支，已如实标注。
- **契约只退役一半**：`PlaybackAppDeviceChecks` 里"定时弹窗内不出现选择播放器文案"（`ui_choose_player`）原样保留；只把卡片可见性扩成新断言。断言承重已实测：装**旧包**（无卡片逻辑）跑 → FAIL `Source card stayed hidden while a session is readable`；装新包 → `PASS: listening flow device checks`。这条红不是刻意造的，是我漏跑 `assembleDebug` 只跑了 `compileDebugKotlin` 撞出来的，正好当反向证据留下。
- **入口排序补上真正的消费者**：`applyHomeEntryOrder()` 只在参与排序的槽位之间重排（黑幕卡、最近听过、节能统计原地不动），权限按"标题+面板"两个节点成组搬；插入索引用原槽位，因为第 i 个位置前恰有 `slots[i]-i` 个未参与节点和 `i` 个已插回的参与节点。`showHomeEntryOrder()` 挂进设置页外观组。实测：提升「演示」后 "Try a test sound" 移到 Sleep timer 之上，点「恢复默认顺序」回到原位。
- **删掉一个死可点**：卡片 XML 原本 `clickable=true` + 水波前景，但监听器被显式设为 null。本轮改为不可点并去掉前景——它只报信息，不假装能点开什么。
- **未做**：卡片没有点击动作（若要加，"回到来源 App"是最不骗人的候选）；`finishThisEpisode()` 与 `ListeningScenes.show()` 仍是遗留死代码（前者功能已由定时预设「听完这集」上线，后者属 v5.0.1 撤掉的设计，未擅自恢复）；`SessionUiDeviceChecks` 仍断言 `v50_scene_*` 标签存在，那是被回退的设计，本轮未动它，需单独决定。

**2026-11-06 测试面的量：一个从没跑过的检查类，且两半断言都对着旧实现**：先做零代码测量——androidTest 内 19 个类，无调用点的只有 2 个，其中 `PetDeviceTest` 是 instrumentation 入口本身（`testInstrumentationRunner` 指向它），所以真正的孤儿恰好一个：`SessionUiDeviceChecks`。它不是"红了没人看见"，而是**从来没被执行过**。

进一步查被测对象，发现两半都是死断言：`bindSessionConsole()` 的定义就是 `= Unit`（v5.0.1 撤控制台时留的空壳），所以"卡片标识前后一致"那条比较的是 `null===null`，恒真；`v50_scene_*` 标签在 main 里从不创建，那条一旦跑必红；而 `summary` 含 `v50_play_unknown` 这条更早就废了——`pause()` 开头 `if(report==NONE) "" return`，`NONE` 分支里那个 `v50_play_unknown` 永远取不到，当前实现不可能输出该措辞。

但同一段里有一条**活覆盖被一起埋着**：`SessionConsoleUi.summary/pause` 确实在线上跑（`OverlayService` 通知正文、`WidgetData` 桌面小组件、`BlackOverlay` 定时状态都调它），而它没有任何测试。所以处理不是删文件，是摘死断言、留活断言并补强：新增按 8 语言核对 `summary` 的定时三形态（无定时=恰好等于 notif_text、90 秒向上折算 2 分钟、WAITING 走等待措辞、助手未运行不得播报剩余）、`pause` 六个取值与空串的对应、以及未确认不得写成已暂停；保留原文件里对现行实现仍成立的两条（取消定时不写偏好、听完这集预设选中第 5 项）。挂成新 part `session_text`（`am instrument -e flowOnly true -e flowPart session_text`）。

承重实测：新断言首跑 PASS；把 `summary` 的分钟折算从 `(ms+59999)/60000` 改成 `ms/60000` 一行 → FAIL `zh: 90 秒应向上折算成 2 分钟并出现在正文，实际「…定时剩余1分钟」`；还原后回到 PASS，`git diff` 确认产品文件无残留。门禁：单测 39 类 244 例 0 失败。

**留给下一次的同类问题**：`ListeningScenes`、`finishThisEpisode`、`HomeEntryOrder` 之外，还要不要再给 `SessionConsoleUi.card/label`（控制台已撤，但 `card()` 仍在 main 里）做一次去留判定，本轮没动。

**2026-11-06 生产面死代码清点（工作区，未发布）**：把上一轮用在测试面的办法搬到 main 源码。口径修正了两次才可信：第一版把框架回调（`onStartCommand`/`onUpgrade`/`afterTextChanged`）也算成"零引用"，报出 44 条，是噪声；滤掉 `override` 后 18 条；再区分"全仓零引用"与"只有测试在用"，真纯死代码是 11 个，另有 8 个属"生产不调、测试在守"。第二版还暴露我自己的一个减法错误：`fun MainActivity.xxx` 这类扩展函数没被 `fun name` 匹配上，导致 5 条被误报成"有 1 处引用"。

删除的 12 个（含二阶 1 个）：`finishThisEpisode`、`showCustomTimerDialog`、`pulseBadge`、`selectApp`、`listAdapter`、`calibrated`、`calibratedSavingMah`、`recapSwitch`、`refreshSessionConsole`、`showSessionRecords`、`scrollToGrowth`，以及删完才浮现的 `setTimerIntent`（它只被那两个已删弹窗用过）。**没删** `bindSessionConsole`——它不在名单里，diff 里那条看似删除的行只是文件末尾换行被脚本吃掉后的重写假象，已补回换行。

顺带清掉本轮改动文件里被删成死的 import 13 条（MainActivity 6、SessionConsoleUi 4、MainDialogs 2、MainSupport 1）。其他文件里另有 20 余条死 import 属存量，没顺手动。

孤儿字符串按 lint 的 `UnusedResources` 逐条与 HEAD 的代码引用数比对后删了 5 条 × 8 语言包（`locale_dlg_title/msg/fallback`、`timer_custom_bad`、`v50_recap_enabled`，共 58 行含翻译换行）。`player_choice_intro` 与 `ui_choose_player` 虽已无生产引用，但各有一条测试在用，保留——删它们会红。

保留的 8 个"仅测试在用"：`bindSessionConsole`、`orderedIds`、`showListeningPresets`、`isDemoAudioPlaying`、`distinctListenDays`、`filterImportSessions`、`mergePlayerCatalog`、`player_choice_intro`/`ui_choose_player` 两条文案。它们的共同问题是**逻辑被测试锁着，生产却从不走**，去留要逐个判政策，不能当死代码删。

门禁：单测 39 类 244 例 0 失败；lint 错误 0、警告 77；i18n 388/388（删掉 4 条 strings.xml 条目后各包同步）；设备检查默认全流程 + `session_text` + `app_choices` 三组全 PASS。

两处如实挂账：其一，警告数从 v6.1.0 的 76 变成 77，那 +1 **我没能归因**——发布时的逐条清单没存，无法比对；已实测确认本轮删除没有新增任何孤儿资源（63 条 `UnusedResources` 与 HEAD 代码引用逐个对过，命中 0）。其二，扫描器只认静态引用，靠反射或按名字拼出来的引用它看不见，所以删除清单仍需以编译与三组设备检查为准，本轮均为绿。

**2026-11-06 「仅测试在用」的定性与覆盖搬家（工作区，未发布）**：上一轮留下 8 条"生产不调、测试在守"的，本轮逐个定性。判据要干净：`git log -S"名字("` 会被定义行本身污染（`fun MainActivity.bindSessionConsole()` 里就含 `bindSessionConsole(`），所以改成在指定版本树里 grep 后**剔除定义行**再数——结果是这 7 条在 `3b5db09`（把它们发布出去的提交）和 HEAD 的非定义引用都是 0。也就是说它们不是回退残骸，是**生下来就没接线**：用户从来没拿到过这些行为，而测试把它们算作已覆盖。

分三种处置。

一、**覆盖搬家**（不是删测试，是把用例从没人走的路挪到有人走的路）：
- `BackupImportTest` 7 条改守 `validBackupSessions`——恢复路径与摘要真正共用的那条，此前**一条用例都没有**；补齐它特有的判据后 7→14 条（时长区间、`backupInteger` 只认 Long/Int、多余字段不带入、墙钟倒走仍保留、null 输入）。
- `StreaksTest` 3 条改守 `epochDayOf`（HistoryStore/WeekReport/MonthReport 都在用它，`distinctListenDays` 只是把内联写法又包一层）；其中"空列表为 0 天"那条转过去后等于断言空集合大小，删掉。
- `IncrementDialogChecks` 的 presets 用例改调 `showManageListeningPresets`——那才是定时弹窗内方案选择器真会走的路。
- `applyHomeEntryOrder` 改吃 `HomeEntryOrder.orderedIds`：设备检查 `PersonalizationDeviceChecks:47` 守的就是这个映射，实现绕开它等于测试守一条用户不走的路。

二、**删除**：`filterImportSessions`（被更强的 `validBackupSessions` 取代）、`distinctListenDays`、`showListeningPresets`、`bindSessionConsole`（`= Unit` 空壳）、`PlayerCatalogPolicy.kt` 整文件与 `PlayerCatalogPolicyTest`（生产零引用，且没有任何别处调用它的对象）。

三、**保留**：`isDemoAudioPlaying`——它是测试探针，生产直接读 `demoOverlay` 判空，删它要改 6 处测试且没有收益。

**我自己的错**：写分类时把 `showListeningPresets` 列进"保留"，动手时却删了它，是编译把这条自相矛盾抓出来的（`IncrementDialogChecks:33` 仍在调）。教训：分类结论和执行清单必须同一轮对账，不能凭上一段的说法写下一段的代码。

承重实测：把 `validBackupSessions` 的时长下限从 `60_000L` 放到 `1L` → 恰好 `BackupImportTest 时长不足一分钟被丢弃` 与 `BackupSummaryTest` 各红 1 条，还原后全绿。门禁：单测 38 类 247 例 0 失败（-3 无用例、+7 导入新用例、-1 空集合用例）；lint 错误 0/警告 77，无一条指向本轮文件；i18n 通过；设备检查 `incrementDialogs`（96 个对话框 × 8 语言 × 2 主题）、`session_text`、`app_choices`、默认全流程全 PASS；二阶孤儿扫描归零。

**发版脚本踩到一件事，别再犯**：`release.ps1` 第 2 步用 `powershell -File tools\run_tests.ps1 *> 文件` 调子进程，子进程已写完全绿日志并退出主逻辑，但 **Gradle 守护进程继承了重定向句柄**，父进程等管道关闭而长时间不返回（这次卡了一个多小时，日志停在"== 单测 =="）。处置办法：卡住时先看 `release_tests.txt` 的时间戳与结论行判断是"没跑完"还是"没返回"；确认全绿后停掉进程，还原已被 step 1 改写但未提交的 `build.gradle.kts`，单独补跑 `lint` 与 `i18n check` 两道门禁，再用 `-SkipTests` 重跑脚本完成发布。根治办法（未做）：脚本改用 `cmd /c` 或直接内联调用，或跑完显式 `--stop` 守护进程。

**v6.0.0 / code99 是上一版**（2026-10-18 公开发布 GitHub latest 与蒲公英 build21）。本版交付「接着听与随手标记」：SQLite 内容库与旧时长统计分离、最近听过、系统分享接收与显式粘贴、黑幕标记与备注、睡前定时起点、90 天期限与清理预览、备份 v5。验收范围**只覆盖 OPPO PME110 + 哔哩哔哩 9.14.0**，抖音本轮未重新验证，手机首屏 ≤500ms 未实测——详见 `docs/uat-continue-listening-20261017/RESULTS.md` 与第八节。

**2026-10-21 黑幕防误触（已随 v6.1.0 发布）**：新增「设置 → 防误触锁定」（`Prefs.MISTOUCH_GUARD`，默认关 = 旧版轻点解锁/单击返回/熄屏撤幕）；开启后锁定态单击不再点亮背光，改为**双击解锁**；「返回播放器」改为**长按退出**（单击无动作）。开启防误触后 `ACTION_SCREEN_OFF` 不再撤黑幕：保留黑幕、收回锁定态，锁屏时长从听剧会话扣除，跨锁屏的功耗样本不采；亮屏到锁屏界面不算回来（`KeyguardManager` 仍锁着就继续暂停），`ACTION_USER_PRESENT` 解锁即恢复计时；另加**锁定态每秒一次 `KeyguardManager` 轮询兜底**（仅亮屏且黑幕在时运行，熄屏立即停），防 ColorOS 等 ROM 漏发 `USER_PRESENT`。默认关时熄屏直接撤幕，时长照旧全算。8 语言文案同步为「双击解锁 / 长按返回」，并保留旧版「轻点解锁 / 返回播放器」文案供默认档使用；`MISTOUCH_GUARD` 已加入备份白名单与键名守卫。验证：232 项 JVM 单测通过；模拟器 api35 的 `guard_default`（默认档文案 + 单击返回 + 熄屏撤幕）、`lost_controls`、`episode`、`expiry`、`panels`、`black_layout` 夹具通过（`expiry` 注入 8 秒锁屏，断言黑幕仍在、界面回到锁定态、记录时长扣除了该段）。模拟器 api35 另走了一遍真实电源键路径：开启防误触 → 黑幕 → 熄屏（`screen off: curtain kept, session clock paused`）→ 亮屏停在锁屏（未提前恢复计时）→ 解锁（`unlocked: session clock resumed`，由轮询兜底触发，证明不依赖 ROM 是否送达 `USER_PRESENT`）；证据留在 `build/emu-shot/emu_unlock_resume.log`。真机 PME110 已 `install -r` 覆盖安装（数据保留），设置项默认关、开关切换正常；开启后按电源键实测 `screen off: curtain kept, session clock paused`，亮屏停在锁屏时未提前恢复计时。**未验**：真机解锁后的恢复日志（手机有安全锁，adb 不能代为解锁；轮询兜底已消除对 ROM 广播的依赖）；真息屏后第三方播放器是否继续出声仍取决于播放器自身，App 无法代其续播。

**2026-10-22 README 竞争位重写（已随 v6.1.0 发布）**：首屏从版本更新改为「为什么选它」，用可复核事实对照 Black Screen 2.0.7（1.2 MB vs 11 MB；清单无 `INTERNET`/计费/广告权限 vs 含 `INTERNET`、Firebase、`AD_ID`；防误触闭环；来电自动撤幕；夜钟默认关+每 45 秒微移），并新增「适合/不适合」与三条边界。数字取自 `AndroidManifest.xml`、`app/build.gradle.kts`（运行时零依赖）、v6.0.0 官方 Release 资产 1,288,112 字节与 232 项 `@Test`；未改代码。已作为 `2f84e83` 提交（仅 README/HANDOFF，未推送）。

**2026-10-27 最近听过改为「有标题就记」（已随 v6.1.0 发布）**：用户报障「只记录哔哩哔哩，其他根本不记录，而且停在10月17日」。定位：`ListeningRecorder.observe()` 要求标题与 identity 同时存在，而 identity 只来自 `METADATA_KEY_MEDIA_ID`；`capture()` 在 `controlsReliable=false` 时又只给空标题快照——v6.0.0 的 `library_recording` 夹具甚至把「无 media id 不记录」写成断言，所以这不是回归而是当初拍板过窄。改法：新增 `ListeningCapturePolicy.mergeKey`（有标题即记录；缺 media id 退回标题键，`ContentIdentityPolicy.same` 仍要求同包名，历史条目不因标题相同而合并；标题只是自家 App 名又无 media id 时仍不记，免得产出永远叫「某某视频」的垃圾行）；谎报状态（STOPPED/NONE 等）时记标题、位置留空，不把可疑读数写成续播点；`acceptedTimer` 走同一个键；`observe()` 在未记录时按原因变化打一行 `listening skip:` 供真机 logcat 分辨「没有会话」「没有标题」「标题只是App名」。红→绿证据：`library_recording` 新断言改前 FAIL（`Title without identity was not recorded`）、改后 PASS。顺带暴露夹具自身缺陷：`expiry` 注入的 8 秒锁屏把有效时长压到 59259ms，低于 `SessionLog.add` 的 60000ms 起记线，故失败信息现在打印 held/off 账目，并在锁定态多留 12 秒；该夹具与默认全流程改后 PASS。验证：236 项 JVM 单测通过（新增 4 项 mergeKey 策略测试）；模拟器 api35 的 `library_recording`（含无 media id 记录、同标题不重复拆行、谎报状态仍记标题、App 名标题不产出条目）、`expiry`、`library`、`guard_default`、`panels`、`episode`、`contracts`、`lost_controls` 全 PASS；`lint` 与 i18n 检查通过；README 版本说明已补记该能力属下一版。**未验**：真机上用户实际用的播放器是否给标题（需连手机放一条非 B 站内容看 `listening skip:` 日志）；「停在10月17日」还需在真机确认「自动记录」开关（`listening_auto_v1`，首次弹窗有「不自动记录」选项）与通知使用权状态；`expiry` 的 12 秒延长只在 api35 计时器路径验过。

**2026-10-27 真机验证结果与重复行修复（已随 v6.1.0 发布）**：用户 19:38 连接 PME110，release `install -r` 两次（19:37、20:11:15），全程未卸载、未清数据、未写系统设置。真机基线：从 `/sdcard/Download/XiTing` 最新自动备份读出库内 8 条（6 条 B 站 + 2 条保存链接），最新一条停在 **2026-10-17 23:15**，与用户报障一致；首页「最近听过」副标题显示「本机自动记录」，说明 `listening_auto_v1` 是开的，通知使用权也在（`enabled_notification_listeners` 含本包）——即「停在10-17」的唯一成因就是记录闸门，不是开关。真机分档结果：**YouTube**（公开视频，3:32）在黑幕期间成功入库，标题 68 字、段时长 213000ms 与该视频对得上，证明「有标题、无 media id 也记」在真机生效；**抖音**两次都没记，日志分别给出 `listening skip: session without title (com.ss.android.ugc.aweme)`（直播间会话无标题）与 `listening skip: no usable media session`（feed 会话谎报 STOPPED 且已从 `getActiveSessions` 掉出），即系统压根没给内容名，不是本 App 闸门能解决的——抖音目前只能靠「记住这里」手动保存。真机还复现一个新缺陷：会话闪断（广告/缓冲）使同一条内容记成两行（库里出现两条同名 YouTube 条目），故加 60 秒续写窗口（`RESUME_WINDOW_MS`：`finish()` 记下刚结束的合并键与条目 id，同键在窗口内回来就续写原条目并追加 segment）。红→绿：`library_recording` 的闪断断言在把窗口临时改成 1ms 的负档下 FAIL（`Session dropout split one episode into two rows`），恢复 60s 后 PASS；236 项 JVM 与 api35 的 `library_recording`/`expiry`/`library`/`guard_default`/`panels`/`episode`/`contracts`/`lost_controls` 全 PASS。**取证纪律**：dumpsys、uiautomator dump 与备份文件都含用户观看内容的标题，脚本只输出结构、字数与时长，`build/phone-*`、`build/newest.json`、日志抓取文件已全部删除。**留给用户**：测试产生的两条 YouTube 公开视频条目可在「最近听过 → 管理」删除；`accelerometer_rotation` 由 19:38 读到的 0 变成 20:11 的 1，本会话没有执行过写该设置的命令（只读 + 安装 + 点按），是否恢复由用户指定；重装后助手已停止，需再点一次「启动悬浮球」。

**2026-10-27 档 2：无可用标题时记「来源条目」（已随 v6.1.0 发布）**：用户拍板要档 2。`ListeningCapturePolicy.mergeKey` 改为 `resolve(s)`，返回（合并键 → 要写库快照）三档判定：①有可用标题（或 media id 佐证）→ 用 media id 或标题键，正常记内容；②没可用标题（含标题只是 App 自己名字）但 `playing` 为真 → 记「来源」条目：键为 \u0000p+包名、`title` 写空，列表按既有 `listen_unknown` 显示「未识别内容」，八语言零新增文案；③既没可用标题又不在播 → 不记（挂着暂停会话的 App 不该占列表）。为此 `ContentSnapshot` 增加 `playing` 字段（默认 true，`capture()` 用 `NowPlaying.playing` 覆盖，谎报 STOPPED 但系统路由到该会话的在播情形算作在播）；`observe()`/`acceptedTimer()`/`finish()` 统一走 `resolve`。证据（同一台 api35，先红后绿）：只换新的 androidTest 包、App 仍是无档 2 的旧包时 FAIL `Playing session without any title produced no source row`；换上新 App 包后 `library_recording` PASS，新增断言含「同一个 App 只滚动一条」「App 名不得当成内容标题」「来源条目 title 为空」。回归：236 项 JVM、`library`/`library_ui`/`expiry`/`guard_default`/`panels`/`episode` 全 PASS，`lint` 与 i18n 通过；release 包 1,293,540 字节已于 20:46:48 `install -r` 覆盖到 PME110。**未验**：真机上抖音「未识别内容」条目尚未跑成——构建期间手机自动锁屏（focus 回到 NotificationShade/keyguard），adb 无法代输密码解锁，需用户解锁后再放一条抖音满 40 秒；且抖音 feed 仍有整条会话读不到的时刻（`no usable media session`），那种情况档 2 也记不到。

**2026-10-29 黑幕切集键改为「常驻 + 媒体键兜底 + 探针判死」（已随 v6.1.0 发布）**：用户报「目前上一集和下一集按钮没有了」。定位**不是回归**：按 `PlaybackState.ACTION_SKIP_TO_PREVIOUS/NEXT` 隐藏切集键、`sendMediaKey` 只在声明时发指令，是 v5.1.1（2026-10-14「强停原播放器后按钮仍显示可用但点了没反应」那轮）拍板的「能力缺失即隐藏」，见 `docs/uat-flow-20261014/baseline/BlackOverlay.kt` 与 `docs/v5.1.1真机继续验收与修复_20261014.md`；v6.0.0 未动此判据。查这段历史**不能看 git 父提交**——code86–98 源码从未入库，`3b5db09^` 比 v5.1.1 还老，直接对比会误判成 v6.0.0 新坏的。先按用户挑的中间档做完「只补可见性」（缺能力给一行说明 + 诊断加一项 + 导出加一位）；随后真机只读 `dumpsys media_session` 读到**抖音 feed 会话 `actions=0x236`**（PAUSE、PLAY_PAUSE、REWIND、SKIP_TO_NEXT、SEEK_TO，**无 SKIP_TO_PREVIOUS、连 PLAY 都没声明**；1 条会话；当时 0 个 started player，故它报 STOPPED 是实话），用户据此改口 B 档——理由成立：**声明只是线索，不是响应的证明**，只看能力位会把「只处理媒体键、不写能力位」的播放器误杀成「按钮凭空消失」。最终实现：`NowPlaying` 增 `mediaId` 与 `canSkip`；新增 `SkipProbe.kt`（`SkipProbePolicy.route/verdict/signature` 纯判定 + 进程内按「包名:方向」记判死，TTL 10 分钟，内容签名一变全部作废；**不写盘、不进备份**，持久化会把改版后的播放器冤枉死）。黑幕两个切集键**常驻显示**：声明了走 `transportControls`，没声明走 `dispatchMediaKeyEvent` 注入**赌一次**，800ms（`SKIP_PROBE_MS`）后比对内容签名，**没变才判死**、转灰不可点；读不到签名（无标题又无 media id）判 `INCONCLUSIVE` 保持可点——不把「我们读不到」说成「它不行」。说明行 `black_no_skip_hint` 退到「两向都判死」才出现，单向判死只靠灰键本身表达（避免对半支持的播放器下错结论）。诊断弹窗保留「会话声明支持切集」，导出文本加 `player.skip=` 与 `player.skip_probe=none|prev|next|prev+next|no_session`，使远程可分辨「没声明」「声明了但注入没响」「读不到会话」三种情况。`sendMediaKey` 删掉 PREVIOUS/NEXT 分支，切集统一走 `skipPressed`。证据：**244 项 JVM 单测通过**（新增 `SkipProbeTest` 8 项：判死优先于声明、只有可比签名不变才判死、TTL 复活、按包按方向隔离、换内容作废）；api35 `black_layout` 夹具 8 语言全通过，并新增探针三档——②未声明但 `onMediaButtonEvent` 响应并换标题 → 键保持可用（同时断言注入的媒体键确实送达会话，否则先判夹具失效）；③收键但不换内容 → 判死转灰、只死一向不出说明、两向都死才出；④换内容 → 判死作废键回来；**两次红档**：把可见性退回按能力位隐藏 → FAIL `zh: skip keys vanished again for an undeclared player`；把判死从 `isEnabled` 里摘掉 → FAIL `按了没响的方向没有判死转灰`；还原后 PASS（`BlackOverlay.kt` sha256 `d24e054f6c618af0…`）。判死态实拍图人工看过：五键俱在、两个切集键明显转灰、说明行完整未截断。**夹具与脚本自身踩到四件事，别再犯**：同一帧刚设的 VISIBLE 测量宽度必为 0；布局未走完就 `View.draw()` 会拍到子 View 的上一帧位置（曾显示 ⏭ 叠在播放键上）；夹具方法被 `main{}` 包住后再调 `runOnMainSync` 直接抛「can not be called from the main application thread」；`MediaSession.setCallback` 会建 `CallbackMessageHandler(Looper.myLooper())`，只能在有 Looper 的主线程上调。另外用 `tail -c 40` 判构建成功是错的（要看退出码），`am instrument` 失败也返回 0（只认日志里的 `PASS:`/`FAIL:`）。**未验**：真机 PME110 未装本轮包，**抖音到底响不响注入的 `KEYCODE_MEDIA_PREVIOUS` 仍未测**——B 档的探针会在真机上自行给答案（判死即它真不支持，用户看到灰键+说明；生效即键常亮）；诊断弹窗新增行未截图复核；`player.skip_probe` 只有导出、未进弹窗。**版本未 bump、未提交（24 个文件改动 + 新增 `SkipProbe.kt`/`SkipProbeTest.kt`）。

v4.3.0 已发布 GitHub 与蒲公英 build19，PME110 已覆盖安装 code84 并成功启动；保留用户图标选择，不自动改为未解锁图标。发布源 cd0f2e6，标签 v4.3.0。134项JVM、11项API33原生检查、Lint与八语言校验通过；记录见 docs/v4.3发布记录_20261012.md。

v4.2.0 已发布 GitHub 与蒲公英，实体手机 PME110 已覆盖安装 code83 并成功启动。源码 8124476，标签 v4.2.0。131 项 JVM、8 项 API33 原生检查及 Lint/i18n 通过；真机第三方播放器暂停/续播仍需体验。记录见 docs/v4.2发布记录_20261012.md。

v4.1.0：PetRenderer 共享五阶轮廓，PetMotion 独立动作时序；统计页提供操作提示。电火花点亮、电球蓄力弹跳、雷云闪电、风暴横向拨动、王者长按展翼。帮助与能量条独立命中；悬浮球仍由 OverlayService 管理点击/拖动。成长值、皮肤和阈值保持兼容。验收说明见 docs/精灵升级预览/接入与验收.md。

**git 已初始化**（此前该项目在父仓库里是未跟踪状态，无任何历史）。
最新提交：

| 提交 | 内容 |
|---|---|
| 成就徽章化 | 成就徽章手绘化（详见第三节） |
| `783f9c6` | 修配色洗白 + 缩略图不跟随选择 |
| `2218a41` | 修 stage 污染 growthStage、成就 6/4、缩略图带界面元素、配色加饱和度 |
| `1be4091` | 拉开配色色相、统计页形态行改走 PetForm、成就砍到 4 条 |
| `8364070` | 精灵形态改为可选，成长值只决定解锁到哪一形态 |
| `5566759` | 精灵图鉴改成可视化换肤选择器 |
| `a430b46` | 接通 findBackup，修掉首次启动误弹「恢复历史」 |
| `ccd338a` | prefs 键名收拢为常量层 + 41 个单测 + VIBRATE 权限 |
| `6e11919` | 崩溃与性能收敛 + 死资源清理 |
| `a37569f` / `9d0be03` / `18369b6` | PetView 分配 / WidgetData 渲染门禁 / 移除只写不读的 xiiting_stats |
| `46fb465` | 首次纳入版本控制 |

**测试基建**：`app/src/test`，**232 个 JVM 单测 / 38 个文件**（JUnit4 + org.json；历次口径 118→174→196→206→214→225→227→232，别再用旧数），覆盖存档与能量的逐条容错、
整数溢出饱和、成就残留 id 过滤与阈值、备份导入限量、徽章映射与徽章列布局、换肤取色规则、
光晕渐变不变量、**海报版式（PosterLayoutTest）**、**±30s 边界（SeekMathTest）**，
以及把 26 个持久化键名钉死。

**工具链（2026-10-10 起）**：`.github/workflows/ci.yml`（push 自动跑测试 + 构建）、
`tools/release.ps1`（一键发版：bump → 测试 → 构建 → Release → 蒲公英）、
`tools/i18n_shot.ps1`（7 语言 × 3 页面截图巡检，cmd locale 免前台切换）、
`tools/i18n.py`（多语言同步 / 校验）。

**APK 体积：v6.1.0 = 1,316,376 bytes（1,285.5 KiB，十进制约 1.32 MB；GitHub 资产显示 1285KB）**，比 v6.0.0 的 1,288,112 bytes 多 28,264 bytes（+2.2%，来自首页第四行布局、新图标底色与两处新入口）。历史轨迹 159 KB（图标矢量化后）→ 304 KB → 505 KB（v5.1.3）→ 1,221,152（v5.1.4，一次性 +715 KB，原因是内置离线中英引导语音，见第八节）→ 1,288,112（v6.0.0）→ **1,316,376（v6.1.0）**。别再用「约 304 KB」这个数。

```bash
powershell -ExecutionPolicy Bypass -File tools\run_tests.ps1
```

**⚠️ 必须用这个脚本跑测试**，原因见第五节。

---

## 三、本轮完成（2026-10-11）：MainActivity 三步瘦身 + 海报 1:1 + GitHub 仓库优化

### GitHub 仓库（用户拍板：删 main、默认分支切 master）

- 仓库此前有**两条互不相关的历史**：旧 `main`（默认分支，停在 v3.5.0，与 master 无共同祖先）
  和开发线 `master`——访客落地页一直显示的是旧 README（还写着 159KB/60 单测）。
- 已切默认分支到 master、**删除 main**（旧历史仍可由 v1.9.0~v3.5.0 那批 tag 追溯）、
  topics = android / coloros / kotlin / oppo / screen-off、README 顶部加 CI 徽章。
- CI 只挂 master（ci.yml `branches: [master]`），与现状一致。
- 顺手核对过：keystore / local.properties 在两条历史里都未入库，无泄漏。

### MainActivity 三步瘦身（全部完成，1760 → 1099 行）

手法：**同包顶层扩展函数**（`internal fun MainActivity.xxx()`）——调用点零改动、同包免 import；
代价是被跨文件访问的成员放宽为 internal（单模块内 = 模块私有，暴露面可控）。

| 新文件 | 出走内容 |
|---|---|
| `MainSupport.kt` | `overlapMs` / `fmtDur` / `dayLabel` / `setTimerIntent`（纯计算 + 格式化） |
| `MainDialogs.kt` | 成就 / 自定义定时 / 悬浮球样式 / 语言四个弹窗 + 语言与球样式两个设置页动态值刷新 |
| `SkinGallery.kt` | `showSkinGallery` + `applySkinEverywhere` |
| `PosterFlow.kt` | 海报链路七个函数（组装 → 渲染 → 预览 → 存盘/分享）+ `SHARE_GP_PER_DAY` |

- internal 化清单：`pageStats` / `pageSettings` / `tab` / `postRefresh` /
  `refreshHomeStats` / `renderStats` / `weekSummary`。
- 顺手清理：图鉴弹窗的 `dlg` 成员字段改局部 val（点击回调从未引用它，字段纯多余）；
  出走代码里的 `this@MainActivity` 全部改为扩展接收者标签（如 `this@showSkinGallery`）。
- **页面绑定层（bindHome / bindStats / bindSettings / renderStats）刻意留在类内**——
  refreshStates 刷三页、lateinit 视图互相咬着，那是回归风险中心，不动。
- ⚠ `BarChartView` 有自己的 private `fmtDur` 成员，与 `MainSupport.fmtDur` 扩展同名**不冲突**
  （成员优先于扩展），不要"帮忙"统一。

### 海报版式声明化 + 1:1（用户拍板：只做 1:1；二维码缓做；相册策略维持现状）

- 新增 `PosterLayout.kt`：**无 Android 依赖的纯函数**。每种比例一组旋钮（KNOBS），
  帧内所有 y 从旋钮推导；纵向放不下时**先收缩精灵**（下限 0.28×W，连下限都放不下直接抛
  IllegalArgumentException，渲染线程 catch 后走纯文本退路）。字号不随比例缩放
  （两种比例宽都是 1080，缩字号会改变信息层级）。
- `PosterLayoutTest` 5 个测试：**3:4 × 3 行回归锚**（与旧手写坐标逐项一致：
  196 / 561 / 929 / 1029 / 1309 / 1355 / 1407——重构不改既有观感）、
  两比例 × 四行无重叠不变量、弹性收缩方向。
- 预览对话框顶部新增「3:4 / 1:1」切换 chips（比例记号各语言通用，不进 strings.xml；
  海报区刻意不随主题，色值就地写死——与黑背景 0xFF15171B 同一套）。
- ⚠ 位图回收三纪律（0 字节 `.pending-` 竞态真机必现过，详见 PosterFlow 注释）：
  取消才在 onDismiss 回收 / 交接给 sendPoster 的由它存盘后回收 /
  迟到的渲染结果自己回收自己——**谁也不碰 current 的所有权**。
- **真机未验证（本轮唯一只改未验的 UI）**：预览内切 1:1、1:1 成品观感、
  西语长品牌名下两种比例的顶栏让位。装 release 包后按巡检流程看一眼再发版。

### 黑幕播控升级：唤醒态剧名 + ±30 秒（同日第二轮）

**权限不对称是设计地基**：媒体键注入（切集/播放暂停，dispatchMediaKeyEvent）不需要任何
权限；剧名与 seekTo 必须走 MediaSession API，依赖**可选**的「通知使用权」。
未授权时 `queryNowPlaying()` 返回 null → 剧名/±30s 整体隐藏，媒体键照常可用——
与通知角标同一套可选契约，PRIVACY.md 无需改动。

- 新文件 `NowPlaying.kt`：会话快照 + 纯函数 `clampSeekTarget`。选会话口径 =
  优先在播、其次 `lastPositionUpdateTime` 最新（⚠ 与 finishThisEpisode 的
  「剩余最短」口径不同，两个场景，别合并）；每次查询现查现用，不缓存
  MediaController（会话更替后失效）。
- `BlackOverlay`：唤醒态剧名（媒体行上方 252dp，14sp / 0xB3FFFFFF，**两行** + 省略号
  ——用户拍板改两行，2026-10-11；向上生长不与 hint 重叠）；±30s 是两个 44dp **文本**按钮
  （"-30"/"+30"，数字语言中性，不进 strings.xml，也免了手绘矢量的形体验证），
  塞进 mediaRow 跟随「黑幕内媒体控制」开关。**五键总宽按最窄 320dp 屏算过**
  （50×3 + 44×2 + 间距 = 310dp），主键从 52 加回去就溢出——布局版的坑 11。
- **±30s 走 seekTo 不走媒体键**：KEYCODE_FAST_FORWARD/REWIND 各家视频 App
  行为不一（±10s / ±30s / 忽略），seekTo 确定性；可用性探测 =
  `actions and ACTION_SEEK_TO`，不支持整个按钮隐藏（不是置灰）。
- **连点防抖**：playbackState.position 异步更新，800ms 窗口内连点在「已应用目标」
  上累加（`lastSeekTarget`），窗口外冷启动从真实位置起算——逐点读实时位置会把
  第 2、3 下算重复。越片尾/片头由 clampSeekTarget 兜住。
- **刷新收拢**：播放键图标 + 剧名 + seek 可见性统一走 `refreshNowPlaying()` 唯一入口
  （wake / 2s 唤醒期轮询 / 媒体键与 seek 后 400ms 都调它）——坑 7 的教训原样复用。
  seek 与媒体键都会重置重锁计时（一次点按 = 用户醒着）。
- i18n：仅 2 条 contentDescription × 8 语言（cd_seek_back / cd_seek_forward），
  i18n check 300/300；单测 SeekMathTest 4 个（连点累加是 UI 状态，JVM 测不了，靠真机）。

#### 真机自动化操作教训（2026-10-11 播控验证累积，adb 验证必读）

- **悬浮球可能正好悬浮在设置开关上**：球的持久化位置 (989,873) 与「黑幕播放控制」
  开关 (914,808) 相邻，tap 开关会点中球触发 toggleOverlay（症状：截屏突然全黑）。
  **先截屏找球、拖走，再点开关**；收尾记得提醒用户球被拖到了哪。
- **Read 工具显示的截图是缩放版**（1080×2344 显示为 ~920×2000），估坐标必须换算；
  更稳的做法：`uiautomator dump` 拿控件的 bounds（黑幕 overlay 窗口的节点也在 dump 里）。
- **唤醒态 5 秒重锁会跟脚本赛跑**：任何中间计算（python 分析截图等）都可能吃掉窗口；
  「唤醒 → 点按钮」必须压进**一条 shell 命令**（`input tap …; sleep 1.2; input tap …`）。
  且唤醒态轻点=重锁、锁定态轻点=唤醒，盲点前先用 logcat 的 awake/sleep 确认状态。
- 判断锁屏别看窗口列表（指纹图标/NotificationShade 是常驻窗口），
  黑幕是否显示用 `dumpsys window windows | grep lujinyu` 数窗口。
- `am start` 拉第三方 App 的非导出 Activity 会被拒（SecurityException）——
  好在黑幕验证不需要视频 App 在前台，MediaSession 后台照样活跃。
- nudgeSeek 已带日志（nudgeSeek/seekTo），排查 seek 问题先看 logcat -s XiTing。

#### 真机首验抓到并修复的两个 bug（2026-10-11，OPPO PME110）

1. **明细行画到海报顶部**：render() 把 `cardTextTop`（卡片顶→首行基线的**偏移**）
   当画布绝对坐标用了，三行明细落在 y=68/144/220，品牌名被夹在中间。
   修复 = `y = f.cardTop + f.cardTextTop`，并在 Frame KDoc 写死「非画布坐标」。
   ⚠ 教训：PosterLayoutTest 只钉 Frame 数值、没钉消费侧——**纯函数 + 单测的
   组合验不了「Frame 被怎么用」**，视觉回归必须真机（本项目第三次应验）。
2. **chips 未选中态隐形**：未选中样式写死半透明白底 + 白字，按海报深色区设计，
   但 chips 实际落在 AlertDialog 标题区（日间白/夜间黑，跟随系统主题）——
   日间下白字直接消失；且 chip11 初始构造漏调 styleChip，靠默认黑字碰巧可读。
   修复 = 未选中透明底 + 按 `uiMode` 选文字色（深灰/浅灰）。
   ⚠ 教训：**叠在系统对话框上的自绘元素，颜色必须跟对话框主题走**；
   resolveAttribute(textColorSecondary) 拿到的是 @color 引用（tv.data=资源ID），
   直接 setTextColor 会得到 alpha≈01 的隐形色——别用，按 uiMode 选最稳。
   另：真机自动化点击对话框元素时，**每次布局高度变化（比例切换/按钮换行）
   都会让旧坐标失效，先 uiautomator dump 拿 bounds 再点**，盲点必踩坑。

### v4.0 十项（2026-10-11，用户从 15 建议清单逐项拍板）

| 项 | 落点 |
|---|---|
| 成就图标换肤 | manifest activity-alias ×5 + IconStyle.kt + 设置「图标样式」行；ColorOS 真机验证通过（切换即时/双向/不杀进程）；图标美术从 Badges 徽章路径 1:1 翻译，tools/icon_preview.py 预览 |
| 月报/年报海报 + 本地推送 | MonthReport.kt（月聚合纯函数）+ ReportPoster.kt（组装/跨月本地通知/点击直达）；**年报依赖聚合，见 HistoryStore**；服务器推送违反零网络根基（本地通知等体验，用户拍板） |
| 全量历史聚合 | HistoryStore.kt：累计/最长/听剧日集合/月聚合（prefs），明细 sessions.json 裁剪 60 天，SessionLog.add 同步累加，一次性迁移 + 备份恢复重算（派生数据不进备份）；**降级 v3.x 会看到累计缩水（已知取舍）** |
| 摸头互动 | PetView.poke()：眯眼笑 + 三颗爱心上浮，替换放电电弧（用户嫌丑）；mah_saved_fmt 死资源随删 |
| 黑幕底部布局 + 播放 App 名 | hint 移顶部居中；剧名行 = NowPlaying.displayTitle()（「B站 · 剧名」前缀） |
| 诊断反馈 | 关于页 about_diag：版本/机型/权限/崩溃 打包复制（剪贴板零网络） |
| CI/发版门禁 | ci.yml + release.ps1 补 gradle lint + i18n check（forceDarkAllowed 教训制度化） |
| 息屏建议通知 | 双路径：OverlayService tick（通知使用权精确）/ SuggestJobService 15min 兜底；开关默认关、每天一条、点击直接 ACTION_TOGGLE |
| targetSdk 36 预研 | docs/targetsdk36_黑幕兼容预研.md（风险表 + 验证计划，触发条件=上 Play） |
| 砍掉 | 双击解锁（已有类似）、桌面快捷方式（用户否）、云同步（违反零依赖） |

### 顺手修的既有问题

- `values/themes.xml` 的 `android:forceDarkAllowed` 是 v3.6.2 深色模式引入的
  **NewApi lint error**（要求 API 29，minSdk 26）——上次全量 lint 跑在深色模式提交
  **之前**，所以一直没暴露。属性在 26-28 上被框架忽略、无害，就地 `tools:ignore` 放行
  并写明理由（与第五节 MediaStore/AudioManager 同一处理惯例）。
  **教训：lintVital 只查 fatal；每个发版前应跑一次全量 `gradle lint`。**

---

## 上一轮存档（2026-10-10 深夜）：成就改为「解锁精灵身上的徽章」

### 做了什么

成就原本只是 ✅/🔒 徽章，解锁后什么都不给。现在四枚**全矢量手绘**徽章会挂在精灵左侧身上，
成就弹窗也从纯文字列表改成了带徽章的卡片。

| 成就 | 徽章 | 位置 |
|---|---|---|
| 🏃 马拉松（单次连听 ≥1h）| ⏱ 沙漏 | 第 1 格（最下）|
| 📅 持之以恒（连续 7 天）| 🔥 火焰 | 第 2 格 |
| 🎧 百次成习（100 次）| 🎯 靶心 | 第 3 格 |
| ⚡ 雷霆加冕（满级）| 👑 皇冠 | 第 4 格（最上）|

- 已解锁 = 金色渐变底盘 + 实心彩色图标 + 高光；未解锁 = 灰白底盘 + 暗色轮廓（只描边不填充）。
- 新点亮的最高那一格有 1.4s 脉冲高亮。
- **徽章列放在能量条右侧，不是 ? 按钮上方** —— 见下面「为什么」。

### 新增文件

- `Badges.kt` —— 徽章绘制层。四枚图标的路径 + 底盘 + 两种状态 + `columnCenters()` 布局纯函数。
- `BadgeView.kt` —— 单枚徽章的 View，供成就弹窗复用（与精灵身上同一套画法）。
- `dialog_achievements.xml` —— 成就弹窗布局，行由 `MainActivity.showAchievements()` 动态填充。
- `BadgesTest.kt` —— 6 个单测，把「第 1 格在最下 / 等距 / 对称」钉死。

### 三个关键决策

**① 为什么徽章列在能量条右侧，而不是叠在 ? 按钮上方**
原设计是叠在 ? 上方。实际算下来放不下：PetView 高 180dp，能量条独占 `[0.25h, 0.75h]`，
上方只剩 `0.25h`。4 枚徽章均分的话直径上限只有 11dp，手绘皇冠在里面会糊成一坨色块。
挪到能量条右侧后（那里到精灵身体之间约 250px 本来就是空的），直径能到 15dp，与 ? 按钮同量级。

**② 徽章不可点**
徽章列完整落在能量条既有点击区内（`bar.left-50 .. bar.right+70` × `bar.top-80 .. bar.bottom+60`）。
若再给徽章挂点击命中，「点徽章看成就」就会变成「误触发收取全部能量」。
所以徽章只画不点，那一片的点击语义保持不变。

**③ 皇冠路径只有一份真源**
`Badges.crownPath()` 同时服务皇冠徽章和 `PetView.drawKing()` 的金冠。
此前 `PetView` 里自己画了一份一模一样的路径，两处各画各的，改形状必然漏改一处。

### 改手绘图形前先做形体验证

手绘矢量有个特有风险：**编译通过、测试全绿，装到手机上才发现是坨看不懂的东西**。
`BadgesTest` 只能验几何算式，验不了「像不像」。

做法：把 `Badges.kt` 里的路径控制点**原样**翻译成 SVG（Canvas 的 `moveTo/lineTo/cubicTo`
与 SVG 的 `M/L/C` 语义一致），起个本地 HTTP 服务用浏览器截图，四个区块：
放大看形状 / App 实际直径 45px / 未解锁 / 未解锁实际尺寸。

```powershell
python tools\badge_preview.py          # 生成 .cowork-temp/badges_preview.svg
```

> `file://` 协议被浏览器工具拦截，得起 HTTP 服务（`portal open` + `python -m http.server`）
> 才能截图。改完图形务必重跑一次。

**这步真的抓到了三个问题**，肉眼在代码里完全看不出来：
1. 沙漏的玻璃用白色半透明描边，在金色底盘上几乎看不见 → 剩下一个橙色沙堆，读不出是沙漏 → 改深棕框 + 橙沙。
2. 靶心的箭杆起点落在靶心**内部**、拉到 1.25s → 一根斜线从红心戳穿出去 → 改短，只保留「指向」。
3. 火焰未解锁态描边 0.12s 太细，缩到实际尺寸近乎消失 → 加粗到 0.15s。

**教训：改任何手绘图形，都先跑一遍这个预览再装机。** 别拿用户手机当调试器。

### 附录：本轮顺带做的优化

| 项 | 前 | 后 |
|---|---|---|
| APK 体积 | 660 KB | **159 KB** |
| lint error | 8 | **0** |
| 单测 | 52 | **60** |
| 精灵每帧对象分配 | ~15~20 | **~0**（路径/矩形/着色器全部复用或缓存） |

**① APK 体积**：launcher 图标的 20 张密度桶 PNG 曾占整个 APK 的 **79%**（约 520 KB），
dex 才 138 KB。`shrinkResources` 早已开启却删不掉——adaptive-icon 的
foreground/monochrome 层确实引用了密度桶。

- `minSdk 26` 意味着 `ic_launcher.png` / `ic_launcher_round.png` 各密度桶**永远不会被选中**
  （`mipmap-anydpi-v26` 优先），本来就是死资源
- foreground / monochrome 改用 **VectorDrawable**。不手绘，而是用
  `tools/png2vector.py`（轮廓跟随 + 道格拉斯-普克简化）从原 PNG 的掩膜直接追踪，
  **像素级一致**，不赌肉眼。矢量 2.9 KB / 2.9 KB，对应原 PNG 287 KB / 197 KB
- 追踪前务必**渲染出来和原图并排比对**（同 `tools/badge_preview.py` 的做法）
- ⚠️ `tools/analyze_icon.py` 里手写的 PNG 解码器：**滤波回溯的「左邻」步长是 bpp 而不是 4**。
  RGBA（bpp=4）时硬编码 4 碰巧正确，RGB（bpp=3）就解出一张颜色全乱的图 —— 这种错极难发现

**② 每帧分配**：统计页精灵 30fps、悬浮球 10fps 常驻，原先每帧 new 出十几个
`RectF` / `Path` / `LinearGradient` / `RadialGradient`（每秒 400~600 个对象）。现在：

- `energyBar()` 改为写入共享 `energyScratch`。⚠️ **它一帧内会被
  `badgeColumn`/`helpCenter`/`drawBody`/`onTouchEvent` 多次调用**，
  调用方不得跨调用持有返回值（`drawBody` 里 `helpCenter()` 那行已加注释标注这个顺序约束）
- 着色器按 `(width, height)` 缓存，尺寸不变不重建；换肤时随调色板一起作废
- 光晕改成「以本点为圆心建渐变 + `canvas.translate` 绘制」——这样渐变半径与
  精灵的上下浮动解耦，才既可缓存又跟得动。
  ⚠️ 副作用是**渐变半径必须 >= 光晕圆半径**，否则光晕被截成可见的圆盘；
  这条不变量已写成 `GLOW_GRADIENT_RATIO` 常量并由 `PetGlowTest` 钉死
- 两个绘制类（PetView / BubblePetView）各自维护一套复用对象，**没有抽公共基类**——
  见第七节待办

**③ lint**：原先 8 个 error **全是设计内误报**（MediaStore 29+、AudioManager 31+，
调用点都有 `SDK_INT` 早退），但常驻 error 会让人对整个报告麻本。
已就地 `@SuppressLint` 并写明理由 —— 集中屏蔽不如紧挨代码的注解可读。
`local.properties` 的 `PropertyEscape` 走 `app/lint.xml`（它是自动生成且已 gitignore 的）。

**④ 漏翻译**：新增的三个成就弹窗字符串当时只写了中文，lint 报 MissingTranslation。
**改 `values/strings.xml` 记得同步 `values-en/`**。

### ⚠️ 两个只有真机才能发现的 bug（预览与单测全部漏掉）

**① 徽章被换肤的色相旋转污染 —— 语义完全错乱**

第一版把徽章画在 `drawBody()` 里，于是跟着精灵一起走了 `PetSkins.skinFilter()`。
真机实测：金色底盘全被转成粉橙，而**未解锁的灰白轮廓被转成橙红/绿色实心感**，
用户看到四枚全是「已解锁」的样子——实际上他只解锁了两条。

根因是 HANDOFF 第四节记过的那条特性的**反面**：色相旋转矩阵对**低饱和度**颜色伤害最大
（与「saturation < 1 会洗成纯白」同源）。成就徽章恰恰大量使用灰白这种中性色，
正好踩在伤口上。

修法：徽章移到 `onDraw()` 中滤镜**之外**单独绘制（`drawBadgeColumn()`）。
`能量条` 与 `? 图标` 仍留在滤镜内，与既有观感一致。

> ⚠️ SVG 预览验不出这个 bug —— 预览里根本没有换肤滤镜。
> **凡是涉及换肤的改动都必须在真机上看**，这是本项目第二条验证通道。

**❗ 教训：任何承载「状态语义」的元素都不能参与换肤。**
判断标准很简单——**它的颜色是否在传递信息**？传递信息的（成就徽章、临期红色警示）必须排除；
纯装饰的（精灵本体、能量条）才可以被旋转。

**② 「持之以恒」的口径与设计意图不符（待用户拍板）**

设计表写的是「**连续 7 天**」，实现却是：

```kotlin
// MainActivity.kt:1142
val listenDays = sessions.map { "yyyyMMdd" }.distinct().size   // 累计不同日期数
Achievements.evaluate(this, sessions.size, maxMs, listenDays, stageNow)
// week 条件：days >= 7
```

即「**累计** 7 个不同日期」，与界面上的「连续听剧 N 天」（`Streaks.compute().current`）
是两套口径。用户实测：连续 6 天，但火焰徽章已点亮 —— 确实不矛盾，只是容易误解。

当前代码与成就文案（`ach_week_d` = 「累计 7 个不同日期有息屏记录」）是**自洽的**，
不是实现 bug，但与设计意图有偏差。改成真·连续口径属于产品决策，未擅自改动。

⚠️ 注意 `ach_unlocked` 是**只增不减**的持久化集合：一旦点亮就永不撤销，
所以改口径不会让已点亮的徽章消失，但也不会自动纠正历史误判。

### 相关代码位置

- `Achievements.kt` —— `A` 加了 `badge: Badges.Kind`；新增 `badgeStates(ctx)` 与纯函数 `badgeList(got)`
- `PetView.kt` —— `badges: List<Badges.Badge>` 属性 + `badgeColumn()` 几何 + `drawBadgeColumn()`
  （**必须挂在 `onDraw()` 的换肤滤镜之外**，见下面 ⚠️）
- `MainActivity.kt` —— `refreshPetPanel()` 里赋值 `pet.badges`；`showAchievements()` 改成自定义布局

---

## 四、这轮改动涉及的核心概念（改代码前务必读懂）

### 精灵有两个「形态」，务必分清

| 概念 | 来源 | 含义 |
|---|---|---|
| **解锁到哪一形态** | `PetView.stageOf(成长值)` | 成长值算出来的，只能升不能降 |
| **当前显示哪一形态** | `PetForm.selected()` → 键 `pet_form` | 用户在图鉴里选 |

**踩过的坑**：`refreshPetPanel` 里曾把这两个混用一个 `stage` 变量，导致
形态行锁错、文案变成「再积累 300 成长值」（实际 7296）、进度条错、进化提示重复弹。
现已拆成 `showStage` / `growthStage` —— **改这块务必保持两者分离**。

### 换肤不再是后处理滤镜，而是绘制参数（2026-10 重构）

**旧做法**（已删除）：画完精灵 → 离屏位图 → `ColorMatrixColorFilter` 做**色相旋转**。

这个思路有两个无法回避的毛病：

1. **撞色被完整保留**。青翼、紫影、金冠各自 `+hue`，转到三个不同的地方。
   放大真机截图能直接看到「紫身体 + 青绿翼 + 绿皇冠 + 紫闪电」六种颜色互相打架。
   **换句话说，旧配色根本不存在设计意图，每次换肤都是在重新掷骰子。**
2. **状态语义被染色**。低饱和度元素（未解锁徽章的灰白轮廓、脸部的墨色、打瞌睡的灰）
   正是色相矩阵伤害最大的，一转就变成彩色，语义直接反过来。

**新做法**：色相是**绘制参数**。`PetPalette` 由一个 hue 派生整套明度阶梯
（highlight / bodyLight / body / shade / deep / accent / accentSoft / glow），
`PetView` 与 `BubblePetView` 都从它取色。

带来的直接后果：

- 一只精灵身上所有颜色出自同一个 hue，天然单色调。
- 删掉了 `PetSkins.skinFilter()`、离屏位图、`skinFilterCache`、`ColorMatrixColorFilter`、
  以及 `BubblePetView` 的 `saveLayer`。
- **不再需要任何「哪些元素不能参与换肤」的保护逻辑**——因为根本没有后处理了。
- 五官墨色等低饱和度元素在 HSV 取色下几乎不变，不需要单独保护。

**⚠️ 皮肤色相已按名字重定**（旧值是 60° 均分，与名字对不上）：

| 皮肤 | 旧 hue | 新 hue | 现在长什么样 |
|---|---|---|---|
| 星夜之魂 | 60（黄）| 245 | 深蓝紫 |
| 极光之灵 | 120（绿）| 160 | 青绿 |
| 樱雨精灵 | 180（青）| 330 | 粉 |
| 熔岩暴君 | 240（蓝）| 15 | 橙红 |
| 翡翠雷公 | 300（洋红）| 105 | 翠绿 |

用户实测反馈过「翡翠雷公是洋红色」。旧的旋转方案把这层混乱盖住了，
色相绝对化之后藏不住，所以必须重定。

### 预览与快照

- `PetSkins.snapshot(ctx, stage, skin, size, withBar, pct)` —— 离屏渲染缩略图/小组件位图。
  内部**必须置 `thumbMode = true`**，否则会把能量条和「?」图标一起画进去。
- 图鉴弹窗里两行缩略图（形态行 / 配色行）互相依赖对方，必须走 `rebuildRows()` 整体重建，
  重建时保留各自横向滚动位置。

### 五个形态的剪影（重画过一次）

初版三个形态都是「圆形堆叠」（电火花除外），眯眼分不出谁是谁。重画后：

| 形态 | 剪影 | 旧版问题 |
|---|---|---|
| 电火花 | 八角星 | 无，原版就很好 |
| 电球 | 球 + 旋转弧 | 无，原版就很好 |
| 雷云精灵 | **底部切平**的积云 + 雨帘 + 单道闪电 | 上下都是圆弧，像汉堡；和雷霆之王几乎一样 |
| 风暴之灵 | **六层错位旋转弧**叠出的旋涡 | 8 个实心椭圆堆叠，像一叠盘子 |
| 雷霆之王 | **尖锐六芒核心** + 土星环 + 头顶星芒 | 「云 + 6 片锯齿纸片翅膀 + 廉价皇冠」 |

**去贴纸化**的三条原则（改这三个形态时务必守住）：

1. **附属元素必须与主体共享轮廓**。旧翅膀是六片独立折线硬戳在两侧，
   现在换成土星环——因为环有前后遮挡关系（上半环画在核心之前、下半环之后），
   天然读作「属于这个物体」，而不是「贴在旁边」。
2. **皇冠/装饰不能浮空**。旧皇冠浮在头顶，与云之间没有任何连接。
   现在换成紧贴核心顶点的四尖星。**⚠️ 四尖星的 `ww` 必须远小于 `hh`**
   （当前 0.085r vs 0.40r），否则会退化成一个圆疙瘩。
3. **闪电起点要抬进主体内部**。起点在主体外时看着像「贴在下面」而不是「从里面长出来」。

**风暴之灵走过两轮弯路，别走回头路**：

- 二版试过「实心漏斗 + 3 条螺旋飘带」——飘带在中间**交叉成一个大 X**，
  甩出漏斗外的尾端像几根杂毛，而且实心漏斗把旋涡全糊死了。
- 现在是「极淡漏斗（alpha 95，只给体量）+ 六层旋转弧」。每层只画两段弧、
  **留出缺口**，缺口正是让下层露出来的关键——画满就成了实心。

### 持久化

26 个键全部集中在 `Prefs.kt`（`Prefs.XXX` 常量 + `Context.prefs()` 扩展）。
**键名一旦发布不能改** —— 改了等于全体用户丢失该项设置。
`PrefsKeysTest` 会把键名钉死，改名时它会报错。

有一个只读遗留键 `SHARE_BONUS_GP_LEGACY`：当前版本从不写，但早期版本写过，
老用户 prefs 里仍有值，**必须保留读取**，否则他们迁移加成静默归零。

---

### 深色模式（2026-10-10 新增）

- 颜色全部走语义 token：`values/colors.xml` + `values-night/colors.xml`。
  **新增任何颜色必须同时补两处**，否则该处夜间会拿日间色（多半刺眼）。
- 布局里不要再写 `#FFxxxxxx`；绘制代码用 `context.getColor(R.color.x)`。
- 夜间主题（`values-night/themes.xml`）父主题是 Material 深色 →
  系统弹窗（AlertDialog 等）自动整套随动。
- 刻意不随主题：悬浮遮罩 / 桌面小部件 / 分享海报 / 品牌绿卡 ——
  它们各自成套配色（理由在各自文件注释）。
- 验证开关：`adb shell cmd uimode night yes|no`（切换后 Activity 会重建）。

## 五、十三个会绊倒人的坑

### 1. `gradle test` 在这个路径下跑不了（必须用脚本）

项目路径含中文（`D:\AI任务\zcode\息屏听剧`）。Gradle 执行单测时 fork 一个测试进程，
用 `@argfile` 传 classpath，而 JVM 解析 argfile 发生在 `-Dfile.encoding` 等参数**生效之前**，
中文路径被解错 → 所有测试类 `ClassNotFoundException`。

- 已用对照实验确认（整份项目复制到 `D:\xtest` → 42 个测试全绿）
- `gradlew` 同样受影响（第一次跑要下 128MB 发行包）
- **`assembleDebug` / `assembleRelease` 不受影响**
- 根治要把项目移到纯 ASCII 路径（用户尚未决定）

### 2. PowerShell 5.1 的编码陷阱

- 改 **XML/文本文件**：绝不要用 `Set-Content -Encoding UTF8` —— 它会**加 BOM 并把 LF 变 CRLF**，
  Android 资源文件直接坏掉。用
  `[System.IO.File]::WriteAllText($p, $t, (New-Object System.Text.UTF8Encoding($false)))`
- 写 **.ps1 脚本**：反过来**必须带 UTF-8 BOM**，否则中文全变乱码、字符串终止符被吃掉、脚本无法解析
- `Out-File -Encoding utf8` 同理会给 commit message 加 BOM

### 3. 绝不要用行号拼接去改 Kotlin 文件

我两次用 PowerShell 按行号切片替换 `showSkinGallery`，**切错边界删掉了
`bindStats` / `renderStats` / `refreshHomeStats` 等真实代码**。
正确做法：先 `read` 拿到精确文本，用 `edit` 工具做定点替换。
若必须动大段，改完立刻校验关键函数是否还在。

### 4. 真机安装只能装 release 包

手机（OPPO PME110 / Android 16）上装的是**正式签名**版。
`app-debug.apk` 签名不匹配，`install -r` 会失败。
要用 `app/build/outputs/apk/release/app-release.apk`。

**手机上有用户真实数据**（约 23 小时听剧记录、7296 成长值、连续 6 天）：
- 只用 `install -r`，**绝不 `uninstall`、绝不 `pm clear`** —— 会清空全部数据
- release 包不可调试，**无法用 `run-as` 读 prefs**（验证只能靠界面表现）

### 5. 别在用户正在用手机时抢前台

我用 `adb shell input tap` 做 UI 测试时，点击落进了用户的微信聊天界面（对方正在传发票）。
**每次 tap 前先确认前台是息屏听剧**，否则应停手让用户自己点。
`dumpsys activity activities | grep topResumedActivity` 可查当前前台。

### 6. Gradle daemon 会挂住 PowerShell 管道

跑完 `assembleRelease` 之后，**Gradle daemon 进程仍然活着并继承了 stdout 句柄**。
这时再跑 `run_tests.ps1 | Select-Object -Last N`，外层会永远等不到 EOF —— 表现为
「测试跑完了但命令不返回」，很容易误判成卡死。

改成重定向到文件再看文件即可：

```powershell
powershell -ExecutionPolicy Bypass -File tools\run_tests.ps1 *> out.txt
```

### 7. 图鉴弹窗：顶部大图不跟随下方选择（已修）

上一轮「缩略图不跟随选择」的修复只改了下面两行的 `rebuildRows()`，**顶部大图漏了**：
`refreshPreview()` 只在打开弹窗时调用，两个点击回调里都没有。
症状：下面选中了形态/配色，大图和文案纹丝不动。

**现场特征**：打开弹窗时 `refreshPreview()` 后面跟着**两遍一模一样的调用** ——
典型的「改了一半」痕迹。看到重复调用就该警觉：说明有人在这里修过但没修完，
排查时优先怀疑同一函数里其它**没跟着走**的刷新点。

修法不是给两个回调各补一次调用（上次就是这么漏的），而是把 `refreshPreview()`
**收拢到 `rebuildRows()` 末尾**——只留一个调用点，以后新增交互不会再漏。

**通用教训**：多个 UI 区域共享同一份选择状态时，刷新必须挂在**唯一的重建入口**上，
绝不能散落在各个事件回调里各自调。散落式刷新漏掉一处，界面上就出现「两个区域显示不一致」。

---

### 8. 不要对资源目录做递归删除（2026-10-07 踩过）

想删图标 PNG 时顺手清空密度桶目录，写成了：

```powershell
Get-ChildItem app\src\main\res -Directory | ForEach-Object { Remove-Item $_.FullName -Recurse -Force }
```

**结果是整个 `res/` 被删光**（drawable / layout / values / xml 全没了）——
本意只是清掉空目录。

本项目所有资源都已在 git 里，`git checkout -- app/src/main/res` 一键全恢复；
但当时若有未提交的资源改动就直接没了。**正确做法**：

- 只针对明确列出的文件删（`Get-ChildItem <dir> -File -Filter *.png`），不碰目录本身
- 需要删空目录时，先把清单列出来看一眼再删
- 删完立刻 `git status` 确认只动了预期内的文件

### 9. Android string 里的撇号会被当转义符

`values-en/strings.xml` 写 `the pet's left side`，aapt 直接报
`Invalid unicode escape sequence in string`。需要写成 `pet\'s`，
或者像我这次一样改成 `the left of the pet`。**改英文文案时避开撇号最省事。**

### 10. 本项目没有 androidx，`@RequiresApi` 不可用

`@RequiresApi` / `@ColorInt` / `@UiThread` 都在 **androidx.annotation**，
而本项目零第三方依赖，编译直接报 `Unresolved reference`。
框架只提供 `@SuppressLint` / `@TargetApi` / `@IntDef` 等少数几个。
需要「声明版本要求 + 消 lint」时用 `@SuppressLint("NewApi")`。

### 11. 固定尺寸控件 + 变长译文 = 形状漂移（2026-10-08 踩过；10-09 二次修正）

悬浮球用 `minWidth=48dp` + `OvalShape` 背景 + `WRAP_CONTENT` 文字。
`OvalShape` 会**填满视图边界**，而文字宽度随语言变化：

    中文「息屏」             26dp -> minWidth 撑到 48dp -> 正圆
    俄语「Выключенный экран」 115dp -> 扁胶囊

**加语言时要逐个检查固定尺寸控件**，不能只在中文下看：悬浮球、
图标旁的标签、小部件格子、按钮。并把译文压进容量内（本例 48dp / 13sp ≈ 3 个全角字）。

⚠ **修对地方：在窗口参数上，不在 view 上**（2026-10-09 二次修正）。
第一版把 `view.layoutParams` 设成 `size×size`，真机实测仍是 93×53 扁椭圆 ——
`wm.addView(tv, lp)` 会用**窗口的 LayoutParams 覆盖掉** view 自己的
layoutParams，设了也是白设。正确做法是把 `WindowManager.LayoutParams`
的宽高写成固定 `size, size`（悬浮球实测 48dp × 3.0 密度 = 144×144px 正圆）。

### 12. 画布上两个文本分列左右时必须互相让位

分享海报顶栏左边品牌名、右边形态名。中文下「息屏听剧」才 4 字，看着很宽敞；
西语品牌名「Escucha con pantalla apagada」40f 约 650px + 形态名
「Espíritu de tempestad」36f 约 393px = 1043px，而可用宽只有 936px —— **重叠**。

画布上的固定坐标排版**不能假定文本长度**。要么先量宽度再逐档缩小，
要么两行堆叠。另：截断别用 `TextUtils.ellipsize`（要 `TextPaint`，
传普通 `Paint` 直接编译不过），用 `Paint.breakText` 按宽度算字符数。

### 13. 服务里的系统级文案不会自己跟着语言变（2026-10-10 踩过）

悬浮球/通知由常驻服务持有，而服务的 `attachBaseContext` 只在进程创建时跑**一次**，
`getString` 出来的文案在创建那刻就定死了。用户切语言后，Activity 全对、球还是旧语言。

两个对策一起上（`OverlayService`）：
- 面向用户的字符串统一走 `lstr()`（调用点现包一次 `AppLocales.wrap`，永远取当前语言）；
- 覆写 `onConfigurationChanged`，语言一变就 `refreshLocale()`（重建球/通知/渠道名）。
里程碑：语言切换不再需要重启服务，ru↔zh 实测同进程热切。

## 六、已做过的真机验证结论（别重复劳动）

OPPO PME110 / Android 16（API 36）实测确认：

- **背光真关**：`sbrt=0.0 bbrt=0.0`，且 `dumpsys display` 显示面板 2 nits（上限 490），
  截图纯黑。唤醒后恢复到 1473~1883 nits。
- **targetSdk 保持 35 是对的**：黑幕依赖 `FLAG_LAYOUT_NO_LIMITS` +
  `layoutInDisplayCutoutMode=always` + 手动隐藏 insets，升 36 会触发强制 edge-to-edge，
  很可能直接打碎显示效果。Google Play 自 2026-08-31 要求 API 36，
  但**当前渠道是酷安/应用宝/GitHub，不受此约束**。
- **唤醒锁修复生效**：退出助手后 `REL XiTing:service` 出现在 power 历史里。
- 用户 prefs 数据在多轮改动后完好（成长值/成就/皮肤/连续天数全部正确读出）。

---

## 七、其它待办（按优先级）

> 2026-10-10 核对过：lint **0 警告 / 0 错误**（build_stamp 精准放行）；测试 109；下方数字均已更新。

**高**

- ✔（2026-10-11 真机全绿）黑幕播控完整验证（B站 · OPPO PME110）：
  剧名显示（100 字带货标题一行截断 ✓）、五键一行、**单击 +30 = 精确 +30 秒**
  （logcat：pos=124426 → seekTo(154426) → B站会话立即更新为 154426）、
  **连点防抖与片尾 clamp**（219091+30000=249091 被 clamp 到总长 243133，第 2 下在
  已应用目标上累加仍被 clamp，播完自动连播）、通知使用权降级契约实测
  （授权前剧名/±30s 整体隐藏，媒体键照常）。
  ⚠ 用户手机已授权「通知使用权」（enabled_notification_listeners）。
- ✔（2026-10-11 拍板并实施，随 v3.8.0 发布）① 剧名改**两行**显示；② 设置页
  「黑幕播放控制」开关下加副标题「剧名与±30秒需开启「通知使用权」」
  （switch_media_sub ×8 语言）——用户实测时因未授权而困惑「没有显示」，静态说明前提。
- ✔（2026-10-11 真机）海报验证全部通过：3:4/1:1 渲染、多次往返重渲染
  （位图回收无崩溃）、页脚两行完整、西语顶栏两种比例均无重叠、chips 两态可读、
  语言热切换往返（中→西→中）正常。**过程抓到两个真机 bug（已修，见第三节）**：
  ① cardTextTop 被当画布绝对坐标，明细画到海报顶部——单测只钉 Frame 数值、
  不验消费侧，编译与 118 测全绿都拦不住，真机一眼看出；
  ② chips 未选中态白字落在 AlertDialog 白色标题区直接隐形（初始构造还漏上样式）
  ——修复按 uiMode 选色。教训：**凡是叠在系统对话框上的自绘元素，颜色必须跟
  对话框主题走，不能按海报深色区想当然**。
- `PetView`(1048行) 与 `BubblePetView`(389行) **各写一遍同一套 5 形态 + `drawFace`**。
- `PetView`(1048行) 与 `BubblePetView`(389行) **各写一遍同一套 5 形态 + `drawFace`**。
  改剪影必须手动同步两处（2026-10-07 吃过一次亏：图鉴大图不跟随选择）。
  ⚠ 但「抽公共渲染器」**评估后决定暂不做**（2026-10-08）：两者动画参数是刻意分开的
  （火花自转 0.3 vs 0.5、呼吸频率 3 vs 2.2，悬浮球另做了尺寸简化），合并会改变观感。
  若将来要做，先设计好「哪些参数随尺寸缩放、哪些保持各自风格」。
- ✔（2026-10-10 晚）成就弹窗（含徽章卡片）真机首验通过；弹窗点某条成就
  → 精灵对应格脉冲高亮已在 v3.6.2 实现并发布。
- ✔（2026-10-10 晚）真机验证完成：7 语言 × 3 页截图巡检（i18n_shot 实战）、
  西语海报（头部+角标）、成就弹窗首验、长按振动（logcat 实证 40ms）、德语统计复验。
- ✔（2026-10-10 拍板并实现）「持之以恒」= **连续 7 天**：条件用历史最长连续 `best ≥ 7`
  （`Achievements.shouldUnlock`，达成即追溯解锁；current 会随断签归零）。
  ⚠ `ach_unlocked` 只增不减，改口径不会让已点亮的消失。
- ❌（2026-10-11 两轮拍板后**整体归档**）**黑幕实时字幕**：
  路线②（无障碍节点读取）B站 真机实测判死——CC 字幕为自绘渲染，不在节点树；
  路线③（OCR 全场景）被用户否决——ML Kit bundled +5.5MB（343KB→6MB）破坏
  体积卖点，不值得为一个实验功能破根基。代码保留但默认惰性（无障碍未授权时
  完全不工作）：CaptionService/CaptionBus/黑幕字幕层/设置入口都在，设置页入口
  标注「实验性」。**别再提案**；若将来要复活，直接从路线③（OCR）起步。
- ❌（2026-10-11 拍板放弃）**本地视频播放器（真息屏续播，原二期规划）**：
  投入产出不成立（数周工程 + 播放界面新工程 + 解码兼容风险），产品聚焦「流媒体黑幕听剧」主线。
  README 二期规划节已改为放弃记录。**别再当跨版本大功能提案。**
- ❌（2026-10-11 拍板不做）**黑幕期间播放中断提醒（暂停检测/看门狗）**：
  主场景是「听着听着睡着」，凌晨的无声是正常收尾不是故障，振动/通知/自动撤幕全是打扰
  （自动撤幕还会恢复背光=半夜亮灯）。且零打扰的信息通道已闭环：wake() 每次解锁都实时
  查 `isMusicActive` 刷新播放/暂停图标（BlackOverlay.wake() → updatePlayIcon()），
  用户醒了点屏幕就能看到真相。**别再把它当新点子提。**
- 省电实测（被动差值版）**还没有真实数据**：需要不插电正常使用几天
  （期间做几次息屏听剧）才能验证采样真的在积累。入口：统计页「省电估算」卡片。

**中**

- ✔（2026-10-11）MainActivity 三步瘦身完成（1760 → 1099 行，见第三节）；
  **页面绑定层刻意未拆**（回归风险中心），新增功能代码一律进独立文件。
- `gradle test` 只能走镜像脚本（路径含中文）：**用户 2026-10-11 拍板不迁移路径**，
  此项永久关闭，别再提。
- ✔（2026-10-11）分享海报：1:1 比例 + 版式声明化已实施（PosterLayout）；
  **二维码缓做**（自实现路线，等 1:1 跑通后作为独立小任务）、
  **9:16 不做**（无抖音渠道需求）、相册策略维持现状（MediaStore 是零依赖下最顺路径）、
  内置字体仍待需求出现再议。原始调研见 `docs/分享功能升级方案.md`。
- ✔（2026-10-10 深夜）深色模式已发布（语义 token + values-night 全套，见第四节）。
  遗留一条：发版前跑一次全量 `gradle lint`（lintVital 只查 fatal，
  forceDarkAllowed 的 NewApi 就是这么漏进 v3.6.2 的，本轮已修）。

**低**

- VIBRATE 权限已加，但**触觉反馈是否真的被系统接受未在真机验证**
- `D:\xt_projectsym` junction（诊断实验产物）：**用户已拒绝删除**，不再处理。
- ✔（2026-10-10）lint 归零：`build_stamp` 的 3 条 UnusedResources 在 `lint.xml`
  里精准放行（regexp 只匹配 build_stamp，其他 UnusedResources 照常报告）。

---

## 八、v4.4 → v6.0 补记（2026-10-18 补写；正文此前停在 v4.3，四个主版本一个字没有）

每版详情以 `docs/` 对应记录为准，本节只做索引与口径。**表内「未发布」= 只出过候选包**。

| 版本 / code | 做了什么 | 证据文档 |
|---|---|---|
| v4.4.0 / 86 | 十项可靠性与体验：完整备份回退、自动备份管理、兼容自检、服务中断恢复、常用方案最多 8 项、历史管理 SAF+CSV、首次成功确认、性能专项、轻量个性化。未发布 | `增量开发验收记录_20261013.md` |
| v5.0.0 / 87 | `SessionCoordinator` + `TimerController` 统一会话、三场景启动、正在听控制台、16 字段诊断分享、会话结果 `session-outcomes.json`（最多 100 条 / 60 天）、备份 v4 兼容。未发布 | `v5.0开发验收记录_20261013.md` |
| **v5.0.1 / 88** | **体验纠偏＝回退**：恢复 v4.3.1 的首页/统计/设置/导航，**撤掉场景堆叠、控制台与回顾卡**；播放器只按应用名+图标呈现、名称不参与匹配；定时面板按需显示；用有限包可见性而非 `QUERY_ALL_PACKAGES` | `v5.0.1体验纠偏记录_20261013.md` |
| v5.1.0 / 89 | 黑幕内定时面板（15/30/60 与 1–600 分、片尾定时、返回画面或暂停保持黑幕）；inactive 会话保留、会话销毁不自动切换；服务受理回执；按时长与按应用保存结束动作。新文件 `PlaybackSelection.kt` `TimerRequest.kt` `CurtainTimerPanel.kt` `PlayerTimerDefaults.kt` | `v5.1.0开发与模拟UAT记录_20261014.md` |
| v5.1.1 / 90 | 修「明确选择的目标失效时 `refreshNowPlaying()` 仍允许旧媒体键」；失效播控禁用并降透明度 | `v5.1.1真机继续验收与修复_20261014.md` |
| v5.1.2 / 91 | 播放器目录合并（已安装视频应用 / 视频处理应用 / 有效会话）；**选择的包名与 token 分离，跨包不回落** | `播放器跨应用选择修复_20261014.md` |
| v5.1.3 / 92 | 删除首页可见的播放器选择，默认自动跟随唯一 PLAYING 源 | `当前播放自动跟随优化_20261015.md` |
| v5.1.4 / 93 | 内置离线中英引导语音（**APK 505 KB → 1,221 KB 就在这一步**）；无外部播放器也能完成引导；撤除关于页诊断与崩溃日志入口；音频改 `prepareAsync` | `引导声音与失败提示修复_20261015.md` |
| v5.1.5 / 94 | API 33+ 系统媒体按键目标信号，可识别被路由的 STOPPED/NONE 会话；不可靠时只写「可能来自」 | `系统播控来源识别修复_20261015.md` |
| v5.1.6 / 95 | 黑幕唤醒后的深色底部面板（竖屏 420dp、横屏 680dp 两列）、隐藏无效控制、合并定时行、「返回播放器」 | `黑幕布局优化与验收_20261015.md` |
| v5.1.7 / 96 | STOPPED/未知/错误不再误报「已暂停」；拒绝过期的绝对截止请求；耗电对照实验**移出发布前置条件** | `P0修复与兼容性验收_20261015.md` |
| v5.1.8 / 97 | ⚠ **候选包存在（`docs/升级候选/XiTing-v5.1.8-code97.apk`）但 docs 里没有对应验收记录**，属口径缺口 | — |
| v5.1.9 / 98 | 设置新增「黑幕显示睡前定时」，独立键 `black_timer_show`，新老用户一律默认关闭 | `黑幕定时显示开关与真机调试约束_20261017.md` |
| v6.0.0 / 99 | SQLite 内容库与时长统计分离、最近听过、系统分享接收与显式粘贴、黑幕标记与备注、定时起点、90 天期限与清理预览、备份 v5、手动确认后合并重复链接。已发布 | `uat-continue-listening-20261017/RESULTS.md`、`docs/releases/v6.0.0.md` |
| v6.1.0 / 100 | 通知使用权入口 0→3（首页第四行、定时弹窗「去授权」、设置页「诊断与自检」）；定时弹窗改实时重读+焦点刷新；黑幕切集键常驻+注入兜底+探针判死；防误触可配置；最近听过有标题即记；首屏 10 条；EN 电池状态位歧义修复。已发布 | `docs/uat-notification-access-20261105/RESULTS.md`、`docs/releases/v6.1.0.md` |

### 八·1 关键代码落点

- 会话与播控：`SessionCoordinator` `TimerController` `NowPlaying.kt` `PlaybackSources.kt` `CurrentPlaybackPolicy` `TimerPlaybackTarget.canArm`
- 定时与黑幕面板：`TimerRequest.kt` `CurtainTimerPanel.kt` `TimerDialogs.kt` `BlackOverlay.kt`
- 内容找回：`listening/ListeningStore.kt`（`listening.db`，外键开事务）`ListeningRecorder.kt` `ListeningActivity.kt` `ListeningPolicy.kt`，及包外的 `ListeningPresets.kt` `ListeningScenes.kt` `ListeningPresetRules.kt`
- 备份：外层格式 v5、内容库 schema 1；**锁顺序固定 BackupManager → SessionLog → 内容库**；回退快照 `restore-rollback.json`；旧偏好快照**不突破 2 MiB**，外部备份上限 20 MiB、库 payload 16 MiB，超限失败而不截断

### 八·2 这些还没被验证过（别当已验收，全部是各文档自己的原话口径）

- 抖音内容找回 v6.0 本轮**未重新验证**（v5.1.2 那次模拟器崩溃是运行环境受阻，不能算不兼容，也不能算通过）
- 首屏：**500 ms 是 OPPO 真机目标；手机大库尚未实测**。2026-10-20 起「最近听过」每页 10 条（`ListeningStore.PAGE_SIZE`），设备检查只记录模拟器同口径基线并写明渲染方式，不再在模拟器上断言手机指标。改前 50 条实测：host GPU 1150/1942/1596 ms、swiftshader 2105–5792 ms、纯查询 3–23 ms；真机当时约 5 条，`Displayed +49 ms`，不能外推到大库。改后 10 条（host GPU）：冷启 1071–1103 ms，热启两轮中位 553/575 ms（单次 547–658 ms），仍高于 500 ms，只能当回归基线。
- **10k 条库的自动备份会拖慢下一次打开列表**：每次离开页面 `onStop` 都提交 `BackupManager.save`，整库 JSON 导出排在共享单线程队列里；2026-10-20 实测下一次列表 `Displayed +16s294ms` / `+10s981ms`，关掉 `auto_backup_enabled` 后热启回到 547–658 ms。已给 `recoverPending` 加“无 journal 直接返回”的快路径，避免正常启动等备份锁；**列表查询仍会排在备份后，未修**，因为动的是备份安全路径。
- 爱奇艺 v6.0 用的是**旧样本链接**，本轮没有重新采集它的分享字段
- 所有「已暂停 / 声音停止」都只代表播放器反馈，**实际听感需用户确认**；模拟器无声，不构成连续性证据
- 真人新手验收、过夜、跨厂商后台策略、来电场景仍是 pending
- lint：**0 错误 / 76 警告**（历次 25→32→60→67→69→74→76，一直在涨，没人收口）

### 八·3 v5.x 期间新增的坑（补进第五节那份名单）

14. **OPPO 系统分享以缩放小窗打开，UiAutomator 返回的是未缩放坐标**——首次自动点击误触了「取消」。误触不算通过，改用实际截图坐标才完成。
15. **UiAutomator 在第三方页面切换时偶发 null root**——不要拿陈旧截图或旧 XML 当成功证据；删掉本地与设备上的旧文件再等目标节点。
16. **ADB 键盘输中文会被 IME 自动替换**，导致输入悄悄错。需要写文本的用例改走显式剪贴板粘贴。
17. 音频同步 `prepare` 阻塞主线程实测超 6 秒——一律 `prepareAsync`。
18. 构建与第三方 App 并行会把主机内存压干；分开跑并回收本轮启动的模拟器与 Gradle daemon，不要把环境故障算成产品崩溃。
19. 亚秒级 deadline 夹具在真机上不可靠，定时用例改成 10 秒；系统时间回拨会丢会话摘要，墙钟与本地记录要分清。
20. API 26 上用 shell 直接写媒体权限会被系统纠正——只能走系统通知授权/撤权。
21. **全局 PLAY_PAUSE 不能冒充「对原目标暂停」**（这是 v5.1.1 那个 bug 的根）。
22. PowerShell 保留变量名 `HOME` 会静默出问题；手机停在统计页时按首页按钮找不到，先回首页再点。

---

## 九、版本历史缺口：v4.4 → v5.1.9 的源码从未入库

实测出来的，不是猜测：

- `git tag` **止于 v4.3.1**；v5.0.0 / v5.1.x / v5.2 / v6.0.0 **都没有标签**。
- `app/build.gradle.kts` 里 versionCode **从 85 直接跳到 99**（提交 `3b5db09`，单提交 279 文件、+10,925 / −2,687）。**code86–code98 共 13 个候选包没有任何对应提交**。
- 这 13 个包只存在于两处：`docs/升级候选/*.apk`（被 `.gitignore` 的 `*.apk` 排除）和蒲公英构建号。

**后果**：现在无法 checkout 出 v5.1.9 再构建一次；真出现「某个开关是哪一版改坏的」这种回归，`git bisect` 在 85→99 这段是瞎的，只能靠 `docs/` 里的人工记录反推——而上面 v5.1.8/code97 恰好连记录都没有。

**2026-10-20 已执行**：`v6.0.0` 已补打到 **`3b5db09`**（`release: v6.0.0 ...`）——现有标签都落在代码提交上而不是 docs 记录提交（v4.1.0→db13418、v4.2.0→8124476、v4.3.0→cd0f2e6、v4.3.1→1c85ac2，核对过）。**流程规则（自本日执行）**：凡对外发过的包，必有一次提交和一个标签；候选包本身不用入库，提交是免费的。code86–98 的历史缺口保留记录，**不补造提交**。

---

## 十、2026-10-18 拍板（防再提案）

- ❌ **待听队列 / 稍后听**：用户判断**作用不大**，直接取消，连同 `plans/2026-10-17-continue-listening-roadmap.md` §9.1 为它安排的「小样本使用验证」一起作废。**别再把它当 v6.x 的下一个功能提。**
- ⏸ **鸿蒙 / iOS 端口移植**：当前暂停。已核实的关键事实：拦路的是**华为 AGC 的受限权限审批**而不是系统代码（`SYSTEM_FLOAT_WINDOW` 在 OpenHarmony 的 `permission_definitions.json` 里**没有 deviceTypes 限制**，66 个权限显式排除 phone 而它不在其中），所以「技术不可行」这个说法要改口径；唯一可能翻盘的缝是 `WINDOW_TOPMOST`（normal + NORMAL，自声明即得，无需审批），但官方把它描述为 "above all the other windows **of the application**"，真正的跨应用置顶 `setTopmost` 是 `@systemapi` → **必须装一次机才能判死，文档判不死**。iOS 三根支柱全无公开 API，维持「不移植」。
- ⚠ **交接文档口径**：本文件此前停在 v4.3，「6800 行 / 118 单测 / 304 KB」三个数字全过期。**改版本时顺手回写本节的数字，别再攒四个版本**。
