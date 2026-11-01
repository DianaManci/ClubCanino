package com.clubcanino.app.seller

import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.common.RolePlaceholderFragment
import com.clubcanino.app.navigation.RoleHostActivity

class SellerMainActivity : RoleHostActivity() {

    override fun bottomMenuRes(): Int = R.menu.menu_seller

    override fun defaultSelectedItemId(): Int = R.id.nav_seller_dashboard

    override fun createFragmentForItem(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_seller_dashboard -> RolePlaceholderFragment.newInstance(
            R.string.seller_dashboard_title,
            R.string.seller_dashboard_subtitle
        )
        R.id.nav_seller_products -> RolePlaceholderFragment.newInstance(
            R.string.seller_products_title,
            R.string.seller_products_subtitle
        )
        R.id.nav_seller_orders -> RolePlaceholderFragment.newInstance(
            R.string.seller_orders_title,
            R.string.seller_orders_subtitle
        )
        R.id.nav_seller_profile -> RolePlaceholderFragment.newInstance(
            R.string.seller_profile_title,
            R.string.seller_profile_subtitle
        )
        else -> null
    }
}
