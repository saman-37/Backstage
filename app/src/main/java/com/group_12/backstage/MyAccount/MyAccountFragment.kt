package com.group_12.backstage.MyAccount

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.group_12.backstage.Authentication.LoginActivity
import com.group_12.backstage.databinding.FragmentMyAccountBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
            vm.uploadProgress.collectLatest { binding.progress.isVisible = it } //for profile image; to show the progress bar
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
    }

    override fun onChevronClicked(id: String) {
        if (id == "sign_out") {
            onSignOutClicked()
        } else {
            Snackbar.make(binding.root, "Open: $id", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onSwitchChanged(id: String, enabled: Boolean) {
        // Update in-app state (and, later, Firestore via ViewModel)
        vm.updateToggle(id, enabled)

        // Handle OS-level behaviour
        when (id) {
            "receive_notifications" -> {
                // User wants notifications ON → check system permission
                if (enabled && !areNotificationsEnabled()) {
                    showNotificationsDisabledDialog()
                }
            }

            "location_content" -> {
                // User wants location-based content → check if location is enabled
                if (enabled && !isLocationEnabled()) {
                    showLocationDisabledDialog()
                } else if (enabled) {
                    requestLocationPermission()
                }
            }
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

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.uploadProfileImage(it) }
    }

    // Implement the click handler function
    override fun onProfileImageClicked() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(requireContext())
            .setTitle("Change Profile Picture")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> checkCameraPermissionAndLaunch()
                    1 -> galleryLauncher.launch("image/*")
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
        // Fallback – shouldn't really be hit for now
        Snackbar.make(binding.root, "Edit: $id", Snackbar.LENGTH_SHORT).show()
    }

    // ---- Notifications helpers ----

    private fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
    }

    private fun showNotificationsDisabledDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Notifications disabled")
            .setMessage(
                "It appears as though notifications for this app are disabled. " +
                        "You can enable them in the app settings."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                openNotificationSettings()
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun openNotificationSettings() {
        val context = requireContext()
        val intent = Intent().apply {
            action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Settings.ACTION_APP_NOTIFICATION_SETTINGS
            } else {
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        startActivity(intent)
    }

    // ---- Location helpers ----
    private fun isLocationEnabled(): Boolean {
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showLocationDisabledDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Location disabled")
            .setMessage(
                "It appears as though location services for this app are disabled. " +
                        "You can enable them in the system settings."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                openLocationSettings()
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted.
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted.
            }
            else -> {
                // No location access granted.
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
