package com.clubcanino.app.navigation

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.buyer.data.BuyerSession
import com.clubcanino.app.buyer.data.CartStore
import com.clubcanino.app.seller.data.ShopCatalog
import com.clubcanino.app.seller.data.ShopOrders
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Base host for role modules: BottomNavigationView + manual Fragment transactions.
 */
abstract class RoleHostActivity : AppCompatActivity() {

    protected abstract fun createFragmentForItem(itemId: Int): Fragment?

    @MenuRes
    protected abstract fun bottomMenuRes(): Int

    protected abstract fun defaultSelectedItemId(): Int

    /** Role stored on the account. The screen is not shown for any other role. */
    protected abstract fun requiredRole(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!sessionMatchesRole()) {
            finish()
            return
        }
        ShopOrders.bind(this)
        ShopCatalog.bind(this)
        ShopUserStore.bind(this)
        CartStore.bind(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_role_host)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.roleHostRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            findViewById<BottomNavigationView>(R.id.bottomNavigation)
                .setPadding(0, 0, 0, bars.bottom)
            insets
        }

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigation.inflateMenu(bottomMenuRes())
        bottomNavigation.setOnItemSelectedListener { item ->
            val fragment = createFragmentForItem(item.itemId) ?: return@setOnItemSelectedListener false
            showFragment(fragment)
            true
        }

        onHostConfigured()

        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = defaultSelectedItemId()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!sessionMatchesRole()) finish()
    }

    private fun sessionMatchesRole(): Boolean {
        val role = BuyerSession.currentRole(this) ?: return false
        return role.equals(requiredRole(), ignoreCase = true)
    }

    /**
     * Visual setup that must run after the host layout exists and before the
     * first screen is created.
     */
    protected open fun onHostConfigured() {}

    protected fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    protected fun bottomNavigation(): BottomNavigationView =
        findViewById(R.id.bottomNavigation)
}
