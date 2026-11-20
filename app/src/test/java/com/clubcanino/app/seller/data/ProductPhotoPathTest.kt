package com.clubcanino.app.seller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductPhotoPathTest {

    @Test
    fun readsTheObjectPathFromADownloadUrl() {
        val url = "https://firebasestorage.googleapis.com/v0/b/club.appspot.com/o/" +
            "products%2Fseller-1%2Ffood.jpg?alt=media&token=abc"
        assertEquals("products/seller-1/food.jpg", ProductPhotoPath.fromDownloadUrl(url))
    }

    @Test
    fun ignoresAddressesOutsideProductPhotos() {
        assertNull(ProductPhotoPath.fromDownloadUrl("https://example.com/photo.jpg"))
        assertNull(ProductPhotoPath.fromDownloadUrl(
            "https://firebasestorage.googleapis.com/v0/b/club.appspot.com/o/other%2Ffile.jpg"
        ))
    }
}
