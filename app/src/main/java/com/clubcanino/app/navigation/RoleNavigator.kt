package com.clubcanino.app.navigation

import android.content.Context
import android.content.Intent
import com.clubcanino.app.admin.AdminMainActivity
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.buyer.BuyerMainActivity
import com.clubcanino.app.common.UserRole
import com.clubcanino.app.seller.SellerMainActivity

/** Abre el panel que corresponde al rol guardado en Firestore y no deja atrás las pantallas de acceso. */
object RoleNavigator {

    fun openRoleHome(context: Context, role: UserRole) {
        val destination = when (role) {
            UserRole.BUYER -> BuyerMainActivity::class.java
            UserRole.SELLER -> SellerMainActivity::class.java
            UserRole.ADMIN -> AdminMainActivity::class.java
        }
        context.startActivity(
            Intent(context, destination).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        )
    }

    fun roleOf(shopRole: String): UserRole = when (shopRole) {
        ShopUserStore.ROLE_ADMIN -> UserRole.ADMIN
        ShopUserStore.ROLE_SELLER -> UserRole.SELLER
        else -> UserRole.BUYER
    }
}
