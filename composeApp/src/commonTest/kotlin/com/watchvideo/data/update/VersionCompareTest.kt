package com.watchvideo.data.update

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VersionCompareTest {

    @Test
    fun vPrefixStripped_andNewerDetected() {
        assertTrue(compareVersions("v1.2.0", "1.1.9") > 0)
        assertTrue(isNewerVersion("v1.2.0", "1.1.9"))
    }

    @Test
    fun equalVersions_returnZero() {
        assertEquals(0, compareVersions("1.0", "1.0"))
        assertEquals(0, compareVersions("v1.0", "1.0"))
        assertFalse(isNewerVersion("1.0", "1.0"))
    }

    @Test
    fun patchBumpIsNewer_evenWithUnequalSegmentCount() {
        assertTrue(compareVersions("1.0.1", "1.0") > 0)
        assertTrue(isNewerVersion("1.0.1", "1.0"))
    }

    @Test
    fun numericCompare_notLexicographic() {
        // 1.10 > 1.2 数字比较，非字典序
        assertTrue(compareVersions("1.10", "1.2") > 0)
        assertFalse(isNewerVersion("1.2", "1.10"))
    }

    @Test
    fun olderRemote_isNotNewer() {
        assertTrue(compareVersions("1.0.0", "1.1.0") < 0)
        assertFalse(isNewerVersion("1.0.0", "1.1.0"))
    }

    @Test
    fun nonNumericSegment_treatedAsZero() {
        assertEquals(0, compareVersions("1.0.beta", "1.0.0"))
    }
}
