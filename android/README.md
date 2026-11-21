# android

原生 Android 工程，业务代码纯 Java；播放层使用 Media3 ExoPlayer，推荐流分页使用 AndroidX ViewPager2。

```powershell
$env:JAVA_HOME='D:\SoftWare\Android\Android Studio\jbr'
.\gradlew.bat --offline assembleDebug
```

入口：`app/src/main/java/com/hongguo/core/MainActivity.java`

```text
namespace:     com.hongguo.core
applicationId: com.hongguo.nativeapp
versionName:   0.2.0
```

详见：

- [整体架构](../docs/01-architecture.md)
- [Android 客户端](../docs/04-android-client.md)
- [编译/安装/测试](../docs/05-build-test.md)
- [路线图](../docs/07-roadmap.md)

## 关键类

| 类 | 职责 |
|---|---|
| `MainActivity` | 五 Tab 壳 + 底部导航 |
| `FeedView` / `FeedPageView` | 全屏单列推荐流：自动播放、上下滑、追剧/选集、预加载 |
| `BrowseView` / `RankView` / `NewView` | 剧场、榜单、新剧 |
| `MineView` | 我的、统计、继续观看、清理、设置 |
| `SearchActivity` | 搜索历史、热词、结果分页 |
| `DetailActivity` | 详情、选集、收藏、相关推荐 |
| `PlayerActivity` | Media3 PlayerView 播放器、铺满/适应、倍速/清晰度面板、手势、连播、横竖屏 |
| `LibraryActivity` | 历史 / 收藏 |
| `HongguoApi` | 签名 HTTP 客户端，轨道解析与编码字段 |
| `PassportSigner` / `PassportConstants` | SM3/F13/Helios/Gorgon/Medusa |
| `DeviceRegister` / `DeviceSessionStore` | 游客注册与身份持久化 |
| `Spade` | `spade_a` → content key |
| `CencMp4` | 流式 CENC 解密、MP4 修复、sample entry 编码检测 |
| `OfflineStore` / `MediaCache` | 旧元数据兼容、播放缓存/预加载文件管理、坏缓存清理 |
| `DramaParser` / `Drama` / `ListPage` | 响应归一化与 UI 模型 |
| `SkeletonView` / `StateView` | 首屏骨架、空态、错误态 |
| `ImageLoader` / `CoverImageView` | 封面缓存、降采样、HEIC 回退、200MB 磁盘上限 |
| `Theme` / `Ui` / 自定义 View | 统一设计系统、按压反馈 |

## 说明

- APK 不包含 native so、WebView、Frida、unidbg；
- 播放缓存位于 `files/play/`，封面缓存在 `cache/covers/`；
- ByteVC2 轨道会被过滤，优先选择 `bytevc1`；坏缓存启动时清理；
- UI 已重做为浅色红果风，含骨架屏、五个 Tab、搜索、详情、播放器、我的、离线；
- 播放器默认铺满，可在控制栏切换适应；列表页锁竖屏，播放器支持横屏与刘海安全区；
- 真机验证机：Realme RMX3161 / Android 12 / arm64 / 1080x2400。
