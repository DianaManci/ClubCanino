package com.clubcanino.app.buyer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.buyer.data.BuyerDemoData
import com.clubcanino.app.buyer.data.Pet
import com.clubcanino.app.seller.data.ShopOrders
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class BuyerProfileFragment : Fragment() {

    private lateinit var petsContainer: LinearLayout
    private lateinit var ordersContainer: LinearLayout
    private var headerBinder: BuyerHeaderBinder? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_buyer_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerBinder = BuyerHeaderBinder(this, view).also { it.bind() }
        BuyerDemoData.ensurePetsLoaded(requireContext())
        bindAccount(view)

        petsContainer = view.findViewById(R.id.containerPets)
        ordersContainer = view.findViewById(R.id.containerOrders)

        view.findViewById<MaterialButton>(R.id.buttonAddPet).setOnClickListener {
            showPetFormDialog(pet = null)
        }

        renderPets()
        renderOrders()
    }

    override fun onStart() {
        super.onStart()
        headerBinder?.start()
    }

    override fun onResume() {
        super.onResume()
        BuyerDemoData.ensurePetsLoaded(requireContext())
        view?.let { bindAccount(it) }
        if (::petsContainer.isInitialized) {
            renderPets()
        }
        if (::ordersContainer.isInitialized) {
            renderOrders()
        }
    }

    private fun bindAccount(view: View) {
        val profile = BuyerDemoData.profile(requireContext())
        view.findViewById<TextView>(R.id.textName).text = profile.fullName
        view.findViewById<TextView>(R.id.textEmail).text = profile.email
        view.findViewById<TextView>(R.id.textPhone).text = profile.phone
        val city = view.findViewById<TextView>(R.id.textCity)
        if (profile.city.isBlank()) {
            city.visibility = View.GONE
        } else {
            city.visibility = View.VISIBLE
            city.text = profile.city
        }
    }

    override fun onStop() {
        headerBinder?.stop()
        super.onStop()
    }

    private fun renderPets() {
        petsContainer.removeAllViews()
        val inflater = layoutInflater
        val pets = BuyerDemoData.pets()
        if (pets.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = getString(R.string.buyer_pets_empty)
                setTextAppearance(R.style.TextAppearance_ClubCanino_Body_Secondary)
                setTextColor(ContextCompat.getColor(context, R.color.buyer_text_muted))
            }
            petsContainer.addView(empty)
            return
        }
        pets.forEach { pet ->
            val row = inflater.inflate(R.layout.item_pet_row, petsContainer, false)
            row.findViewById<TextView>(R.id.textPetName).text = pet.name
            row.findViewById<TextView>(R.id.textPetDetails).text =
                getString(R.string.pet_details_format, pet.species, pet.breed, pet.ageLabel)
            row.findViewById<MaterialButton>(R.id.buttonEditPet).setOnClickListener {
                showPetFormDialog(pet)
            }
            row.findViewById<MaterialButton>(R.id.buttonDeletePet).setOnClickListener {
                showDeletePetConfirmation(pet)
            }
            petsContainer.addView(row)
        }
    }

    private fun renderOrders() {
        ordersContainer.removeAllViews()
        val inflater = layoutInflater
        ShopOrders.bind(requireContext())
        val orders = ShopOrders.forBuyer(BuyerDemoData.profile(requireContext()).email)
        if (orders.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = getString(R.string.buyer_orders_empty)
                setTextAppearance(R.style.TextAppearance_ClubCanino_Body_Secondary)
                setTextColor(ContextCompat.getColor(context, R.color.buyer_text_muted))
            }
            ordersContainer.addView(empty)
            return
        }
        orders.forEach { order ->
            val row = inflater.inflate(R.layout.item_order_row, ordersContainer, false)
            row.findViewById<TextView>(R.id.textOrderId).text = order.id
            row.findViewById<TextView>(R.id.textOrderMeta).text = getString(
                R.string.order_meta_format,
                order.dateLabel,
                order.status,
                order.itemCount
            )
            row.findViewById<TextView>(R.id.textOrderTotal).text = MoneyFormat.format(order.total)
            ordersContainer.addView(row)
        }
    }

    private fun showPetFormDialog(pet: Pet?) {
        val context = requireContext()
        val isEdit = pet != null
        val form = layoutInflater.inflate(R.layout.dialog_pet_form, null, false)

        val layoutName = form.findViewById<TextInputLayout>(R.id.layoutPetName)
        val layoutSpecies = form.findViewById<TextInputLayout>(R.id.layoutPetSpecies)
        val editName = form.findViewById<TextInputEditText>(R.id.editPetName)
        val editSpecies = form.findViewById<TextInputEditText>(R.id.editPetSpecies)
        val editBreed = form.findViewById<TextInputEditText>(R.id.editPetBreed)
        val editAge = form.findViewById<TextInputEditText>(R.id.editPetAge)

        if (pet != null) {
            editName.setText(pet.name)
            editSpecies.setText(pet.species)
            editBreed.setText(pet.breed)
            editAge.setText(pet.ageLabel)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (isEdit) R.string.action_edit_pet else R.string.action_add_pet)
            .setView(form)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                layoutName.error = null
                layoutSpecies.error = null

                val name = editName.text?.toString()?.trim().orEmpty()
                val species = editSpecies.text?.toString()?.trim().orEmpty()
                val breed = editBreed.text?.toString()?.trim().orEmpty()
                val age = editAge.text?.toString()?.trim().orEmpty()

                var valid = true
                if (name.isEmpty()) {
                    layoutName.error = getString(R.string.error_field_required)
                    valid = false
                }
                if (species.isEmpty()) {
                    layoutSpecies.error = getString(R.string.error_field_required)
                    valid = false
                }
                if (!valid) return@setOnClickListener

                val resolvedBreed = breed.ifEmpty { getString(R.string.pet_breed_default) }
                val resolvedAge = age.ifEmpty { getString(R.string.pet_age_default) }

                if (isEdit) {
                    BuyerDemoData.updatePet(
                        id = pet!!.id,
                        name = name,
                        species = species,
                        breed = resolvedBreed,
                        ageLabel = resolvedAge
                    )
                    Toast.makeText(context, R.string.msg_pet_updated, Toast.LENGTH_SHORT).show()
                } else {
                    BuyerDemoData.addPet(
                        name = name,
                        species = species,
                        breed = resolvedBreed,
                        ageLabel = resolvedAge
                    )
                    Toast.makeText(context, R.string.msg_pet_added, Toast.LENGTH_SHORT).show()
                }
                renderPets()
                dialog.dismiss()
            }
            // Cancel closes without mutating data (default AlertDialog behavior).
        }

        dialog.show()
    }

    private fun showDeletePetConfirmation(pet: Pet) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_delete_pet_title)
            .setMessage(getString(R.string.dialog_delete_pet_message, pet.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                BuyerDemoData.removePet(pet.id)
                renderPets()
                Toast.makeText(requireContext(), R.string.msg_pet_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
