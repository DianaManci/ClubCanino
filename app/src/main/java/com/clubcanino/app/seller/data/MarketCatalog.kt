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
import com.google.firebase.storage.StorageMetadata
import java.io.ByteArrayOutputStream

/**
 * Catálogo de productos en Firestore. La foto se intenta guardar en Storage;
 * si ese servicio no acepta el archivo, el producto igual queda guardado.
 */
object MarketCatalog {

    enum class ProductWrite {
        SAVED,
        SAVED_WITHOUT_PHOTO,
        FAILED
    }

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

    fun searchAll(query: String): List<ShopProduct> = filterQuery(products, query)

    fun activeCount(sellerId: String?): Int = scoped(sellerId).count { it.active }

    fun lowStockCount(sellerId: String?): Int =
        scoped(sellerId).count { it.active && it.stock in 0..3 }

    private fun scoped(sellerId: String?): List<ShopProduct> =
        if (sellerId.isNullOrBlank()) products else products.filter { it.sellerId == sellerId }

    private fun filterQuery(source: List<ShopProduct>, query: String): List<ShopProduct> {
        val q = query.trim().lowercase()
        return source.filter { product ->
            q.isEmpty() || product.name.lowercase().contains(q) || product.description.lowercase().contains(q)
        }
    }

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
        onResult: (ProductWrite) -> Unit
    ) {
        val db = FirebaseGate.db(context) ?: return onResult(ProductWrite.FAILED)
        val authUid = FirebaseGate.auth(context)?.currentUser?.uid ?: return onResult(ProductWrite.FAILED)
        ShopUserStore.loadCurrent(context) {
            val existing = existingId?.let { shopProduct(it) }
            val adminEditing = BuyerSession.currentRole(context) == ShopUserStore.ROLE_ADMIN && existing != null
            val sellerId = if (adminEditing) existing?.sellerId.orEmpty().ifBlank { authUid } else authUid
            val email = BuyerSession.currentEmail(context).orEmpty()
            val storeName = if (adminEditing) {
                existing?.storeName.orEmpty().ifBlank { "Club Canino" }
            } else {
                ShopUserStore.findByEmail(email)?.storeName.orEmpty().ifBlank { "Mi tienda" }
            }
            saveWithStore(context, db, authUid, sellerId, storeName, existingId, name, description, price, category, petType, stock, active, photo, onResult)
        }
    }

    private fun saveWithStore(
        context: Context,
        db: com.google.firebase.firestore.FirebaseFirestore,
        authUid: String,
        sellerId: String,
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
        onResult: (ProductWrite) -> Unit
    ) {
        val id = existingId ?: db.collection(PRODUCTS).document().id
        val current = products.find { it.id == id }
        fun finish(saved: Boolean, withoutPhoto: Boolean) {
            onResult(
                when {
                    !saved -> ProductWrite.FAILED
                    withoutPhoto -> ProductWrite.SAVED_WITHOUT_PHOTO
                    else -> ProductWrite.SAVED
                }
            )
        }
        if (photo == null && current?.photoUrl.isNullOrBlank()) {
            writeProduct(
                context, id, sellerId, storeName, name, description, price, category,
                petType, stock, active, ""
            ) { finish(it, false) }
            return
        }
        if (photo == null) {
            writeProduct(
                context, id, sellerId, storeName, name, description, price, category,
                petType, stock, active, current?.photoUrl.orEmpty()
            ) { finish(it, false) }
            return
        }
        val previousPhoto = current?.photoUrl.orEmpty()
        val bytes = jpegBytes(context, photo)
        val storage = FirebaseGate.storage(context)
        if (bytes == null || storage == null) {
            writeProduct(
                context, id, sellerId, storeName, name, description, price, category,
                petType, stock, active, previousPhoto
            ) { finish(it, true) }
            return
        }
        val ref = storage.reference.child("products/$authUid/$id.jpg")
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        ref.putBytes(bytes, metadata)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("upload")
                ref.downloadUrl
            }
            .addOnSuccessListener { url ->
                val photoUrl = url.toString()
                if (!photoUrl.startsWith("https://")) {
                    writeProduct(
                        context, id, sellerId, storeName, name, description, price, category,
                        petType, stock, active, previousPhoto
                    ) { finish(it, true) }
                    return@addOnSuccessListener
                }
                writeProduct(
                    context, id, sellerId, storeName, name, description, price, category,
                    petType, stock, active, photoUrl
                ) { finish(it, false) }
            }
            .addOnFailureListener {
                writeProduct(
                    context, id, sellerId, storeName, name, description, price, category,
                    petType, stock, active, previousPhoto
                ) { finish(it, true) }
            }
    }

    fun remove(context: Context, id: String, onResult: (Boolean) -> Unit) {
        val db = FirebaseGate.db(context) ?: return onResult(false)
        val photoUrl = products.find { it.id == id }?.photoUrl.orEmpty()
        db.collection(PRODUCTS).document(id).delete()
            .addOnSuccessListener {
                products.removeAll { it.id == id }
                deleteStoredPhoto(context, photoUrl)
                onResult(true)
            }
            .addOnFailureListener { onResult(false) }
    }

    private fun deleteStoredPhoto(context: Context, photoUrl: String) {
        val path = ProductPhotoPath.fromDownloadUrl(photoUrl) ?: return
        val storage = FirebaseGate.storage(context) ?: return
        storage.reference.child(path).delete()
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
