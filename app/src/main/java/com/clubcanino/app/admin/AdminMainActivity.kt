package com.clubcanino.app.admin

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.clubcanino.app.R
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.admin.ui.AdminDashboardFragment
import com.clubcanino.app.admin.ui.AdminProductsFragment
import com.clubcanino.app.admin.ui.AdminSalesFragment
import com.clubcanino.app.admin.ui.AdminUsersFragment
import com.clubcanino.app.navigation.RoleChrome
import com.clubcanino.app.navigation.RoleHostActivity

class AdminMainActivity : RoleHostActivity() {

    override fun bottomMenuRes(): Int = R.menu.menu_admin

    override fun defaultSelectedItemId(): Int = R.id.nav_admin_dashboard

    override fun requiredRole(): String = ShopUserStore.ROLE_ADMIN

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
                    RoleChrome.bindReturnToRoles(v, this@AdminMainActivity)
                }
            },
            false
        )
    }

    override fun createFragmentForItem(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_admin_dashboard -> AdminDashboardFragment()
        R.id.nav_admin_users -> AdminUsersFragment()
        R.id.nav_admin_products -> AdminProductsFragment()
        R.id.nav_admin_sales -> AdminSalesFragment()
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
