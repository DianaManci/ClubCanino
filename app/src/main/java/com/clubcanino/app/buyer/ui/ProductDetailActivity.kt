package com.clubcanino.app.buyer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.clubcanino.app.R
import com.clubcanino.app.buyer.data.CartStore
import com.clubcanino.app.seller.data.MarketCatalog
import com.clubcanino.app.seller.data.ShopCatalog
import com.google.android.material.button.MaterialButton

class ProductDetailActivity : AppCompatActivity() {

    private var quantity = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_product_detail)
        window.statusBarColor = getColor(R.color.buyer_forest)
        window.navigationBarColor = getColor(R.color.buyer_cream)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailScreen)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val header = findViewById<View>(R.id.detailHeader)
            val content = findViewById<View>(R.id.detailRoot)
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

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )

        ShopCatalog.bind(this)
        CartStore.bind(this)
        val productId = intent.getStringExtra(EXTRA_PRODUCT_ID).orEmpty()
        val product = MarketCatalog.product(productId) ?: ShopCatalog.buyerProduct(productId)
        if (product == null) {
            Toast.makeText(this, R.string.msg_product_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val image = findViewById<ImageView>(R.id.imageProduct)
        val textQuantity = findViewById<TextView>(R.id.textQuantity)
        findViewById<View>(R.id.imageContainer).clipToOutline = true
        RemoteImages.load(image, product.photoUrl, BuyerVisuals.photoRes(product))
        findViewById<TextView>(R.id.textName).text = product.name
        val availability = getString(
            if (product.available) R.string.product_available else R.string.product_unavailable
        )
        findViewById<TextView>(R.id.textMeta).text =
            "${product.petType} · ${product.category} · $availability"
        findViewById<TextView>(R.id.textPrice).text = MoneyFormat.format(product.price)
        findViewById<TextView>(R.id.textDescription).text = product.description
        textQuantity.text = quantity.toString()

        val buttonMinus = findViewById<MaterialButton>(R.id.buttonMinus)
        val buttonPlus = findViewById<MaterialButton>(R.id.buttonPlus)
        val buttonAddToCart = findViewById<MaterialButton>(R.id.buttonAddToCart)
        val maxQuantity = product.stock.coerceAtLeast(1)
        buttonMinus.isEnabled = product.available
        buttonPlus.isEnabled = product.available
        buttonAddToCart.isEnabled = product.available
        buttonMinus.setOnClickListener {
            if (quantity > 1) {
                quantity--
                textQuantity.text = quantity.toString()
            }
        }
        buttonPlus.setOnClickListener {
            if (quantity < maxQuantity) {
                quantity++
                textQuantity.text = quantity.toString()
            }
        }
        buttonAddToCart.setOnClickListener {
            if (!product.available) return@setOnClickListener
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
