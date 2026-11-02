package com.clubcanino.app.buyer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.clubcanino.app.R
import com.clubcanino.app.buyer.data.CartStore
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
        val image = view.findViewById<ImageView>(R.id.imageProduct)
        (image.parent as View).clipToOutline = true
        image.setImageResource(BuyerVisuals.photoRes(product))
        view.findViewById<TextView>(R.id.textName).text = product.name
        view.findViewById<TextView>(R.id.textCategory).text = if (product.available) {
            view.context.getString(R.string.product_card_meta, product.petType, product.category)
        } else {
            view.context.getString(
                R.string.product_card_meta_unavailable,
                product.petType,
                product.category
            )
        }
        view.findViewById<TextView>(R.id.textPrice).text = MoneyFormat.format(product.price)

        val badge = view.findViewById<TextView>(R.id.textBadge)
        when {
            product.onPromotion -> {
                badge.visibility = View.VISIBLE
                badge.text = view.context.getString(R.string.badge_offer)
                badge.setBackgroundResource(R.drawable.bg_buyer_badge_offer)
            }
            product.featured -> {
                badge.visibility = View.VISIBLE
                badge.text = view.context.getString(R.string.badge_new)
                badge.setBackgroundResource(R.drawable.bg_buyer_badge_new)
            }
            else -> badge.visibility = View.GONE
        }

        val addButton = view.findViewById<View>(R.id.buttonAddToCart)
        addButton.isEnabled = product.available
        addButton.alpha = if (product.available) 1f else 0.4f
        addButton.setOnClickListener {
            if (!product.available) return@setOnClickListener
            CartStore.add(product, 1)
            Toast.makeText(view.context, R.string.msg_added_to_cart, Toast.LENGTH_SHORT).show()
        }
        view.setOnClickListener { onClick(product) }
    }
}
