package com.watchvideo

import platform.Foundation.NSDate

actual fun platformEpochMs(): Long = (NSDate.date().timeIntervalSince1970 * 1000).toLong()
