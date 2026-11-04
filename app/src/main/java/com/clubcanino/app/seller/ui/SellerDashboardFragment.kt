package com.clubcanino.app.seller.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.buyer.ui.MoneyFormat
import com.clubcanino.app.navigation.StaffStat
import com.clubcanino.app.navigation.StaffStats
import com.clubcanino.app.seller.SellerMainActivity
import com.clubcanino.app.seller.data.SellerProfileStore
import com.clubcanino.app.seller.data.ShopCatalog
import com.clubcanino.app.seller.data.ShopOrders

class SellerDashboardFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_seller_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.buttonOpenProducts).setOnClickListener {
            (activity as? SellerMainActivity)?.openTab(R.id.nav_seller_products)
        }
        view.findViewById<View>(R.id.buttonOpenOrders).setOnClickListener {
            (activity as? SellerMainActivity)?.openTab(R.id.nav_seller_orders)
        }
        view.findViewById<View>(R.id.buttonOpenProfile).setOnClickListener {
            (activity as? SellerMainActivity)?.openTab(R.id.nav_seller_profile)
        }
    }

    override fun onResume() {
        super.onResume()
        val root = view ?: return
        val profile = SellerProfileStore.current(requireContext())
        val firstName = profile.fullName.substringBefore(' ').ifBlank { profile.fullName }
        root.findViewById<TextView>(R.id.textWelcome).text =
            getString(R.string.seller_dashboard_hello, firstName)

        val stats = root.findViewById<LinearLayout>(R.id.containerStats)
        StaffStats.show(
            stats,
            listOf(
                StaffStat(R.string.seller_stat_products, ShopCatalog.activeProducts().size.toString(), R.drawable.ic_stat_box),
                StaffStat(R.string.seller_stat_orders, ShopOrders.activeOrders().size.toString(), R.drawable.ic_stat_clipboard),
                StaffStat(R.string.seller_stat_sales, MoneyFormat.format(ShopOrders.deliveredRevenue()), R.drawable.ic_stat_chart),
                StaffStat(R.string.seller_stat_low_stock, ShopCatalog.lowStockCount().toString(), R.drawable.ic_stat_warning)
            )
        )
    }
}
