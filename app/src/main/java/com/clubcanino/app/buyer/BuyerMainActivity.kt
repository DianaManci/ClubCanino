package com.clubcanino.app.buyer

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.buyer.data.BuyerDemoData
import com.clubcanino.app.buyer.ui.BuyerCartFragment
import com.clubcanino.app.buyer.ui.BuyerCatalogFragment
import com.clubcanino.app.buyer.ui.BuyerHomeFragment
import com.clubcanino.app.buyer.ui.BuyerProfileFragment
import com.clubcanino.app.navigation.RoleChrome
import com.clubcanino.app.navigation.RoleHostActivity

class BuyerMainActivity : RoleHostActivity() {

    private var pendingCatalogCategory: String = "Todos"
    private var pendingCatalogQuery: String = ""

    override fun bottomMenuRes(): Int = R.menu.menu_buyer

    override fun defaultSelectedItemId(): Int = R.id.nav_buyer_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BuyerDemoData.ensurePetsLoaded(this)
        RoleChrome.apply(this)
        // Reselecting Home always restores the buyer start screen.
        bottomNavigation().setOnItemReselectedListener { item ->
            if (item.itemId == R.id.nav_buyer_home) {
                showFragment(BuyerHomeFragment())
            }
        }
    }

    override fun createFragmentForItem(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_buyer_home -> BuyerHomeFragment()
        R.id.nav_buyer_catalog -> BuyerCatalogFragment.newInstance(
            category = pendingCatalogCategory,
            query = pendingCatalogQuery
        ).also {
            pendingCatalogCategory = "Todos"
            pendingCatalogQuery = ""
        }
        R.id.nav_buyer_cart -> BuyerCartFragment()
        R.id.nav_buyer_profile -> BuyerProfileFragment()
        else -> null
    }

    fun openCatalog(category: String = "Todos", query: String = "") {
        pendingCatalogCategory = category
        pendingCatalogQuery = query
        val nav = bottomNavigation()
        if (nav.selectedItemId == R.id.nav_buyer_catalog) {
            showFragment(
                BuyerCatalogFragment.newInstance(
                    category = pendingCatalogCategory,
                    query = pendingCatalogQuery
                )
            )
            pendingCatalogCategory = "Todos"
            pendingCatalogQuery = ""
        } else {
            nav.selectedItemId = R.id.nav_buyer_catalog
        }
    }

    fun openCart() {
        bottomNavigation().selectedItemId = R.id.nav_buyer_cart
    }

    fun openProfile() {
        bottomNavigation().selectedItemId = R.id.nav_buyer_profile
    }
}
