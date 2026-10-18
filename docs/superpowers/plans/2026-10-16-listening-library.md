# 听剧记录库 Implementation Plan

> 2026-10-16范围调整：用户质疑整理功能价值并同意先验证“记录后能否找回”。本文件保留为历史方案，暂不照原八项任务开发。搜索后置、收藏简化为“保留这条”、合集移出首版；分享保存仅在找回价值成立时纳入。当前执行以`2026-10-16-content-return-probe.md`为准，用户要求先在模拟器安装真实App验证，不等待手机。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变原听剧操作的前提下，实现本机内容记录、分享保存、标记、整理、找回及可控清理。

**Architecture:** 内容库使用独立原生SQLite，继续保留SessionLog/HistoryStore的时段统计。可靠媒体快照和用户分享作为两类输入，经记录服务落库；清理、整理、备份统一经过库接口，避免UI直接写数据。

**Tech Stack:** Kotlin / Android原生View与SQLiteOpenHelper / compileSdk36、minSdk26、targetSdk35 / JVM及现有Instrumentation验收框架。无新增三方依赖。

**Spec:** `docs/superpowers/specs/2026-10-16-listening-library-design.md`

## Global Constraints

- 专注辅助其他视频App；不增加播放器、账号、云同步、录音、AI摘要。
- 点击悬浮球默认只开黑幕；不增加手动选择当前播放器。
- 普通记录默认90天，可选30/180天/永久；保护项长期保存，具体条件见Spec。
- 黑幕时段不等于实际听完，未知信息不补造。精确续播不作为本版承诺。
- 内容库清理不修改成长、成就和累计统计。sessions.json对外格式保持不变。
- 备份v5兼容v1–v4；恢复和回滚必须覆盖内容库。D批完成前不对外发布新内容库功能。
- 不全量暂存、不重置当前脏工作区；开发前列出已存在改动并保存基线清单。只提交本轮可归属文件，混合历史修改不能直接整体提交。
- 本计划只编制文档；已有v5.1.7发布授权独立保留，不等同本次大版本已开发或已发布。

## Review Focus

- 过期清理与新增保护同时发生：提交时重新判定，不能删掉刚收藏的内容（Task2）。
- 来源/标题刷新或换集：标记保留原内容身份，不能关联到新视频（Task4/5）。
- 非法分享/超长文本/危险链接：预览后保存，取消不写入，不执行外来scheme（Task3）。
- 旧记录重复迁移、老备份缺新字段：幂等、不造内容、不清空新库（Task1/7）。
- 恢复中断和大库：旧数据可回滚，不死锁、不依赖现有2MiB单JSON快照（Task7）。

## 批次与依赖

| 批次 | 任务 | 可检查成果 | 进入下一批条件 |
| --- | --- | --- | --- |
| A | 1–2 | 数据库、旧时段迁移、保留和清理规则 | 数据完整性及删除保护测试通过 |
| B | 3–4 | 分享保存、自动记录 | 分享取消/异常、换源/换集不污染记录 |
| C | 5–6 | 黑幕标记、搜索/收藏/合集/详情/找回 | 四页交互评审通过，真实操作闭环通过 |
| D | 7–8 | 备份恢复、性能、升级及真机UAT | 同一签名APK完成声明范围验收 |

建议当前会话由主Agent顺序实现；核心任务共享模型、清理及恢复契约，不拆成同时修改同一状态的并行开发。不承诺未经验证的固定工期，每批验收后调整剩余范围。

## 文件地图

新增主代码目录仍为`app/src/main/java/com/lujinyu/xiting/`：
- `ListeningRecord.kt`：模型、信息来源、保护判定。
- `ListeningLibraryDb.kt` / `ListeningLibraryStore.kt`：SQLite结构、事务与分页。
- `ListeningRetention.kt`：保留规则、清理预览及提交。
- `ListeningShareParser.kt` / `ListeningShareActivity.kt`：分享解析与确认。
- `ListeningRecorder.kt`：媒体快照、时段分段及标记输入。
- `ListeningLibraryActivity.kt` / `ListeningRecordDetailActivity.kt` / `ListeningLibraryDialogs.kt`：列表、详情、合集与清理UI。
- `ListeningReturn.kt`：按实际信息提供返回动作。
- `ListeningLibraryBackup.kt`：版本化导入导出和库快照。

