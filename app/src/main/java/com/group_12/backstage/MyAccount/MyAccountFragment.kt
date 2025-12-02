package com.group_12.backstage.MyAccount

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.group_12.backstage.Authentication.LoginActivity
import com.group_12.backstage.R
import com.group_12.backstage.databinding.FragmentMyAccountBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyAccountFragment : Fragment(), MyAccountNavigator {

    private var _binding: FragmentMyAccountBinding? = null
    private val binding get() = _binding!!

    private val vm: MyAccountViewModel by viewModels()
    private lateinit var adapter: SettingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = SettingsAdapter(this)

        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.list.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )

        binding.progress.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            vm.items.collectLatest { adapter.submitList(it) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.uploadProgress.collectLatest { binding.progress.isVisible = it } //for profile image; to show the progress bar
        }

        viewLifecycleOwner.lifecycleScope.launch {
             vm.userMessages.collectLatest { message ->
                 Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
             }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh list to check for Auth changes (e.g. if coming back from LoginActivity)
        vm.refreshAuthStatus()
    }

    // --- Navigator callbacks ---
    override fun onSignInClicked() {
        // Launch LoginActivity
        val intent = Intent(requireContext(), LoginActivity::class.java)
        startActivity(intent)
    }

    override fun onSignOutClicked() {
        FirebaseAuth.getInstance().signOut()
        Snackbar.make(binding.root, "Signed out successfully", Snackbar.LENGTH_SHORT).show()
        vm.refreshAuthStatus()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        startActivity(intent)
    }

    override fun onChevronClicked(id: String) {
        when (id) {
            "sign_out" -> onSignOutClicked()
            "reset_password" -> showResetPasswordConfirmationDialog()
            else -> Snackbar.make(binding.root, "Open: $id", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onSwitchChanged(id: String, enabled: Boolean) {
        vm.updateToggle(id, enabled)

        when (id) {
            "receive_notifications" -> {
                if (enabled) {
                    showNotificationConfirmationDialog()
                }
            }
        }
    }

    // for notifications
    private val requestNotificationPermissionLauncher = 
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                vm.updateToggle("receive_notifications", false)
                Snackbar.make(binding.root, "Notifications cannot be sent without permission.", Snackbar.LENGTH_SHORT).show()
            }
        }
    
    private fun showNotificationConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Enable Notifications")
            .setMessage("Do you want this application to send you notifications about new messages?")
            .setPositiveButton("Yes") { dialog, _ ->
                requestNotificationPermission()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                vm.updateToggle("receive_notifications", false)
                dialog.dismiss()
            }
            .show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // for profile photo
    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val tempUri = getTempImageUri(requireContext())
            vm.tempImageUri = tempUri
            cameraLauncher.launch(tempUri)
        } else {
            Snackbar.make(binding.root, "Camera permission is required.", Snackbar.LENGTH_SHORT).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            vm.tempImageUri?.let { uri ->
                vm.uploadProfileImage(uri)
            }
        }
    }

    private fun getTempImageUri(context: Context): Uri {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val tempImageFile = File.createTempFile("JPEG_${timeStamp}_", ".jpg", context.cacheDir)

        return FileProvider.getUriForFile(context, "${context.packageName}.provider", tempImageFile)
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.uploadProfileImage(uri)
        }
    }

    override fun onProfileImageClicked() {
        val headerItem = vm.items.value.find { it is SettingsItem.Header } as? SettingsItem.Header
        val imageUrl = headerItem?.profileImageUrl

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_view_profile_image, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.dialogProfileImage)
        val btnChange = dialogView.findViewById<Button>(R.id.btnChangePhoto)

        Glide.with(requireContext())
            .load(imageUrl)
            .placeholder(R.drawable.ic_account_circle)
            .error(R.drawable.ic_account_circle)
            .circleCrop()
            .into(imageView)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnChange.setOnClickListener {
            dialog.dismiss() 
            showChangePhotoOptions() 
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showChangePhotoOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(requireContext())
            .setTitle("Change Profile Picture")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> checkCameraPermissionAndLaunch()
                    1 -> galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    2 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val tempUri = getTempImageUri(requireContext())
            vm.tempImageUri = tempUri
            cameraLauncher.launch(tempUri)
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onEditClicked(id: String) {
        // This function is no longer needed as location is handled automatically.
    }

    // --- Password Reset ---
    private fun showResetPasswordConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset Password")
            .setMessage("Are you sure you want to send a password reset email?")
            .setPositiveButton("Yes") { _, _ ->
                vm.sendPasswordReset()
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
