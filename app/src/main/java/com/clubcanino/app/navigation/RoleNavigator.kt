package com.clubcanino.app.navigation

import android.content.Context
import android.content.Intent
import com.clubcanino.app.admin.AdminMainActivity
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.buyer.BuyerMainActivity
import com.clubcanino.app.common.UserRole
import com.clubcanino.app.seller.SellerMainActivity

object RoleNavigator {

    fun openRoleHome(context: Context, role: UserRole) {
        val destination = when (role) {
            UserRole.BUYER -> BuyerMainActivity::class.java
            UserRole.SELLER -> SellerMainActivity::class.java
            UserRole.ADMIN -> AdminMainActivity::class.java
        }
        context.startActivity(Intent(context, destination))
    }

    fun roleOf(shopRole: String): UserRole = when (shopRole) {
        ShopUserStore.ROLE_ADMIN -> UserRole.ADMIN
        ShopUserStore.ROLE_SELLER -> UserRole.SELLER
        else -> UserRole.BUYER
    }
}
