package com.clubcanino.app.buyer.data

import android.content.Context
import com.clubcanino.app.R
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.auth.FirebaseGate

object BuyerDemoData {

    val categories = listOf(
        "Todos",
        "Perros",
        "Gatos",
        "Alimentos",
        "Accesorios",
        "Salud",
        "Promociones"
    )

    private var appContext: Context? = null
    private var loadedBuyerId: String? = null
    private var petsReady = false
    private val petsInternal = mutableListOf<Pet>()

    fun profile(context: Context): BuyerProfile {
        ShopUserStore.bind(context)
        val email = BuyerSession.currentEmail(context).orEmpty()
        if (email.isEmpty()) {
            return BuyerProfile(fullName = "", email = "", phone = "", city = "")
        }
        val user = ShopUserStore.findByEmail(email)
        return BuyerProfile(
            fullName = user?.fullName?.takeIf { it.isNotBlank() } ?: email,
            email = user?.email?.takeIf { it.isNotBlank() } ?: email,
            phone = user?.phone.orEmpty(),
            city = ""
        )
    }

    /**
     * Loads only the pets saved for the signed-in user. A new account starts empty.
     */
    fun ensurePetsLoaded(context: Context, onReady: (() -> Unit)? = null) {
        val app = context.applicationContext
        appContext = app
        val uid = FirebaseGate.auth(app)?.currentUser?.uid.orEmpty()
        val db = FirebaseGate.db(app)
        if (uid.isEmpty() || db == null) {
            loadedBuyerId = null
            petsReady = false
            petsInternal.clear()
            onReady?.invoke()
            return
        }
        if (loadedBuyerId == uid && petsReady) {
            onReady?.invoke()
            return
        }
        loadedBuyerId = uid
        petsReady = false
        petsInternal.clear()
        db.collection("users").document(uid).collection("pets").get()
            .addOnSuccessListener { query ->
                if (loadedBuyerId != uid) return@addOnSuccessListener
                petsInternal.clear()
                query.documents.forEach { doc ->
                    val name = doc.getString("name").orEmpty().trim()
                    if (name.isEmpty()) return@forEach
                    petsInternal.add(
                        Pet(
                            id = doc.id,
                            name = name,
                            species = doc.getString("species").orEmpty().ifBlank { "Mascota" },
                            breed = doc.getString("breed").orEmpty().ifBlank { "Sin raza" },
                            ageLabel = doc.getString("ageLabel").orEmpty().ifBlank { "Edad no indicada" }
                        )
                    )
                }
                petsReady = true
                onReady?.invoke()
            }
            .addOnFailureListener { onReady?.invoke() }
    }

    fun pets(): List<Pet> = petsInternal.toList()

    fun petById(id: String): Pet? = petsInternal.find { it.id == id }

    fun addPet(
        name: String,
        species: String,
        breed: String,
        ageLabel: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        val context = appContext
        val uid = loadedBuyerId
        val db = context?.let { FirebaseGate.db(it) }
        if (context == null || uid.isNullOrBlank() || db == null) {
            onDone(false)
            return
        }
        val data = mapOf(
            "name" to name,
            "species" to species,
            "breed" to breed,
            "ageLabel" to ageLabel
        )
        db.collection("users").document(uid).collection("pets").add(data)
            .addOnSuccessListener { ref ->
                petsInternal.add(Pet(ref.id, name, species, breed, ageLabel))
                onDone(true)
            }
            .addOnFailureListener { onDone(false) }
    }

    fun updatePet(
        id: String,
        name: String,
        species: String,
        breed: String,
        ageLabel: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        val context = appContext
        val uid = loadedBuyerId
        val db = context?.let { FirebaseGate.db(it) }
        val index = petsInternal.indexOfFirst { it.id == id }
        if (context == null || uid.isNullOrBlank() || db == null || index < 0) {
            onDone(false)
            return
        }
        val data = mapOf(
            "name" to name,
            "species" to species,
            "breed" to breed,
            "ageLabel" to ageLabel
        )
        db.collection("users").document(uid).collection("pets").document(id).set(data)
            .addOnSuccessListener {
                petsInternal[index] = Pet(id, name, species, breed, ageLabel)
                onDone(true)
            }
            .addOnFailureListener { onDone(false) }
    }

    fun removePet(id: String, onDone: (Boolean) -> Unit = {}) {
        val context = appContext
        val uid = loadedBuyerId
        val db = context?.let { FirebaseGate.db(it) }
        if (context == null || uid.isNullOrBlank() || db == null) {
            onDone(false)
            return
        }
        db.collection("users").document(uid).collection("pets").document(id).delete()
            .addOnSuccessListener {
                petsInternal.removeAll { it.id == id }
                onDone(true)
            }
            .addOnFailureListener { onDone(false) }
    }

