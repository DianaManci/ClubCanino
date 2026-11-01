package com.clubcanino.app.buyer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.buyer.BuyerMainActivity
import com.clubcanino.app.buyer.data.BuyerDemoData
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class BuyerHomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_buyer_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val editSearch = view.findViewById<TextInputEditText>(R.id.editSearch)
        val categoriesContainer = view.findViewById<LinearLayout>(R.id.containerCategories)
        val featuredContainer = view.findViewById<LinearLayout>(R.id.containerFeatured)
        val inflater = layoutInflater

        BuyerDemoData.categories.filter { it != "Todos" }.forEach { category ->
            val chip = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = category
                isAllCaps = false
                minimumHeight = resources.getDimensionPixelSize(R.dimen.button_height_small)
                setOnClickListener {
                    (activity as? BuyerMainActivity)?.openCatalog(category = category)
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_sm) }
            categoriesContainer.addView(chip, params)
        }

        BuyerDemoData.featured().forEach { product ->
            featuredContainer.addView(
                ProductViews.inflateProductCard(inflater, featuredContainer, product) {
                    startActivity(ProductDetailActivity.createIntent(requireContext(), it.id))
                }
            )
        }

        view.findViewById<MaterialButton>(R.id.buttonSearch).setOnClickListener {
            val query = editSearch.text?.toString().orEmpty()
            (activity as? BuyerMainActivity)?.openCatalog(query = query)
        }
        view.findViewById<MaterialButton>(R.id.buttonSeePromos).setOnClickListener {
            (activity as? BuyerMainActivity)?.openCatalog(category = "Promociones")
        }
    }
}
