package com.watchvideo.data.local

import com.russhwolf.settings.Settings
import com.watchvideo.data.model.FavoriteItem

class FavoritesStore(
    settings: Settings = Settings(),
) {
    private val store = JsonStore(
        settings = settings,
        key = FAVORITES_KEY,
        serializer = FavoriteItem.serializer(),
    )

    fun add(item: FavoriteItem) {
        val updated = list().filter { it.contentKey != item.contentKey }
        store.write((listOf(item) + updated).sortedByDescending { it.favoriteAtEpochMs })
    }

    fun remove(contentKey: String) {
        store.write(list().filter { it.contentKey != contentKey })
    }

    fun list(): List<FavoriteItem> = store.read().sortedByDescending { it.favoriteAtEpochMs }

    fun isFavorite(contentKey: String): Boolean = list().any { it.contentKey == contentKey }

    fun clear() {
        store.write(emptyList())
    }

    private companion object {
        const val FAVORITES_KEY = "favorite_items"
    }
}
