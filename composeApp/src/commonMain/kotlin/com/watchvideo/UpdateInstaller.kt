package com.watchvideo

/** 更新安装结果。 */
enum class InstallResult {
    /** 已拉起系统安装器。 */
    LAUNCHED,
    /** apk 已下载，但缺少"安装未知应用"权限，需引导用户授权后再装。 */
    NEED_PERMISSION,
    /** 下载或安装失败（含 iOS 不支持），调用方降级为跳转 release 页。 */
    FAILED,
}

/**
 * 下载 apk 并尝试拉起系统安装器。
 * @param onProgress 下载进度回调 0f..1f；总长未知时回调负值表示不确定进度。
 */
expect suspend fun installUpdate(apkUrl: String, onProgress: (Float) -> Unit): InstallResult

/** 跳转系统"安装未知应用"权限设置页（Android）。iOS 为空实现。 */
expect fun requestInstallPermission()

/** 用已下载缓存的 apk 直接拉起安装（授权返回后调用，不重新下载）。 */
expect fun installDownloadedApk(): Boolean
