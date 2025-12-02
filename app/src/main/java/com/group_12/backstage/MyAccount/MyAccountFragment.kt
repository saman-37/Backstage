package com.group_12.backstage.MyAccount

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Filter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.charset.StandardCharsets

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
                if (enabled) {
                    showNotificationConfirmationDialog()                }
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

    // for notifications
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                // User denied the permission, so turn the switch back off
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
                // User said no, so turn the toggle back off in the ViewModel
                vm.updateToggle("receive_notifications", false)
                dialog.dismiss()
            }
            .show()
    }
    private fun requestNotificationPermission() {
        // Check for Android 13 (TIRAMISU) or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use the launcher to request the new POST_NOTIFICATIONS permission
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // For older versions, the permission is granted by default, so we don't need to do anything.
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

    // Replaced GetContent with PickVisualMedia for native Photo Picker
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.uploadProfileImage(uri)
        }
    }

    // Implement the click handler function
    override fun onProfileImageClicked() {
        // 1. Find the current profile image URL from the ViewModel state
        val headerItem = vm.items.value.find { it is SettingsItem.Header } as? SettingsItem.Header
        val imageUrl = headerItem?.profileImageUrl

        // 2. Inflate the custom dialog layout
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_view_profile_image, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.dialogProfileImage)
        val btnChange = dialogView.findViewById<Button>(R.id.btnChangePhoto)

        // 3. Load image into the large view
        Glide.with(requireContext())
            .load(imageUrl)
            .placeholder(R.drawable.ic_account_circle)
            .error(R.drawable.ic_account_circle)
            .circleCrop()
            .into(imageView)

        // 4. Create and show the dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // 5. Handle "Change Photo" click
        btnChange.setOnClickListener {
            dialog.dismiss() // Close view dialog
            showChangePhotoOptions() // Open selection dialog
        }

        dialog.show()
        // Make the dialog window transparent to remove the default white box
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
        // Only handle city and country
        if (id == "city" || id == "country") {
            showLocationEditDialog(id)
        } else {
            // Fallback for other fields if any
             val currentValue = vm.getCurrentValue(id)
             Snackbar.make(binding.root, "Edit: $id", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showLocationEditDialog(fieldId: String) {
        val currentValue = vm.getCurrentValue(fieldId) ?: ""
        
        // Get context for filtering
        val currentCountry = vm.getCurrentValue("country") ?: ""

        // Layout for the dialog
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_autocomplete_location, null)
        val input = view.findViewById<AutoCompleteTextView>(R.id.autoCompleteInput)
        input.setText(currentValue)
        input.hint = "Start typing ${fieldId.replaceFirstChar { it.uppercase() }}..."
        
        // Use custom layout for dropdown items
        val adapter = object : ArrayAdapter<String>(requireContext(), R.layout.item_autocomplete_city) {
            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        return FilterResults() // No internal filtering
                    }

                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        // No-op: We handle data updates and notifications manually in the Volley callback
                    }
                }
            }
        }
        input.setAdapter(adapter)
        input.threshold = 1 

        // Track selection to avoid over-writing with partial text
        var selectedCity = ""
        var selectedCountry = ""
        
        // Map to store data for selections
        val suggestionData = mutableMapOf<String, Map<String, String>>()

        // API Request Queue
        val requestQueue = Volley.newRequestQueue(requireContext())
        val requestTag = "LocationSearch"

        // Handler for Debounce
        val handler = Handler(Looper.getMainLooper())
        var searchRunnable: Runnable? = null

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                
                // 1. Remove any pending search tasks (Debounce)
                searchRunnable?.let { handler.removeCallbacks(it) }
                
                // 2. Cancel any in-flight Volley requests to avoid backlog
                requestQueue.cancelAll(requestTag)

                if (query.length >= 2) {
                    // 3. Schedule the new search after a reduced delay (200ms is enough for Photon)
                    searchRunnable = Runnable {
                        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
                        
                        // Photon API URL
                        var url = "https://photon.komoot.io/api/?q=$encodedQuery&limit=10"
                        
                        // Customize Query based on field
                        if (fieldId == "country") {
                             url += "&osm_tag=place:country"
                        } else if (fieldId == "city") {
                             // If we have a country, append it to context for better accuracy
                             if (currentCountry.isNotEmpty()) {
                                 val encodedCountry = URLEncoder.encode(currentCountry, StandardCharsets.UTF_8.toString())
                                 // Photon prefers space-separated query terms
                                 url = "https://photon.komoot.io/api/?q=$encodedQuery+$encodedCountry&limit=10"
                             }
                             // Filter for places (cities, towns, villages)
                             url += "&osm_tag=place:city&osm_tag=place:town&osm_tag=place:village"
                        }
                        
                        // Photon returns a JSON Object (FeatureCollection), not Array
                        val jsonObjectRequest = object : JsonObjectRequest(
                            Method.GET, url, null,
                            { response ->
                                // Ensure the view is still active/attached
                                if (input.isAttachedToWindow) {
                                    adapter.clear()
                                    suggestionData.clear()
                                    
                                    val features = response.optJSONArray("features")
                                    if (features != null) {
                                        for (i in 0 until features.length()) {
                                            val feature = features.getJSONObject(i)
                                            val props = feature.optJSONObject("properties")
                                            
                                            if (props != null) {
                                                val name = props.optString("name")
                                                val country = props.optString("country")
                                                val state = props.optString("state")
                                                
                                                // Determine if this is a city or just a country
                                                val city = if (fieldId == "city") name else props.optString("city")
                                                
                                                // Build a nice display string
                                                val displayParts = mutableListOf<String>()
                                                displayParts.add(name)
                                                if (state.isNotEmpty() && state != name) displayParts.add(state)
                                                if (country.isNotEmpty() && country != name) displayParts.add(country)
                                                
                                                val displayName = displayParts.joinToString(", ")
                                                
                                                adapter.add(displayName)
                                                suggestionData[displayName] = mapOf(
                                                    "city" to (if (fieldId == "city") name else city),
                                                    "country" to country
                                                )
                                            }
                                        }
                                    }
                                    
                                    adapter.notifyDataSetChanged()
                                    
                                    if (input.hasFocus() && adapter.count > 0) {
                                        input.showDropDown()
                                    }
                                }
                            },
                            { it.printStackTrace() }
                        ) {
                            override fun getHeaders(): MutableMap<String, String> {
                                val headers = HashMap<String, String>()
                                headers["User-Agent"] = "BackstageApp_StudentProject/1.0"
                                return headers
                            }
                        }
                        jsonObjectRequest.tag = requestTag
                        requestQueue.add(jsonObjectRequest)
                    }
                    
                    // Shorter delay because Photon is faster
                    handler.postDelayed(searchRunnable!!, 300)
                }
            }
        })
        
        input.setOnItemClickListener { _, _, position, _ ->
             val selection = adapter.getItem(position)
             val data = suggestionData[selection]
             if (data != null) {
                 selectedCity = data["city"] ?: ""
                 selectedCountry = data["country"] ?: ""
             }
        }
        
        input.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) input.dismissDropDown()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Update Location")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                searchRunnable?.let { handler.removeCallbacks(it) }
                requestQueue.cancelAll(requestTag)
                
                if (selectedCity.isNotEmpty() || selectedCountry.isNotEmpty()) {
                    vm.updateLocation(selectedCity, selectedCountry)
                } else {
                    // Fallback: User typed manually
                    vm.updateValueRow(fieldId, input.text.toString())
                }
            }
            .setNegativeButton("Cancel") { _, _ -> 
                searchRunnable?.let { handler.removeCallbacks(it) }
                requestQueue.cancelAll(requestTag)
            }
            .setOnDismissListener { 
                 searchRunnable?.let { handler.removeCallbacks(it) }
                 requestQueue.cancelAll(requestTag)
            }
            .show()
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
