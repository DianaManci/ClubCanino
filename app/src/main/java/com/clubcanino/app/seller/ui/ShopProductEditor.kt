package com.clubcanino.app.seller.ui

import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.auth.FirebaseGate
import com.clubcanino.app.buyer.data.BuyerSession
import com.clubcanino.app.buyer.ui.MoneyFormat
import com.clubcanino.app.seller.data.MarketCatalog
import com.clubcanino.app.seller.data.ShopCatalog
import com.clubcanino.app.seller.data.ShopInput
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

object ShopProductEditor {

    private var photoCallback: ((Uri) -> Unit)? = null

    fun deliverPhoto(uri: Uri?) {
        uri?.let { photoCallback?.invoke(it) }
    }

    private fun sellerId(fragment: Fragment): String? {
        val role = BuyerSession.currentRole(fragment.requireContext())
        if (role != ShopUserStore.ROLE_SELLER) return null
        return FirebaseGate.auth(fragment.requireContext())?.currentUser?.uid
    }

    fun render(
        fragment: Fragment,
        container: LinearLayout,
        emptyView: TextView,
        query: String
    ) {
        val ownerId = sellerId(fragment)
        val products = if (ownerId == null) {
            ShopCatalog.search(query)
        } else {
            MarketCatalog.search(ownerId, query)
        }
        container.removeAllViews()
        emptyView.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
        val inflater = fragment.layoutInflater
        products.forEach { product ->
            val row = inflater.inflate(R.layout.item_manage_product, container, false)
            row.findViewById<TextView>(R.id.textName).text = product.name
            row.findViewById<TextView>(R.id.textMeta).text = fragment.getString(
                R.string.shop_product_meta,
                product.petType,
                product.category,
                product.stock,
                fragment.getString(
                    if (product.active) R.string.shop_product_active else R.string.shop_product_inactive
                )
            )
            row.findViewById<TextView>(R.id.textPrice).text = MoneyFormat.format(product.price)
            row.findViewById<MaterialButton>(R.id.buttonView).setOnClickListener {
                showDetail(fragment, product.id)
            }
            row.findViewById<MaterialButton>(R.id.buttonEdit).setOnClickListener {
                showForm(fragment, product.id) { render(fragment, container, emptyView, query) }
            }
            row.findViewById<MaterialButton>(R.id.buttonDelete).setOnClickListener {
                confirmDelete(fragment, product.id) { render(fragment, container, emptyView, query) }
            }
            container.addView(row)
        }
    }