    val orders = listOf(
        OrderSummary("ORD-1042", "28 oct 2026", "Entregado", 129900.0, 3),
        OrderSummary("ORD-1038", "12 oct 2026", "En camino", 45900.0, 1),
        OrderSummary("ORD-1021", "30 sep 2026", "Entregado", 88900.0, 2)
    )

    val products = listOf(
        Product(
            id = "p1",
            name = "Croquetas Premium Adulto",
            description = "Alimento completo para perros adultos con proteína de pollo y omega 3.",
            price = 89900.0,
            category = "Alimentos",
            petType = "Perros",
            featured = true,
            onPromotion = true,
            imageColorRes = R.color.green_container,
            imageIconRes = R.drawable.ic_pet_dog
        ),
        Product(
            id = "p2",
            name = "Arena aglomerante",
            description = "Arena sanitaria de control de olores, ideal para gatos exigentes.",
            price = 32900.0,
            category = "Accesorios",
            petType = "Gatos",
            featured = true,
            imageColorRes = R.color.accent_warm_container,
            imageIconRes = R.drawable.ic_pet_cat
        ),
        Product(
            id = "p3",
            name = "Juguete mordedor resistente",
            description = "Caucho natural para morder y reducir el estrés dental.",
            price = 24900.0,
            category = "Accesorios",
            petType = "Perros",
            featured = true,
            imageColorRes = R.color.cream_surface_variant,
            imageIconRes = R.drawable.ic_pet_dog
        ),
        Product(
            id = "p4",
            name = "Antipulgas spot-on",
            description = "Tratamiento mensual para perros medianos. Consulta veterinaria recomendada.",
            price = 55900.0,
            category = "Salud",
            petType = "Perros",
            onPromotion = true,
            imageColorRes = R.color.success_container,
            imageIconRes = R.drawable.ic_pet_dog
        ),
        Product(
            id = "p5",
            name = "Snacks dentales felinos",
            description = "Premios crujientes que ayudan a limpiar el sarro de tu gato.",
            price = 18900.0,
            category = "Alimentos",
            petType = "Gatos",
            featured = true,
            imageColorRes = R.color.warning_container,
            imageIconRes = R.drawable.ic_pet_cat
        ),
        Product(
            id = "p6",
            name = "Collar reflectivo ajustable",
            description = "Collar ligero con hebilla de seguridad y costuras reforzadas.",
            price = 27900.0,
            category = "Accesorios",
            petType = "Perros",
            imageColorRes = R.color.green_container,
            imageIconRes = R.drawable.ic_pet_dog
        ),
        Product(
            id = "p7",
            name = "Rascador vertical",
            description = "Torre compacta con sisal natural para uñas y descanso.",
            price = 119900.0,
            category = "Accesorios",
            petType = "Gatos",
            featured = true,
            imageColorRes = R.color.accent_warm_container,
            imageIconRes = R.drawable.ic_pet_cat
        ),
        Product(
            id = "p8",
            name = "Shampoo hipoalergénico",
            description = "Fórmula suave sin fragancias agresivas para piel sensible.",
            price = 34900.0,
            category = "Salud",
            petType = "Perros",
            onPromotion = true,
            imageColorRes = R.color.cream_surface_variant,
            imageIconRes = R.drawable.ic_pet_dog
        ),
        Product(
            id = "p9",
            name = "Transportín ligero",
            description = "Ideal para visitas al veterinario. Ventilación lateral y correa segura.",
            price = 149900.0,
            category = "Accesorios",
            petType = "Gatos",
            imageColorRes = R.color.success_container,
            imageIconRes = R.drawable.ic_pet_cat
        ),
        Product(
            id = "p10",
            name = "Kit cepillo + peine",
            description = "Set de aseo para reducir nudos y pelo suelto en casa.",
            price = 39900.0,
            category = "Accesorios",
            petType = "Perros",
            featured = true,
            imageColorRes = R.color.warning_container,
            imageIconRes = R.drawable.ic_pet_dog
        )
    )

    fun productById(id: String): Product? = products.find { it.id == id }

    fun featured(): List<Product> = products.filter { it.featured }

    fun promotions(): List<Product> = products.filter { it.onPromotion }

    fun filter(category: String, query: String): List<Product> {
        val q = query.trim().lowercase()
        return products.filter { product ->
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
}
