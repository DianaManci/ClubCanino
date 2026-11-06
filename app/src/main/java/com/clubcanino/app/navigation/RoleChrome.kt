package com.clubcanino.app.navigation

import android.app.Activity
import android.content.res.ColorStateList
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.clubcanino.app.R
import com.clubcanino.app.buyer.data.BuyerDemoData
import com.clubcanino.app.buyer.data.BuyerSession
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
            BuyerSession.clear(activity)
            BuyerDemoData.clearMemory()
            activity.finish()
        }
    }
}
