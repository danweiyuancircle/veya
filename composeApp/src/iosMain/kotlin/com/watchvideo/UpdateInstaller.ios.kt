package com.watchvideo

// iOS 不支持应用内安装 apk，返回 false 让调用方降级为跳转 release 页。
actual suspend fun installUpdate(apkUrl: String): Boolean = false
