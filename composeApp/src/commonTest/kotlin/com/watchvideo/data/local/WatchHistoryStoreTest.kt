package com.watchvideo.data.local

import com.russhwolf.settings.Settings
import com.watchvideo.data.model.WatchHistoryItem
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchHistoryStoreTest {
    @Test
    fun upsert_keeps_only_latest_item_for_same_content_key() {
        val store = WatchHistoryStore(FakeSettings())
        val older = WatchHistoryItem(
            contentKey = "modu:hero",
            sourceKey = "modu",
            sourceContentId = "hero-1",
            routeKey = "line1",
            episodeKey = "ep1",
            title = "旧记录",
            coverUrl = "http://localhost/old.jpg",
            episodeLabel = "第1集",
            progressMs = 1_000,
            durationMs = 10_000,
            lastWatchedAtEpochMs = 100,
        )
        val newer = older.copy(
            routeKey = "line2",
            sourceContentId = "hero-2",
            title = "新记录",
            episodeKey = "ep2",
            episodeLabel = "第2集",
            progressMs = 2_000,
            lastWatchedAtEpochMs = 200,
        )
        val another = WatchHistoryItem(
            contentKey = "xiaobao:hero",
            sourceKey = "xiaobao",
            sourceContentId = "hero-3",
            routeKey = "line2",
            episodeKey = "ep3",
            title = "另一条",
            coverUrl = "http://localhost/another.jpg",
            episodeLabel = "第3集",
            progressMs = 3_000,
            durationMs = 10_000,
            lastWatchedAtEpochMs = 150,
        )

        store.upsert(older)
        store.upsert(another)
        store.upsert(newer)

        assertEquals(listOf(newer, another), store.list())

        store.remove("modu:hero")
        assertEquals(listOf(another), store.list())

        store.clear()
        assertEquals(emptyList(), store.list())
    }

    private class FakeSettings : Settings {
        private val data = mutableMapOf<String, Any>()

        override val keys: Set<String>
            get() = data.keys
        override val size: Int
            get() = data.size

        override fun clear() = data.clear()
        override fun remove(key: String) {
            data.remove(key)
        }

        override fun hasKey(key: String): Boolean = data.containsKey(key)
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int): Int = data[key] as? Int ?: defaultValue
        override fun getIntOrNull(key: String): Int? = data[key] as? Int
        override fun putLong(key: String, value: Long) { data[key] = value }
        override fun getLong(key: String, defaultValue: Long): Long = data[key] as? Long ?: defaultValue
        override fun getLongOrNull(key: String): Long? = data[key] as? Long
        override fun putString(key: String, value: String) { data[key] = value }
        override fun getString(key: String, defaultValue: String): String = data[key] as? String ?: defaultValue
        override fun getStringOrNull(key: String): String? = data[key] as? String
        override fun putFloat(key: String, value: Float) { data[key] = value }
        override fun getFloat(key: String, defaultValue: Float): Float = data[key] as? Float ?: defaultValue
        override fun getFloatOrNull(key: String): Float? = data[key] as? Float
        override fun putDouble(key: String, value: Double) { data[key] = value }
        override fun getDouble(key: String, defaultValue: Double): Double = data[key] as? Double ?: defaultValue
        override fun getDoubleOrNull(key: String): Double? = data[key] as? Double
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = data[key] as? Boolean ?: defaultValue
        override fun getBooleanOrNull(key: String): Boolean? = data[key] as? Boolean
    }
}
