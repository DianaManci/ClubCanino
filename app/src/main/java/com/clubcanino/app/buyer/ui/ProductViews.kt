package com.clubcanino.app.buyer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.clubcanino.app.R
import com.clubcanino.app.buyer.data.Product

object ProductViews {

    fun inflateProductCard(
        inflater: LayoutInflater,
        parent: ViewGroup,
        product: Product,
        onClick: (Product) -> Unit
    ): View {
        val view = inflater.inflate(R.layout.item_product_card, parent, false)
        bindProductCard(view, product, onClick)
        return view
    }

    fun bindProductCard(view: View, product: Product, onClick: (Product) -> Unit) {
        val imageContainer = view.findViewById<FrameLayout>(R.id.imageContainer)
        val image = view.findViewById<ImageView>(R.id.imageProduct)
        imageContainer.setBackgroundColor(view.context.getColor(product.imageColorRes))
        image.setImageResource(product.imageIconRes)
        view.findViewById<TextView>(R.id.textName).text = product.name
        view.findViewById<TextView>(R.id.textCategory).text =
            "${product.petType} · ${product.category}"
        view.findViewById<TextView>(R.id.textPrice).text = MoneyFormat.format(product.price)
        view.setOnClickListener { onClick(product) }
    }
}
