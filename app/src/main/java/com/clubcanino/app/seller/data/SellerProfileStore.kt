package com.clubcanino.app.seller.data

data class SellerProfile(
    val fullName: String,
    val storeName: String,
    val email: String,
    val phone: String,
    val city: String
)

object SellerProfileStore {

    private var profile = SellerProfile(
        fullName = "Camila Ruiz",
        storeName = "Tienda Club Canino",
        email = "camila.ruiz@clubcanino.demo",
        phone = "+57 310 444 8899",
        city = "Medellín"
    )

    fun get(): SellerProfile = profile

    fun update(updated: SellerProfile) {
        profile = updated
    }
}
