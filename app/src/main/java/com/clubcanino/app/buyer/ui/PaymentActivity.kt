package com.clubcanino.app.buyer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import com.clubcanino.app.R
import com.clubcanino.app.buyer.data.BuyerDemoData
import com.clubcanino.app.buyer.data.CartStore
import com.clubcanino.app.buyer.data.PurchaseAttempt
import com.clubcanino.app.seller.data.ShopOrderLine
import com.clubcanino.app.seller.data.ShopOrderStatus
import com.clubcanino.app.seller.data.ShopOrders
import com.google.android.material.button.MaterialButton
import java.util.Calendar
import java.util.UUID
import android.os.Handler
import android.os.Looper

/** Checkout del comprador. Guarda la dirección y la ubicación en un pedido pendiente y solo abre ePayco si el backend está configurado. */
class PaymentActivity : AppCompatActivity() {

    private var purchaseConfirmed = false
    private var detectedAddress = ""
    private var latitude = 0.0
    private var longitude = 0.0
    private var requestAttempt = PurchaseAttempt.Attempt("", "")
    private val attempts by lazy { PurchaseAttempt.PreferenceAttemptMemory(this) }
    private var pendingOrderId: String? = null
    private var placing = false
    private var attemptToken = 0
    private val checkoutTimeout = Handler(Looper.getMainLooper())

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!alive()) return@registerForActivityResult
        if (granted) readLocation() else showLocationStatus(getString(R.string.payment_location_denied))
    }

    private val epaycoPayment = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (!alive()) return@registerForActivityResult
        findViewById<MaterialButton>(R.id.buttonConfirmPurchase).isEnabled = true
        val reference = result.data?.getStringExtra(EpaycoActivity.EXTRA_REF).orEmpty()
        val accepted = result.resultCode == RESULT_OK &&
            result.data?.getBooleanExtra(EpaycoActivity.EXTRA_ACCEPTED, false) == true &&
            reference.isNotBlank()
        if (!accepted) {
            findViewById<MaterialButton>(R.id.buttonConfirmPurchase).isEnabled = true
            val error = result.data?.getStringExtra(EpaycoActivity.EXTRA_ERROR).orEmpty()
            Toast.makeText(this, unpaidMessage(error), Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        val confirmedSignature = attempts.checkoutSignature()?.takeIf { it.isNotBlank() }
            ?: requestAttempt.signature
        attempts.forget(confirmedSignature)
        purchaseConfirmed = true
        val total = CartStore.subtotal()
        CartStore.clear()
        showConfirmation(pendingOrderId.orEmpty(), reference, total, paid = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreAttempt()
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
        loadDeliveryLocation()

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
        if (purchaseConfirmed || placing) return
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
        val address = resolvedAddress()
        if (address.isBlank()) {
            findViewById<TextView>(R.id.textMethodError).apply {
                text = getString(R.string.payment_address_required)
                visibility = View.VISIBLE
            }
            return
        }
        val cardCheckout = selectedId == R.id.radioPayCard
        if (cardCheckout && !ClubBackend.isConfigured()) {
            Toast.makeText(this, R.string.payment_backend_missing, Toast.LENGTH_LONG).show()
            return
        }
        val lines = items.map { item ->
            ShopOrderLine(
                productId = item.product.id,
                productName = item.product.name,
                quantity = item.quantity,
                unitPrice = item.product.price,
                sellerId = item.product.sellerId
            )
        }
        val signature = PurchaseAttempt.signature(lines, address, method)
        requestAttempt = PurchaseAttempt.resume(
            attempts,
            requestAttempt,
            signature,
            UUID.randomUUID().toString()
        )
        placing = true
        val token = ++attemptToken
        findViewById<MaterialButton>(R.id.buttonConfirmPurchase).isEnabled = false
        checkoutTimeout.removeCallbacksAndMessages(null)
        checkoutTimeout.postDelayed({
            if (token != attemptToken || !alive()) return@postDelayed
            attemptToken++
            placing = false
            findViewById<MaterialButton>(R.id.buttonConfirmPurchase).isEnabled = true
            Toast.makeText(this, R.string.payment_order_timeout, Toast.LENGTH_LONG).show()
        }, ORDER_TIMEOUT_MS)
        ShopOrders.placePending(
            context = this,
            requestId = requestAttempt.requestId,
            buyerName = buyer.fullName,
            buyerEmail = buyer.email,
            lines = lines,
            dateLabel = todayLabel(),
            address = address,
            latitude = latitude,
            longitude = longitude,
            paymentMethod = method
        ) { placement ->
            if (!alive() || token != attemptToken) return@placePending
            checkoutTimeout.removeCallbacksAndMessages(null)
            placing = false
            val orderId = placement.orderId
            if (orderId.isNullOrBlank()) {
                findViewById<MaterialButton>(R.id.buttonConfirmPurchase).isEnabled = true
                Toast.makeText(this, unpaidMessage(placement.failure.orEmpty()), Toast.LENGTH_LONG).show()
                return@placePending
            }
            pendingOrderId = orderId
            if (cardCheckout) {
                openCardCheckout(orderId, token)
            } else {
                registerUnpaidOrder(orderId)
            }
        }
    }

    private fun openCardCheckout(orderId: String, token: Int) {
        placing = true
        findViewById<MaterialButton>(R.id.buttonConfirmPurchase).isEnabled = false
        checkoutTimeout.removeCallbacksAndMessages(null)
        checkoutTimeout.postDelayed({
            if (token != attemptToken || !alive()) return@postDelayed
            attemptToken++
            placing = false
            findViewById<MaterialButton>(R.id.buttonConfirmPurchase).isEnabled = true
            Toast.makeText(this, R.string.payment_order_timeout, Toast.LENGTH_LONG).show()
        }, ORDER_TIMEOUT_MS)
        ClubBackend.paymentAvailability { ready, error ->
            if (!alive() || token != attemptToken) return@paymentAvailability
            checkoutTimeout.removeCallbacksAndMessages(null)
            placing = false
            if (!ready) {
                if (error == "network" || error == "unauthorized") {
                    findViewById<MaterialButton>(R.id.buttonConfirmPurchase).isEnabled = true
                    Toast.makeText(this, R.string.payment_order_network, Toast.LENGTH_LONG).show()
                    return@paymentAvailability
                }
                registerUnpaidOrder(orderId)
                return@paymentAvailability
            }
            epaycoPayment.launch(EpaycoActivity.createIntent(this, orderId))
        }
    }

    private fun registerUnpaidOrder(orderId: String) {
        val signature = attempts.checkoutSignature()?.takeIf { it.isNotBlank() }
            ?: requestAttempt.signature
        attempts.forget(signature)
        purchaseConfirmed = true
        val total = CartStore.subtotal()
        CartStore.clear()
        showConfirmation(orderId, "", total, paid = false)
    }

    private fun unpaidMessage(error: String): Int = when (error) {
        "permission" -> R.string.payment_order_denied
        "index" -> R.string.payment_order_index
        "network", "unauthorized" -> R.string.payment_order_network
        "epayco" -> R.string.payment_epayco_error
        "timeout" -> R.string.payment_order_timeout
        "data" -> R.string.msg_error_generic
        else -> R.string.payment_order_saved_unpaid
    }

    private fun resolvedAddress(): String {
        val manual = findViewById<EditText>(R.id.editManualAddress).text?.toString()?.trim().orEmpty()
        return manual.ifBlank { detectedAddress }
    }

    private fun restoreAttempt() {
        val signature = attempts.checkoutSignature() ?: return
        val requestId = attempts.requestIdFor(signature) ?: return
        requestAttempt = PurchaseAttempt.Attempt(requestId, signature)
    }

    private fun alive(): Boolean = !isFinishing && !isDestroyed

    override fun onDestroy() {
        checkoutTimeout.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun loadDeliveryLocation() {
        showLocationStatus(getString(R.string.payment_location_waiting))
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) readLocation() else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun readLocation() {
        val client = LocationServices.getFusedLocationProviderClient(this)
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (!alive()) return@addOnSuccessListener
                if (location == null) {
                    showLocationStatus(getString(R.string.payment_location_missing))
                    return@addOnSuccessListener
                }
                latitude = location.latitude
                longitude = location.longitude
                Thread {
                    val label = geocode(location.latitude, location.longitude)
                    runOnUiThread {
                        if (alive()) acceptDetectedAddress(label)
                    }
                }.start()
            }
            .addOnFailureListener {
                if (alive()) showLocationStatus(getString(R.string.payment_location_missing))
            }
    }

    private fun geocode(lat: Double, lng: Double): String {
        return try {
            @Suppress("DEPRECATION")
            val place = Geocoder(this, Locale("es", "CO")).getFromLocation(lat, lng, 1)?.firstOrNull()
            place?.getAddressLine(0)?.takeIf { it.isNotBlank() }
                ?: getString(R.string.payment_location_coordinates, lat, lng)
        } catch (_: Exception) {
            getString(R.string.payment_location_coordinates, lat, lng)
        }
    }

    private fun showLocationStatus(text: String) {
        if (!alive()) return
        findViewById<TextView>(R.id.textDeliveryAddress).text = text
    }

    private fun acceptDetectedAddress(text: String) {
        detectedAddress = text
        showLocationStatus(text)
    }

    private fun showConfirmation(orderId: String, paymentRef: String, total: Double, paid: Boolean) {
        val buyerName = BuyerDemoData.profile(this).fullName
        val method = findViewById<RadioGroup>(R.id.groupPaymentMethods).checkedRadioButtonId
            .let { id -> if (id == -1) "" else findViewById<RadioButton>(id).text.toString() }
        val status = if (paid) ShopOrderStatus.PAYMENT_APPROVED else ShopOrderStatus.PAYMENT_PENDING
        val detail = if (paid && paymentRef.isNotBlank()) {
            getString(R.string.payment_epayco_confirmed, paymentRef)
        } else {
            getString(R.string.payment_registered_unpaid)
        }
        findViewById<View>(R.id.groupCheckout).visibility = View.GONE
        findViewById<View>(R.id.groupConfirmation).visibility = View.VISIBLE
        findViewById<TextView>(R.id.textConfirmationBody).text = getString(
            R.string.payment_success_body,
            orderId,
            status
        ) + "\n" + resolvedAddress() + "\n" + detail
        findViewById<TextView>(R.id.textConfirmationId).text = orderId
        findViewById<TextView>(R.id.textConfirmationBuyer).text =
            getString(R.string.payment_buyer_line, buyerName)
        findViewById<TextView>(R.id.textConfirmationDate).text =
            getString(R.string.payment_date_line, todayLabel())
        findViewById<TextView>(R.id.textConfirmationStatus).text =
            getString(R.string.payment_status_line, status)
        findViewById<TextView>(R.id.textConfirmationMethod).text =
            getString(R.string.payment_method_line, method)
        findViewById<TextView>(R.id.textConfirmationTotal).text =
            getString(R.string.cart_total, MoneyFormat.format(total))
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

    companion object {
        private const val ORDER_TIMEOUT_MS = 25_000L
    }
}
