# 交接说明（HANDOFF）

给下一个接手此项目的 agent / 开发者。读完这一份就能接着干，不用翻整段对话。

> 最后更新：2026-10-07 · 对应提交 `见 git log（成就徽章化一轮）`

---

## 一、项目是什么

`D:\AI任务\zcode\息屏听剧` —— OPPO/ColorOS 的「息屏听剧」App。

核心功能：在任意视频 App 播放时，点悬浮球让屏幕全黑（**背光物理关闭**）而声音继续。
纯 Kotlin + 纯 Android SDK，**零第三方依赖、无 INTERNET 权限**，release 包约 655 KB。

- 包名 `com.lujinyu.xiting`，versionCode 73 / versionName 3.5.0
- `minSdk 26` / **`targetSdk 35`（刻意不升，见第五节）**
- 源码约 5200 行 Kotlin，单模块

---

## 二、当前状态

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

**测试基建**：`app/src/test`，**52 个 JVM 单测**（JUnit4 + org.json），覆盖存档与能量的逐条容错、
整数溢出饱和、成就残留 id 过滤、备份导入限量、徽章映射与徽章列布局、以及把 26 个持久化键名钉死。

```bash
powershell -ExecutionPolicy Bypass -File tools\run_tests.ps1
```

**⚠️ 必须用这个脚本跑测试**，原因见第五节。

---

## 三、本轮完成：成就改为「解锁精灵身上的徽章」

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
放大看形状 / App 实际直径 45px / 未解锁 / 未解锁实际尺寸。脚本在
`.cowork-temp/badge_preview.py`（临时目录，不入库）。

**这步真的抓到了三个问题**，肉眼在代码里完全看不出来：
1. 沙漏的玻璃用白色半透明描边，在金色底盘上几乎看不见 → 剩下一个橙色沙堆，读不出是沙漏 → 改深棕框 + 橙沙。
2. 靶心的箭杆起点落在靶心**内部**、拉到 1.25s → 一根斜线从红心戳穿出去 → 改短，只保留「指向」。
3. 火焰未解锁态描边 0.12s 太细，缩到实际尺寸近乎消失 → 加粗到 0.15s。

**教训：改任何手绘图形，都先跑一遍这个预览再装机。** 别拿用户手机当调试器。

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
- `PetView.kt` —— `badges: List<Badges.Badge>` 属性 + `badgeColumn()` 几何 + `drawBody` 里的绘制
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

### 换肤 = 色相旋转 + 饱和度

`PetSkins.Skin(id, hue, saturation, nameRes, condRes)`
`PetSkins.skinFilter(hue, saturation)` 生成 ColorMatrix。

- 色相按 **60° 均匀排开**（0/60/120/180/240/300）
- **`saturation` 必须 >= 1.0**：精灵主体接近白色，**一旦 <1 直接洗成纯白**（用户实测反馈）
- 调用点一律用 `applySkin(skin)`（PetView / BubblePetView 都有），**不要单独设 skinHue**
  —— 漏设饱和度不崩、只是「看起来没区别」，属最难查的一类问题

### 预览与快照

- `PetSkins.snapshot(ctx, stage, skin, size, withBar, pct)` —— 离屏渲染缩略图/小组件位图。
  内部**必须置 `thumbMode = true`**，否则会把能量条和「?」图标一起画进去。
- 图鉴弹窗里两行缩略图（形态行 / 配色行）互相依赖对方，必须走 `rebuildRows()` 整体重建，
  重建时保留各自横向滚动位置。

### 持久化

26 个键全部集中在 `Prefs.kt`（`Prefs.XXX` 常量 + `Context.prefs()` 扩展）。
**键名一旦发布不能改** —— 改了等于全体用户丢失该项设置。
`PrefsKeysTest` 会把键名钉死，改名时它会报错。

有一个只读遗留键 `SHARE_BONUS_GP_LEGACY`：当前版本从不写，但早期版本写过，
老用户 prefs 里仍有值，**必须保留读取**，否则他们迁移加成静默归零。

---

## 五、五个会绊倒人的坑

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

---

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

## 七、其它待办（优先级低）

- `gradle test` 的根治（见坑 1）
- 成就徽章：弹窗里点某个成就能不能也高亮精灵身上对应那一格（当前只展示，不联动）
- VIBRATE 权限已加，但**触觉反馈是否真的被系统接受未在真机验证**
- 剩余死代码清理（lint `UnusedResources` 仍有条目）
- `PetView` 各形态弧线处约 15 处每帧 `RectF` 分配（收益递减，未动）
- `BackupManager.findBackup` 已接通；`Stats.totals` / `xiiting_stats` 已随 `addDelta` 一并移除
- 项目根目录有个遗留的目录联接 `D:\xt_projectsym`（指向本项目，诊断实验产物），
  运行时拒绝删除，需手动 `rmdir D:\xt_projectsym`
