package com.clubcanino.app.buyer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.buyer.data.BuyerDemoData
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class BuyerCatalogFragment : Fragment() {

    private var selectedCategory: String = "Todos"
    private lateinit var productsContainer: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var editSearch: TextInputEditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_buyer_catalog, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedCategory = arguments?.getString(ARG_CATEGORY) ?: "Todos"
        val initialQuery = arguments?.getString(ARG_QUERY).orEmpty()

        productsContainer = view.findViewById(R.id.containerProducts)
        emptyView = view.findViewById(R.id.textEmpty)
        editSearch = view.findViewById(R.id.editSearch)
        editSearch.setText(initialQuery)

        val filtersContainer = view.findViewById<LinearLayout>(R.id.containerFilters)
        BuyerDemoData.categories.forEach { category ->
            val chip = MaterialButton(requireContext()).apply {
                text = category
                isAllCaps = false
                minimumHeight = resources.getDimensionPixelSize(R.dimen.button_height_small)
                setOnClickListener {
                    selectedCategory = category
                    renderProducts()
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_sm) }
            filtersContainer.addView(chip, params)
        }

        editSearch.doAfterTextChanged { renderProducts() }
        renderProducts()
    }

    private fun renderProducts() {
        val query = editSearch.text?.toString().orEmpty()
        val products = BuyerDemoData.filter(selectedCategory, query)
        productsContainer.removeAllViews()
        emptyView.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
        val inflater = layoutInflater
        products.forEach { product ->
            productsContainer.addView(
                ProductViews.inflateProductCard(inflater, productsContainer, product) {
                    startActivity(ProductDetailActivity.createIntent(requireContext(), it.id))
                }
            )
        }
    }

    companion object {
        private const val ARG_CATEGORY = "arg_category"
        private const val ARG_QUERY = "arg_query"

        fun newInstance(category: String = "Todos", query: String = ""): BuyerCatalogFragment {
            return BuyerCatalogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CATEGORY, category)
                    putString(ARG_QUERY, query)
                }
            }
        }
    }
}
