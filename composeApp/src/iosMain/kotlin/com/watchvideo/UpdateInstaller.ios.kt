package com.watchvideo

// iOS 不支持应用内安装 apk，installUpdate 返回 FAILED 让调用方降级为跳转 release 页。
actual suspend fun installUpdate(apkUrl: String, onProgress: (Float) -> Unit): InstallResult =
    InstallResult.FAILED

actual fun requestInstallPermission() { /* iOS 不适用 */ }

actual fun installDownloadedApk(): Boolean = false
