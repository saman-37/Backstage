package com.group_12.backstage.MyAccount

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.group_12.backstage.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MyAccountViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val _items = MutableStateFlow<List<SettingsItem>>(emptyList())
    val items = _items.asStateFlow()

    // Feedback messages for the Fragment
    private val _userMessages = MutableSharedFlow<String>()
    val userMessages = _userMessages.asSharedFlow()

    init {
        refreshAuthStatus()
    }

    fun refreshAuthStatus() {
        val user = auth.currentUser
        if (user == null) {
            _items.value = listOf(
                SettingsItem.Header(
                    welcomeBrand = "Backstage",
                    showSignIn = true,
                    profileImageUrl = null
                )
            )
        } else {
            listenToUserSettings(user.uid)
        }
    }

    private fun listenToUserSettings(uid: String) {
        db.collection("users").document(uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("MyAccountViewModel", "Listen failed.", e)
                    return@addSnapshotListener
                }

                val firebaseUser = auth.currentUser
                val displayName = firebaseUser?.displayName?.takeIf { it.isNotBlank() }
                val greetingName = displayName ?: firebaseUser?.email ?: "User"
                val profileImageUrl = snapshot?.getString("profileImageUrl")?.takeIf { it.isNotBlank() }

                val receiveNotifications =
                    snapshot?.getBoolean("receiveNotifications") ?: false
                val city = snapshot?.getString("city") ?: ""
                val country = snapshot?.getString("country") ?: ""

                val locationContent =
                    snapshot?.getBoolean("locationBasedContent") ?: false

                _items.value = buildList {
                    // Header
                    add(
                        SettingsItem.Header(
                            welcomeBrand = greetingName,
                            showSignIn = false,
                            profileImageUrl = profileImageUrl
                        )
                    )

                    // Notifications section
                    add(SettingsItem.SectionTitle(title = "Notifications"))

                    add(
                        SettingsItem.Switch(
                            id = "receive_notifications",
                            title = "Receive Notifications?",
                            checked = receiveNotifications,
                            icon = R.drawable.ic_notifications
                        )
                    )

                    // Location toggle button
                    add(
                        SettingsItem.SectionTitle(
                            title = "Location Settings",
                            badge = "NEW!"
                        )
                    )

                    add(
                        SettingsItem.Switch(
                            id = "location_content",
                            title = "Location Based Content",
                            checked = locationContent,
                            icon = R.drawable.ic_location
                        )
                    )
                    add(
                        SettingsItem.ValueRow(
                            id = "city",
                            title = "City",
                            value = city,
                            icon = R.drawable.ic_location,
                            showEdit = true
                        )
                    )
                    // State removed as requested
                    add(
                        SettingsItem.ValueRow(
                            id = "country",
                            title = "Country",
                            value = country,
                            icon = R.drawable.ic_location,
                            showEdit = true
                        )
                    )

                    // Account section
                    add(SettingsItem.SectionTitle(title = "Account"))
                    add(
                        SettingsItem.Chevron(
                            id = "sign_out",
                            title = "Sign Out",
                            icon = R.drawable.ic_arrow_back
                        )
                    )
                }
            }
    }

    // ---  FUNCTIONS FOR HANDLING THE PROFILE IMAGE for uploading ---
    private val _uploadProgress = MutableStateFlow(false)
    val uploadProgress = _uploadProgress.asStateFlow()

    var tempImageUri: Uri?
        get() = savedStateHandle.get<Uri>("temp_image_uri")
        set(value) = savedStateHandle.set("temp_image_uri", value)


    fun uploadProfileImage(uri: Uri) {
        _uploadProgress.value = true
        val user = auth.currentUser ?: run { 
            _uploadProgress.value = false
            return 
        }
        
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference
            .child("profile_images")
            .child("${user.uid}.jpg")

        Log.d("MyAccountViewModel", "Starting upload to ${storageRef.path} from URI: $uri")

        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()

        // 1. Upload the file
        storageRef.putFile(uri, metadata)
            .addOnSuccessListener { taskSnapshot ->
                Log.d("MyAccountViewModel", "Upload SUCCESS. Bytes: ${taskSnapshot.bytesTransferred}")
                
                // 2. Get the Download URL
                taskSnapshot.storage.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        Log.d("MyAccountViewModel", "Download URL: $downloadUri")
                        updateUserProfileUrl(downloadUri)
                    }
                    .addOnFailureListener { e ->
                        Log.e("MyAccountViewModel", "Failed to get download URL", e)
                        _uploadProgress.value = false
                        
                        val msg = e.message ?: ""
                        if (msg.contains("Object does not exist", ignoreCase = true) || msg.contains("404")) {
                             viewModelScope.launch { 
                                 _userMessages.emit("Upload Success, but Read Failed. Please ALLOW READ access in Firebase Storage Rules.") 
                             }
                        } else {
                            viewModelScope.launch { 
                                _userMessages.emit("Failed to get image URL: $msg") 
                            }
                        }
                    }
            }
            .addOnFailureListener { e ->
                Log.e("MyAccountViewModel", "Upload FAILED", e)
                _uploadProgress.value = false
                
                if (e is java.io.FileNotFoundException) {
                     viewModelScope.launch { _userMessages.emit("Error: Local file not found. Please try again.") }
                } else {
                     viewModelScope.launch { _userMessages.emit("Upload Failed: ${e.message}") }
                }
            }
    }

    private fun updateUserProfileUrl(uri: Uri) {
        val user = auth.currentUser!!

        val timestampedUri = uri.buildUpon()
            .appendQueryParameter("t", System.currentTimeMillis().toString())
            .build()

        db.collection("users").document(user.uid)
            .set(mapOf("profileImageUrl" to timestampedUri.toString()), SetOptions.merge())
            .addOnSuccessListener {
                Log.d("MyAccountViewModel", "Firestore profileImageUrl updated")
                
                val profileUpdates = userProfileChangeRequest { photoUri = timestampedUri }
                user.updateProfile(profileUpdates).addOnCompleteListener {
                    _uploadProgress.value = false
                    Log.d("MyAccountViewModel", "Auth profile updated")
                    viewModelScope.launch { _userMessages.emit("Profile Photo Updated!") }
                }
            }
            .addOnFailureListener { e ->
                Log.e("MyAccountViewModel", "Firestore update failed", e)
                _uploadProgress.value = false
                viewModelScope.launch { _userMessages.emit("Failed to save profile URL: ${e.message}") }
            }
    }


    // Called from MyAccountFragment when a switch is toggled
    fun updateToggle(id: String, enabled: Boolean) {
        // Update local list so UI responds instantly
        _items.value = _items.value.map {
            if (it is SettingsItem.Switch && it.id == id) it.copy(checked = enabled) else it
        }

        // Persist to Firestore
        val uid = auth.currentUser?.uid ?: return
        val field = when (id) {
            "receive_notifications" -> "receiveNotifications"
            "location_content" -> "locationBasedContent"
            else -> null
        } ?: return

        db.collection("users").document(uid).set(mapOf(field to enabled), SetOptions.merge())
    }

    // Used by the edit dialog to get the current text
    fun getCurrentValue(id: String): String? {
        return _items.value
            .firstOrNull { it is SettingsItem.ValueRow && it.id == id }
            ?.let { (it as SettingsItem.ValueRow).value }
    }

    // Called from MyAccountFragment when user saves new value
    fun updateValueRow(id: String, newValue: String) {
        // Update local list
        _items.value = _items.value.map {
            if (it is SettingsItem.ValueRow && it.id == id) it.copy(value = newValue) else it
        }

        // Persist to Firestore
        val uid = auth.currentUser?.uid ?: return
        val field = when (id) {
            "city" -> "city"
            "country" -> "country"
            else -> null
        } ?: return

        db.collection("users").document(uid).set(mapOf(field to newValue), SetOptions.merge())
    }
    
    // Helper to update location fields (simplified for City + Country)
    fun updateLocation(city: String, country: String) {
         val uid = auth.currentUser?.uid ?: return
         val updates = mapOf(
             "city" to city,
             "country" to country
         )
         db.collection("users").document(uid).set(updates, SetOptions.merge())
    }
}
