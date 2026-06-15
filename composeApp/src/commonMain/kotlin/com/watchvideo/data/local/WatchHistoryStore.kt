package com.watchvideo.data.local

import com.russhwolf.settings.Settings
import com.watchvideo.data.model.WatchHistoryItem

class WatchHistoryStore(
    settings: Settings = Settings(),
) {
    private val store = JsonStore(
        settings = settings,
        key = WATCH_HISTORY_KEY,
        serializer = WatchHistoryItem.serializer(),
    )

    fun upsert(item: WatchHistoryItem) {
        val updated = list().filter { it.contentKey != item.contentKey }
        store.write((listOf(item) + updated).sortedByDescending { it.lastWatchedAtEpochMs })
    }

    fun list(): List<WatchHistoryItem> = store.read().sortedByDescending { it.lastWatchedAtEpochMs }

    fun remove(contentKey: String) {
        store.write(list().filter { it.contentKey != contentKey })
    }

    fun clear() {
        store.write(emptyList())
    }

    private companion object {
        const val WATCH_HISTORY_KEY = "watch_history_items"
    }
}
