# 交接说明（HANDOFF）

给下一个接手此项目的 agent / 开发者。读完这一份就能接着干，不用翻整段对话。

> 最后更新：2026-10-08 · v3.6.0 已发布（tag / GitHub Release / 蒲公英）；
> 其后又修了悬浮球形状随语言变形、海报头部重叠等，见 git log。

---

## 一、项目是什么

`D:\AI任务\zcode\息屏听剧` —— OPPO/ColorOS 的「息屏听剧」App。

核心功能：在任意视频 App 播放时，点悬浮球让屏幕全黑（**背光物理关闭**）而声音继续。
纯 Kotlin + 纯 Android SDK，**零第三方依赖、无 INTERNET 权限**，release 包约 294 KB。

- 包名 `com.lujinyu.xiting`，versionCode 74 / versionName 3.6.0
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

**测试基建**：`app/src/test`，**60 个 JVM 单测**（JUnit4 + org.json），覆盖存档与能量的逐条容错、
整数溢出饱和、成就残留 id 过滤、备份导入限量、徽章映射与徽章列布局、换肤取色规则、
光晕渐变不变量，以及把 26 个持久化键名钉死。

**APK 体积 159 KB**（2026-10-07 从 660 KB 降下来）。

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

## 五、十二个会绊倒人的坑

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

### 11. 固定尺寸控件 + 变长译文 = 形状漂移（2026-10-08 踩过）

悬浮球用 `minWidth=48dp` + `OvalShape` 背景 + `WRAP_CONTENT` 文字。
`OvalShape` 会**填满视图边界**，而文字宽度随语言变化：

    中文「息屏」             26dp -> minWidth 撑到 48dp -> 正圆
    俄语「Выключенный экран」 115dp -> 扁胶囊

**加语言时要逐个检查固定尺寸控件**，不能只在中文下看：悬浮球、
图标旁的标签、小部件格子、按钮。修法是把尺寸写死（`layoutParams(size, size)`），
并把译文压进容量内（本例 48dp / 13sp ≈ 3 个全角字）。

### 12. 画布上两个文本分列左右时必须互相让位

分享海报顶栏左边品牌名、右边形态名。中文下「息屏听剧」才 4 字，看着很宽敞；
西语品牌名「Escucha con pantalla apagada」40f 约 650px + 形态名
「Espíritu de tempestad」36f 约 393px = 1043px，而可用宽只有 936px —— **重叠**。

画布上的固定坐标排版**不能假定文本长度**。要么先量宽度再逐档缩小，
要么两行堆叠。另：截断别用 `TextUtils.ellipsize`（要 `TextPaint`，
传普通 `Paint` 直接编译不过），用 `Paint.breakText` 按宽度算字符数。

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

> 2026-10-08 核对过：lint 已降到 **3 警告 / 0 错误**；下方数字均已更新。

**高**

- `PetView`(947行) 与 `BubblePetView`(370行) **各写一遍同一套 5 形态 + `drawFace`**。
  改剪影必须手动同步两处（2026-10-07 吃过一次亏：图鉴大图不跟随选择）。
  ⚠️ 但「抽公共渲染器」**评估后决定暂不做**（2026-10-08）：两者动画参数是刻意分开的
  （火花自转 0.3 vs 0.5、呼吸频率 3 vs 2.2，悬浮球另做了尺寸简化），合并会改变观感。
  若将来要做，先设计好「哪些参数随尺寸缩放、哪些保持各自风格」。
- 成就弹窗已改成徽章卡片，但**从未在真机上看过**（只改未验）。
  弹窗里点某个成功能不能也高亮精灵身上对应那一格（当前只展示、不联动）。
- 「持之以恒」口径：实现用**累计不同日期数 ≥ 7**，设计表写的是「连续 7 天」，
  与界面上另有的一套连续天数统计并不一致。代码与文案自洽（非 bug），待用户拍板。
  ⚠️ `ach_unlocked` 只增不减，改口径不会让已点亮的消失。
- 省电实测（被动差值版）**还没有真实数据**：需要不插电正常使用几天
  （期间做几次息屏听剧）才能验证采样真的在积累。入口：统计页「省电估算」卡片。

**中**

- `MainActivity` 1614 行。拆 class 评估过（2026-10-08）：涉及大量 lateinit 视图
  引用与跨页调用（`refreshStates` 刷三页、`bindHome` 调设置区函数），
  收益低于回归风险，暂缓。
- `gradle test` 的根治：项目路径含中文，每次测试要镜像到 temp（30s+）。
  把项目移到纯 ASCII 路径即可根治（用户尚未决定）。
- 分享海报升级（已调研，见 `docs/分享功能升级方案.md`）：多比例（1:1 / 9:16）、
  二维码、版式声明化、内置字体。均待用户拍板后实施。

**低**

- VIBRATE 权限已加，但**触觉反馈是否真的被系统接受未在真机验证**
- `D:\xt_projectsym` junction（诊断实验产物）：**用户已拒绝删除**，不再处理。
- lint 3 条警告全是 `build_stamp` 的 UnusedResources 误报（Gradle 注入的溯源字段，
  lint 看不到引用），已在 `lint.xml` 写明理由。
