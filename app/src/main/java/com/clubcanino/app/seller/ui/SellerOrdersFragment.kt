package com.clubcanino.app.seller.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.seller.data.ShopOrders

class SellerOrdersFragment : Fragment() {

    private lateinit var activeContainer: LinearLayout
    private lateinit var historyContainer: LinearLayout
    private lateinit var activeEmpty: TextView
    private lateinit var historyEmpty: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_seller_orders, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activeContainer = view.findViewById(R.id.containerActiveOrders)
        historyContainer = view.findViewById(R.id.containerHistoryOrders)
        activeEmpty = view.findViewById(R.id.textActiveEmpty)
        historyEmpty = view.findViewById(R.id.textHistoryEmpty)
    }

    override fun onResume() {
        super.onResume()
        ShopOrders.bind(requireContext())
        if (this::activeContainer.isInitialized) render()
    }

    private fun render() {
        val active = ShopOrders.activeOrders()
        val history = ShopOrders.historyOrders()
        activeEmpty.visibility = if (active.isEmpty()) View.VISIBLE else View.GONE
        historyEmpty.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
        ShopOrderDialogs.renderRows(this, activeContainer, active, allowChanges = true) { render() }
        ShopOrderDialogs.renderRows(this, historyContainer, history, allowChanges = false) { render() }
    }
}
