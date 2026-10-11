# targetSdk 36 · 黑幕兼容性预研

> 2026-10-11 · v4.0 战略预研（未实施，仅记录风险与验证计划）
> 背景：Google Play 自 2026-08-31 起要求 targetSdk 36。当前渠道（酷安/应用宝/GitHub）
> 不受此约束，因此 v3.x/v4.0 刻意保持 targetSdk 35（HANDOFF 第五节）。本文档回答
> 「将来要升 36 时，黑幕方案会怎样、怎么验证、有什么替代路」。

## 一、为什么 36 是黑幕的最大威胁

黑幕的核心实现（BlackOverlay）：
- `WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS`：让黑幕铺满整屏（含刘海/导航区）
- `layoutInDisplayCutoutMode = ALWAYS` + `setFitInsetsTypes(0)`：无视挖孔与系统栏 insets
- `screenBrightness = BRIGHTNESS_OVERRIDE_OFF`：背光物理关闭
- 反射注入 `privateFlags = SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS`：盖住 OEM 边缘组件

targetSdk 36 的强制 edge-to-edge：窗口默认铺满全屏、`setFitInsetsTypes` 等对 insets 的
手动控制行为变化，系统栏的让位逻辑与 35 不同。风险点：

| 风险 | 影响 | 概率 |
|---|---|---|
| FLAG_LAYOUT_NO_LIMITS 语义收紧或失效 | 黑幕铺不满（漏出状态栏/刘海） | 中 |
| cutout mode 强制修正 | 刘海区域漏光 | 中 |
| screenBrightness override 行为变化 | 背光关不掉 = 核心卖点失效 | 低（无相关变更公告） |
| 隐藏非系统悬浮窗的 privateFlags 反射被禁 | ColorOS 小白条压在黑幕上 | 中（反射本就脆） |
| 前台服务类型/specialUse 政策收紧 | 服务被杀 = 黑幕消失 | 低 |

## 二、验证计划（一天，模拟器 + 真机）

1. Android 16 模拟器（API 36）装 targetSdk 36 构建版，跑黑幕核心流程：
   开/关/唤醒/重锁/媒体键，截图对比 35 基线（漏光、错位、亮度）
2. OPPO PME110（Android 16 真机）：同上 + 小白条遮挡检查
3. 记录每项 pass/fail 到本文件
4. 若 NO_LIMITS 失效：备选方案 = 保持 35（渠道不变论）或改用
   `Window.setDecorFitsSystemWindows(false)` + 手动全屏参数的新写法重新推演

## 三、结论（待验证后更新）

- 当前决策：**targetSdk 保持 35**（HANDOFF 第五节），渠道策略不受 Play 政策约束
- 触发条件：需要上架 Google Play、或应用宝/酷安强制要求时再启动本预研的实施部分
- 本文档的验证计划即「被逼升级那天」的操作手册
