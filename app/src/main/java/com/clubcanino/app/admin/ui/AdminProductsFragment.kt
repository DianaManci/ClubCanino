package com.clubcanino.app.admin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.seller.ui.ShopProductEditor
import com.google.android.material.textfield.TextInputEditText

class AdminProductsFragment : Fragment() {

    private lateinit var container: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var editSearch: TextInputEditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_products, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view.findViewById(R.id.containerProducts)
        emptyView = view.findViewById(R.id.textEmpty)
        editSearch = view.findViewById(R.id.editSearch)
        view.findViewById<View>(R.id.buttonAddProduct).setOnClickListener {
            ShopProductEditor.showForm(this, productId = null) { render() }
        }
        editSearch.doAfterTextChanged { render() }
    }

    override fun onResume() {
        super.onResume()
        if (this::container.isInitialized) render()
    }

    private fun render() {
        ShopProductEditor.render(
            fragment = this,
            container = container,
            emptyView = emptyView,
            query = editSearch.text?.toString().orEmpty()
        )
    }
}
