package com.clubcanino.app.admin.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.clubcanino.app.R
import com.clubcanino.app.seller.data.MarketCatalog
import com.clubcanino.app.seller.ui.ShopProductEditor
import com.google.android.material.textfield.TextInputEditText
import java.io.File

class AdminProductsFragment : Fragment(), ShopProductEditor.ProductPhotoHost {

    private lateinit var container: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var editSearch: TextInputEditText
    private var cameraUri: Uri? = null
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        ShopProductEditor.deliverPhoto(uri)
    }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) ShopProductEditor.deliverPhoto(cameraUri)
    }
    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!isAdded) return@registerForActivityResult
        if (granted) openCamera() else Toast.makeText(requireContext(), R.string.msg_camera_denied, Toast.LENGTH_SHORT).show()
    }

    override fun launchPhotoPick() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.photo_source_title)
            .setItems(arrayOf(getString(R.string.photo_source_gallery), getString(R.string.photo_source_camera))) { _, which ->
                if (which == 0) pickPhoto.launch("image/*")
                else if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
                else requestCamera.launch(Manifest.permission.CAMERA)
            }
            .show()
    }

    private fun openCamera() {
        val file = File(requireContext().cacheDir, "product-${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        takePhoto.launch(cameraUri)
    }

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
        if (this::container.isInitialized) {
            MarketCatalog.refresh(requireContext()) { if (isAdded) render() }
        }
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
