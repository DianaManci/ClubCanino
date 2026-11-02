package com.clubcanino.app.buyer.ui

import androidx.annotation.DrawableRes
import com.clubcanino.app.R
import com.clubcanino.app.buyer.data.Product

object BuyerVisuals {

    @DrawableRes
    fun photoRes(product: Product): Int = when (product.id) {
        "p1" -> R.drawable.img_product_food
        "p2" -> R.drawable.img_product_litter
        "p3" -> R.drawable.img_product_toy
        "p4" -> R.drawable.img_product_health
        "p5" -> R.drawable.img_product_treats
        "p6" -> R.drawable.img_product_collar
        "p7" -> R.drawable.img_product_scratcher
        "p8" -> R.drawable.img_product_shampoo
        "p9" -> R.drawable.img_product_carrier
        "p10" -> R.drawable.img_product_groom
        else -> if (product.petType.equals("Gatos", ignoreCase = true)) {
            R.drawable.img_product_scratcher
        } else {
            R.drawable.img_product_food
        }
    }
}
