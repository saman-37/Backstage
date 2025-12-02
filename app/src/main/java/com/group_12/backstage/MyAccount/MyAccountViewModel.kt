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
                val email = firebaseUser?.email ?: ""

                val receiveNotifications =
                    snapshot?.getBoolean("receiveNotifications") ?: false

                _items.value = buildList {
                    // Header
                    add(
                        SettingsItem.Header(
                            welcomeBrand = greetingName,
                            showSignIn = false,
                            profileImageUrl = profileImageUrl
                        )
                    )

                    // Email
                    add(SettingsItem.ValueRow(
                        id = "email",
                        title = "Email",
                        value = email,
                        icon = R.drawable.ic_email,
                        showEdit = false
                    ))

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

                    // Account section
                    add(SettingsItem.SectionTitle(title = "Account"))
                    add(
                        SettingsItem.Chevron(
                            id = "reset_password",
                            title = "Reset Password",
                            icon = R.drawable.ic_lock
                        )
                    )
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
            else -> null
        } ?: return

        db.collection("users").document(uid).set(mapOf(field to enabled), SetOptions.merge())
    }

    fun sendPasswordReset() {
        val user = auth.currentUser ?: return
        val email = user.email
        if (email != null) {
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    viewModelScope.launch { _userMessages.emit("Password reset email sent to $email") }
                }
                .addOnFailureListener { e ->
                    viewModelScope.launch { _userMessages.emit("Failed to send reset email: ${e.message}") }
                }
        }
    }
}
