package com.clubcanino.app.seller.data

import android.content.Context
import com.clubcanino.app.R
import com.clubcanino.app.buyer.data.BuyerDemoData
import com.clubcanino.app.buyer.data.Product

/**
 * Catalog shared by the buyer, the seller and the admin.
 * Demo products are the first saved copy. Later edits stay on the device.
 */
object ShopCatalog {

    const val LOW_STOCK_AT = 5

    val categories = listOf("Alimentos", "Accesorios", "Salud")
    val petTypes = listOf("Perros", "Gatos")

    private val productsInternal = mutableListOf<ShopProduct>()
    private var loaded = false
    private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
        ensureLoaded()
    }

    private val seedStock = listOf(24, 8, 3, 15, 2, 40, 6, 0, 12, 18)

    fun products(): List<ShopProduct> {
        ensureLoaded()
        return productsInternal.toList()
    }

    fun productById(id: String): ShopProduct? {
        ensureLoaded()
        return productsInternal.find { it.id == id }
    }

    fun activeProducts(): List<ShopProduct> {
        ensureLoaded()
        return productsInternal.filter { it.active }
    }

    fun lowStockCount(): Int {
        ensureLoaded()
        return productsInternal.count { it.stock <= LOW_STOCK_AT }
    }

    fun search(query: String): List<ShopProduct> {
        ensureLoaded()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return productsInternal.toList()
        return productsInternal.filter { product ->
            product.name.lowercase().contains(q) ||
                product.description.lowercase().contains(q) ||
                product.category.lowercase().contains(q) ||
                product.petType.lowercase().contains(q)
        }
    }

    fun add(
        name: String,
        description: String,
        price: Double,
        category: String,
        petType: String,
        stock: Int,
        active: Boolean
    ): ShopProduct {
        ensureLoaded()
        val product = ShopProduct(
            id = "sp_${System.currentTimeMillis()}",
            name = name,
            description = description,
            price = price,
            category = category,
            petType = petType,
            stock = stock,
            active = active,
            imageColorRes = colorFor(category),
            imageIconRes = iconFor(petType)
        )
        if (productsInternal.any { it.id == product.id }) return productsInternal.first { it.id == product.id }
        productsInternal.add(product)
        persist()
        return product
    }

    fun update(
        id: String,
        name: String,
        description: String,
        price: Double,
        category: String,
        petType: String,
        stock: Int,
        active: Boolean
    ): Boolean {
        ensureLoaded()
        val index = productsInternal.indexOfFirst { it.id == id }
        if (index < 0) return false
        val current = productsInternal[index]
        productsInternal[index] = current.copy(
            name = name,
            description = description,
            price = price,
            category = category,
            petType = petType,
            stock = stock,
            active = active,
            imageColorRes = colorFor(category),
            imageIconRes = iconFor(petType)
        )
        persist()
        return true
    }

    fun remove(id: String): Boolean {
        ensureLoaded()
        val removed = productsInternal.removeAll { it.id == id }
        if (removed) persist()
        return removed
    }

    fun forBuyer(category: String, query: String): List<Product> {
        ensureLoaded()
        val q = query.trim().lowercase()
        return activeProducts().map { it.toBuyerProduct() }.filter { product ->
            val matchesCategory = when (category) {
                "Todos", "" -> true
                "Promociones" -> product.onPromotion
                "Perros", "Gatos" -> product.petType.equals(category, ignoreCase = true)
                else -> product.category.equals(category, ignoreCase = true)
            }
            val matchesQuery = q.isEmpty() ||
                product.name.lowercase().contains(q) ||
                product.description.lowercase().contains(q) ||
                product.category.lowercase().contains(q)
            matchesCategory && matchesQuery
        }
    }

    fun featuredForBuyer(): List<Product> {
        ensureLoaded()
        return activeProducts()
            .filter { it.featured }
            .map { it.toBuyerProduct() }
    }

    fun buyerProduct(id: String): Product? {
        ensureLoaded()
        val product = productsInternal.find { it.id == id && it.active } ?: return null
        return product.toBuyerProduct()
    }

    fun canonicalCategory(raw: String): String? =
        categories.find { it.equals(raw.trim(), ignoreCase = true) }

    fun canonicalPetType(raw: String): String? =
        petTypes.find { it.equals(raw.trim(), ignoreCase = true) }

    private fun ensureLoaded() {
        if (loaded) return
        val context = appContext ?: return
        loaded = true
        val store = ShopCatalogStore(context)
        if (store.hasSavedCatalog()) {
            productsInternal.clear()
            productsInternal.addAll(store.load())
        } else {
            productsInternal.clear()
            productsInternal.addAll(demoProducts())
            persist()
        }
    }

    private fun persist() {
        val context = appContext ?: return
        ShopCatalogStore(context).save(productsInternal.toList())
    }

    private fun demoProducts(): List<ShopProduct> {
        val seen = HashSet<String>()
        return BuyerDemoData.products.mapIndexedNotNull { index, product ->
            if (!seen.add(product.id)) return@mapIndexedNotNull null
            ShopProduct(
                id = product.id,
                name = product.name,
                description = product.description,
                price = product.price,
                category = product.category,
                petType = product.petType,
                stock = seedStock.getOrElse(index) { 10 },
                active = product.id != "p8",
                imageColorRes = product.imageColorRes,
                imageIconRes = product.imageIconRes,
                featured = product.featured,
                onPromotion = product.onPromotion
            )
        }
    }

    private fun ShopProduct.toBuyerProduct(): Product = Product(
        id = id,
        name = name,
        description = description,
        price = price,
        category = category,
        petType = petType,
        featured = featured,
        onPromotion = onPromotion,
        imageColorRes = imageColorRes,
        imageIconRes = imageIconRes,
        stock = stock,
        available = active && stock > 0
    )

    private fun iconFor(petType: String): Int =
        if (petType.equals("Gatos", ignoreCase = true)) {
            R.drawable.ic_pet_cat
        } else {
            R.drawable.ic_pet_dog
        }

    private fun colorFor(category: String): Int = when (category.lowercase()) {
        "alimentos" -> R.color.green_container
        "salud" -> R.color.success_container
        else -> R.color.accent_warm_container
    }
}
