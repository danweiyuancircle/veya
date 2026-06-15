package com.watchvideo.data.update

/** 比较语义版本，a>b 返回正、相等 0、a<b 负。容忍 "v" 前缀和段数不等，非数字段当 0。 */
fun compareVersions(a: String, b: String): Int {
    val pa = normalize(a)
    val pb = normalize(b)
    val n = maxOf(pa.size, pb.size)
    for (i in 0 until n) {
        val va = pa.getOrElse(i) { 0 }
        val vb = pb.getOrElse(i) { 0 }
        if (va != vb) return va - vb
    }
    return 0
}

/** remote 是否比 current 新 */
fun isNewerVersion(remote: String, current: String): Boolean = compareVersions(remote, current) > 0

private fun normalize(v: String): List<Int> =
    v.trim().trimStart('v', 'V')
        .split('.')
        .map { it.toIntOrNull() ?: 0 }
