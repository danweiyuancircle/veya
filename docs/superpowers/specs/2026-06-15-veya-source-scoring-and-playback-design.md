# Veya Source Scoring And Playback Design

## 目标

为 `Veya` 设计一套适用于 KMP 统一 UI 的多源搜索与播放核心模型，优先解决以下三件事：

1. 源评分模型
2. 历史与收藏本地数据结构
3. 详情页统一播放状态机

同时要求所有第三方源解析适配器输出统一模型，避免每个源各自返回不同结构，降低后续扩源和修复成本。

## 当前问题

当前仓库中：

- `SiteParser` 仅定义 `search/detail/playInfo`
- `SearchResult` / `Route` / `PlayInfo` 模型过轻
- 搜索结果只按源分组，不支持按本地评分排序
- 详情页只支持单源加载，不支持自动切源 / 切线路 / 失败回退
- 没有历史、收藏、评分持久化模型
- 没有把 ExoPlayer / Media3 的实际播放结果回填到源评分

这会导致：

- 源一旦变更，产品可用性快速下降
- 用户每次都要重新判断哪个源可播
- 搜索页无法把最稳源放在最前
- 详情页不能做到真正的“进入即播”

## 总体设计原则

### 1. 统一输出模型

所有源适配器必须输出相同的领域模型，不允许每个 parser 暴露自己特有结构到 UI 层。

### 2. 播放成功优先于解析成功

源评分只以真实播放结果为准，解析器标签只用于辅助，不参与最终清晰度评分。

### 3. 本地自治

历史、收藏、评分、失败记忆全部保存在用户本地，不依赖后端。

### 4. 自动决策但可人工覆盖

系统默认自动选最优源 / 线路 / 集数；用户仍可手动切换源和线路。

### 5. 稳定性高于清晰度

评分优先级固定为：

1. 可用性
2. 稳定性
3. 实际清晰度
4. 加载速度

## 一、统一源输出模型

### 1.1 解析器接口升级目标

现有：

```kotlin
interface SiteParser {
    val siteKey: String
    val siteName: String
    val baseUrl: String

    suspend fun search(keyword: String): List<SearchResult>
    suspend fun detail(id: String): List<Route>
    suspend fun playInfo(playPageUrl: String): PlayInfo
}
```

目标是不改变“三阶段”结构，但统一每一阶段的输出精度与校验要求。

### 1.2 搜索输出模型

```kotlin
data class SourceSearchItem(
    val sourceKey: String,
    val sourceName: String,
    val contentKey: String,
    val sourceContentId: String,
    val title: String,
    val coverUrl: String?,
    val subtitle: String?,
    val tags: List<String>,
    val detailUrl: String?,
)
```

说明：

- `contentKey`：同一内容的聚合主键，用于历史、收藏去重；允许初期用 `title + normalized year/type` 的弱聚合策略
- `sourceContentId`：该源内部唯一 id
- `detailUrl`：允许部分源没有完整详情页 URL，只保留 id

校验要求：

- `sourceKey` 不可空
- `sourceContentId` 不可空
- `title` 不可空
- 搜索结果列表为空不算异常，但记为搜索失败样本

### 1.3 详情输出模型

```kotlin
data class SourceDetail(
    val sourceKey: String,
    val sourceName: String,
    val contentKey: String,
    val sourceContentId: String,
    val title: String,
    val coverUrl: String?,
    val summary: String?,
    val metadata: List<MetaBadge>,
    val routes: List<PlaybackRoute>,
)

data class MetaBadge(
    val label: String,
    val value: String
)

data class PlaybackRoute(
    val routeKey: String,
    val routeName: String,
    val episodes: List<PlaybackEpisode>
)

data class PlaybackEpisode(
    val episodeKey: String,
    val episodeLabel: String,
    val playPageUrl: String
)
```

校验要求：

- `routes` 至少一条才算详情成功
- 某条 `route` 没有 `episodes`，该 route 丢弃
- 最终若所有 route 都无有效 episode，记为详情失败

### 1.4 播放输出模型

```kotlin
data class PlaybackCandidate(
    val sourceKey: String,
    val routeKey: String,
    val episodeKey: String,
    val title: String,
    val streamUrl: String,
    val headers: Map<String, String>,
)
```

校验要求：

- `streamUrl` 不可空
- 必须是合法 URL
- 返回后不立即算成功，只有真正进入播放成功态才算最终成功

### 1.5 聚合后的详情页模型

详情页不应只持有单源 `SourceDetail`，而应持有聚合模型：

