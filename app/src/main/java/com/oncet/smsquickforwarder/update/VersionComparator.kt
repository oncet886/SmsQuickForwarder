package com.oncet.smsquickforwarder.update

object VersionComparator {
    fun compare(left: String, right: String): Int? {
        val a = parse(left) ?: return null
        val b = parse(right) ?: return null
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    fun isRemoteNewer(currentVersion: String, remoteVersion: String): Boolean =
        compare(remoteVersion, currentVersion)?.let { it > 0 } == true

    fun normalize(version: String): String? = parse(version)?.joinToString(".")

    private fun parse(version: String): List<Int>? {
        val clean = version.trim().removePrefix("v").removePrefix("V")
        if (clean.isBlank()) return null
        return clean.split(".").map { part ->
            if (part.isBlank() || !part.all { it.isDigit() }) return null
            part.toIntOrNull() ?: return null
        }
    }
}