已有文件只在任务列明的接入处修改。新增资源`values*/library_strings.xml`、记录列表/详情布局；同名测试放在现有test/androidTest包下。实现时如需更改接口先同步本计划，避免任务间签名漂移。

### Task 1 — 数据模型、事务存储与旧时段迁移

**Files:** Create ListeningRecord.kt, ListeningLibraryDb.kt, ListeningLibraryStore.kt; Read SessionLog.kt, HistoryStore.kt; Test ListeningLibraryDeviceChecks.kt。

**Interfaces:** `RecordOrigin { AUTOMATIC, SHARED, LEGACY }`; `ListeningRecord(id:String, createdAt:Long, updatedAt:Long, startedAt:Long?, endedAt:Long?, sourcePackage:String?, sourceLabel:String?, contentKey:String?, title:String?, contentUrl:String?, origin:RecordOrigin, note:String, favorite:Boolean)`；`ListeningMark(id:String, recordId:String, createdAt:Long, positionMs:Long?, note:String)`。库提供`save(record):String`、`get(id):ListeningRecord?`、`page(query:String, beforeId:String?, limit:Int=50):List<ListeningRecord>`；排序createdAt DESC、id DESC，beforeId解析对应游标。迁移键单独唯一索引，不依赖标题。

- [ ] 编写原生失败用例：稳定ID更新不重复、相同标题不同内容保持两条、非法父ID标记拒绝、旧时段重复迁移只一份。
- [ ] 注册模拟器专用入口`flowPart=library_store`，运行确认失败；不在真机注入测试数据。
- [ ] 实现记录、标记、合集和关系表；事务、外键、创建时间/来源索引；工作线程访问。迁移旧时段不改原文件/聚合，迁移完成标志避免删后重建。
- [ ] 重跑原生用例，验证写入失败不覆盖原库、数据库重开后一致；保存结果。

### Task 2 — 保留策略与可审查清理

**Files:** Create ListeningRetention.kt; Modify ListeningLibraryStore.kt; Test ListeningRetentionTest.kt, ListeningCleanupDeviceChecks.kt。

**Interfaces:** `RetentionPeriod { DAYS_30, DAYS_90, DAYS_180, FOREVER }`；`RetentionCandidate(id:String, baseAt:Long, protected:Boolean)`；`expiredIds(items:List<RetentionCandidate>, period:RetentionPeriod, now:Long):Set<String>`；`CleanupPreview(ids:Set<String>, protectedCount:Int)`；`CleanupResult(deleted:Int, skippedProtected:Int)`；`previewCleanup(period,now):CleanupPreview`、`commitCleanup(preview,includeProtected:Boolean=false):CleanupResult`。明确选中删除和清空全库必须由UI确认后传true。

- [ ] JVM失败用例：90天边界等于截止时间删除，晚1ms保留；永久不删；五类保护条件逐项保留；最后一个条件移除仍需下次清理流程。
- [ ] 实现纯策略与清理事务；提交重新查询保护状态，级联删除标记/关系，不删除合集与统计。
- [ ] 原生用例：预览后新增收藏/备注不删；批量事务中断不产生半删；清空库后原统计与成长不变。
- [ ] 运行JVM及原生清理检查通过。自动清理首次预览、恢复后预览、24小时频率由UI/调度层接入并验证；活动段不清理。

### Task 3 — 接收分享并明确保存

**Files:** Create ListeningShareParser.kt, ListeningShareActivity.kt; Modify AndroidManifest.xml; Test ListeningShareParserTest.kt, ListeningShareDeviceChecks.kt。

**Interfaces:** `SharedContent(title:String?, text:String, url:String?)`；`parseSharedContent(text:String?, subject:String?):SharedContent?`。输入文本上限32KiB、标题上限500字符、备注上限4000字符；超限提示修改，不静默截断。多个HTTP(S)链接让用户选择一个；无链接也可保存文字。URL去首尾空白，不删查询参数或改变短链，链接最长8192字符。