```kotlin
data class AggregatedDetail(
    val contentKey: String,
    val title: String,
    val preferredCoverUrl: String?,
    val preferredSummary: String?,
    val metadata: List<MetaBadge>,
    val sourceDetails: List<SourceDetail>,
)
```

作用：

- 同一内容可合并多个源
- 详情页默认显示聚合信息
- 源切换不再意味着离开详情页

## 二、源评分模型

### 2.1 评分对象

评分对象是“源”，不是“单次搜索结果”。

可选扩展：

- 源级评分：`sourceKey`
- 源内线路评分：`sourceKey + routeKey`

第一阶段先实现源级评分即可。

### 2.2 源评分持久化模型

```kotlin
data class SourceScoreRecord(
    val sourceKey: String,
    val baseScore: Int,
    val searchSuccessCount: Int,
    val searchFailureCount: Int,
    val detailSuccessCount: Int,
    val detailFailureCount: Int,
    val playCandidateSuccessCount: Int,
    val playCandidateFailureCount: Int,
    val playbackStartSuccessCount: Int,
    val playbackStartFailureCount: Int,
    val consecutiveFailureCount: Int,
    val avgFirstFrameMs: Long?,
    val stableObservedHeight: Int?,
    val maxObservedHeight: Int?,
    val lastFailureType: FailureType?,
    val lastFailureAtEpochMs: Long?,
    val cooldownUntilEpochMs: Long?,
)
```

```kotlin
enum class FailureType {
    SEARCH_EMPTY,
    SEARCH_ERROR,
    DETAIL_ERROR,
    DETAIL_EMPTY,
    PLAY_CANDIDATE_ERROR,
    PLAY_CANDIDATE_EMPTY,
    PLAYBACK_START_ERROR,
    PLAYBACK_INTERRUPTED,
}
```

### 2.3 评分维度

#### 可用性

- 搜索能否返回结果
- 详情能否解析出有效线路
- 播放候选地址能否取到

#### 稳定性

- 首帧成功率
- 连续失败次数
- 最近成功率

#### 清晰度

- 仅以播放器真实返回分辨率为准
- 记录 `stableObservedHeight`
- 不信解析器自己写的 `1080P` / `超清`

#### 速度

- 首帧耗时

### 2.4 推荐权重

第一阶段固定权重：

- 稳定性：`50%`
- 清晰度：`25%`
- 速度：`15%`
- 基础人工权重：`10%`

### 2.5 冷却机制

规则：

- 单次失败：轻微降权
- 连续失败 `>= 3`：重度降权
- 连续失败 `>= 5`：进入冷却

冷却期建议：

- 第一次：`30 分钟`
- 第二次：`2 小时`
- 重复失败：`24 小时`

处于冷却中的源：

- 搜索结果仍可展示
- 但默认排到后面
- 自动选源时默认跳过

## 三、历史与收藏本地模型

### 3.1 观看历史模型

```kotlin
data class WatchHistoryItem(
    val contentKey: String,
    val sourceKey: String,
    val sourceContentId: String,
    val routeKey: String,
    val episodeKey: String,
    val title: String,
    val coverUrl: String?,
    val episodeLabel: String,
    val progressMs: Long,
    val durationMs: Long,
    val lastWatchedAtEpochMs: Long,
)
```

规则：

- 历史按 `lastWatchedAtEpochMs` 倒序
- 同一 `contentKey` 只保留一条“最后观看现场”
- 进度超过阈值才落库

进度阈值建议：

- `progressMs >= 30_000`
  或
- `progress >= 3%`

### 3.2 收藏模型

```kotlin
data class FavoriteItem(
    val contentKey: String,
    val title: String,
    val coverUrl: String?,
    val summary: String?,
    val preferredSourceKey: String?,
    val sourceContentId: String?,
    val favoriteAtEpochMs: Long,
)
```

规则：

- 收藏页按 `favoriteAtEpochMs` 倒序
- `contentKey` 去重
- 从收藏进入详情页：
  - 有历史，优先恢复历史
  - 无历史，按评分系统自动选源

## 四、详情页统一播放状态机

### 4.1 状态目标

详情页必须做到：

- 信息、播放器、选集同屏
- 进入即尝试自动播放
- 自动切线路
- 自动切源
- 从历史进入时可恢复现场

### 4.2 顶层状态

