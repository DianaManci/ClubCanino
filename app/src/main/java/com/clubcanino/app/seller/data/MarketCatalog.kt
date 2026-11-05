package com.clubcanino.app.seller.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.clubcanino.app.R
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.auth.FirebaseGate
import com.clubcanino.app.buyer.data.BuyerSession
import com.clubcanino.app.buyer.data.Product
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream

object MarketCatalog {

    private val products = mutableListOf<ShopProduct>()

    fun refresh(context: Context, onReady: () -> Unit) {
        val db = FirebaseGate.db(context)
        if (db == null) {
            onReady()
            return
        }
        db.collection(PRODUCTS).get()
            .addOnSuccessListener { query ->
                products.clear()
                query.documents.forEach { doc ->
                    val name = doc.getString("name").orEmpty().trim()
                    if (name.isEmpty()) return@forEach
                    products.add(
                        ShopProduct(
                            id = doc.id,
                            name = name,
                            description = doc.getString("description").orEmpty(),
                            price = doc.getDouble("price") ?: 0.0,
                            category = doc.getString("category").orEmpty().ifBlank { "Accesorios" },
                            petType = doc.getString("petType").orEmpty().ifBlank { "Perros" },
                            stock = (doc.getLong("stock") ?: 1L).toInt(),
                            active = doc.getBoolean("active") ?: true,
                            imageColorRes = R.color.green_container,
                            imageIconRes = R.drawable.ic_pet_dog,
                            sellerId = doc.getString("sellerId").orEmpty(),
                            storeName = doc.getString("storeName").orEmpty(),
                            photoUrl = doc.getString("photoUrl").orEmpty()
                        )
                    )
                }
                onReady()
            }
            .addOnFailureListener { onReady() }
    }

    fun forBuyer(category: String, query: String): List<Product> {
        val q = query.trim().lowercase()
        return products.filter { it.active && it.stock > 0 }.map { it.toProduct() }.filter { product ->
            val matchesCategory = when (category) {
                "Todos", "" -> true
                "Promociones" -> product.onPromotion
                "Perros", "Gatos" -> product.petType.equals(category, ignoreCase = true)
                else -> product.category.equals(category, ignoreCase = true)
            }
            val matchesQuery = q.isEmpty() ||
                product.name.lowercase().contains(q) ||
                product.description.lowercase().contains(q) ||
                product.storeName.lowercase().contains(q)
            matchesCategory && matchesQuery
        }
    }

    fun featured(): List<Product> = products.filter { it.active }.take(8).map { it.toProduct() }

    fun product(id: String): Product? = products.find { it.id == id }?.toProduct()

    fun search(sellerId: String, query: String): List<ShopProduct> {
        val q = query.trim().lowercase()
        return products.filter { product ->
            product.sellerId == sellerId &&
                (q.isEmpty() || product.name.lowercase().contains(q) || product.description.lowercase().contains(q))
        }
    }

    fun shopProduct(id: String): ShopProduct? = products.find { it.id == id }

    fun save(
        context: Context,
        existingId: String?,
        name: String,
        description: String,
        price: Double,
        category: String,
        petType: String,
        stock: Int,
        active: Boolean,
        photo: Uri?,
        onResult: (Boolean) -> Unit
    ) {
        val db = FirebaseGate.db(context) ?: return onResult(false)
        val uid = FirebaseGate.auth(context)?.currentUser?.uid ?: return onResult(false)
        ShopUserStore.loadCurrent(context) {
            val email = BuyerSession.currentEmail(context).orEmpty()
            val storeName = ShopUserStore.findByEmail(email)?.storeName.orEmpty().ifBlank { "Mi tienda" }
            saveWithStore(context, db, uid, storeName, existingId, name, description, price, category, petType, stock, active, photo, onResult)
        }
    }

    private fun saveWithStore(
        context: Context,
        db: com.google.firebase.firestore.FirebaseFirestore,
        uid: String,
        storeName: String,
        existingId: String?,
        name: String,
        description: String,
        price: Double,
        category: String,
        petType: String,
        stock: Int,
        active: Boolean,
        photo: Uri?,
        onResult: (Boolean) -> Unit
    ) {
        val id = existingId ?: db.collection(PRODUCTS).document().id
        val current = products.find { it.id == id }
        if (photo == null && current?.photoUrl.isNullOrBlank()) {
            onResult(false)
            return
        }
        if (photo == null) {
            writeProduct(context, id, uid, storeName, name, description, price, category, petType, stock, active, current?.photoUrl.orEmpty(), onResult)
            return
        }
        val bytes = jpegBytes(context, photo) ?: return onResult(false)
        val storage = FirebaseGate.storage(context) ?: return onResult(false)
        val ref = storage.reference.child("products/$uid/$id.jpg")
        ref.putBytes(bytes)
            .continueWithTask { ref.downloadUrl }
            .addOnSuccessListener { url ->
                writeProduct(context, id, uid, storeName, name, description, price, category, petType, stock, active, url.toString(), onResult)
            }
            .addOnFailureListener { onResult(false) }
    }

    fun remove(context: Context, id: String, onResult: (Boolean) -> Unit) {
        val db = FirebaseGate.db(context) ?: return onResult(false)
        db.collection(PRODUCTS).document(id).delete()
            .addOnSuccessListener {
                products.removeAll { it.id == id }
                onResult(true)
            }
            .addOnFailureListener { onResult(false) }
    }

    private fun writeProduct(
        context: Context,
        id: String,
        sellerId: String,
        storeName: String,
        name: String,
        description: String,
        price: Double,
        category: String,
        petType: String,
        stock: Int,
        active: Boolean,
        photoUrl: String,
        onResult: (Boolean) -> Unit
    ) {
        val db = FirebaseGate.db(context) ?: return onResult(false)
        val data = mapOf(
            "sellerId" to sellerId,
            "storeName" to storeName,
            "name" to name,
            "description" to description,
            "price" to price,
            "category" to category,
            "petType" to petType,
            "stock" to stock,
            "active" to active,
            "photoUrl" to photoUrl
        )
        db.collection(PRODUCTS).document(id).set(data, SetOptions.merge())
            .addOnSuccessListener {
                val product = ShopProduct(
                    id = id,
                    name = name,
                    description = description,
                    price = price,
                    category = category,
                    petType = petType,
                    stock = stock,
                    active = active,
                    imageColorRes = R.color.green_container,
                    imageIconRes = R.drawable.ic_pet_dog,
                    sellerId = sellerId,
                    storeName = storeName,
                    photoUrl = photoUrl
                )
                val index = products.indexOfFirst { it.id == id }
                if (index >= 0) products[index] = product else products.add(product)
                onResult(true)
            }
            .addOnFailureListener { onResult(false) }
    }

    private fun jpegBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            val source = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return null
            val maxSide = 1280
            val largest = maxOf(source.width, source.height).coerceAtLeast(1)
            val scale = minOf(1f, maxSide.toFloat() / largest)
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                source
            }
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
                output.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ShopProduct.toProduct(): Product = Product(
        id = id,
        name = name,
        description = description,
        price = price,
        category = category,
        petType = petType,
        imageColorRes = imageColorRes,
        imageIconRes = imageIconRes,
        stock = stock,
        available = active && stock > 0,
        sellerId = sellerId,
        storeName = storeName,
        photoUrl = photoUrl
    )

    private const val PRODUCTS = "products"
}
