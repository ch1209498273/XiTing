<div align="center">

<img src=".github/assets/banner.svg" width="720"/>

**看剧时一键熄屏，声音继续 —— 任意视频App可用**

[下载最新版](https://github.com/ch1209498273/XiTing/releases/latest) · [使用说明](#使用) · [常见问题](#faq)

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![离线](https://img.shields.io/badge/完全离线-零数据收集-blue)
![适配](https://img.shields.io/badge/深度适配-ColorOS-101010)

</div>

## 它解决什么

手机没有"息屏听剧"功能？这个App补上：

- **黑幕模式** —— 任意视频App里点悬浮球，整块屏幕（含状态栏、导航栏）背光物理关闭，视频照常出声，触摸全部屏蔽
- **真息屏模式** —— 直接按电源键，熄屏后声音自动恢复，最省电
- 听网课、听播客、挂直播，同理可用

专为 ColorOS 深度适配：状态栏、导航栏、手势条全部盖黑，和真息屏一样干净。

## 使用

1. 安装后打开App，按提示完成三项授权（悬浮窗 / 电池加白 / 通知）
2. 视频App里点悬浮球，或直接按电源键

> 轻点黑屏解除锁定 → 点「返回视频」按钮回到画面 · 通知栏可随时退出

## FAQ

**和小米的息屏听剧一样吗？**
体验对齐：黑幕下背光完全关闭，和真息屏一样黑、一样省电，而且任意App可用。

**为什么有的App熄屏后没声音？**
该App不响应系统媒体键。改用黑幕模式，或打开App自带的"后台播放"开关。

**费电吗？**
黑幕/真息屏下背光已关闭，显示耗电≈0；剩余耗电来自视频解码本身。

**安全吗？**
无 INTERNET 权限，无法联网；不读取任何屏幕内容。

## 构建

```bash
git clone https://github.com/ch1209498273/XiTing.git
cd XiTing && gradle assembleDebug
```

要求 JDK 17+、Android SDK 36。

## 许可

非商业许可：个人学习交流可自由使用与分享（需保留署名）；**任何商业用途均被禁止**；禁止移除或篡改应用内署名与溯源标识。详见 [LICENSE](LICENSE)。

本项目与 YouTube、Google 及任何视频平台无关联。
