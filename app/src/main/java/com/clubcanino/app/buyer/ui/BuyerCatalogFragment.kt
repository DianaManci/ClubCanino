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
import com.clubcanino.app.seller.data.ShopCatalog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class BuyerCatalogFragment : Fragment() {

    private var selectedCategory: String = "Todos"
    private lateinit var productsContainer: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var editSearch: TextInputEditText
    private val filterChips = mutableListOf<MaterialButton>()
    private var headerBinder: BuyerHeaderBinder? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_buyer_catalog, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerBinder = BuyerHeaderBinder(this, view).also { it.bind() }
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
                setOnClickListener {
                    selectedCategory = category
                    styleFilterChips()
                    renderProducts()
                }
            }
            filterChips += chip
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_sm) }
            filtersContainer.addView(chip, params)
        }
        styleFilterChips()

        editSearch.doAfterTextChanged { renderProducts() }
        renderProducts()
    }

    override fun onStart() {
        super.onStart()
        headerBinder?.start()
    }

    override fun onResume() {
        super.onResume()
        if (this::productsContainer.isInitialized) {
            ShopCatalog.bind(requireContext())
            renderProducts()
        }
    }

    override fun onStop() {
        headerBinder?.stop()
        super.onStop()
    }

    private fun styleFilterChips() {
        filterChips.forEach { chip ->
            BuyerChrome.styleChip(chip, selected = chip.text.toString() == selectedCategory)
        }
    }

    private fun renderProducts() {
        val query = editSearch.text?.toString().orEmpty()
        val products = ShopCatalog.forBuyer(selectedCategory, query)
        productsContainer.removeAllViews()
        emptyView.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
        val inflater = layoutInflater
        val gap = resources.getDimensionPixelSize(R.dimen.spacing_sm)
        var row: LinearLayout? = null
        products.forEachIndexed { index, product ->
            if (index % 2 == 0) {
                row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                productsContainer.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = gap }
                )
            }
            val card = ProductViews.inflateProductCard(inflater, row!!, product) {
                startActivity(ProductDetailActivity.createIntent(requireContext(), it.id))
            }
            card.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                if (index % 2 == 0) marginEnd = gap else marginStart = gap
            }
            row?.addView(card)
        }
        if (products.size % 2 == 1) {
            row?.addView(
                View(requireContext()),
                LinearLayout.LayoutParams(0, 0, 1f)
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
