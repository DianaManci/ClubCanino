package com.clubcanino.app.seller.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.auth.AuthValidator
import com.clubcanino.app.seller.data.SellerProfile
import com.clubcanino.app.seller.data.SellerProfileStore
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SellerProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_seller_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind(view)
        view.findViewById<View>(R.id.buttonEditProfile).setOnClickListener {
            showEditDialog()
        }
    }

    private fun bind(view: View) {
        val profile = SellerProfileStore.get()
        view.findViewById<TextView>(R.id.textSellerName).text = profile.fullName
        view.findViewById<TextView>(R.id.textStoreName).text =
            getString(R.string.seller_store_line, profile.storeName)
        view.findViewById<TextView>(R.id.textSellerEmail).text = profile.email
        view.findViewById<TextView>(R.id.textSellerPhone).text = profile.phone
        view.findViewById<TextView>(R.id.textSellerCity).text = profile.city
    }

    private fun showEditDialog() {
        val profile = SellerProfileStore.get()
        val form = layoutInflater.inflate(R.layout.dialog_seller_profile, null, false)
        val layoutName = form.findViewById<TextInputLayout>(R.id.layoutSellerName)
        val layoutStore = form.findViewById<TextInputLayout>(R.id.layoutStoreName)
        val layoutEmail = form.findViewById<TextInputLayout>(R.id.layoutSellerEmail)
        val layoutPhone = form.findViewById<TextInputLayout>(R.id.layoutSellerPhone)
        val layoutCity = form.findViewById<TextInputLayout>(R.id.layoutSellerCity)
        val editName = form.findViewById<TextInputEditText>(R.id.editSellerName)
        val editStore = form.findViewById<TextInputEditText>(R.id.editStoreName)
        val editEmail = form.findViewById<TextInputEditText>(R.id.editSellerEmail)
        val editPhone = form.findViewById<TextInputEditText>(R.id.editSellerPhone)
        val editCity = form.findViewById<TextInputEditText>(R.id.editSellerCity)

        editName.setText(profile.fullName)
        editStore.setText(profile.storeName)
        editEmail.setText(profile.email)
        editPhone.setText(profile.phone)
        editCity.setText(profile.city)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_edit_seller_profile)
            .setView(form)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                layoutName.error = null
                layoutStore.error = null
                layoutEmail.error = null
                layoutPhone.error = null
                layoutCity.error = null

                val name = editName.text?.toString()?.trim().orEmpty()
                val store = editStore.text?.toString()?.trim().orEmpty()
                val email = editEmail.text?.toString()?.trim().orEmpty()
                val phone = editPhone.text?.toString()?.trim().orEmpty()
                val city = editCity.text?.toString()?.trim().orEmpty()

                var valid = true
                if (name.isEmpty()) {
                    layoutName.error = getString(R.string.error_field_required)
                    valid = false
                }
                if (store.isEmpty()) {
                    layoutStore.error = getString(R.string.error_field_required)
                    valid = false
                }
                if (email.isEmpty()) {
                    layoutEmail.error = getString(R.string.error_field_required)
                    valid = false
                } else if (!AuthValidator.isValidEmail(email)) {
                    layoutEmail.error = getString(R.string.error_email_invalid)
                    valid = false
                }
                if (phone.isEmpty()) {
                    layoutPhone.error = getString(R.string.error_field_required)
                    valid = false
                }
                if (city.isEmpty()) {
                    layoutCity.error = getString(R.string.error_field_required)
                    valid = false
                }
                if (!valid) return@setOnClickListener

                SellerProfileStore.update(
                    SellerProfile(
                        fullName = name,
                        storeName = store,
                        email = email,
                        phone = phone,
                        city = city
                    )
                )
                view?.let { bind(it) }
                Toast.makeText(requireContext(), R.string.msg_seller_profile_updated, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
