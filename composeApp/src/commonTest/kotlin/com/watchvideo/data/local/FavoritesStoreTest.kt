package com.watchvideo.data.local

import com.russhwolf.settings.Settings
import com.watchvideo.data.model.FavoriteItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavoritesStoreTest {
    @Test
    fun add_deduplicates_by_content_key() {
        val store = FavoritesStore(FakeSettings())
        val older = FavoriteItem(
            contentKey = "modu:hero",
            title = "旧收藏",
            coverUrl = "http://localhost/old.jpg",
            summary = "old summary",
            preferredSourceKey = "modu",
            sourceContentId = "hero-1",
            favoriteAtEpochMs = 100,
        )
        val newer = older.copy(
            title = "新收藏",
            summary = "new summary",
            favoriteAtEpochMs = 200,
        )
        val another = FavoriteItem(
            contentKey = "xiaobao:hero",
            title = "另一条",
            coverUrl = "http://localhost/another.jpg",
            summary = "another summary",
            preferredSourceKey = "xiaobao",
            sourceContentId = "hero-2",
            favoriteAtEpochMs = 150,
        )

        store.add(older)
        store.add(another)
        store.add(newer)

        assertEquals(listOf(newer, another), store.list())
        assertTrue(store.isFavorite("modu:hero"))

        store.remove("modu:hero")

        assertFalse(store.isFavorite("modu:hero"))
        assertEquals(listOf(another), store.list())
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
