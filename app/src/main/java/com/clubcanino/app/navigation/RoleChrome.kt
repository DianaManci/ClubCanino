package com.clubcanino.app.navigation

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.clubcanino.app.R
import com.clubcanino.app.auth.FirebaseGate
import com.clubcanino.app.auth.LoginActivity
import com.clubcanino.app.buyer.data.BuyerDemoData
import com.clubcanino.app.buyer.data.BuyerSession
import com.clubcanino.app.seller.data.ShopOrders
import com.google.android.material.bottomnavigation.BottomNavigationView

object RoleChrome {

    fun apply(activity: AppCompatActivity) {
        val forest = ContextCompat.getColor(activity, R.color.buyer_forest)
        activity.findViewById<View>(R.id.roleHostRoot).setBackgroundColor(forest)
        activity.window.statusBarColor = forest
        activity.window.navigationBarColor = forest
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val nav = activity.findViewById<BottomNavigationView>(R.id.bottomNavigation)
        nav.setBackgroundColor(forest)
        nav.backgroundTintList = ColorStateList.valueOf(forest)
        val tint = ContextCompat.getColorStateList(activity, R.color.buyer_nav_item)
        nav.itemIconTintList = tint
        nav.itemTextColor = tint
        nav.itemActiveIndicatorColor = ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.buyer_nav_indicator)
        )
        nav.itemRippleColor = ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.buyer_nav_indicator)
        )
        nav.elevation = 0f

        ViewCompat.setOnApplyWindowInsetsListener(nav) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(0, 0, 0, bottom)
            val desired = activity.resources.getDimensionPixelSize(R.dimen.bottom_nav_height) + bottom
            if (view.layoutParams.height != desired) {
                view.layoutParams.height = desired
                view.requestLayout()
            }
            insets
        }
        ViewCompat.requestApplyInsets(nav)
    }

    fun bindReturnToRoles(root: View, activity: Activity) {
        root.findViewById<View>(R.id.buttonReturnRoles)?.setOnClickListener {
            ShopOrders.stop()
            FirebaseGate.auth(activity)?.signOut()
            BuyerSession.clear(activity)
            BuyerDemoData.clearMemory()
            activity.startActivity(
                Intent(activity, LoginActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            )
        }
    }
}