    fun showForm(fragment: Fragment, productId: String?, onSaved: () -> Unit) {
        val context = fragment.requireContext()
        val cloud = sellerId(fragment) != null
        val product = productId?.let { id ->
            if (cloud) MarketCatalog.shopProduct(id) else ShopCatalog.productById(id)
        }
        var selectedPhoto: Uri? = null
        val form = fragment.layoutInflater.inflate(R.layout.dialog_product_form, null, false)

        val layoutName = form.findViewById<TextInputLayout>(R.id.layoutProductName)
        val layoutPrice = form.findViewById<TextInputLayout>(R.id.layoutProductPrice)
        val layoutCategory = form.findViewById<TextInputLayout>(R.id.layoutProductCategory)
        val layoutPetType = form.findViewById<TextInputLayout>(R.id.layoutProductPetType)
        val layoutStock = form.findViewById<TextInputLayout>(R.id.layoutProductStock)
        val editName = form.findViewById<TextInputEditText>(R.id.editProductName)
        val editDescription = form.findViewById<TextInputEditText>(R.id.editProductDescription)
        val editPrice = form.findViewById<TextInputEditText>(R.id.editProductPrice)
        val editCategory = form.findViewById<TextInputEditText>(R.id.editProductCategory)
        val editPetType = form.findViewById<TextInputEditText>(R.id.editProductPetType)
        val editStock = form.findViewById<TextInputEditText>(R.id.editProductStock)
        val checkActive = form.findViewById<MaterialCheckBox>(R.id.checkProductActive)
        val buttonPhoto = form.findViewById<MaterialButton>(R.id.buttonPickPhoto)
        buttonPhoto.visibility = if (cloud) View.VISIBLE else View.GONE
        buttonPhoto.setOnClickListener {
            photoCallback = { uri ->
                selectedPhoto = uri
                buttonPhoto.text = fragment.getString(R.string.msg_photo_selected)
            }
            (fragment as? SellerProductsFragment)?.launchPhotoPick()
        }

        if (product != null) {
            editName.setText(product.name)
            editDescription.setText(product.description)
            editPrice.setText(plainPrice(product.price))
            editCategory.setText(product.category)
            editPetType.setText(product.petType)
            editStock.setText(product.stock.toString())
            checkActive.isChecked = product.active
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (product == null) R.string.dialog_add_product else R.string.dialog_edit_product)
            .setView(form)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                layoutName.error = null
                layoutPrice.error = null
                layoutCategory.error = null
                layoutPetType.error = null
                layoutStock.error = null

                val name = editName.text?.toString()?.trim().orEmpty()
                val description = editDescription.text?.toString()?.trim().orEmpty()
                val priceText = editPrice.text?.toString()?.trim().orEmpty()
                val categoryRaw = editCategory.text?.toString()?.trim().orEmpty()
                val petTypeRaw = editPetType.text?.toString()?.trim().orEmpty()
                val stockText = editStock.text?.toString()?.trim().orEmpty()

                var valid = true
                if (name.isEmpty()) {
                    layoutName.error = fragment.getString(R.string.error_field_required)
                    valid = false
                }

                val price = ShopInput.parsePrice(priceText)
                when {
                    priceText.isEmpty() -> {
                        layoutPrice.error = fragment.getString(R.string.error_field_required)
                        valid = false
                    }
                    price == null -> {
                        layoutPrice.error = fragment.getString(R.string.error_price_invalid)
                        valid = false
                    }
                    price <= 0.0 -> {
                        layoutPrice.error = fragment.getString(R.string.error_price_positive)
                        valid = false
                    }
                }

                val category = ShopCatalog.canonicalCategory(categoryRaw)
                if (categoryRaw.isEmpty()) {
                    layoutCategory.error = fragment.getString(R.string.error_field_required)
                    valid = false
                } else if (category == null) {
                    layoutCategory.error = fragment.getString(R.string.error_category_invalid)
                    valid = false
                }

                val petType = ShopCatalog.canonicalPetType(petTypeRaw)
                if (petTypeRaw.isEmpty()) {
                    layoutPetType.error = fragment.getString(R.string.error_field_required)
                    valid = false
                } else if (petType == null) {
                    layoutPetType.error = fragment.getString(R.string.error_pet_type_invalid)
                    valid = false
                }

                val stock = ShopInput.parseStock(stockText)
                when {
                    stockText.isEmpty() -> {
                        layoutStock.error = fragment.getString(R.string.error_field_required)
                        valid = false
                    }
                    stock == null || stock < 0 -> {
                        layoutStock.error = fragment.getString(R.string.error_stock_invalid)
                        valid = false
                    }
                }

                if (!valid || price == null || category == null || petType == null || stock == null) {
                    return@setOnClickListener
                }
                if (cloud && product == null && selectedPhoto == null) {
                    Toast.makeText(context, R.string.msg_photo_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (cloud) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    MarketCatalog.save(
                        context = context,
                        existingId = product?.id,
                        name = name,
                        description = description,
                        price = price,
                        category = category,
                        petType = petType,
                        stock = stock,
                        active = checkActive.isChecked,
                        photo = selectedPhoto
                    ) { saved ->
                        if (!saved) {
                            Toast.makeText(context, R.string.msg_error_generic, Toast.LENGTH_SHORT).show()
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            return@save
                        }
                        val message = if (product == null) R.string.msg_product_added else R.string.msg_product_updated
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        onSaved()
                        dialog.dismiss()
                    }
                    return@setOnClickListener
                }

                if (product == null) {
                    ShopCatalog.add(
                        name = name,
                        description = description,
                        price = price,
                        category = category,
                        petType = petType,
                        stock = stock,
                        active = checkActive.isChecked
                    )
                    Toast.makeText(context, R.string.msg_product_added, Toast.LENGTH_SHORT).show()
                } else {
                    ShopCatalog.update(
                        id = product.id,
                        name = name,
                        description = description,
                        price = price,
                        category = category,
                        petType = petType,
                        stock = stock,
                        active = checkActive.isChecked
                    )
                    Toast.makeText(context, R.string.msg_product_updated, Toast.LENGTH_SHORT).show()
                }
                onSaved()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    fun showDetail(fragment: Fragment, productId: String) {
        val product = MarketCatalog.shopProduct(productId) ?: ShopCatalog.productById(productId) ?: return
        val description = product.description.ifBlank {
            fragment.getString(R.string.shop_product_no_description)
        }
        val state = fragment.getString(
            if (product.active) R.string.shop_product_active else R.string.shop_product_inactive
        )
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(product.name)
            .setMessage(
                fragment.getString(
                    R.string.shop_product_detail,
                    product.petType,
                    product.category,
                    MoneyFormat.format(product.price),
                    product.stock,
                    state,
                    description
                )
            )
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    fun confirmDelete(fragment: Fragment, productId: String, onDeleted: () -> Unit) {
        val product = MarketCatalog.shopProduct(productId) ?: ShopCatalog.productById(productId) ?: return
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.dialog_delete_product_title)
            .setMessage(fragment.getString(R.string.dialog_delete_product_message, product.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (sellerId(fragment) != null) {
                    MarketCatalog.remove(fragment.requireContext(), product.id) { removed ->
                        if (!removed) return@remove
                        Toast.makeText(
                            fragment.requireContext(),
                            R.string.msg_product_deleted,
                            Toast.LENGTH_SHORT
                        ).show()
                        onDeleted()
                    }
                    return@setPositiveButton
                }
                if (ShopCatalog.remove(product.id)) {
                    Toast.makeText(
                        fragment.requireContext(),
                        R.string.msg_product_deleted,
                        Toast.LENGTH_SHORT
                    ).show()
                    onDeleted()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun plainPrice(price: Double): String {
        return if (price % 1.0 == 0.0) price.toLong().toString() else price.toString()
    }
}
