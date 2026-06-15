# Changelog

所有版本变更记录。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，版本号遵循 [Semantic Versioning](https://semver.org/)。

发布新版本步骤：
1. 在本文件顶部添加新版本段落
2. 提交代码：`git commit`
3. 打 tag：`git tag v1.x.x`
4. 推送 tag：`git push origin v1.x.x`
5. GitHub Actions 自动构建并发布 Release

---

## [v1.0.2] - 2026-06-15

### 新增
- 应用品牌升级为 `Veya`
- Android 启动图标替换为新的 `Veya` 图标资源
- README 新增 logo 展示和品牌资源说明

### 变更
- Android 应用名从 `WatchVideo` 改为 `Veya`
- Gradle 根项目名从 `WatchVideo` 改为 `Veya`

## [v1.0.1] - 2026-06-12

### 修复
- 修复 CI 构建失败：开启 `buildFeatures.buildConfig`，解决 `BuildConfig` 无法解析的编译错误
- Release 构建下 logD 不输出日志，避免搜索词写入 logcat

---

## [v1.0.0] - 2026-06-12

### 新增
- 搜索页：关键词搜索，支持多数据源并发查询
- 搜索历史：自动记录最近 20 条，点输入框展示，支持单条删除
- 详情页：多线路 / 选集切换，当前集高亮
- 内嵌播放器：ExoPlayer HLS 播放，无广告无跳转
- 手势控制：单击切换控制栏 / 水平滑动快进快退 / 长按 2 秒 2× 倍速
- 全屏模式：横屏 + 隐藏状态栏导航栏，切换不中断播放
- 数据源：模板影视（caiji.moduapi.cc）苹果CMS v10 JSON API
