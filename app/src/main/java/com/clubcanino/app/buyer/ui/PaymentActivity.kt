package com.clubcanino.app.buyer.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.clubcanino.app.R
import com.clubcanino.app.buyer.data.BuyerDemoData
import com.clubcanino.app.buyer.data.CartStore
import com.clubcanino.app.seller.data.ShopOrder
import com.clubcanino.app.seller.data.ShopOrderLine
import com.clubcanino.app.seller.data.ShopOrders
import com.google.android.material.button.MaterialButton
import java.util.Calendar

class PaymentActivity : AppCompatActivity() {

    private var purchaseConfirmed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShopOrders.bind(this)
        CartStore.bind(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_payment)
        window.statusBarColor = getColor(R.color.buyer_forest)
        window.navigationBarColor = getColor(R.color.buyer_cream)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.paymentScreen)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val header = findViewById<View>(R.id.paymentHeader)
            val content = findViewById<View>(R.id.paymentRoot)
            val screenPad = resources.getDimensionPixelSize(R.dimen.screen_padding_horizontal)
            header.setPadding(
                bars.left + resources.getDimensionPixelSize(R.dimen.spacing_xs),
                bars.top + resources.getDimensionPixelSize(R.dimen.spacing_sm),
                bars.right + screenPad,
                resources.getDimensionPixelSize(R.dimen.spacing_md)
            )
            content.setPadding(
                bars.left + screenPad,
                resources.getDimensionPixelSize(R.dimen.spacing_lg),
                bars.right + screenPad,
                bars.bottom + resources.getDimensionPixelSize(R.dimen.spacing_xl)
            )
            insets
        }

        if (CartStore.items().isEmpty()) {
            Toast.makeText(this, R.string.buyer_cart_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        renderSummary()

        val methodError = findViewById<TextView>(R.id.textMethodError)
        findViewById<RadioGroup>(R.id.groupPaymentMethods).setOnCheckedChangeListener { _, _ ->
            methodError.visibility = View.GONE
        }
        findViewById<MaterialButton>(R.id.buttonConfirmPurchase).setOnClickListener {
            confirmPurchase()
        }
        val leave = View.OnClickListener { finish() }
        findViewById<ImageButton>(R.id.buttonNavigateBack).setOnClickListener(leave)
        findViewById<MaterialButton>(R.id.buttonCancelPurchase).setOnClickListener(leave)
        findViewById<MaterialButton>(R.id.buttonBackToStore).setOnClickListener(leave)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )
    }

    private fun renderSummary() {
        val container = findViewById<LinearLayout>(R.id.containerPaymentLines)
        container.removeAllViews()
        val gap = resources.getDimensionPixelSize(R.dimen.spacing_sm)
        CartStore.items().forEach { item ->
            val block = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            val name = TextView(this).apply {
                text = item.product.name
                setTextColor(getColor(R.color.buyer_text))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_medium))
            }
            val meta = TextView(this).apply {
                text = getString(
                    R.string.payment_line_meta,
                    item.quantity,
                    MoneyFormat.format(item.product.price),
                    MoneyFormat.format(item.lineTotal)
                )
                setTextColor(getColor(R.color.buyer_text_muted))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_small))
            }
            block.addView(name)
            block.addView(meta)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = gap
            container.addView(block, params)
        }
        findViewById<TextView>(R.id.textPaymentTotal).text =
            getString(R.string.cart_total, MoneyFormat.format(CartStore.subtotal()))
    }

    private fun confirmPurchase() {
        if (purchaseConfirmed) return
        val methods = findViewById<RadioGroup>(R.id.groupPaymentMethods)
        val selectedId = methods.checkedRadioButtonId
        if (selectedId == -1) {
            findViewById<TextView>(R.id.textMethodError).visibility = View.VISIBLE
            return
        }
        val items = CartStore.items()
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.buyer_cart_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val method = findViewById<RadioButton>(selectedId).text.toString()
        val buyer = BuyerDemoData.profile(this)
        if (buyer.fullName.isBlank() || buyer.email.isBlank()) {
            Toast.makeText(this, R.string.msg_error_generic, Toast.LENGTH_SHORT).show()
            return
        }
        val lines = items.map { item ->
            ShopOrderLine(
                productId = item.product.id,
                productName = item.product.name,
                quantity = item.quantity,
                unitPrice = item.product.price
            )
        }
        val order = ShopOrders.place(buyer.fullName, lines, todayLabel(), buyer.email)
        if (order == null) {
            Toast.makeText(this, R.string.msg_error_generic, Toast.LENGTH_SHORT).show()
            return
        }
        purchaseConfirmed = true
        CartStore.clear()
        showConfirmation(order, buyer.fullName, method)
    }

    private fun showConfirmation(order: ShopOrder, buyerName: String, method: String) {
        findViewById<View>(R.id.groupCheckout).visibility = View.GONE
        findViewById<View>(R.id.groupConfirmation).visibility = View.VISIBLE
        findViewById<TextView>(R.id.textConfirmationBody).text = getString(
            R.string.payment_success_body,
            order.id,
            order.status
        )
        findViewById<TextView>(R.id.textConfirmationId).text = order.id
        findViewById<TextView>(R.id.textConfirmationBuyer).text =
            getString(R.string.payment_buyer_line, buyerName)
        findViewById<TextView>(R.id.textConfirmationDate).text =
            getString(R.string.payment_date_line, order.dateLabel)
        findViewById<TextView>(R.id.textConfirmationStatus).text =
            getString(R.string.payment_status_line, order.status)
        findViewById<TextView>(R.id.textConfirmationMethod).text =
            getString(R.string.payment_method_line, method)
        findViewById<TextView>(R.id.textConfirmationTotal).text =
            getString(R.string.cart_total, MoneyFormat.format(order.total))
    }

    private fun todayLabel(): String {
        val calendar = Calendar.getInstance()
        val months = arrayOf(
            "ene", "feb", "mar", "abr", "may", "jun",
            "jul", "ago", "sep", "oct", "nov", "dic"
        )
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = months[calendar.get(Calendar.MONTH)]
        val year = calendar.get(Calendar.YEAR)
        return "$day $month $year"
    }
}
