package com.clubcanino.app.admin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.admin.data.ShopUser
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.admin.data.ShopUserWrite
import com.clubcanino.app.auth.AuthValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AdminUsersFragment : Fragment() {

    private lateinit var container: LinearLayout
    private lateinit var emptyView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_users, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view.findViewById(R.id.containerUsers)
        emptyView = view.findViewById(R.id.textEmpty)
        view.findViewById<View>(R.id.buttonAddUser).setOnClickListener {
            showForm(user = null)
        }
    }

    override fun onResume() {
        super.onResume()
        if (this::container.isInitialized) render()
    }

    private fun render() {
        val users = ShopUserStore.users()
        container.removeAllViews()
        emptyView.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
        users.forEach { user ->
            val row = layoutInflater.inflate(R.layout.item_shop_user, container, false)
            row.findViewById<TextView>(R.id.textUserName).text = user.fullName
            row.findViewById<TextView>(R.id.textUserMeta).text =
                getString(R.string.user_meta, user.role, user.email)
            row.findViewById<TextView>(R.id.textUserPhone).text = user.phone
            row.findViewById<MaterialButton>(R.id.buttonEditUser).setOnClickListener {
                showForm(user)
            }
            row.findViewById<MaterialButton>(R.id.buttonDeleteUser).setOnClickListener {
                confirmDelete(user)
            }
            container.addView(row)
        }
    }

    private fun showForm(user: ShopUser?) {
        val form = layoutInflater.inflate(R.layout.dialog_user_form, null, false)
        val layoutName = form.findViewById<TextInputLayout>(R.id.layoutUserName)
        val layoutEmail = form.findViewById<TextInputLayout>(R.id.layoutUserEmail)
        val layoutPhone = form.findViewById<TextInputLayout>(R.id.layoutUserPhone)
        val layoutRole = form.findViewById<TextInputLayout>(R.id.layoutUserRole)
        val editName = form.findViewById<TextInputEditText>(R.id.editUserName)
        val editEmail = form.findViewById<TextInputEditText>(R.id.editUserEmail)
        val editPhone = form.findViewById<TextInputEditText>(R.id.editUserPhone)
        val editRole = form.findViewById<TextInputEditText>(R.id.editUserRole)

        if (user != null) {
            editName.setText(user.fullName)
            editEmail.setText(user.email)
            editPhone.setText(user.phone)
            editRole.setText(user.role)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (user == null) R.string.dialog_add_user else R.string.dialog_edit_user)
            .setView(form)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                layoutName.error = null
                layoutEmail.error = null
                layoutPhone.error = null
                layoutRole.error = null

                val name = editName.text?.toString()?.trim().orEmpty()
                val email = editEmail.text?.toString()?.trim().orEmpty()
                val phone = editPhone.text?.toString()?.trim().orEmpty()
                val roleRaw = editRole.text?.toString()?.trim().orEmpty()
                val role = ShopUserStore.canonicalRole(roleRaw)

                var valid = true
                if (name.isEmpty()) {
                    layoutName.error = getString(R.string.error_field_required)
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
                if (roleRaw.isEmpty()) {
                    layoutRole.error = getString(R.string.error_field_required)
                    valid = false
                } else if (role == null) {
                    layoutRole.error = getString(R.string.error_role_invalid)
                    valid = false
                }
                if (!valid || role == null) return@setOnClickListener

                val result = if (user == null) {
                    ShopUserStore.add(name, email, phone, role)
                } else {
                    ShopUserStore.update(user.id, name, email, phone, role)
                }
                when (result) {
                    ShopUserWrite.SAVED -> {
                        val message = if (user == null) R.string.msg_user_added else R.string.msg_user_updated
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        render()
                        dialog.dismiss()
                    }
                    ShopUserWrite.DUPLICATE_EMAIL -> {
                        layoutEmail.error = getString(R.string.msg_user_duplicate_email)
                    }
                    ShopUserWrite.LAST_ADMIN -> {
                        layoutRole.error = getString(R.string.msg_user_last_admin)
                    }
                    ShopUserWrite.NOT_FOUND -> {
                        Toast.makeText(requireContext(), R.string.msg_error_generic, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(user: ShopUser) {
        val wouldRemoveLastAdmin = user.role == ShopUserStore.ROLE_ADMIN &&
            ShopUserStore.users().count { it.role == ShopUserStore.ROLE_ADMIN } <= 1
        if (wouldRemoveLastAdmin) {
            Toast.makeText(requireContext(), R.string.msg_user_last_admin, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_delete_user_title)
            .setMessage(getString(R.string.dialog_delete_user_message, user.fullName))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                when (ShopUserStore.remove(user.id)) {
                    ShopUserWrite.SAVED -> {
                        Toast.makeText(requireContext(), R.string.msg_user_deleted, Toast.LENGTH_SHORT).show()
                        render()
                    }
                    ShopUserWrite.LAST_ADMIN -> {
                        Toast.makeText(requireContext(), R.string.msg_user_last_admin, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(requireContext(), R.string.msg_error_generic, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
