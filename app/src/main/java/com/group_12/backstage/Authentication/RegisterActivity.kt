package com.group_12.backstage.Authentication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.group_12.backstage.R
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.group_12.backstage.MyAccount.LocationHelper

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val name = findViewById<TextInputEditText>(R.id.nameEditText)
        val email = findViewById<TextInputEditText>(R.id.emailEditText)
        val password = findViewById<TextInputEditText>(R.id.passwordEditText)
        val registerBtn = findViewById<Button>(R.id.registerButton)
        val loginLink = findViewById<TextView>(R.id.tvLoginLink)

        // Register button click
        registerBtn.setOnClickListener {
            val nameText = name.text.toString().trim()
            val emailText = email.text.toString().trim()
            val passText = password.text.toString().trim()

            if (nameText.isEmpty() || emailText.isEmpty() || passText.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. Check if the username already exists in Firestore
            db.collection("users")
                .whereEqualTo("name", nameText)
                .get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        if (task.result != null && !task.result!!.isEmpty) {
                            // Username already exists
                            Toast.makeText(this, "Username already exists. Please choose another.", Toast.LENGTH_LONG).show()
                        } else {
                            // Username is unique, proceed with registration
                            createUser(nameText, emailText, passText)
                        }
                    } else {
                        // Error checking for username
                        Toast.makeText(this, "Error checking username: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // Login link click (already handled)
        loginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    // seperate user creation logic
    private fun createUser(nameText: String, emailText: String, passText: String) {
        // Create Firebase Auth account
        auth.createUserWithEmailAndPassword(emailText, passText)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    val uid = firebaseUser!!.uid

                    // Create the profile update request with the name
                    val profileUpdates = userProfileChangeRequest {
                        displayName = nameText
                    }

                    // Update the profile AND WAIT for it to finish
                    firebaseUser.updateProfile(profileUpdates).addOnCompleteListener { profileTask ->
                        if (profileTask.isSuccessful) {
                            Toast.makeText(this, "Registered!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        } else {
                            // Profile update failed, but user was still created.
                            Toast.makeText(this, "Registration successful, but failed to set display name.", Toast.LENGTH_LONG).show()
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }
                    }

                    // Save user data in Firestore in the background
                    val userMap = hashMapOf(
                        "uid" to uid,
                        "name" to nameText,
                        "email" to emailText,
                        "profileImageUrl" to "",
                        "bio" to "",
                        "receiveNotifications" to false,
                        "city" to "",
                        "state" to "",
                        "country" to "",
                        "locationBasedContent" to false
                    )
                    db.collection("users").document(uid).set(userMap)
                        .addOnSuccessListener {
                            // One-time location set at account creation
                            ensureLocationAndUpdate(uid)
                        }
                        .addOnFailureListener {
                            it.printStackTrace()  // Optional: log the error, but user is already navigated
                        }

                } else {
                    // --- IMPROVED ERROR HANDLING ---
                    try {
                        throw task.exception!!
                    } catch (e: FirebaseAuthWeakPasswordException) {
                        // Specific error for short password
                        Toast.makeText(this, "Password must contain at least 6 characters.", Toast.LENGTH_LONG).show()
                    } catch (e: FirebaseAuthInvalidCredentialsException) {
                        // Specific error for badly formatted email
                        Toast.makeText(this, "Invalid email format.", Toast.LENGTH_LONG).show()
                    } catch (e: FirebaseAuthUserCollisionException) {
                        // Specific error for email that already exists
                        Toast.makeText(this, "An account with this email already exists.", Toast.LENGTH_LONG).show()
                    }
                    catch (e: Exception) {
                        // All other registration errors
                        Toast.makeText(this, "Registration failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
    }


    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val granted = result[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@registerForActivityResult
                LocationHelper.updateUserLocationIfPossible(this, uid, uid)
            }
        }

    //for one-time location that we save to save in our database
    private fun ensureLocationAndUpdate(uid: String) {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            // Already allowed → just do one-time update
            LocationHelper.updateUserLocationIfPossible(this, uid, uid)
        } else {
            // Ask ONCE at first registration or first login
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}
