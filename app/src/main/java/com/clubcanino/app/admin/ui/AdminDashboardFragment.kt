package com.clubcanino.app.admin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.admin.AdminMainActivity
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.buyer.ui.MoneyFormat
import com.clubcanino.app.navigation.StaffStat
import com.clubcanino.app.navigation.StaffStats
import com.clubcanino.app.seller.data.ShopCatalog
import com.clubcanino.app.seller.data.ShopOrders
import com.clubcanino.app.seller.ui.ShopOrderDialogs

class AdminDashboardFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.buttonOpenUsers).setOnClickListener {
            (activity as? AdminMainActivity)?.openTab(R.id.nav_admin_users)
        }
        view.findViewById<View>(R.id.buttonOpenSales).setOnClickListener {
            (activity as? AdminMainActivity)?.openTab(R.id.nav_admin_sales)
        }
        view.findViewById<View>(R.id.buttonOpenProducts).setOnClickListener {
            (activity as? AdminMainActivity)?.openTab(R.id.nav_admin_products)
        }
        view.findViewById<View>(R.id.buttonSeeAllOrders).setOnClickListener {
            (activity as? AdminMainActivity)?.openTab(R.id.nav_admin_sales)
        }
    }

    override fun onResume() {
        super.onResume()
        val root = view ?: return
        val stats = root.findViewById<LinearLayout>(R.id.containerStats)
        StaffStats.show(
            stats,
            listOf(
                StaffStat(R.string.admin_stat_users, ShopUserStore.count().toString(), R.drawable.ic_nav_users),
                StaffStat(R.string.admin_stat_products, ShopCatalog.activeProducts().size.toString(), R.drawable.ic_stat_box),
                StaffStat(R.string.admin_stat_orders, ShopOrders.activeOrders().size.toString(), R.drawable.ic_nav_cart),
                StaffStat(R.string.admin_stat_sales, MoneyFormat.format(ShopOrders.deliveredRevenue()), R.drawable.ic_stat_dollar)
            )
        )

        val recent = root.findViewById<LinearLayout>(R.id.containerRecentOrders)
        ShopOrderDialogs.renderRows(
            fragment = this,
            container = recent,
            orders = ShopOrders.recent(4),
            allowChanges = false,
            onChanged = {}
        )
    }
}
