package com.clubcanino.app.admin.data

import android.content.Context
import com.clubcanino.app.auth.PasswordHasher
import org.json.JSONArray
import org.json.JSONObject

enum class ShopUserWrite {
    SAVED,
    DUPLICATE_EMAIL,
    LAST_ADMIN,
    NOT_FOUND
}

object ShopUserStore {

    const val ROLE_BUYER = "Comprador"
    const val ROLE_SELLER = "Vendedor"
    const val ROLE_ADMIN = "Administrador"

    /**
     * Initial password for the demo accounts and for users created by the admin.
     * Only its salted hash is stored.
     */
    const val INITIAL_PASSWORD = "demo123"

    val roles = listOf(ROLE_BUYER, ROLE_SELLER, ROLE_ADMIN)

    private val usersInternal = mutableListOf<ShopUser>()
    private val passwords = mutableMapOf<String, PasswordHasher.StoredPassword>()
    private var loaded = false
    private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
        ensureLoaded()
    }

    fun users(): List<ShopUser> {
        ensureLoaded()
        return usersInternal.toList()
    }

    fun count(): Int {
        ensureLoaded()
        return usersInternal.size
    }

    fun canonicalRole(raw: String): String? =
        roles.find { it.equals(raw.trim(), ignoreCase = true) }

    fun findByEmail(email: String): ShopUser? {
        ensureLoaded()
        val key = email.trim()
        if (key.isEmpty()) return null
        return usersInternal.find { it.email.equals(key, ignoreCase = true) }
    }

    fun authenticate(email: String, password: String): ShopUser? {
        ensureLoaded()
        val user = usersInternal.find { it.email.equals(email.trim(), ignoreCase = true) } ?: return null
        val stored = passwords[user.id] ?: return null
        return if (PasswordHasher.matches(password, stored.salt, stored.hash)) user else null
    }

    fun register(fullName: String, email: String, password: String): ShopUserWrite {
        return create(
            fullName = fullName,
            email = email,
            phone = DEFAULT_PHONE,
            role = ROLE_BUYER,
            password = password
        )
    }

    fun add(fullName: String, email: String, phone: String, role: String): ShopUserWrite {
        return add(fullName, email, phone, role, INITIAL_PASSWORD)
    }

    fun add(
        fullName: String,
        email: String,
        phone: String,
        role: String,
        password: String
    ): ShopUserWrite {
        return create(fullName, email, phone, role, password)
    }

    fun update(
        id: String,
        fullName: String,
        email: String,
        phone: String,
        role: String,
        newPassword: String? = null
    ): ShopUserWrite {
        ensureLoaded()
        val index = usersInternal.indexOfFirst { it.id == id }
        if (index < 0) return ShopUserWrite.NOT_FOUND
        if (emailTaken(email, ignoreId = id)) return ShopUserWrite.DUPLICATE_EMAIL
        val current = usersInternal[index]
        if (removesLastAdmin(current, role)) return ShopUserWrite.LAST_ADMIN
        usersInternal[index] = current.copy(
            fullName = fullName,
            email = email.trim(),
            phone = phone,
            role = role
        )
        if (!newPassword.isNullOrEmpty()) {
            passwords[id] = PasswordHasher.create(newPassword)
        }
        persist()
        return ShopUserWrite.SAVED
    }

    fun remove(id: String): ShopUserWrite {
        ensureLoaded()
        val user = usersInternal.find { it.id == id } ?: return ShopUserWrite.NOT_FOUND
        if (user.role == ROLE_ADMIN && adminCount() <= 1) return ShopUserWrite.LAST_ADMIN
        usersInternal.removeAll { it.id == id }
        passwords.remove(id)
        persist()
        return ShopUserWrite.SAVED
    }

    private fun create(
        fullName: String,
        email: String,
        phone: String,
        role: String,
        password: String
    ): ShopUserWrite {
        ensureLoaded()
        if (emailTaken(email, ignoreId = null)) return ShopUserWrite.DUPLICATE_EMAIL
        val user = ShopUser(
            id = "u_${System.currentTimeMillis()}",
            fullName = fullName,
            email = email.trim(),
            phone = phone,
            role = role
        )
        usersInternal.add(user)
        passwords[user.id] = PasswordHasher.create(password)
        persist()
        return ShopUserWrite.SAVED
    }

    private fun ensureLoaded() {
        if (loaded) return
        val context = appContext ?: return
        loaded = true
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USERS, null)
        if (raw != null) {
            read(raw)
            if (usersInternal.isEmpty()) {
                seedDemoUsers()
                persist()
            }
        } else {
            seedDemoUsers()
            persist()
        }
    }

    private fun seedDemoUsers() {
        usersInternal.clear()
        passwords.clear()
        listOf(
            ShopUser("u1", "Ana Gómez", "ana.gomez@clubcanino.demo", "+57 300 555 1212", ROLE_BUYER),
            ShopUser("u2", "Luis Herrera", "luis.herrera@clubcanino.demo", "+57 301 222 3344", ROLE_BUYER),
            ShopUser("u3", "Camila Ruiz", "camila.ruiz@clubcanino.demo", "+57 310 444 8899", ROLE_SELLER),
            ShopUser("u4", "Diego Salas", "diego.salas@clubcanino.demo", "+57 315 777 1010", ROLE_SELLER),
            ShopUser("u5", "Valeria Ortiz", "valeria.ortiz@clubcanino.demo", "+57 320 888 4545", ROLE_ADMIN)
        ).forEach { user ->
            if (usersInternal.none { it.id == user.id || it.email.equals(user.email, true) }) {
                usersInternal.add(user)
                passwords[user.id] = PasswordHasher.create(INITIAL_PASSWORD)
            }
        }
    }

    private fun persist() {
        val context = appContext ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USERS, write())
            .commit()
    }

    private fun write(): String {
        val array = JSONArray()
        usersInternal.forEach { user ->
            val password = passwords[user.id] ?: PasswordHasher.create(INITIAL_PASSWORD).also {
                passwords[user.id] = it
            }
            array.put(
                JSONObject().apply {
                    put(JSON_ID, user.id)
                    put(JSON_NAME, user.fullName)
                    put(JSON_EMAIL, user.email)
                    put(JSON_PHONE, user.phone)
                    put(JSON_ROLE, user.role)
                    put(JSON_SALT, password.salt)
                    put(JSON_HASH, password.hash)
                }
            )
        }
        return array.toString()
    }

    private fun read(raw: String) {
        usersInternal.clear()
        passwords.clear()
        try {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val id = obj.optString(JSON_ID).trim()
                val email = obj.optString(JSON_EMAIL).trim()
                val name = obj.optString(JSON_NAME).trim()
                val role = canonicalRole(obj.optString(JSON_ROLE)).orEmpty()
                val salt = obj.optString(JSON_SALT)
                val hash = obj.optString(JSON_HASH)
                if (id.isEmpty() || email.isEmpty() || name.isEmpty() || role.isEmpty()) continue
                if (usersInternal.any { it.id == id || it.email.equals(email, true) }) continue
                if (salt.isEmpty() || hash.isEmpty()) continue
                usersInternal.add(
                    ShopUser(
                        id = id,
                        fullName = name,
                        email = email,
                        phone = obj.optString(JSON_PHONE),
                        role = role
                    )
                )
                passwords[id] = PasswordHasher.StoredPassword(salt, hash)
            }
        } catch (_: Exception) {
            usersInternal.clear()
            passwords.clear()
        }
    }

    private fun adminCount(): Int = usersInternal.count { it.role == ROLE_ADMIN }

    private fun removesLastAdmin(current: ShopUser, newRole: String): Boolean {
        return current.role == ROLE_ADMIN && newRole != ROLE_ADMIN && adminCount() <= 1
    }

    private fun emailTaken(email: String, ignoreId: String?): Boolean {
        val normalized = email.trim().lowercase()
        return usersInternal.any { user ->
            user.id != ignoreId && user.email.trim().lowercase() == normalized
        }
    }

    private const val PREFS_NAME = "club_canino_users"
    private const val KEY_USERS = "shop_users"
    private const val DEFAULT_PHONE = "Sin teléfono"
    private const val JSON_ID = "id"
    private const val JSON_NAME = "fullName"
    private const val JSON_EMAIL = "email"
    private const val JSON_PHONE = "phone"
    private const val JSON_ROLE = "role"
    private const val JSON_SALT = "passwordSalt"
    private const val JSON_HASH = "passwordHash"
}
