package com.clubcanino.app.seller.data

import android.content.Context
import com.clubcanino.app.R
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.buyer.data.BuyerSession
import org.json.JSONObject

data class SellerProfile(
    val fullName: String,
    val storeName: String,
    val email: String,
    val phone: String,
    val city: String
)

/**
 * One saved profile per seller email. A seller only reads and writes their own record.
 */
object SellerProfileStore {

    private val profiles = mutableMapOf<String, SellerProfile>()
    private var loaded = false
    private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
        ensureLoaded()
    }

    fun current(context: Context): SellerProfile {
        bind(context)
        val email = BuyerSession.currentEmail(context)?.let(BuyerSession::normalizeEmail).orEmpty()
        if (email.isEmpty()) {
            return SellerProfile(
                fullName = "",
                storeName = context.getString(R.string.seller_store_default),
                email = "",
                phone = "",
                city = ""
            )
        }
        profiles[email]?.let { saved ->
            return saved.copy(email = email)
        }
        ShopUserStore.bind(context)
        val user = ShopUserStore.findByEmail(email)
        return SellerProfile(
            fullName = user?.fullName?.takeIf { it.isNotBlank() } ?: email,
            storeName = user?.storeName?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.seller_store_default),
            email = email,
            phone = user?.phone.orEmpty(),
            city = ""
        )
    }

    fun update(context: Context, profile: SellerProfile) {
        bind(context)
        val email = BuyerSession.currentEmail(context)?.let(BuyerSession::normalizeEmail) ?: return
        profiles[email] = profile.copy(email = email)
        persist()
        ShopUserStore.saveStoreProfile(context, profile.fullName, profile.phone, profile.storeName)
    }

    private fun ensureLoaded() {
        if (loaded) return
        val context = appContext ?: return
        loaded = true
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROFILES, null) ?: return
        read(raw)
    }

    private fun persist() {
        val context = appContext ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILES, write())
            .commit()
    }

    private fun write(): String {
        val root = JSONObject()
        profiles.forEach { (email, profile) ->
            root.put(
                email,
                JSONObject().apply {
                    put(JSON_NAME, profile.fullName)
                    put(JSON_STORE, profile.storeName)
                    put(JSON_EMAIL, email)
                    put(JSON_PHONE, profile.phone)
                    put(JSON_CITY, profile.city)
                }
            )
        }
        return root.toString()
    }

    private fun read(raw: String) {
        profiles.clear()
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next()
                val email = BuyerSession.normalizeEmail(rawKey)
                val obj = root.optJSONObject(rawKey) ?: continue
                val name = obj.optString(JSON_NAME).trim()
                val store = obj.optString(JSON_STORE).trim()
                if (email.isEmpty() || name.isEmpty() || store.isEmpty()) continue
                if (profiles.containsKey(email)) continue
                profiles[email] = SellerProfile(
                    fullName = name,
                    storeName = store,
                    email = email,
                    phone = obj.optString(JSON_PHONE),
                    city = obj.optString(JSON_CITY)
                )
            }
        } catch (_: Exception) {
            profiles.clear()
        }
    }

    private const val PREFS_NAME = "club_canino_seller_profiles"
    private const val KEY_PROFILES = "seller_profiles"
    private const val JSON_NAME = "fullName"
    private const val JSON_STORE = "storeName"
    private const val JSON_EMAIL = "email"
    private const val JSON_PHONE = "phone"
    private const val JSON_CITY = "city"
}
