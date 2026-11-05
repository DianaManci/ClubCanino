package com.clubcanino.app.buyer.ui

import android.graphics.BitmapFactory
import android.widget.ImageView
import java.net.URL
import kotlin.concurrent.thread

object RemoteImages {

    fun load(image: ImageView, url: String, fallbackRes: Int) {
        image.setImageResource(fallbackRes)
        image.tag = url
        if (url.isBlank()) return
        thread {
            val bitmap = try {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            }
            image.post {
                if (image.tag == url && bitmap != null) {
                    image.setImageBitmap(bitmap)
                }
            }
        }
    }
}
