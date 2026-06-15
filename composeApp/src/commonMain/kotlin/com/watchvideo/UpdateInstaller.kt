package com.watchvideo

/** 触发更新：Android 下载 apk 并拉起系统安装器；iOS 不支持，返回 false 让调用方降级为跳转 release 页。 */
expect suspend fun installUpdate(apkUrl: String): Boolean
