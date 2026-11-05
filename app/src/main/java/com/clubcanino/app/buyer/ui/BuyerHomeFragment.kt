package com.clubcanino.app.buyer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.buyer.BuyerMainActivity
import com.clubcanino.app.buyer.data.BuyerDemoData
import com.clubcanino.app.seller.data.MarketCatalog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class BuyerHomeFragment : Fragment() {

    private var headerBinder: BuyerHeaderBinder? = null
    private var featuredContainer: LinearLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_buyer_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerBinder = BuyerHeaderBinder(this, view).also { it.bind() }

        val editSearch = view.findViewById<TextInputEditText>(R.id.editSearch)
        val categoriesContainer = view.findViewById<LinearLayout>(R.id.containerCategories)
        featuredContainer = view.findViewById(R.id.containerFeatured)
        val host = activity as? BuyerMainActivity

        BuyerDemoData.categories.filter { it != "Todos" }.forEach { category ->
            val chip = MaterialButton(requireContext()).apply {
                text = category
                BuyerChrome.styleChip(this, selected = false)
                setOnClickListener { host?.openCatalog(category = category) }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_sm) }
            categoriesContainer.addView(chip, params)
        }

        val openSearch = {
            host?.openCatalog(query = editSearch.text?.toString().orEmpty())
        }
        view.findViewById<MaterialButton>(R.id.buttonSearch).setOnClickListener { openSearch() }
        editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                openSearch()
                true
            } else {
                false
            }
        }
        view.findViewById<MaterialButton>(R.id.buttonSeePromos).setOnClickListener {
            host?.openCatalog(category = "Promociones")
        }
        view.findViewById<View>(R.id.buttonBuyNow).setOnClickListener {
            host?.openCatalog()
        }
        view.findViewById<View>(R.id.buttonSeeAllFeatured).setOnClickListener {
            host?.openCatalog()
        }
        view.findViewById<View>(R.id.buttonQuickCatalog).setOnClickListener {
            host?.openCatalog()
        }
        view.findViewById<View>(R.id.buttonQuickPromos).setOnClickListener {
            host?.openCatalog(category = "Promociones")
        }
        view.findViewById<View>(R.id.buttonQuickPets).setOnClickListener {
            host?.openProfile()
        }
        view.findViewById<View>(R.id.buttonQuickProfile).setOnClickListener {
            host?.openProfile()
        }
    }

    override fun onStart() {
        super.onStart()
        headerBinder?.start()
    }

    override fun onResume() {
        super.onResume()
        MarketCatalog.refresh(requireContext()) { if (isAdded) renderFeatured() }
    }

    private fun renderFeatured() {
        val container = featuredContainer ?: return
        container.removeAllViews()
        val cardWidth = resources.getDimensionPixelSize(R.dimen.buyer_featured_card_width)
        val cardGap = resources.getDimensionPixelSize(R.dimen.spacing_md)
        MarketCatalog.featured().forEach { product ->
            val card = ProductViews.inflateProductCard(layoutInflater, container, product) {
                startActivity(ProductDetailActivity.createIntent(requireContext(), it.id))
            }
            card.layoutParams = LinearLayout.LayoutParams(
                cardWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = cardGap }
            container.addView(card)
        }
    }

    override fun onStop() {
        headerBinder?.stop()
        super.onStop()
    }
}
