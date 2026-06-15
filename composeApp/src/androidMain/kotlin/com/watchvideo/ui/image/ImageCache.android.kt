package com.watchvideo.ui.image

import com.watchvideo.appContext
import okio.Path
import okio.Path.Companion.toOkioPath

actual fun imageCacheDir(): Path =
    appContext.cacheDir.resolve("image_cache").toOkioPath()
