package com.watchvideo.ui.favorites

import com.watchvideo.data.local.FavoritesStore
import com.watchvideo.data.local.LocalStores
import com.watchvideo.data.model.FavoriteItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FavoritesViewModel(
    private val store: FavoritesStore = LocalStores.favorites,
) {
    private val _list = MutableStateFlow(store.list())
    val list: StateFlow<List<FavoriteItem>> = _list.asStateFlow()

    fun refresh() {
        _list.value = store.list()
    }

    fun remove(contentKey: String) {
        store.remove(contentKey)
        refresh()
    }

    fun clear() {
        store.clear()
        refresh()
    }
}
