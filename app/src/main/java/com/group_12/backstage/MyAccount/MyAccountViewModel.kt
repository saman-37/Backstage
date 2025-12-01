package com.group_12.backstage.MyAccount

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.group_12.backstage.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MyAccountViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val _items = MutableStateFlow<List<SettingsItem>>(emptyList())
    val items = _items.asStateFlow()

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
            .addSnapshotListener { snapshot, _ ->
                val firebaseUser = auth.currentUser
                val displayName = firebaseUser?.displayName?.takeIf { it.isNotBlank() }
                val greetingName = displayName ?: firebaseUser?.email ?: "User"
                val profileImageUrl = snapshot?.getString("profileImageUrl")?.takeIf { it.isNotBlank() }

                val receiveNotifications =
                    snapshot?.getBoolean("receiveNotifications") ?: false
                val city = snapshot?.getString("city") ?: ""
                val state = snapshot?.getString("state") ?: ""
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

                    // Location toggle button (city, state and country are the fields that we will extract from user's current location and save in db)
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
                    add(
                        SettingsItem.ValueRow(
                            id = "state",
                            title = "State",
                            value = state,
                            icon = R.drawable.ic_location,
                            showEdit = true
                        )
                    )
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
        val user = auth.currentUser ?: run { _uploadProgress.value = false; return }
        // Firebase Storage library is separate from Auth and Firestore
        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("profile_images/${user.uid}.jpg")

        storageRef.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let { throw it }
                }
                storageRef.downloadUrl
            }
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val downloadUri = task.result
                    updateUserProfileUrl(downloadUri)
                } else {
                    _uploadProgress.value = false
                    // Handle failure
                }
            }
    }

    private fun updateUserProfileUrl(uri: Uri) {
        val user = auth.currentUser!!

        // First, update the Firestore document.
        // The listener on this document will automatically update the UI.
        db.collection("users").document(user.uid)
            .update("profileImageUrl", uri.toString())
            .addOnSuccessListener {
                // Once Firestore is updated, also update the Firebase Auth profile
                // This is for consistency.
                val profileUpdates = userProfileChangeRequest { photoUri = uri }
                user.updateProfile(profileUpdates).addOnCompleteListener {
                    _uploadProgress.value = false // Hide progress bar
                }
            }
            .addOnFailureListener {
                _uploadProgress.value = false
                // Handle Firestore update failure
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

        db.collection("users").document(uid).update(field, enabled)
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
            "state" -> "state"
            "country" -> "country"
            else -> null
        } ?: return

        db.collection("users").document(uid).update(field, newValue)
    }
}
