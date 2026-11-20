package com.clubcanino.app.seller.data

import java.net.URLDecoder

object ProductPhotoPath {

    private val allowed = Regex("^products/[^/]+/[^/]+\\.jpg$")

    fun fromDownloadUrl(photoUrl: String): String? {
        val marker = "/o/"
        val start = photoUrl.indexOf(marker)
        if (start < 0) return null
        val encoded = photoUrl.substring(start + marker.length).substringBefore("?").substringBefore("#")
        val path = try {
            URLDecoder.decode(encoded, Charsets.UTF_8.name())
        } catch (_: Exception) {
            return null
        }
        if (!allowed.matches(path)) return null
        return path
    }
}