- [ ] 先展示分享预览交互草图，连同Task6其他三页收集一次视觉评审。
- [ ] 编写并运行失败用例：空输入、超限、危险scheme、多个链接、中文文本、同链接重复分享。
- [ ] 实现仅text/plain ACTION_SEND接收、预览、编辑、保存和取消；不读取剪贴板、不访问网页、不信任文本里的App名。只有确认保存才写入SHARED记录。
- [ ] 模拟器验证取消零写入、重复分享不覆盖备注、异常Intent不崩溃。真机逐一记录各视频App实际分享字段，不预设都能拿到链接。

### Task 4 — 自动记录与可靠分段

**Files:** Create ListeningRecorder.kt; Modify BlackOverlay.kt, OverlayService.kt; Read NowPlaying.kt, CurrentPlaybackPolicy.kt; Test ListeningRecorderTest.kt, ListeningRecorderDeviceChecks.kt。

**Interfaces:** `ContentSnapshot(sourcePackage:String?, sourceLabel:String?, contentKey:String?, title:String?, positionMs:Long?, reliable:Boolean)`；`begin(runId:String,at:Long,snapshot:ContentSnapshot)`、`observe(runId,at,snapshot)`、`end(runId,at)`；同runId和分段键幂等。可靠性要求复用已有能力判断，不能由标题非空推断。

- [ ] 先写失败用例：重复end只一条、可靠内容改变分段、未知更新不改旧标题、暂停不计为听完、演示体验不进入用户库。
- [ ] 实现工作线程事件队列、活动段检查点和进程中断恢复；黑幕渲染与音频操作不等待数据库写入。
- [ ] 来源未知仅保存时段；同标题不同可靠身份分段；标题变化但无可靠身份不猜换集。崩溃恢复结束在最后可信检查点，标注中断，不补算未知时间。
- [ ] 原生验证换源/换集/连续黑幕/服务退出及进程恢复；保留旧SessionLog单次结算、不重复累计。

### Task 5 — 黑幕随手标记

**Files:** Modify BlackOverlay.kt, ListeningRecorder.kt; Create library_strings.xml各语言; Test ListeningMarkDeviceChecks.kt。

**Interfaces:** `addMark(runId:String,at:Long,snapshot:ContentSnapshot):String`返回稳定标记ID；相同事件ID重试不重复，用户连续两次点击可生成两条。标记自动保护父记录。

- [ ] 失败用例：换集前标记不能随刷新移动；无可靠位置保存null；保存失败不显示成功；演示模式不留记录。
- [ ] 增加一个“标记”入口，保存当时快照；工作线程落库后短暂显示“已标记”，不自动弹输入法、抢焦点或重置定时。
- [ ] 真机确认声音连续，验证横屏、大字体、5秒重锁和返回；备注留在详情编辑。

### Task 6 — 记录页、整理、找回与清理入口

**Files:** Create ListeningLibraryActivity.kt, ListeningRecordDetailActivity.kt, ListeningLibraryDialogs.kt, ListeningReturn.kt, res/layout/activity_listening_library.xml, activity_listening_record_detail.xml; Modify MainActivity.kt, res/layout/page_home.xml, AndroidManifest.xml; Test ListeningLibraryUiDeviceChecks.kt, ListeningReturnTest.kt。

**Interfaces:** `ReturnAction { OPEN_CONTENT, OPEN_APP, COPY_INFORMATION }`；`resolveReturn(record:ListeningRecord,installed:Boolean):ReturnAction`。库补充`setFavorite(id,value)`、`updateNote(id,note)`、`createCollection(name):String`、`setCollections(id,collectionIds:Set<String>)`；合集名1–50字符、同名提示复用，不静默生成重复项。

- [ ] 先完成记录列表、详情、分享预览、清理确认四页交互草图并取得用户评审；保持三个页签与主按钮语义。
- [ ] 用失败用例固定：有HTTP(S)链接为OPEN_CONTENT，仅有已安装来源为OPEN_APP，否则COPY_INFORMATION；包不可用/链接处理失败提供友好提示，不删除内容。
- [ ] 实现分页、300ms搜索防抖、收藏、合集、备注与编辑；结果不显示包名为主要内容，未知标题可编辑。
- [ ] 接入Task2预览和期限选择，展示总数、受保护跳过数、实际删除数；选删保护项和清空全部单独确认。移除最后保护条件时提示恢复期限管理。
- [ ] 原生检查空列表、搜索无结果、连续分页、批量操作、危险链接、大字体、横屏、浅深色与长译文；界面失败不能显示底层异常。

