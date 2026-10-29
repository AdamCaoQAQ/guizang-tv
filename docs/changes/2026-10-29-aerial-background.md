# 苹果航拍动态首页背景

- Agent: Codex
- Change-Id: guizang-tv-20261029-aerial-background
- Agent-Session: 01a1628c-dc74-7b63-923b-6b80d2c3111c
- 基线：codex/wallpaper-crossfade / 416be92821d5302e75cd890eeefbc2fe6f852cae。
- 用户请求：将 pengchujin/aerial-tv 的航拍屏保内容作为现有首页的动态背景。

新增独立「苹果航拍」背景模式，保留原有背景模式值与默认值。来源固定到 Aerial TV 提交 `269e928057b492ce698c0d36df6bde9ef8b2a9b5`。复用 Aerial TV 的 114 条视频目录和中文时间点说明，Java 控制器使用 Media3 ExoPlayer 与 SurfaceView，将航拍放在现有首页控件下方。静音、1080p、随机连续播放，地名按片段进度更新。保留照片作为加载/失败背景，设置可选回退照片。

只在未被面板遮挡且 resumed 的首页播放。离开首页、待机、进入设置/应用库/Codex 面板及模式切换释放播放器；返回后从当前进度恢复。连续错误或缓冲超时尝试下一段，3 次失败停止本次播放直到下次进入。24MB 目标媒体缓冲，无后台下载、无视频文件打包。仅向 Apple 视频域名增加证书信任，不引入明文或跳过证书验证。

验证：

- Gradle 8.9、JDK 21（Java 17 源码目标）、Android SDK 34 / Build Tools 34.0.0：assembleDebug、testDebugUnitTest、lintDebug 通过；最终离线增量构建再次通过。
- 26 项单元测试，0 失败 / 0 错误 / 0 跳过；新增测试覆盖视频源地址边界、失败重试上限、背景模式值向后兼容。
- lint：0 errors / 24 warnings；新增依赖有新版本提示，暂保留适配现有 compileSdk 34 的 Media3 1.3.1。
- 目录检查：114 个唯一视频 ID，全部源地址为 HTTPS Apple 域名，字幕时间点有序。
- Apple 影片仅请求头实测 HTTPS 200，支持 Range；没有下载完整影片。
- APK 检查：已包含目录、MIT 通知与 Apple 公共根证书，无 .mov / .mp4；git diff --check 通过。
- 未连接电视，未验证硬件解码、SurfaceView 与首页叠加、断网回退、待机、HDMI 或真实遥控导航。不能将构建与单测通过等同于实机兼容。
- 当前产物是 Debug 签名测试包；安装到已有应用前应核对签名，不能靠卸载解决冲突而丢失用户设置。

## 实机验收步骤

1. 保留原安装签名与偏好，在「设置 → 背景」选择苹果航拍，关闭设置，确认视频位于应用列表 / Dock 与时钟下方，地名随进度更新且没有声音。
2. 打开设置/应用库/对话，或切到其他应用与 HDMI，确认视频停止；返回首页恢复播放，不出现双播放器或黑层遮住控件。
3. 切回照片，确认原轮播、0.7–1.5 秒渐变和偏好仍有效；切换浅色/深色确认照片、视频均不显示。
4. 断开网络后等待缓冲耗尽，确认有界重试和照片回退，重新进入首页后重试；连续开关设置和待机唤醒观察解码器释放。
5. 运行 `adb shell dumpsys gfxinfo io.github.op7418.guizangtv framestats` 与 `adb shell dumpsys meminfo io.github.op7418.guizangtv`，对比照片 / 航拍下遥控导航与内存。检查 `dumpsys activity` 的 Aerial 状态。
