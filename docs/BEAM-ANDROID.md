# 从 Libraries.dev Web 样式适配 Android

本次先阅读组件实现与示例 CSS，再改 Android 材质，不把网页截图作为唯一依据。

参考仓库：[Jakubantalik/Libraries.dev](https://github.com/Jakubantalik/Libraries.dev)，固定版本 `b0e5a8f1e659ac549e6035e42a750e73678ed67a`。读取的公开源码适用 MIT；许可证保留在 [`app/src/main/assets/licenses/LIBRARIES-DEV-MIT.txt`](../app/src/main/assets/licenses/LIBRARIES-DEV-MIT.txt)，随 APK 打包。不引入 WebView、React、Skia 或 Pro 导出内容。

## 实际阅读的代码

- [`BorderBeam.tsx`](https://github.com/Jakubantalik/Libraries.dev/blob/b0e5a8f1e659ac549e6035e42a750e73678ed67a/packages/border-beam/src/BorderBeam.tsx)：容器圆角、尺寸变化、可见性与动画启停。
- [`styles.ts`](https://github.com/Jakubantalik/Libraries.dev/blob/b0e5a8f1e659ac549e6035e42a750e73678ed67a/packages/border-beam/src/styles.ts)：`line`、`pulse-inner`、`pulse-outside` 分离细亮边、内部光和外部 bloom，并说明避免叠加第二圈描边。
- [`examples.css`](https://github.com/Jakubantalik/Libraries.dev/blob/b0e5a8f1e659ac549e6035e42a750e73678ed67a/sites/home/public/assets/examples.css)：`mock-vchat` 的半透明底色、`mock-vchat-btn` 的内侧细边和顶部高光、明暗主题，以及 Voice 的 dots/lines 示例控件使用 `backdrop-filter: blur(...)`。
- [`voice-glow/styles.ts`](https://github.com/Jakubantalik/Libraries.dev/blob/b0e5a8f1e659ac549e6035e42a750e73678ed67a/packages/voice-glow/src/styles.ts)：底部 glow、遮罩与 bloom 的分层；不把该库的麦克风处理移植到电视。

## 如何对应原生绘制

| Web 机制 | Android 实现 | 本项目取舍 |
| --- | --- | --- |
| `backdrop-filter: blur(...)` | 后台计算桌面快照的高斯模糊，胶囊内按屏幕坐标取样 | 独立于全屏渐变模糊，胶囊内的桌面文字不会透成重影；电视距离下采用更浓的雾感 |
| 半透明背景色与 light/dark theme | 模糊图像上覆盖明暗渐变色，再绘制文字 | 按用户要求固定为用户白底深字、AI 黑底浅字 |
| 内侧 ring、顶部 edge light | 圆角内缩 0.75dp 的渐变细描边 | 顶部略亮、底部收敛，只画一圈，避免粗双边 |
| 材质表面 | 低饱和模糊，加极弱静态颗粒 | 颗粒是本项目原生补充，不冒称原组件使用噪点；不逐帧重新生成 |
| border/core/bloom 分层 | 输入条的材质与彩色亮边、缓存柔光遮罩分别绘制 | 保留用户指定的下沿光效，不把整圈 pulse 样式全搬过来 |
| 圆角裁剪与内容层级 | Drawable 的圆角 Path 裁剪，TextView 在背景上绘制 | 只模糊背后的图像，文字、图标保持清晰 |

这些是针对电视的原生适配，不是逐像素移植全部组件。核心文件为 `AssistantGlass.java`、`AssistantBackdrop.java`、`AssistantComposer.java`；消息角色与材质映射在 `HomeAssistant.bubble()`。

## 维护与验证

- 一次打开浮层共用一份浓模糊材质；单个胶囊不各做一次高斯模糊。字段准备好后刷新当前可见材质。
- 胶囊移动、历史滚动、输入法调整高度时，背景按胶囊当前位置取样；不把同一小块壁纸平铺到所有消息。
- 短消息仍按内容收窄；长回复换行；新消息滚动、自动输入法、返回层级和默认关闭行为沿用现有逻辑。
- 实机检查白底/深字、黑底/浅字的对比度；检查细边与输入条只有一层材质边，磨砂不会模糊文字。
- 此实现仍使用打开时的桌面快照。浮层显示期间背景与时钟不实时更新，关闭恢复；未声称支持所有 Android 设备或所有输入法。