### Task 7 — 备份v5、迁移、导出与恢复回滚

**Files:** Create ListeningLibraryBackup.kt; Modify BackupManager.kt, RestoreSnapshot.kt, BackupSummary.kt, BackupFlow.kt, BackupManagement.kt; Test ListeningLibraryBackupDeviceChecks.kt。

**Interfaces:** `exportLibrary():JSONObject`、`validateLibrary(payload:JSONObject):ValidatedLibrary`、`replaceLibrary(validated:ValidatedLibrary)`、`captureLibrarySnapshot(dir:File)`、`restoreLibrarySnapshot(dir:File)`。ValidatedLibrary包含校验后记录、标记、合集及关系；未知schema拒绝，关系悬空拒绝。库JSON schema=1，外层备份v=5。

- [ ] 先写失败用例：v1–v4无library不清现库；v5完整往返；同ID不重复；坏关系/新schema先拒绝且全数据不变。
- [ ] 扩展恢复私有快照目录及阶段journal，记录库逻辑快照；统一锁顺序，启动时先恢复未完成事务。不得简单将全库添加进原2MiB JSON。
- [ ] 在用户确认全量恢复前展示库数量与“旧备份可能找回已删除记录”；恢复缺library时保留当前库。恢复成功后暂停自动清理至预览完成。
- [ ] 逐阶段注入失败：库替换前/后、偏好/sessions写入、journal结束；重启均恢复一致基线。验证撤销恢复也包含库。
- [ ] 实现用户主动JSON/CSV导出；CSV正确转义换行/引号且防公式执行（以=,+,-,@起始文本加前置单引号），JSON保持原始文本；公开导出前说明包含标题、链接与备注。
- [ ] 验证1万记录2万标记完整备份/恢复，容量检查发生在改数据之前；临时文件有成功/失败清理路径。

### Task 8 — 完整UAT与大版本交付

**Files:** Create docs/uat-listening-library-20261016/acceptance.json、compatibility.md、UAT.md; Modify README.md, PRIVACY.md, app/build.gradle.kts仅在候选定版时。

- [ ] 运行`powershell -ExecutionPolicy Bypass -File tools/run_tests.ps1`，预期0失败/错误；运行`python tools/i18n.py check`，预期无缺失；运行Gradle `assembleDebug assembleDebugAndroidTest assembleRelease lintDebug`，预期成功、无新增未解释错误。
- [ ] 通过现有PetDeviceTest注册并顺序执行library_store、library_cleanup、library_share、library_recorder、library_marks、library_ui、library_backup；每组要求PASS并归档结果，不把合成数据计为真机兼容。
- [ ] 验证1万记录2万标记分页和搜索目标≤500ms，查询与I/O不在主线程；性能fixture仅模拟器/隔离测试库，禁止写入用户手机真实库。
- [ ] 在真实手机用抖音、哔哩哔哩、爱奇艺完成“播放→黑幕→标记→结束→记录查看→整理→打开来源/内容”；记录App版本、内容类型、实得字段和失败路径。至少核验一个有链接与一个无可靠进度路径。
- [ ] 用隔离设备验证旧版覆盖升级、重复启动迁移、备份恢复、删除保护、进程中断；真机覆盖安装保留原统计/成长/引导状态，不清用户数据。
- [ ] 用户确认黑幕声音连续、标记易用、四页布局可理解；工程、兼容性、体验认可分别留证。
- [ ] 同一签名候选APK记录版本、SHA256、签名与已支持范围；未通过项目列pending，不宣称万能续播。发布沿用用户授权，但不得运行会漏提交当前脏改动的一键脚本。
- [ ] 清理本次测试临时文件与合成记录；历史报告、用户记录及失败证据保留。

## 计划自审与执行入口

- 已覆盖：本机免登录、自动/主动保存、标记、收藏/合集/搜索、找回分级、90天与保护项、清理入口、旧统计不受影响、备份与回滚。
- 关键技术风险有对应门槛：可靠内容身份Task4、分享字段Task3、清理并发Task2、大库恢复Task7。字段不足采用Spec内降级，不现场追加敏感权限。
- 本轮只落设计与计划；下一步先审阅两份文件并完成四页交互草图，不直接跳到产品代码。建议采用当前会话顺序执行，最终做独立审查。
