package com.clubcanino.app.navigation

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clubcanino.app.R
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

        findViewById<MaterialButton>(R.id.buttonBuyer).setOnClickListener {
            openRole(UserRole.BUYER)
        }
        findViewById<MaterialButton>(R.id.buttonSeller).setOnClickListener {
            openRole(UserRole.SELLER)
        }
        findViewById<MaterialButton>(R.id.buttonAdmin).setOnClickListener {
            openRole(UserRole.ADMIN)
        }
        findViewById<MaterialButton>(R.id.buttonBackToLogin).setOnClickListener {
            finish()
        }
    }

    private fun openRole(role: UserRole) {
        RoleNavigator.openRoleHome(this, role)
    }
}
