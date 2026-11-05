package com.clubcanino.app.admin.data

import android.content.Context
import com.clubcanino.app.auth.FirebaseGate
import com.clubcanino.app.buyer.data.BuyerSession
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.SetOptions

enum class ShopUserWrite {
    SAVED,
    DUPLICATE_EMAIL,
    LAST_ADMIN,
    NOT_FOUND,
    NOT_CONFIGURED,
    FAILED
}

object ShopUserStore {

    const val ROLE_BUYER = "Comprador"
    const val ROLE_SELLER = "Vendedor"
    const val ROLE_ADMIN = "Administrador"

    val roles = listOf(ROLE_BUYER, ROLE_SELLER, ROLE_ADMIN)

    private val usersInternal = mutableListOf<ShopUser>()
    private var appContext: Context? = null

    fun bind(context: Context, onReady: (() -> Unit)? = null) {
        appContext = context.applicationContext
        loadCurrent(context, onReady)
    }

    fun users(): List<ShopUser> = usersInternal.toList()

    fun count(): Int = usersInternal.size

    fun canonicalRole(raw: String): String? =
        roles.find { it.equals(raw.trim(), ignoreCase = true) }

    fun findByEmail(email: String): ShopUser? {
        val key = email.trim()
        if (key.isEmpty()) return null
        return usersInternal.find { it.email.equals(key, ignoreCase = true) }
    }

    fun loadCurrent(context: Context, onReady: (() -> Unit)? = null) {
        appContext = context.applicationContext
        val db = FirebaseGate.db(context)
        val uid = FirebaseGate.auth(context)?.currentUser?.uid
        if (db == null || uid.isNullOrBlank()) {
            onReady?.invoke()
            return
        }
        db.collection(USERS).document(uid).get()
            .addOnSuccessListener { snapshot ->
                snapshot.toUser(uid)?.let { remember(it) }
                onReady?.invoke()
            }
            .addOnFailureListener { onReady?.invoke() }
    }

    fun refresh(context: Context, onReady: () -> Unit) {
        appContext = context.applicationContext
        val db = FirebaseGate.db(context)
        if (db == null) {
            onReady()
            return
        }
        db.collection(USERS).get()
            .addOnSuccessListener { query ->
                usersInternal.clear()
                query.documents.forEach { doc ->
                    doc.toUser(doc.id)?.let { usersInternal.add(it) }
                }
                onReady()
            }
            .addOnFailureListener { onReady() }
    }

