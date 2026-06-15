package com.watchvideo.data.local

import com.watchvideo.platformEpochMs

/** 进程级共享的本地存储实例，确保各页 StateFlow 看到同一份数据。 */
object LocalStores {
    val history: WatchHistoryStore by lazy { WatchHistoryStore() }
    val favorites: FavoritesStore by lazy { FavoritesStore() }
    val sourceScore: SourceScoreStore by lazy { SourceScoreStore(nowEpochMsProvider = { platformEpochMs() }) }
}
