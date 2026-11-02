package com.clubcanino.app.seller.data

object ShopInput {

    fun parsePrice(raw: String): Double? {
        val trimmed = raw.trim().replace(" ", "").replace("$", "")
        if (trimmed.isEmpty()) return null
        val normalized = when {
            trimmed.matches(Regex("""\d{1,3}(\.\d{3})+""")) -> trimmed.replace(".", "")
            trimmed.matches(Regex("""\d+,\d{1,2}""")) -> trimmed.replace(',', '.')
            trimmed.matches(Regex("""\d+(\.\d{1,2})?""")) -> trimmed
            else -> return null
        }
        return normalized.toDoubleOrNull()
    }

    fun parseStock(raw: String): Int? = raw.trim().toIntOrNull()
}
