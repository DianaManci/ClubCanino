package com.clubcanino.app.buyer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.buyer.data.CartStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class BuyerCartFragment : Fragment() {

    private lateinit var containerItems: LinearLayout
    private lateinit var textEmpty: TextView
    private lateinit var scrollCart: View
    private lateinit var cardTotals: MaterialCardView
    private lateinit var textSubtotal: TextView
    private lateinit var textTotal: TextView
    private lateinit var buttonCheckout: MaterialButton

    private val cartListener: () -> Unit = { renderCart() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_buyer_cart, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        containerItems = view.findViewById(R.id.containerCartItems)
        textEmpty = view.findViewById(R.id.textEmptyCart)
        scrollCart = view.findViewById(R.id.scrollCart)
        cardTotals = view.findViewById(R.id.cardTotals)
        textSubtotal = view.findViewById(R.id.textSubtotal)
        textTotal = view.findViewById(R.id.textTotal)
        buttonCheckout = view.findViewById(R.id.buttonCheckout)

        buttonCheckout.setOnClickListener {
            if (CartStore.items().isEmpty()) {
                Toast.makeText(requireContext(), R.string.buyer_cart_empty, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.msg_checkout_demo, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        CartStore.addListener(cartListener)
        renderCart()
    }

    override fun onStop() {
        CartStore.removeListener(cartListener)
        super.onStop()
    }

    private fun renderCart() {
        val items = CartStore.items()
        containerItems.removeAllViews()
        val empty = items.isEmpty()
        textEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        scrollCart.visibility = if (empty) View.GONE else View.VISIBLE
        cardTotals.visibility = if (empty) View.GONE else View.VISIBLE
        buttonCheckout.isEnabled = !empty

        val inflater = layoutInflater
        items.forEach { item ->
            val row = inflater.inflate(R.layout.item_cart_line, containerItems, false)
            row.findViewById<TextView>(R.id.textName).text = item.product.name
            row.findViewById<TextView>(R.id.textUnitPrice).text =
                getString(R.string.cart_unit_price, MoneyFormat.format(item.product.price))
            val textQuantity = row.findViewById<TextView>(R.id.textQuantity)
            val textLineTotal = row.findViewById<TextView>(R.id.textLineTotal)
            textQuantity.text = item.quantity.toString()
            textLineTotal.text = MoneyFormat.format(item.lineTotal)

            row.findViewById<MaterialButton>(R.id.buttonMinus).setOnClickListener {
                CartStore.setQuantity(item.product.id, item.quantity - 1)
            }
            row.findViewById<MaterialButton>(R.id.buttonPlus).setOnClickListener {
                CartStore.setQuantity(item.product.id, item.quantity + 1)
            }
            row.findViewById<MaterialButton>(R.id.buttonRemove).setOnClickListener {
                CartStore.remove(item.product.id)
            }
            containerItems.addView(row)
        }

        val subtotal = CartStore.subtotal()
        textSubtotal.text = getString(R.string.cart_subtotal, MoneyFormat.format(subtotal))
        textTotal.text = getString(R.string.cart_total, MoneyFormat.format(subtotal))
    }
}
