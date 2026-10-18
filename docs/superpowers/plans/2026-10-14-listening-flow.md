# 播放器内听剧流程 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline, task-by-task. Preserve the existing dirty development baseline; no automatic commit, publish, or phone install.

**Goal:** 实现稳定选源、服务确认定时、黑幕内配置、统一结果与模拟UAT，保留v4操作骨架。

**Architecture:** PlaybackSelection保留用户明确选择的控制器及失效状态；查询区分权限/空/失效/错误。服务统一处理TimerRequest并通过内存回调反馈TimerAcceptance，UI成功后才关闭。黑幕使用现有窗口内的嵌入式定时编辑区，配置期间暂停重锁；定时目标仍独立持有原会话。

**Tech Stack:** Kotlin / Android View / MediaSession，min26、target35、compile36；无新增依赖和权限。

**Spec:** `docs/系统推演与主版本升级建议_20261014.md`，用户于2026-10-14明确授权开发和UAT模拟。实体机当前未连接，因此P0真实播放器探针与后续真机门槛保留待验；通用实现以模拟候选交付，不宣称哔哩哔哩兼容已修复。

## Global Constraints

- 不改变主按钮启动/停止、三个页签、精灵位置、成长/时长/成就/备份规则和退出手势。
- token只在内存存在；显式选源失效不回退另一个应用；撤销权限后不使用旧控制器。
- 定时确认复查目标与媒体身份；拒绝保留原定时和输入。每次请求都有唯一结果，关闭的UI不被迟到回调重开。
- 结束结果区分请求、确认、未确认；黑幕下不弹亮屏提示。
- 不改变未经真机验证的持锁策略；性能计数不作为耗电结论。

## Review Focus

- 显式目标暂时inactive但仍有效，与destroyed必须区别。
- 设置前后媒体变化、双播放器、权限撤销，不能误发。
- 对话框/Activity销毁后反馈，以及连续提交/取消时的结果归属。
- 黑幕编辑时的重锁、手势吞噬、窄屏/横屏/大字体与返回出口。
- 进程恢复不重放旧目标；已有备份/成长与单调时钟契约保持。

## Task 1: 选源与可观察失败

Files: PlaybackSources.kt、NowPlaying.kt、PlaybackSourceObserver.kt、UpgradeUi.kt；新增PlaybackSelection.kt、ListeningFlowDeviceChecks.kt。

Interfaces: `PlaybackSelection.select(controller)`、`token`兼容既有入口、`resolve(active):MediaController?`、`clear()`；`Context.playbackLookup():PlaybackLookup`区分AVAILABLE/NO_ACCESS/EMPTY/SELECTED_UNAVAILABLE/ERROR。

- [x] 原生用例先复现：显式目标销毁后另一个会话仍活跃，queryNowPlaying必须为空（旧代码应失败）。
- [x] 实现控制器生命周期及只在有效授权下使用缓存；inactive保留，destroyed失效；选源列表可显式重选。
- [x] 验证暂时inactive仍控制原目标、destroyed不误控、权限撤销不复用；首页必要空态给可理解下一步。

## Task 2: 服务受理定时

Files: 新增TimerRequest.kt、修改TimerDialogs.kt、OverlayService.kt；新增原生服务/面板检查。

Interfaces: `TimerRequest(minutes,endAt,token,episode,pauseKeepBlack,mediaSignature)`；`TimerAcceptance(accepted,errorResource)`；`TimerRequests.submit(context,request,callback)`通过ResultReceiver转发，由`handleSetTimer`返回结果。

- [x] 原生用例复现无效目标拒绝时原定时不变；新结果路径RED后实现。
- [x] UI不写偏好后就假定成功；受理后才保存选择/偏好和关闭，拒绝恢复可编辑状态。
- [x] 原生UAT实际选择预设/结束动作、点击确定、等待反馈，再验证服务deadline/目标；编辑无效值、取消、销毁。

## Task 3: 黑幕内定时

Files: 新增CurtainTimerPanel.kt，修改BlackOverlay.kt、OverlayService.kt、资源与原生用例。

Interfaces: `CurtainTimerPanel(context,onClose,onAccepted)`使用相同TimerRequest与选择契约；服务进入黑幕前保留有效来源；BlackOverlay编辑期间暂停5秒重锁，关闭后恢复。

- [x] 原生用例先断言black_timer_setup入口存在并可打开，旧代码应失败。
- [x] 使用现有窗口内面板：15/30/60/本集，1–600自定义通过步进；目标应用名称、结束动作、反馈和确定/取消。保留返回视频出口，不弹新亮色窗口。
- [x] 点击设定、受理、延长、取消、失效、到期、返回；编辑超过5秒不消失；没有进度时不接受片尾。

## Task 4: 结果、首次成功与常用偏好

Files: SessionConsoleUi.kt、BlackOverlay.kt、OverlayService.kt、FirstSuccessFlow.kt、MainActivity.kt；新增PlayerTimerDefaults.kt、测试与八语言资源。

- [x] 验证最终暂停报告替换请求中提示，黑幕/通知/返回读取同一结果；迟到回调不改新会话。
- [x] 首次成功两项确认后才持久化并收起首次引导；帮助仍可重新体验。
- [x] 按应用记住时长和结束动作，仅受理成功后保存，不保存token/标题；读取需校验范围，旧备份保持兼容。
- [x] v4页面骨架保留；实际截图与八语言动态面板检查。

## Task 5: UAT与候选

- [x] 完整JVM、核心14、v50、纠偏、多语言布局，以及新增真实框架会话UAT；API26/33/35逐次运行，避免并行冷启动导致短定时误报。
- [x] 模拟UAT记录每条输入/点击/预期/实际/证据；真机声音、哔哩哔哩、来电/耳机、过夜、耗电保留待验，不伪造。
- [x] debug/release/lint，签名和安装启动检查；提供独立候选APK、SHA256与报告。保留旧包，不发布，不安装手机。
- [x] 独立代码复核并修正阻断；清理本轮临时镜像，保存必要证据。

## Ledger

2026-10-14开始。Task1–4共享PlaybackSelection与TimerRequest；所有入口以同一请求契约校验，不复制全局媒体键兜底。当前开发分支已有200个dirty条目；只改计划内文件，不自动提交或创建丢失未提交基线的worktree。



2026-10-14收尾：模拟候选5.1.0/code89完成。JVM206通过，API26/33/35完整一分钟流程、核心14与v50回归通过；API26/35边界用例最终连续3次通过，API33原3次及新夹具复验通过。8语言2主题在普通/窄屏大字体/横屏大字体各16组合通过。正式签名APK在API26安装启动通过。P0哔哩哔哩及声音/耗电真机门槛仍待验；无手机连接，无发布。完整证据见docs/v5.1.0开发与模拟UAT记录_20261014.md。
