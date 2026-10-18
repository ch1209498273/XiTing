# 内容找回可行性结果（2026-10-16）

## 结论

模拟器已证明两种真实内容找回路径：哔哩哔哩系统分享可返回同一视频；爱奇艺复制链接经网页可打开同一集。B站“从当前进度分享”样本还能带回时间位置。但不能承诺自动识别所有App的具体内容，更不能承诺所有App自动续播。

本轮只开发测试探针，无正式版记录库、收藏页或合集功能；正式版入口与权限未新增，尚未发布新版本。

## 环境与安装

- uat/API33镜像仅x86_64、不支持ARM转译，B站32位包安装失败（NO_MATCHING_ABIS）。
- 切换api35/Android15现有镜像，ABI为x86_64,arm64-v8a，libndk_translation.so；三个官方包均实际安装成功。
- 哔哩哔哩9.15.0/code9150300，ARM64。
- 爱奇艺17.10.0/code800171050，ARM64/ARMv7。
- 抖音官网页面提供的独立包32.2.6/code320206，包名com.ss.android.ugc.aweme.mobile；这是此次下载版本，不当作当前商店最新版，也不能替代用户手机39.9.0的兼容结果。
- 安装包SHA256与大小见installer-receipt.json。B站初次启动有ANR，释放空闲构建内存后重试进入游客页成功；不能仅据此断言ANR根因已确定。

官方下载来源：[B站](https://app.bilibili.com/)、[抖音](https://www.douyin.com/downloadpage/app)、[爱奇艺](https://www.iqiyi.com/appstore.html)。旧爱奇艺官网跳转返回404，改用当前appstore页面链接。

## 实际样本矩阵

| App / 样本 | 自动媒体信息 | 主动保存路径 | 实际找回 | 边界 |
| --- | --- | --- | --- | --- |
| B站 / 公开铁路vlog普通投稿 | PLAYING；标题与画面一致；进度229549ms、时长1787925ms；提供mediaId，没有HTTP mediaUri | 分享→More→系统选择器→测试接收器，收到subject、文字及b23.tv短链 | 实际打开短链后回到同一视频详情页 | mediaId只是存在，尚未证明可直接作为长期内容链接或稳定跨版本身份 |
| B站 / 同一投稿从当前进度分享 | 同上 | Share from Here产生“视频标记点03:28”及另一短链；复制后显式粘贴进探针 | force-stop原B站后打开该短链，回到同一内容；读取position215208ms（03:35），与03:28开始后经过数秒播放一致 | 仅此样本验证位置恢复；普通分享不能由此推断也带位置 |
| 爱奇艺 / 把黑夜点燃第1集公开可播内容 | 已有通知读取权限；正文画面播放时原生lookup无可用来源；广告阶段dumpsys为NONE且metadata为空 | 原分享菜单有复制链接；“更多分享”只是海报分组，不是通用系统分享入口；复制内容含具体集名与qy.net短链 | 点击短链进入m.iqiyi.com内容页，再点打开APP回到同剧第1集；截图与选集状态留证 | 不承诺自动标题/进度，不承诺恢复时间位置；不能宣传一步直接分享给本应用 |
| 抖音 / 官网独立包游客推荐页 | 未形成稳定播放探针结果 | 未完成 | 未完成 | 两次原生SIGABRT，Abort message为Cannot process signal 11；记录为模拟器运行受阻，不能判定产品不兼容，也不能算通过 |

## 已实现的测试工具与检查

- androidTest专用ContentReturnProbeActivity：系统text/plain分享接收、显式粘贴、HTTP(S)链接解析与打开。仅在ranchu/goldfish模拟器运行。
- 首次Kotlin版独立Activity缺目标APK Kotlin运行库而崩溃，已替换为独立Java版，实际接收B站分享及爱奇艺粘贴路径通过。
- content_return_probe五项输入检查通过：正常链接、危险scheme、超长输入、错误action、空文本。无合成媒体会话替代第三方结果。
- content_return_metadata只读取目标App提供的标题、位置、时长与ID/HTTP URI是否存在，不保存原始媒体token或opaque mediaId。
- 最终assembleDebugAndroidTest成功；仅测试源与测试manifest增加入口，不属于发布版功能验收。

## 产品范围建议

1. 保留“最近听过”：仅对可靠媒体信息记录已知内容；没有信息不能伪装成具体视频记录。
2. 黑幕“记住这里”：有可靠进度时记录位置；有用户保存的同内容链接时可关联。不能仅按标题相同自动合并。
3. 提供“保留这条/删除”和普通记录90天期限，保留原清理保护原则；不做独立收藏页、合集，搜索后置。
4. 链接保存作为补充：支持真实系统分享；只有复制链接的App，允许用户显式粘贴，不能自动读取剪贴板。此路径多一步，应明确其价值是找回具体内容。
5. 返回动作分级：打开原内容、打开来源App、查看记录位置。精确恢复只在验证过的实际路径开放，不构造未经验证的深链或媒体键。

仍待验证：内容换集/切App前后身份稳定性、黑幕下新增标记是否影响播放、其他App版本和内容类型、长期链接有效性。下一阶段最小产品设计必须以这些边界收口；原完整记录库计划暂停。

## 证据索引

- bili-system-share.json / bili-from-here-share.json：原App实际交出的公开文字与短链。
- bili-metadata.json / bili-position-link-return-metadata.json：实际媒体读取结果。
- bili-link-return.xml / bili-position-link-return.xml：同一视频返回页。
- iqiyi-copied-share.json / iqiyi-link-web.xml：原App复制内容及落地页。
- iqiyi-link-return-app.xml / iqiyi-link-return-app.png / iqiyi-metadata.json / iqiyi-body-metadata.png：同集返回与正文无可用媒体来源。
- probe-input-checks.log / probe-build.log / environment-failures.log：输入检查、构建和失败证据。

这些结果均为Android15模拟器，不替代用户手机验收；本轮不要求连接手机，也未做耗电实验。
