# v4.2 核心体验修复与开发 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline, then a fresh whole-change review.

**Goal:** 修复图鉴和对外文案，完成睡前定时结束选项、无副作用的五阶互动预览、用户主动触发的首次黑幕演示。
**Architecture:** 保留纯 Android SDK；纯 Kotlin 负责成长提示、定时期限/动作决策，平台层绑定指定 MediaSession，现有 PetView 承担预览。演示复用 BlackOverlay，独立计时且不计入听剧历史。
**Tech Stack:** Kotlin, Android SDK 26–36, JUnit, native Instrumentation.
**Spec:** docs/升级建议_v4.1之后_20261011.md，用户已要求进行修复和开发；本轮落实“先修”和“优先”项。

## Global Constraints
- minSdk 26；无第三方运行时依赖，无 INTERNET 权限。
- 成长阈值0/30/300/1500/6000、待收上限200、3天过期和存量备份不变。
- 旧定时结束动作仍可选且默认沿用；新动作明确为暂停请求，不承诺播放器响应。
- 不恢复OCR字幕、本地播放器、播放中断提醒。
- 沿用当前目录开发；仅现有建议文档未跟踪。使用本地功能分支隔离提交，不移动项目或重新请求工作区审批。
- 本轮交付候选，不自动发布新版本。

## Review Focus
- 新用户、门槛前后、满级及切回旧形态，成长提示仍准确。
- 未解锁预览不写形态、皮肤或成长数据，帮助/能量控件不出现在预览。
- 定时绑定同一媒体会话；已暂停不反转；无授权或不支持时不替换现有定时。
- 修改系统时间、取消/重新设置、演示与正常黑幕交错，不意外撤掉后来的黑幕。
- 演示无历史/能量/采样入账；来电/电源键/主动退出仍能撤幕；所有新增文案八语言完整。

## Tasks
- [x] 1. PetGrowth.progress(Long): GrowthProgress(stage,remaining,complete)，边界回归先红后绿；接入图鉴，修正文案。
- [x] 2. PreviewPetView/PetPreviewDialog：复用PetView新增previewOnly参数，任意阶段互动；图鉴锁定形态进入预览，另加全形态预览入口；佩戴操作保留现有限制。Android测试验证不写成长/装扮。
- [x] 3. SleepTimerPolicy.deadline、pauseDecision先红后绿；TimerPlaybackTarget持有指定controller并只发pause；服务定时改为单调时钟，UI提供结束动作选项，听完这集保留所选session。
- [x] 4. FirstUseGuide：主页主动入口，解释必要/可选权限；用户准备好视频后开始10秒黑幕演示，成功返回后询问声音是否继续；演示不计成长且不干扰现有定时。
- [x] 5. 全量JVM、Lint、i18n、构建及原生模拟器手势/对话框/媒体/演示检查；独立审查并修复；输出正式签名候选APK与验收记录。

## Verification
- powershell -File tools/run_tests.ps1：所有suite零失败/错误。
- gradle assembleDebug assembleDebugAndroidTest assembleRelease lint --offline --console=plain：BUILD SUCCESSFUL。
- python tools/i18n.py check：七语言100%。
- adb instrument (read-only API33 emulator)：旧精灵检查加成长提示、锁定预览隔离、pause请求与demo不入账检查。

## Ledger
- BASE: 1105596，用户已有未跟踪升级建议保留；不推送/不发布。
- Ruling: 首次使用采用主动入口的10秒真实黑幕演示；不强制弹多步权限向导，不读取用户视频内容。
- Ruling: 进化转场为随后版本项，本轮先完成用户刚批准的“先修+优先”列表；养成数值暂不变。

- Verified: 最终候选构建 Debug/AndroidTest/Release 与 Lint 成功；Lint No issues found；八语言356/356。
- Verified: API33 read-only emulator 全部8项原生检查通过，含真实服务定时暂停保留幕、默认撤幕、完整10秒演示、演示拒绝新定时、采样恢复。
- Review: 独立只读审查发现两项P2（演示结束停止功耗采样、撤幕后暂停结果未展示），均修复并复核通过。
- Artifact: v4.2.0/code83 正式签名候选，376914 bytes；未发布渠道，未安装到实体手机。
