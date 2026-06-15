package com.watchvideo

import platform.Foundation.NSBundle

actual fun currentAppVersion(): String =
    // 读不到时返回极大版本号，避免误判为有新版而反复弹更新
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "999.999.999"
