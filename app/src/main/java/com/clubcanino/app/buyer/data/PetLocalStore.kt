package com.clubcanino.app.buyer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-local pet persistence with SharedPreferences + JSON.
 * Pets are stored per buyer email so accounts do not share lists.
 */
class PetLocalStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSavedPets(buyerId: String): Boolean =
        prefs.contains(petsKey(buyerId))

    fun load(buyerId: String): List<Pet> {
        val raw = prefs.getString(petsKey(buyerId), null) ?: return emptyList()
        return decode(raw)
    }

    fun save(buyerId: String, pets: List<Pet>) {
        prefs.edit()
            .putString(petsKey(buyerId), encode(pets))
            .apply()
    }

    private fun petsKey(buyerId: String): String =
        KEY_PETS_PREFIX + BuyerSession.normalizeEmail(buyerId)

    private fun encode(pets: List<Pet>): String {
        val array = JSONArray()
        pets.forEach { pet ->
            array.put(
                JSONObject().apply {
                    put(JSON_ID, pet.id)
                    put(JSON_NAME, pet.name)
                    put(JSON_SPECIES, pet.species)
                    put(JSON_BREED, pet.breed)
                    put(JSON_AGE, pet.ageLabel)
                }
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<Pet> {
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val id = obj.optString(JSON_ID).trim()
                    val name = obj.optString(JSON_NAME).trim()
                    if (id.isEmpty() || name.isEmpty()) continue
                    add(
                        Pet(
                            id = id,
                            name = name,
                            species = obj.optString(JSON_SPECIES).ifBlank { "Mascota" },
                            breed = obj.optString(JSON_BREED).ifBlank { "Sin raza" },
                            ageLabel = obj.optString(JSON_AGE).ifBlank { "Edad no indicada" }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "club_canino_pets"
        private const val KEY_PETS_PREFIX = "pets_"
        private const val JSON_ID = "id"
        private const val JSON_NAME = "name"
        private const val JSON_SPECIES = "species"
        private const val JSON_BREED = "breed"
        private const val JSON_AGE = "ageLabel"
    }
}