```kotlin
sealed interface DetailPlaybackState {
    data object Idle : DetailPlaybackState
    data object LoadingDetail : DetailPlaybackState
    data class Ready(
        val detail: AggregatedDetail,
        val selectedSourceKey: String,
        val selectedRouteKey: String?,
        val selectedEpisodeKey: String?,
    ) : DetailPlaybackState
    data class ResolvingStream(
        val sourceKey: String,
        val routeKey: String,
        val episodeKey: String,
    ) : DetailPlaybackState
    data class Buffering(
        val sourceKey: String,
        val routeKey: String,
        val episodeKey: String,
    ) : DetailPlaybackState
    data class Playing(
        val sourceKey: String,
        val routeKey: String,
        val episodeKey: String,
        val resolutionHeight: Int?,
    ) : DetailPlaybackState
    data class Recovering(
        val attemptedSourceKeys: List<String>,
        val attemptedRouteKeys: List<String>,
    ) : DetailPlaybackState
    data class Failed(
        val message: String
    ) : DetailPlaybackState
}
```

### 4.3 自动选源顺序

进入详情页后：

1. 如果有历史记录，优先尝试历史 `sourceKey + routeKey + episodeKey`
2. 如果历史恢复失败，按源评分从高到低尝试
3. 选中某源后，优先尝试最稳线路
4. 再尝试默认集数

### 4.4 恢复与降级顺序

#### 从历史进入

1. 恢复历史源
2. 恢复历史线路
3. 恢复历史集数
4. 恢复历史进度

任一层失效：

- 线路失效：切该源默认线路
- 集数失效：切该线路第一可播集
- 源失效：按评分切下一源

#### 自动失败恢复

1. 当前线路下一候选
2. 当前源其他线路
3. 下一高分源
4. 全部失败后展示错误态

### 4.5 播放成功定义

只有满足以下条件之一，才算“真正播放成功”：

- ExoPlayer / Media3 进入稳定播放状态
- 已有首帧
- `VideoSize.width > 0 && VideoSize.height > 0`

这时才执行：

- 清晰度回填
- 历史更新
- 源评分加分

## 五、播放器真实清晰度回填

### 5.1 数据来源

只认播放器回调：

- `Player.Listener.onVideoSizeChanged(VideoSize)`

记录：

- `width`
- `height`

### 5.2 使用规则

- 第一次拿到 `height > 0` 记录为候选值
- 播放稳定 `10-20 秒` 后回填为 `stableObservedHeight`
- 若中途切到更高或更低分辨率，可更新 `maxObservedHeight`

### 5.3 分档建议

- `>= 2160`：4K
- `>= 1080`：FHD
- `>= 720`：HD
- `>= 480`：SD
- `< 480`：LD

评分只用 `stableObservedHeight`。

## 六、KMP UI 层如何消费这些模型

### 搜索页

- 输入关键词
- 按 `sourceScore` 排序后的源分组展示
- 每组展示：
  - 站点名
  - 轻标签：`稳定` / `高清` / `较快`
  - 内容项

### 历史页

- 读取 `WatchHistoryItem`
- 点击后进入聚合详情页，并带历史上下文

### 收藏页

- 读取 `FavoriteItem`
- 点击后进入聚合详情页
- 有历史则恢复，无历史则自动选源

### 详情页

- 持有 `AggregatedDetail + DetailPlaybackState`
- 所有源切换、线路切换、选集切换都在同一页完成

## 七、范围边界

本次设计不包含：

- 云同步
- 账号体系
- 后端集中评分
- 站点健康定时巡检服务
- 下载 / 离线缓存
- 弹幕 / 社区 / 推荐流

## 八、推荐落地顺序

实现优先级固定为：

1. 统一解析器输出模型
2. 聚合详情模型
3. 详情页播放状态机
4. 本地历史 / 收藏存储
5. 本地源评分与冷却机制
6. 播放器真实清晰度回填
7. 搜索页按评分排序

## 九、自检

### Placeholder scan

无 `TODO`、`TBD`、未定占位项。

### Internal consistency

- 清晰度评分统一以实际播放回调为准
- 详情页统一消费聚合模型，不再以单源模型直出到 UI
- 历史恢复优先级高于自动推荐

### Scope check

范围聚焦在用户要求的三项：

- 源评分模型
- 历史与收藏本地结构
- 详情页统一播放状态机

同时补足了解析器统一输出模型，这是完成三项前置条件，不属于额外扩张。

### Ambiguity check

“清晰度是否可参与评分” 已明确：

- 可以
- 但只以播放器真实返回分辨率为准
- 且权重低于稳定性
