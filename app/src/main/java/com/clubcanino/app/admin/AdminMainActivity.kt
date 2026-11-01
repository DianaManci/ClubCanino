package com.clubcanino.app.admin

import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.common.RolePlaceholderFragment
import com.clubcanino.app.navigation.RoleHostActivity

class AdminMainActivity : RoleHostActivity() {

    override fun bottomMenuRes(): Int = R.menu.menu_admin

    override fun defaultSelectedItemId(): Int = R.id.nav_admin_dashboard

    override fun createFragmentForItem(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_admin_dashboard -> RolePlaceholderFragment.newInstance(
            R.string.admin_dashboard_title,
            R.string.admin_dashboard_subtitle
        )
        R.id.nav_admin_users -> RolePlaceholderFragment.newInstance(
            R.string.admin_users_title,
            R.string.admin_users_subtitle
        )
        R.id.nav_admin_products -> RolePlaceholderFragment.newInstance(
            R.string.admin_products_title,
            R.string.admin_products_subtitle
        )
        R.id.nav_admin_sales -> RolePlaceholderFragment.newInstance(
            R.string.admin_sales_title,
            R.string.admin_sales_subtitle
        )
        else -> null
    }
}