    fun signIn(
        context: Context,
        email: String,
        password: String,
        onResult: (ShopUser?) -> Unit
    ) {
        val auth = FirebaseGate.auth(context) ?: return onResult(null)
        val db = FirebaseGate.db(context) ?: return onResult(null)
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid.isNullOrBlank()) {
                    onResult(null)
                    return@addOnSuccessListener
                }
                db.collection(USERS).document(uid).get()
                    .addOnSuccessListener { snapshot ->
                        val user = snapshot.toUser(uid)
                        if (user == null) {
                            onResult(null)
                        } else {
                            remember(user)
                            onResult(user)
                        }
                    }
                    .addOnFailureListener { onResult(null) }
            }
            .addOnFailureListener { onResult(null) }
    }

    fun register(
        context: Context,
        fullName: String,
        email: String,
        password: String,
        role: String,
        storeName: String,
        onResult: (ShopUserWrite) -> Unit
    ) {
        val safeRole = if (role == ROLE_SELLER) ROLE_SELLER else ROLE_BUYER
        val auth = FirebaseGate.auth(context) ?: return onResult(ShopUserWrite.NOT_CONFIGURED)
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid.isNullOrBlank()) {
                    onResult(ShopUserWrite.FAILED)
                    return@addOnSuccessListener
                }
                writeUser(
                    context = context,
                    user = ShopUser(
                        id = uid,
                        fullName = fullName.trim(),
                        email = email.trim(),
                        phone = DEFAULT_PHONE,
                        role = safeRole,
                        storeName = if (safeRole == ROLE_SELLER) storeName.trim() else ""
                    ),
                    onResult = onResult
                )
            }
            .addOnFailureListener { error ->
                onResult(
                    if (error is FirebaseAuthUserCollisionException) {
                        ShopUserWrite.DUPLICATE_EMAIL
                    } else {
                        ShopUserWrite.FAILED
                    }
                )
            }
    }

    fun sendPasswordReset(context: Context, email: String, onResult: (Boolean) -> Unit) {
        val auth = FirebaseGate.auth(context) ?: return onResult(false)
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    fun add(
        context: Context,
        fullName: String,
        email: String,
        phone: String,
        role: String,
        password: String,
        onResult: (ShopUserWrite) -> Unit
    ) {
        val auth = FirebaseGate.accountAuth(context) ?: return onResult(ShopUserWrite.NOT_CONFIGURED)
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                auth.signOut()
                if (uid.isNullOrBlank()) {
                    onResult(ShopUserWrite.FAILED)
                    return@addOnSuccessListener
                }
                writeUser(
                    context,
                    ShopUser(uid, fullName.trim(), email.trim(), phone.trim(), role, ""),
                    onResult
                )
            }
            .addOnFailureListener { error ->
                auth.signOut()
                onResult(
                    if (error is FirebaseAuthUserCollisionException) {
                        ShopUserWrite.DUPLICATE_EMAIL
                    } else {
                        ShopUserWrite.FAILED
                    }
                )
            }
    }

    fun update(
        context: Context,
        id: String,
        fullName: String,
        email: String,
        phone: String,
        role: String,
        newPassword: String? = null,
        onResult: (ShopUserWrite) -> Unit
    ) {
        val current = usersInternal.find { it.id == id } ?: return onResult(ShopUserWrite.NOT_FOUND)
        if (emailTaken(email, id)) return onResult(ShopUserWrite.DUPLICATE_EMAIL)
        if (current.role == ROLE_ADMIN && role != ROLE_ADMIN && adminCount() <= 1) {
            return onResult(ShopUserWrite.LAST_ADMIN)
        }
        val updated = current.copy(
            fullName = fullName.trim(),
            email = email.trim(),
            phone = phone.trim(),
            role = role
        )
        writeUser(context, updated) { result ->
            if (result == ShopUserWrite.SAVED && !newPassword.isNullOrBlank()) {
                FirebaseGate.auth(context)?.sendPasswordResetEmail(updated.email)
            }
            onResult(result)
        }
    }

    fun remove(context: Context, id: String, onResult: (ShopUserWrite) -> Unit) {
        val user = usersInternal.find { it.id == id } ?: return onResult(ShopUserWrite.NOT_FOUND)
        if (user.role == ROLE_ADMIN && adminCount() <= 1) return onResult(ShopUserWrite.LAST_ADMIN)
        val db = FirebaseGate.db(context) ?: return onResult(ShopUserWrite.NOT_CONFIGURED)
        db.collection(USERS).document(id).delete()
            .addOnSuccessListener {
                usersInternal.removeAll { it.id == id }
                onResult(ShopUserWrite.SAVED)
            }
            .addOnFailureListener { onResult(ShopUserWrite.FAILED) }
    }

    fun saveStoreProfile(context: Context, fullName: String, phone: String, storeName: String) {
        val email = BuyerSession.currentEmail(context) ?: return
        val current = findByEmail(email) ?: return
        val updated = current.copy(
            fullName = fullName.trim(),
            phone = phone.trim(),
            storeName = storeName.trim()
        )
        writeUser(context, updated) {}
    }

    private fun writeUser(context: Context, user: ShopUser, onResult: (ShopUserWrite) -> Unit) {
        val db = FirebaseGate.db(context) ?: return onResult(ShopUserWrite.NOT_CONFIGURED)
        val data = mapOf(
            "fullName" to user.fullName,
            "email" to BuyerSession.normalizeEmail(user.email),
            "phone" to user.phone,
            "role" to user.role,
            "storeName" to user.storeName
        )
        db.collection(USERS).document(user.id).set(data, SetOptions.merge())
            .addOnSuccessListener {
                remember(user.copy(email = BuyerSession.normalizeEmail(user.email)))
                onResult(ShopUserWrite.SAVED)
            }
            .addOnFailureListener { onResult(ShopUserWrite.FAILED) }
    }

    private fun remember(user: ShopUser) {
        val index = usersInternal.indexOfFirst {
            it.id == user.id || it.email.equals(user.email, ignoreCase = true)
        }
        if (index >= 0) {
            usersInternal[index] = user
        } else {
            usersInternal.add(user)
        }
    }

    private fun emailTaken(email: String, ignoreId: String): Boolean =
        usersInternal.any { it.id != ignoreId && it.email.equals(email.trim(), ignoreCase = true) }

    private fun adminCount(): Int = usersInternal.count { it.role == ROLE_ADMIN }

    private fun com.google.firebase.firestore.DocumentSnapshot.toUser(id: String): ShopUser? {
        if (!exists()) return null
        val email = getString("email").orEmpty().trim()
        if (email.isEmpty()) return null
        return ShopUser(
            id = id,
            fullName = getString("fullName").orEmpty().ifBlank { email },
            email = email,
            phone = getString("phone").orEmpty(),
            role = getString("role").orEmpty().ifBlank { ROLE_BUYER },
            storeName = getString("storeName").orEmpty()
        )
    }

    private const val USERS = "users"
    private const val DEFAULT_PHONE = "Sin teléfono"
}
