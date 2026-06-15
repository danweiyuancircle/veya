package com.watchvideo.data.local

import com.russhwolf.settings.Settings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class JsonStore<T>(
    private val settings: Settings,
    private val key: String,
    serializer: KSerializer<T>,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    private val listSerializer = ListSerializer(serializer)

    fun read(): List<T> {
        val raw = settings.getStringOrNull(key) ?: return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }

    fun write(items: List<T>) {
        settings.putString(key, json.encodeToString(listSerializer, items))
    }
}
