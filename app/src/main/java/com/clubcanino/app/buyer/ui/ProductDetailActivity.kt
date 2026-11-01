package com.clubcanino.app.buyer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clubcanino.app.R
import com.clubcanino.app.buyer.data.BuyerDemoData
import com.clubcanino.app.buyer.data.CartStore
import com.google.android.material.button.MaterialButton

class ProductDetailActivity : AppCompatActivity() {

    private var quantity = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_product_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft + bars.left,
                view.paddingTop + bars.top,
                view.paddingRight + bars.right,
                view.paddingBottom + bars.bottom
            )
            insets
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )

        val productId = intent.getStringExtra(EXTRA_PRODUCT_ID).orEmpty()
        val product = BuyerDemoData.productById(productId)
        if (product == null) {
            Toast.makeText(this, R.string.msg_product_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val imageContainer = findViewById<FrameLayout>(R.id.imageContainer)
        val image = findViewById<ImageView>(R.id.imageProduct)
        val textQuantity = findViewById<TextView>(R.id.textQuantity)

        imageContainer.setBackgroundColor(getColor(product.imageColorRes))
        image.setImageResource(product.imageIconRes)
        findViewById<TextView>(R.id.textName).text = product.name
        findViewById<TextView>(R.id.textMeta).text = "${product.petType} · ${product.category}"
        findViewById<TextView>(R.id.textPrice).text = MoneyFormat.format(product.price)
        findViewById<TextView>(R.id.textDescription).text = product.description
        textQuantity.text = quantity.toString()

        findViewById<MaterialButton>(R.id.buttonMinus).setOnClickListener {
            if (quantity > 1) {
                quantity--
                textQuantity.text = quantity.toString()
            }
        }
        findViewById<MaterialButton>(R.id.buttonPlus).setOnClickListener {
            quantity++
            textQuantity.text = quantity.toString()
        }
        findViewById<MaterialButton>(R.id.buttonAddToCart).setOnClickListener {
            CartStore.add(product, quantity)
            Toast.makeText(this, R.string.msg_added_to_cart, Toast.LENGTH_SHORT).show()
        }

        val goBack = View.OnClickListener { finish() }
        findViewById<ImageButton>(R.id.buttonNavigateBack).setOnClickListener(goBack)
        findViewById<MaterialButton>(R.id.buttonBack).setOnClickListener(goBack)
    }

    companion object {
        private const val EXTRA_PRODUCT_ID = "extra_product_id"

        fun createIntent(context: Context, productId: String): Intent {
            return Intent(context, ProductDetailActivity::class.java).putExtra(
                EXTRA_PRODUCT_ID,
                productId
            )
        }
    }
}
