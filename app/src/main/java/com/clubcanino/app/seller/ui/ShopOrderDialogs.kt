package com.clubcanino.app.seller.ui

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.buyer.ui.MoneyFormat
import com.clubcanino.app.seller.data.ShopOrder
import com.clubcanino.app.seller.data.ShopOrderStatus
import com.clubcanino.app.seller.data.ShopOrders
import com.google.android.material.button.MaterialButton

/** Detalle del pedido para vendedor y administrador, incluida la dirección de entrega. */
object ShopOrderDialogs {

    fun renderRows(
        fragment: Fragment,
        container: LinearLayout,
        orders: List<ShopOrder>,
        allowChanges: Boolean,
        onChanged: () -> Unit
    ) {
        container.removeAllViews()
        val inflater = fragment.layoutInflater
        orders.forEach { order ->
            val row = inflater.inflate(R.layout.item_seller_order, container, false)
            row.findViewById<TextView>(R.id.textOrderId).text = order.id
            row.findViewById<TextView>(R.id.textOrderMeta).text = fragment.getString(
                R.string.shop_order_meta,
                order.dateLabel,
                order.buyerName,
                order.displayStatus,
                order.itemCount
            )
            row.findViewById<TextView>(R.id.textOrderTotal).text = MoneyFormat.format(order.total)
            val status = row.findViewById<TextView>(R.id.textOrderStatus)
            status.text = order.displayStatus
            val statusStyle = statusStyle(order.displayStatus)
            status.setBackgroundResource(statusStyle.background)
            status.setTextColor(fragment.requireContext().getColor(statusStyle.textColor))
            row.findViewById<MaterialButton>(R.id.buttonViewOrder).setOnClickListener {
                show(fragment, order.id, allowChanges, onChanged)
            }
            container.addView(row)
        }
    }

    private data class StatusStyle(val background: Int, val textColor: Int)

    private fun statusStyle(status: String): StatusStyle = when (status) {
        ShopOrderStatus.DELIVERED -> StatusStyle(R.drawable.bg_status_delivered, R.color.status_delivered_text)
        ShopOrderStatus.SHIPPING -> StatusStyle(R.drawable.bg_status_shipping, R.color.status_shipping_text)
        ShopOrderStatus.CANCELLED -> StatusStyle(R.drawable.bg_status_cancelled, R.color.status_cancelled_text)
        else -> StatusStyle(R.drawable.bg_status_preparing, R.color.status_preparing_text)
    }

    fun show(
        fragment: Fragment,
        orderId: String,
        allowChanges: Boolean,
        onChanged: () -> Unit
    ) {
        val order = ShopOrders.orderById(orderId) ?: return
        val context = fragment.requireContext()
        val content = fragment.layoutInflater.inflate(R.layout.dialog_order_detail, null, false)
        content.findViewById<TextView>(R.id.textOrderBuyer).text = order.buyerName
        content.findViewById<TextView>(R.id.textOrderDate).text = order.dateLabel
        val addressView = content.findViewById<TextView>(R.id.textOrderAddress)
        val address = order.address.trim()
        val hasLocation = order.latitude != 0.0 || order.longitude != 0.0
        if (address.isBlank() && !hasLocation) {
            addressView.visibility = View.GONE
        } else {
            addressView.visibility = View.VISIBLE
            addressView.text = if (hasLocation) {
                fragment.getString(R.string.order_delivery_with_location, address.ifBlank { "—" }, order.latitude, order.longitude)
            } else {
                fragment.getString(R.string.order_delivery_address, address)
            }
        }
        content.findViewById<TextView>(R.id.textOrderStatus).text = order.displayStatus
        content.findViewById<TextView>(R.id.textOrderDetailTotal).text =
            fragment.getString(R.string.cart_total, MoneyFormat.format(order.total))

        val lines = content.findViewById<LinearLayout>(R.id.containerOrderLines)
        order.lines.forEach { line ->
            val text = TextView(context).apply {
                this.text = fragment.getString(
                    R.string.shop_order_line,
                    line.productName,
                    line.quantity,
                    MoneyFormat.format(line.unitPrice),
                    MoneyFormat.format(line.lineTotal)
                )
                setTextAppearance(R.style.TextAppearance_ClubCanino_Body)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = fragment.resources.getDimensionPixelSize(R.dimen.spacing_xs)
            lines.addView(text, params)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(order.id)
            .setView(content)
            .setNegativeButton(R.string.action_close, null)
            .create()

        val buttonAdvance = content.findViewById<MaterialButton>(R.id.buttonAdvanceOrder)
        val buttonCancel = content.findViewById<MaterialButton>(R.id.buttonCancelOrder)
        val next = ShopOrderStatus.next(order.status)
        if (allowChanges && order.isPaid && next != null) {
            buttonAdvance.visibility = View.VISIBLE
            buttonAdvance.text = fragment.getString(R.string.action_advance_status, next)
            buttonAdvance.setOnClickListener {
                ShopOrders.advance(order.id) { updated ->
                    if (!fragment.isAdded) return@advance
                    if (!updated) {
                        Toast.makeText(context, R.string.msg_error_generic, Toast.LENGTH_SHORT).show()
                        return@advance
                    }
                    Toast.makeText(context, R.string.msg_order_updated, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    onChanged()
                }
            }
        } else {
            buttonAdvance.visibility = View.GONE
        }

        if (allowChanges && !order.isHistory) {
            buttonCancel.visibility = View.VISIBLE
            buttonCancel.setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle(R.string.dialog_cancel_order_title)
                    .setMessage(fragment.getString(R.string.dialog_cancel_order_message, order.id))
                    .setPositiveButton(R.string.action_cancel_order) { _, _ ->
                        ShopOrders.cancel(order.id) { updated ->
                            if (!fragment.isAdded) return@cancel
                            if (!updated) {
                                Toast.makeText(context, R.string.msg_error_generic, Toast.LENGTH_SHORT).show()
                                return@cancel
                            }
                            Toast.makeText(context, R.string.msg_order_cancelled, Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            onChanged()
                        }
                    }
                    .setNegativeButton(R.string.action_close, null)
                    .show()
            }
        } else {
            buttonCancel.visibility = View.GONE
        }

        dialog.show()
    }
}
