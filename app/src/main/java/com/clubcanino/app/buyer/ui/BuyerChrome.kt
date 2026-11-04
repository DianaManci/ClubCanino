package com.clubcanino.app.buyer.ui

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.buyer.BuyerMainActivity
import com.clubcanino.app.buyer.data.CartStore
import com.google.android.material.button.MaterialButton

object BuyerChrome {

    fun styleChip(button: MaterialButton, selected: Boolean) {
        val context = button.context
        button.isAllCaps = false
        button.cornerRadius = context.resources.getDimensionPixelSize(R.dimen.radius_full)
        button.insetTop = 0
        button.insetBottom = 0
        button.minimumWidth = 0
        button.minimumHeight = context.resources.getDimensionPixelSize(R.dimen.button_height_small)
        button.elevation = 0f
        val horizontal = context.resources.getDimensionPixelSize(R.dimen.spacing_lg)
        button.setPadding(horizontal, 0, horizontal, 0)
        if (selected) {
            button.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.buyer_forest)
            )
            button.setTextColor(ContextCompat.getColor(context, R.color.white))
            button.strokeWidth = 0
        } else {
            button.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.white)
            )
            button.setTextColor(ContextCompat.getColor(context, R.color.buyer_forest))
            button.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.buyer_chip_stroke)
            button.strokeColor = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.buyer_outline)
            )
        }
    }
}

class BuyerHeaderBinder(
    private val fragment: Fragment,
    private val root: View
) {
    private val listener: () -> Unit = { refreshBadge() }

    fun bind() {
        root.findViewById<View>(R.id.buttonHeaderCart).setOnClickListener {
            (fragment.activity as? BuyerMainActivity)?.openCart()
        }
        refreshBadge()
    }

    fun start() {
        CartStore.bind(root.context)
        CartStore.addListener(listener)
        refreshBadge()
    }

    fun stop() {
        CartStore.removeListener(listener)
    }

    private fun refreshBadge() {
        val badge = root.findViewById<TextView>(R.id.textCartBadge) ?: return
        val count = CartStore.itemCount()
        badge.text = if (count > 9) "9+" else count.toString()
        badge.visibility = if (count > 0) View.VISIBLE else View.GONE
    }
}
