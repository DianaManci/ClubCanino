package com.clubcanino.app.navigation

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clubcanino.app.R
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.buyer.data.BuyerSession
import com.clubcanino.app.common.UserRole
import com.google.android.material.button.MaterialButton

class RoleSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_role_selection)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.roleSelectionRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft + bars.left,
                view.paddingTop + bars.top,
                view.paddingRight + bars.right,
                view.paddingBottom + bars.bottom
            )
            insets
        }

        val sessionRole = BuyerSession.currentRole(this)
        if (sessionRole == null) {
            finish()
            return
        }
        val buttonBuyer = findViewById<MaterialButton>(R.id.buttonBuyer)
        val buttonSeller = findViewById<MaterialButton>(R.id.buttonSeller)
        val buttonAdmin = findViewById<MaterialButton>(R.id.buttonAdmin)
        buttonBuyer.isEnabled = sessionRole == ShopUserStore.ROLE_BUYER
        buttonSeller.isEnabled = sessionRole == ShopUserStore.ROLE_SELLER
        buttonAdmin.isEnabled = sessionRole == ShopUserStore.ROLE_ADMIN
        buttonBuyer.setOnClickListener { openRole(UserRole.BUYER) }
        buttonSeller.setOnClickListener { openRole(UserRole.SELLER) }
        buttonAdmin.setOnClickListener { openRole(UserRole.ADMIN) }
        findViewById<MaterialButton>(R.id.buttonBackToLogin).setOnClickListener {
            BuyerSession.clear(this)
            finish()
        }
    }

    private fun openRole(role: UserRole) {
        val sessionRole = BuyerSession.currentRole(this) ?: return
        if (RoleNavigator.roleOf(sessionRole) != role) return
        RoleNavigator.openRoleHome(this, role)
    }
}
