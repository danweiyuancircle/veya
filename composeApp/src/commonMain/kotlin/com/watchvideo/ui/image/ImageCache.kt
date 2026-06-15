package com.watchvideo.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.watchvideo.data.createHttpClient
import okio.Path

/** 图片磁盘缓存目录（平台相关）。 */
expect fun imageCacheDir(): Path

/**
 * 全局图片加载器：内存 + 磁盘缓存，网络层复用带 Referer/UA 的 HttpClient（防盗链）。
 * 缓存命中后不再走网络，避免搜索页首次加载失败。
 */
fun buildImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
    .components {
        add(KtorNetworkFetcherFactory(httpClient = { createHttpClient() }))
    }
    .memoryCache {
        MemoryCache.Builder()
            .maxSizePercent(context, 0.25)
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(imageCacheDir())
            .maxSizeBytes(128L * 1024 * 1024) // 128MB
            .build()
    }
    .crossfade(true)
    .build()
