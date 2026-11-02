package com.clubcanino.app.seller.data

import android.content.Context
import com.clubcanino.app.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-local catalog. Logout does not clear it.
 */
class ShopCatalogStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSavedCatalog(): Boolean = prefs.contains(KEY_PRODUCTS)

    fun load(): List<ShopProduct> {
        val raw = prefs.getString(KEY_PRODUCTS, null) ?: return emptyList()
        return decode(raw)
    }

    fun save(products: List<ShopProduct>) {
        prefs.edit()
            .putString(KEY_PRODUCTS, encode(products))
            .commit()
    }

    private fun encode(products: List<ShopProduct>): String {
        val array = JSONArray()
        products.forEach { product ->
            array.put(
                JSONObject().apply {
                    put(JSON_ID, product.id)
                    put(JSON_NAME, product.name)
                    put(JSON_DESCRIPTION, product.description)
                    put(JSON_PRICE, product.price)
                    put(JSON_CATEGORY, product.category)
                    put(JSON_PET_TYPE, product.petType)
                    put(JSON_STOCK, product.stock)
                    put(JSON_ACTIVE, product.active)
                    put(JSON_COLOR, product.imageColorRes)
                    put(JSON_ICON, product.imageIconRes)
                    put(JSON_FEATURED, product.featured)
                    put(JSON_PROMOTION, product.onPromotion)
                }
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<ShopProduct> {
        return try {
            val array = JSONArray(raw)
            val seen = HashSet<String>()
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val id = obj.optString(JSON_ID).trim()
                    val name = obj.optString(JSON_NAME).trim()
                    if (id.isEmpty() || name.isEmpty() || !seen.add(id)) continue
                    add(
                        ShopProduct(
                            id = id,
                            name = name,
                            description = obj.optString(JSON_DESCRIPTION),
                            price = obj.optDouble(JSON_PRICE, 0.0),
                            category = obj.optString(JSON_CATEGORY).ifBlank { "Accesorios" },
                            petType = obj.optString(JSON_PET_TYPE).ifBlank { "Perros" },
                            stock = obj.optInt(JSON_STOCK, 0),
                            active = obj.optBoolean(JSON_ACTIVE, true),
                            imageColorRes = obj.optInt(JSON_COLOR, R.color.green_container),
                            imageIconRes = obj.optInt(JSON_ICON, R.drawable.ic_pet_dog),
                            featured = obj.optBoolean(JSON_FEATURED, false),
                            onPromotion = obj.optBoolean(JSON_PROMOTION, false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "club_canino_catalog"
        private const val KEY_PRODUCTS = "shop_products"
        private const val JSON_ID = "id"
        private const val JSON_NAME = "name"
        private const val JSON_DESCRIPTION = "description"
        private const val JSON_PRICE = "price"
        private const val JSON_CATEGORY = "category"
        private const val JSON_PET_TYPE = "petType"
        private const val JSON_STOCK = "stock"
        private const val JSON_ACTIVE = "active"
        private const val JSON_COLOR = "imageColorRes"
        private const val JSON_ICON = "imageIconRes"
        private const val JSON_FEATURED = "featured"
        private const val JSON_PROMOTION = "onPromotion"
    }
}
