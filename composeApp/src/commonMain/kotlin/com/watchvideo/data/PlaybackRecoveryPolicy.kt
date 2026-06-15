package com.watchvideo.data

import com.watchvideo.data.model.AggregatedDetail

/**
 * 播放失败降级策略（纯函数）。
 *
 * 降级顺序：
 *  1. 当前线路下一候选集数
 *  2. 当前源其他线路（依次遍历各集取首个未尝试项）
 *  3. 下一高分源（按 sourceDetails 排序，已按评分降序）
 *  4. 全部失败 → null
 */
object PlaybackRecoveryPolicy {

    /**
     * @param aggregated       聚合详情（sourceDetails 已按评分降序排列）
     * @param currentSourceKey 当前播放的源 key
     * @param currentRouteKey  当前播放的线路 key
     * @param currentEpisodeKey 当前播放的集数 key
     * @param tried            已尝试过的 (sourceKey, routeKey, episodeKey) 集合
     * @return 下一个候选 Triple(sourceKey, routeKey, episodeKey)，或 null 表示全部耗尽
     */
    fun nextCandidate(
        aggregated: AggregatedDetail,
        currentSourceKey: String,
        currentRouteKey: String,
        currentEpisodeKey: String,
        tried: Set<Triple<String, String, String>>,
    ): Triple<String, String, String>? {
        // 降级 1：同线路下一集
        val currentSource = aggregated.sourceDetails.find { it.sourceKey == currentSourceKey }
        val currentRoute = currentSource?.routes?.find { it.routeKey == currentRouteKey }
        if (currentRoute != null) {
            val afterCurrent = currentRoute.episodes
                .dropWhile { it.episodeKey != currentEpisodeKey }
                .drop(1)
            for (ep in afterCurrent) {
                val candidate = Triple(currentSourceKey, currentRouteKey, ep.episodeKey)
                if (candidate !in tried) return candidate
            }
        }

        // 降级 2：同源其他线路（从第一集开始）
        if (currentSource != null) {
            for (route in currentSource.routes) {
                if (route.routeKey == currentRouteKey) continue
                for (ep in route.episodes) {
                    val candidate = Triple(currentSourceKey, route.routeKey, ep.episodeKey)
                    if (candidate !in tried) return candidate
                }
            }
        }

        // 降级 3：下一高分源
        for (source in aggregated.sourceDetails) {
            if (source.sourceKey == currentSourceKey) continue
            for (route in source.routes) {
                for (ep in route.episodes) {
                    val candidate = Triple(source.sourceKey, route.routeKey, ep.episodeKey)
                    if (candidate !in tried) return candidate
                }
            }
        }

        // 降级 4：全部耗尽
        return null
    }
}
