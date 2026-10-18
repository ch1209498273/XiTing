# UI 与功能全量升级 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline. Steps use checkbox syntax. Follow the scope and the execution ledger; do not mark a feature verified from compilation alone.

**Goal:** 按统一优先级实现全部升级，并完成充分 UAT。

**Architecture:** 保留原生 View/XML、离线与轻量定位。播放时间数学、目标选择、成就记录分别抽为独立单元；重排布局复用现有状态与绘制源。所有状态跨浅深色、语言、权限生命周期验证。

**Tech Stack:** Kotlin / Android SDK / XML / JUnit / 原生 instrumentation / adb。

**Spec:** docs/UI与功能升级全量方案_20261012.md

## Global Constraints

- 保留全部20项范围；F09的交付为明确规则评估，不静默更改200上限/三天过期。
- 用户已明确要求全部实现并充分UAT，本会话连续执行，不逐批重复索要开工确认。
- 不读取签名密码、不覆盖无关文件、不公开发布；测试优先隔离模拟器，真机保留数据。
- 不新增INTERNET权限、产品第三方依赖、账号、OCR或本地播放器。
- 当前Git沙箱访问异常，先在已授权目录保留可检查改动；不绕过.git保护或清理未知文件。

## Review Focus

- 多会话、失效时间戳与倍速：控制目标不漂移、位置夹到合法范围。
- 定时期间暂停/拖动/换集：片尾计时跟随原会话，不在暂停期间误到期。
- 旧成就与备份：未知历史日期不编造，当前佩戴与成长阶段保持分离。
- 大字体、长译文、窄屏：重要操作不裁切，滚动目标与新布局一致。
- 黑幕生命周期：无意外亮屏，面板可退出，自动重锁、来电与耳机动作不回归。

## Task 1: 播放正确性 F01/F02

Files: PlaybackMath.kt (new), NowPlaying.kt, MainActivity.kt, OverlayService.kt, TimerPlaybackTarget.kt, PlaybackMathTest.kt (new).
Interfaces: PlaybackMath.position(positionMs, updateMs, nowMs, speed, advancing, durationMs): Long; remainingMs(durationMs, positionMs, speed): Long?.
- [ ] 加入过期位置、暂停、倍速、异常速度、未来/零时间戳、上下界回归，执行观察RED。
- [ ] 实现共享数学并接入黑幕seek与片尾计算，观察GREEN。
- [ ] 片尾模式保存原会话及媒体ID；原会话回调触发重算，暂停悬置、拖动/变速重排、换集取消，退出清理回调。
- [ ] 模拟媒体会话验证实际回调、定时到期及目标失效。

## Task 2: 省电 F03 与事实 F04

Files: MeasMath.kt/Test, PowerCalib.kt, values*/strings.xml, PRIVACY.md, README.md.
- [ ] 异常阈值用µAh/ms比较，测试1500mA边界、负计数器与不合法总时长，观察RED/GREEN。
- [ ] 保留旧持久化key兼容，解释单位与样本等级，黑幕耗电展示改为电荷口径。
- [ ] 核对无障碍、媒体数据、自动备份、系统迁移，更新八语言及文档。

## Task 3: 基础UI U01/U02

Files: colors.xml pairs, drawables, activity_main.xml, page_home.xml, MainActivity.kt/MainSupport.kt.
- [ ] 统一tokens、卡片/按钮/列表与导航；提供原生页面渲染证据。
- [ ] 首页突出真实主操作、定时与条件权限，保留旧入口与绑定兼容。
- [ ] 浅深色、权限授予返回、启动/停止/演示状态UAT。

## Task 4: 播放器/定时/新手 F05/U03/F06

Files: NowPlaying.kt, TimerDialogs.kt, FirstUseGuide.kt, OverlayService.kt, Prefs.kt; new focused support files as needed.
- [ ] 统一目标列表，显示暂停/seek能力，用户选择保持会话token而非仅包名。
- [ ] 单面板呈现时长、结束动作、目标；输入错误留在面板、原定时不变。
- [ ] 演示失败分类并提供系统设置入口，权限返回刷新。
- [ ] 多会话、权限撤销、不支持暂停、原定时编辑/取消UAT。

## Task 5: 设置 U04 / 成就 F07 / 统计 U05 / 图鉴 U06

Files: page_settings.xml, page_stats.xml, MainDialogs.kt, Achievements.kt/Test, BackupManager.kt/Test, SkinGallery.kt, PetPreviewDialog.kt.
- [ ] 设置分组与依赖说明；统一关于及帮助。
- [ ] 持久化新解锁记录顺序/日期，旧记录unknown，备份version兼容；边界回归RED/GREEN。
- [ ] 成就进度采用原有指标；统计重排后修正所有滚动定位，不改统计口径。
- [ ] 图鉴清晰区分预览、解锁、佩戴；未解锁详情说明。
- [ ] 新用户、存量、坏备份、跨语言、浅深色UAT。

## Task 6: 扩展 U07–U10 / 进化 F08 / 规则 F09

Files: BlackOverlay.kt, XiTingWidget.kt layouts, OverlayService.kt, PosterFlow.kt, SharePoster.kt, ReportPoster.kt, BackupManager.kt, MainActivity.kt; focused helpers.
- [ ] 黑幕主动唤醒时显示定时调整；面板期间安全处理重锁，关闭恢复锁定契约。
- [ ] 悬浮球、通知、小组件统一；事件驱动刷新与手势不变。
- [ ] 分享数据来源标签与预览；恢复前摘要与失败反馈。
- [ ] 真实跨阶一次性转场，动画关闭/隐藏停止；不写预览成长。
- [ ] F09提交现行规则影响及自动收取方案评估，保持原契约。

## Task 7: Q01 全范围UAT与交付

Evidence: docs/uat-upgrade-20261012/ (logs, matrix, screenshots), docs/升级实施记录_20261012.md.
- [ ] 自动化：全量JVM、debug/release构建、lint、i18n；保存机器可读结果。
- [ ] 模拟器：覆盖新旧数据、双主题、八语言、字体、旋转、所有入口与异常；原生像素及点击验证。
- [ ] 真机：先保数据确认环境，再验证黑幕/亮度恢复、媒体、定时、后台与系统联动；未测环境标pending。
- [ ] 独立审查关键改动并修复，复验受影响流程。
- [ ] 逐项审计20项与所有验收标准，生成候选APK与SHA256、体积对照，清理本次无用临时文件。

不将发布、第三方全兼容、真实用户UAT签字或耗电改善当作自动化检查的结论。
