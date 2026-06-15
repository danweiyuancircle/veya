package com.watchvideo.ui.history

import com.watchvideo.data.local.LocalStores
import com.watchvideo.data.local.WatchHistoryStore
import com.watchvideo.data.model.WatchHistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HistoryViewModel(
    private val store: WatchHistoryStore = LocalStores.history,
) {
    private val _list = MutableStateFlow(store.list())
    val list: StateFlow<List<WatchHistoryItem>> = _list.asStateFlow()

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
