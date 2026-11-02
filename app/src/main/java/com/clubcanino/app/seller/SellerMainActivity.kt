package com.clubcanino.app.seller

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.clubcanino.app.R
import com.clubcanino.app.navigation.RoleChrome
import com.clubcanino.app.navigation.RoleHostActivity
import com.clubcanino.app.seller.ui.SellerDashboardFragment
import com.clubcanino.app.seller.ui.SellerOrdersFragment
import com.clubcanino.app.seller.ui.SellerProductsFragment
import com.clubcanino.app.seller.ui.SellerProfileFragment

class SellerMainActivity : RoleHostActivity() {

    override fun bottomMenuRes(): Int = R.menu.menu_seller

    override fun defaultSelectedItemId(): Int = R.id.nav_seller_dashboard

    override fun onHostConfigured() {
        RoleChrome.apply(this)
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: android.os.Bundle?
                ) {
                    RoleChrome.bindReturnToRoles(v, this@SellerMainActivity)
                }
            },
            false
        )
    }

    override fun createFragmentForItem(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_seller_dashboard -> SellerDashboardFragment()
        R.id.nav_seller_products -> SellerProductsFragment()
        R.id.nav_seller_orders -> SellerOrdersFragment()
        R.id.nav_seller_profile -> SellerProfileFragment()
        else -> null
    }

    fun openTab(itemId: Int) {
        val navigation = bottomNavigation()
        if (navigation.selectedItemId == itemId) {
            createFragmentForItem(itemId)?.let { showFragment(it) }
        } else {
            navigation.selectedItemId = itemId
        }
    }
}
