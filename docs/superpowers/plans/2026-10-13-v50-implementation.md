# v5.0 安心听剧 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 完成已确认v5.0六模块的原生候选及模拟器验收。
**Architecture:** SessionCoordinator统一状态与结束事件，现有服务负责Android执行。独立结果存储与UI消费者通过稳定接口集成，不重写播放器与成长系统。
**Tech Stack:** Kotlin、Android原生View、JUnit、现有Instrumentation，无新增运行依赖。
**Spec:** ../specs/2026-10-13-v50-peaceful-listening-design.md

## Global Constraints

- min26/target35/compile36，离线、八语言、浅深主题；不保存媒体token或剧名到摘要。
- 保留现有未提交工作与v4.4候选，原地连续开发，不暂存/提交不明文件。
- 用户明确“进行开发”授权本计划执行；本计划落实已批准设计，不再次请求过程性审批。当前会话执行，独立存储与UI任务可按dispatching-parallel-agents技能并行。
- 发布、真机、真实耗电与真人测试不作未经实测的完成声明；默认模拟器优先。

## Review Focus

- 同一结束事件重复回调不能重复结算或写两条结果。
- 启动与停止竞态不能在前台提升前直接停服务。
- 未确认暂停不写成暂停成功；旧目标失效不改发新播放器。
- 进程重建不能重放定时或伪造最后停止时间。
- 新文件恢复失败必须全量回滚，不损失原成长/聚合。

## Task A 会话核心（主Agent）
Files: 新SessionCoordinator.kt、SessionRuntime.kt；修改OverlayService.kt、TimerPlaybackTarget.kt；测试SessionCoordinatorTest.kt。
Interface: enum AssistantPhase/CurtainPhase/PlaybackReport/TimerPhase/SessionEndReason/PauseReport；data SessionSnapshot(id:String,assistant,curtain,playback,timer,remainingMs:Long,pauseKeepBlack:Boolean,scene:String,pause:PauseReport)；object SessionRuntime.snapshot():SessionSnapshot, publish(ctx,snapshot)；SessionCoordinator以纯函数/串行事件处理实际状态。
- [x] RED：重复结束、未知播放、取消定时、请求/确认区分、重建测试失败。
- [x] GREEN：实现模型；接入实际遮幕/服务/定时回调，保留既有结算。
- [x] 提取TimerController持有原有定时字段与策略，旧公共方法代理；暂停观察上限5秒。
- [x] 所有展示入口使用共享快照；JVM及原生回归。

## Task B 结果存储和v4备份（独立工作）
Files: 新SessionOutcomeStore.kt；修改BackupManager.kt、RestoreSnapshot.kt；新增纯编解码/设备测试。
Interface: data SessionOutcome(id:String,startedAt:Long,endedAt:Long?,scene:String,reason:String,pause:String)；SessionOutcomeStore.list(ctx):List<SessionOutcome>, record(ctx,item), encode(ctx):JSONArray, restore(ctx,rows:JSONArray), clear(ctx)；校验reason/scene/pause白名单，最多100条/60天，未知结束null，不记录token/剧名。
- [x] RED→GREEN覆盖校验/去重/上限/旧数据缺失保留。
- [x] v4备份与回退包含新文件；v1–v3仍兼容；恢复助手运行保护不变。
- [x] 新结果不修改SessionLog/成长聚合；提交失败回滚设备用例。

## Task C 场景与界面（独立工作）
Files: 新ListeningScenes.kt、SessionConsoleUi.kt、v50_strings.xml八语言；只修改UpgradeUi.kt/可靠性入口绑定，不修改主服务与存储。
Consumes: SessionRuntime.snapshot()、OverlayService现有timerRemainingMs/timerKeepsBlack/extendTimer/cancelTimer、现有showTimerOptions/queryNowPlaying/hasMediaAccess。
Produces: MainActivity.bindSessionConsole(), refreshSessionConsole()；SessionConsoleUi.summary(ctx,snapshot):String供通知/组件复用。
- [x] 场景启动只填入/确认既有面板，持续听显式取消旧定时；启动前重查能力，不自动遮住App。
- [x] 首页本次状态、结果反馈、调整入口；统计可选上次摘要，成长复用；设置入口分组。
- [x] 实际页面/弹窗测试覆盖缺权限、无目标、取消、大字体。

## Task D 诊断与稳定性（主Agent）
Files: 新SessionDiagnostics.kt、WakeLockMetrics.kt；修改SupportDiagnostics.kt、WidgetData.kt及服务通知。
- [x] 用户预览后系统分享诊断，字段白名单、不含标识/剧名/token/路径。
- [x] 唤醒锁状态与时长基线；无真机测量前保留现有保活策略，不声称节电。
- [x] API26/33/35兼容性表与未测边界。

## Task E 集成验收与交付（主Agent）
- [x] 版本5.0.0/code87；v4.4候选保留。
- [x] 完整单测、debug/androidTest/release/lint；串行构建避免Windows文件锁。
- [x] API26/33/35旧核心、新会话/场景/摘要/恢复原生测试；八语言主题/大字体布局。
- [x] 独立代码审查，修复实质问题；签名包安装启动/实际点击。
- [x] 更新README/隐私、开发验收账本与候选文件/hash；清理本轮临时文件，不自动发布。

## Ledger

2026-10-13 Ruling: 在现有脏候选工作区连续开发 — 新worktree不会包含已验证的未提交v4.4基线 — 所有改动单独留证，不重置或提交未知文件。
2026-10-13 Ruling: 执行用户明确开发指令，不再重复询问计划执行方式 — 用户已审阅设计并要求开发 — 当前会话统筹，真正独立任务并行。

2026-10-13 Ruling: TimerController集中定时状态与原目标生命周期，既有Android调度仍由服务执行 — 避免无验收收益的整体重写 — 后续需更多复用时再提取调度接口。
2026-10-13 验收：196单测通过，API26/33/35原生流程与核心回归证据已保存；最终API26/33复跑通过，API35正式签名包另做入口验收。真机耗电/厂商ROM/外部播放器/真人测试未执行，未发布。详见docs/v5.0开发验收记录_20261013.md。
