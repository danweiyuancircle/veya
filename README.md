# WatchVideo

<div align="center">

**[English](#english) · [中文](#中文)**

---

*免费、无广告的视频聚合播放器 · Free, ad-free video aggregation player*

[![Build APK](https://github.com/danweiyuancircle/watch-video/actions/workflows/build-apk.yml/badge.svg)](https://github.com/danweiyuancircle/watch-video/actions/workflows/build-apk.yml)
[![Release](https://img.shields.io/github/v/release/danweiyuancircle/watch-video?label=latest)](https://github.com/danweiyuancircle/watch-video/releases/latest)
[![Platform](https://img.shields.io/badge/platform-Android-green)](https://github.com/danweiyuancircle/watch-video/releases/latest)

</div>

---

## 中文

### ⚠️ 免责声明

本项目为**纯个人学习工具**，不用于任何商业用途，不托管、不分发、不存储任何版权内容。所有视频资源均来自第三方公开 API，本项目仅作聚合播放，不对内容的合法性负责。

**如您是版权方且认为本项目侵害了您的权益，请通过 [Issues](https://github.com/danweiyuancircle/watch-video/issues) 或邮件联系我，我将在 24 小时内下线相关内容或整个项目。**

### 简介

一键搜索多个影视站点资源，无广告、无跳转，直接在 App 内播放 HLS 串流。

### 下载

👉 [前往 Releases 下载最新 APK](https://github.com/danweiyuancircle/watch-video/releases/latest)

安装前需在手机设置中允许"安装未知来源应用"。

### 功能

| 功能 | 说明 |
|------|------|
| 全网搜索 | 并发查询多个影视源，结果合并展示 |
| 搜索历史 | 自动记录最近 20 条，点输入框复用 |
| 多线路选集 | 支持多条播放线路切换，当前集高亮 |
| 无广告播放 | 内嵌 ExoPlayer，直接串流，无跳转 |
| 手势控制 | 单击切换控制栏 / 水平滑动快进快退 / 长按 2 秒倍速 |
| 全屏模式 | 横屏 + 隐藏系统栏，切换不中断播放 |

### 手势说明

```
单击        — 显示 / 隐藏播放控制栏
水平滑动    — 快进快退（实时预览秒数，松手跳转）
长按 2 秒   — 2× 倍速播放，松手立即恢复
```

### 技术栈

- **框架**：Kotlin Multiplatform + Compose Multiplatform
- **网络**：Ktor Client (OkHttp) + gzip
- **播放**：ExoPlayer (media3) HLS
- **图片**：Coil 3

### 添加数据源

在 `commonMain/data/parsers/` 新建 `XxxParser.kt` 实现 `SiteParser` 接口，在 `ParserRegistry.kt` 注册即可。接口三个方法：`search()` / `detail()` / `playInfo()`。

---

## English

### ⚠️ Disclaimer

This project is a **personal learning tool only** — not for any commercial use. It does not host, distribute, or store any copyrighted content. All video resources are fetched from third-party public APIs; this app merely aggregates and plays them.

**If you are a rights holder and believe this project infringes your copyright, please open an [Issue](https://github.com/danweiyuancircle/watch-video/issues) or contact me directly. I will take down the relevant content or the entire project within 24 hours.**

### About

A free, ad-free Android video player that searches across multiple streaming sources and plays HLS streams directly in-app — no redirects, no ads.

### Download

👉 [Get the latest APK from Releases](https://github.com/danweiyuancircle/watch-video/releases/latest)

You may need to enable "Install from unknown sources" in your phone settings.

### Features

| Feature | Description |
|---------|-------------|
| Multi-source search | Concurrent queries across multiple sites, results merged |
| Search history | Last 20 queries, tap the search box to reuse |
| Multi-route episodes | Switch between playback routes; current episode highlighted |
| Ad-free playback | Embedded ExoPlayer, direct HLS stream, no redirects |
| Gesture controls | Tap / horizontal swipe seek / long-press 2× speed |
| Fullscreen | Landscape + hidden system bars, no playback interruption |

### Gestures

```
Tap               — Show / hide playback controls
Horizontal swipe  — Seek (live preview in seconds, commits on release)
Long press 2s     — 2× speed, releases back to 1×
```

### Tech Stack

- **Framework**: Kotlin Multiplatform + Compose Multiplatform
- **Network**: Ktor Client (OkHttp) + gzip
- **Player**: ExoPlayer (media3) HLS
- **Images**: Coil 3

### Adding a Source

Create `XxxParser.kt` in `commonMain/data/parsers/` implementing `SiteParser`, then register it in `ParserRegistry.kt`. Three methods required: `search()` / `detail()` / `playInfo()`.

---

<div align="center">

MIT License · Personal use only · Not for commercial use

</div>
