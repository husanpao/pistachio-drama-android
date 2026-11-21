# Artifacts

本目录只保留可侧载的 release APK 与真机截图；debug APK 请在本地构建，不提交到仓库。

| 文件 | 说明 |
|---|---|
| `app-release.apk` | v0.5.4 / versionCode 18，已签名，仅供侧载验证 |
| `screenshots/app-icon-512.png` | 应用图标 512 × 512 |
| `screenshots/app-icon-round-512.png` | 圆形图标 512 × 512 |
| `screenshots/splash.webp` | 开屏页 |
| `screenshots/feed.webp` | 推荐 Feed |
| `screenshots/feed-like.webp` | 双击追剧反馈 |
| `screenshots/browse.webp` | 剧场筛选 |
| `screenshots/rank.webp` | 排行榜 |
| `screenshots/new.webp` | 新剧 |
| `screenshots/search.webp` | 搜索 |
| `screenshots/mine.webp` | 我的 |
| `screenshots/detail.webp` | 详情与选集 |
| `screenshots/player.webp` | 播放器 |
| `screenshots/player-horizontal.webp` | 横屏视频竖屏播放 |
| `screenshots/player-fit.webp` | 画面适应 |
| `screenshots/player-pip.webp` | PiP 小窗 |
| `screenshots/player-swipe.webp` | 上滑切集 |
| `screenshots/player-landscape.webp` | 横屏控制层 |
| `screenshots/player-v5.webp` | v5 播放协议实测 |
| `screenshots/skeleton.webp` | 首屏骨架 |

## 安装

```powershell
adb install -r app-release.apk
adb shell am start -n com.hongguo.nativeapp/com.hongguo.core.SplashActivity
```

release 与本机 debug 签名不同，必要时先卸载旧包：

```powershell
adb uninstall com.hongguo.nativeapp
```

## 重新构建

```powershell
cd ..\android
$env:JAVA_HOME = "C:\Program Files\Java\jdk-18.0.1.1"
.\gradlew.bat assembleRelease
```

未签名的 release 位于 `app/build/outputs/apk/release/app-release-unsigned.apk`，
需要使用自己的 keystore 完成 `zipalign` + `apksigner`。

## 注意

- 包名：`com.hongguo.nativeapp`
- 启动入口：`com.hongguo.core.SplashActivity`
- 不包含原生 SO、账号数据或受版权保护的视频文件
