package com.clubcanino.app.admin.ui

import android.content.res.ColorStateList
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
import com.clubcanino.app.seller.data.ShopOrderStatus
import com.clubcanino.app.seller.data.ShopOrders
import com.clubcanino.app.seller.ui.ShopOrderDialogs
import com.google.android.material.button.MaterialButton

class AdminSalesFragment : Fragment() {

    private lateinit var statsContainer: LinearLayout
    private lateinit var filtersContainer: LinearLayout
    private lateinit var ordersContainer: LinearLayout
    private lateinit var emptyView: TextView
    private var selectedFilter: String = ShopOrders.FILTER_ALL

    private val filters = listOf(
        ShopOrders.FILTER_ALL,
        ShopOrderStatus.PENDING,
        ShopOrderStatus.PREPARING,
        ShopOrderStatus.SHIPPING,
        ShopOrderStatus.DELIVERED,
        ShopOrderStatus.CANCELLED
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_sales, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statsContainer = view.findViewById(R.id.containerSalesStats)
        filtersContainer = view.findViewById(R.id.containerFilters)
        ordersContainer = view.findViewById(R.id.containerSalesOrders)
        emptyView = view.findViewById(R.id.textEmpty)
        buildFilters()
    }

    override fun onResume() {
        super.onResume()
        if (this::ordersContainer.isInitialized) render()
    }

    private fun buildFilters() {
        filtersContainer.removeAllViews()
        val margin = resources.getDimensionPixelSize(R.dimen.spacing_sm)
        filters.forEach { label ->
            val button = MaterialButton(requireContext()).apply {
                text = label
                isAllCaps = false
                tag = label
                minimumHeight = resources.getDimensionPixelSize(R.dimen.button_height_small)
                setOnClickListener {
                    selectedFilter = label
                    render()
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = margin
            filtersContainer.addView(button, params)
        }
    }

    private fun render() {
        styleFilters()
        statsContainer.removeAllViews()
        val visible = ShopOrders.byStatus(selectedFilter)
        StaffStats.show(
            statsContainer,
            listOf(
                StaffStat(R.string.admin_stat_order_count, visible.size.toString(), R.drawable.ic_stat_clipboard),
                StaffStat(R.string.admin_stat_delivered, MoneyFormat.format(ShopOrders.deliveredRevenue()), R.drawable.ic_stat_dollar),
                StaffStat(R.string.admin_stat_pending, ShopOrders.countByStatus(ShopOrderStatus.PENDING).toString(), R.drawable.ic_stat_warning),
                StaffStat(R.string.admin_stat_cancelled, ShopOrders.countByStatus(ShopOrderStatus.CANCELLED).toString(), R.drawable.ic_stat_warning)
            )
        )

        emptyView.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        ShopOrderDialogs.renderRows(
            fragment = this,
            container = ordersContainer,
            orders = visible,
            allowChanges = false
        ) { render() }
    }

    private fun styleFilters() {
        val selectedBg = ColorStateList.valueOf(requireContext().getColor(R.color.buyer_forest))
        val idleBg = ColorStateList.valueOf(requireContext().getColor(R.color.white))
        val selectedText = requireContext().getColor(R.color.white)
        val idleText = requireContext().getColor(R.color.buyer_forest)
        for (index in 0 until filtersContainer.childCount) {
            val button = filtersContainer.getChildAt(index) as MaterialButton
            val selected = button.tag == selectedFilter
            button.backgroundTintList = if (selected) selectedBg else idleBg
            button.setTextColor(if (selected) selectedText else idleText)
            button.strokeWidth = if (selected) 0 else resources.getDimensionPixelSize(R.dimen.buyer_chip_stroke)
            button.strokeColor = ColorStateList.valueOf(requireContext().getColor(R.color.buyer_outline))
            button.cornerRadius = resources.getDimensionPixelSize(R.dimen.radius_full)
        }
    }
}
