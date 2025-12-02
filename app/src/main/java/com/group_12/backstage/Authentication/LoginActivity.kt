package com.group_12.backstage.Authentication

import com.group_12.backstage.R
import com.google.firebase.auth.FirebaseAuth
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.content.Intent
import android.widget.TextView
import com.group_12.backstage.MainActivity
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.group_12.backstage.MyAccount.LocationHelper
import com.group_12.backstage.notifications.FcmTokenManager
import android.os.Build

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val email = findViewById<EditText>(R.id.emailLoginEditText)
        val password = findViewById<EditText>(R.id.passwordLoginEditText)
        val loginBtn = findViewById<Button>(R.id.loginButton)
        val forgotPassword = findViewById<TextView>(R.id.tvForgotPassword)

        loginBtn.setOnClickListener {
            val emailText = email.text.toString().trim()
            val passText = password.text.toString().trim()

            // Check if either field is empty
            if (emailText.isEmpty() || passText.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(emailText, passText)
                .addOnSuccessListener {
                    val uid = auth.currentUser!!.uid
                    // One-time location refresh on fresh login
                    ensureLocationAndUpdate(uid)
                    // Request notification permission (Android 13+)
                    requestNotificationPermission()
                    // Initialize FCM token for push notifications
                    FcmTokenManager.initializeFcmToken()
                    Toast.makeText(this, "Logged in!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
                }
        }

        forgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        val tvSignUp = findViewById<TextView>(R.id.tvSignUp)
        tvSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

    }

    private fun showForgotPasswordDialog() {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialog_forgot_password, null)
        val emailEditText = dialogLayout.findViewById<EditText>(R.id.emailResetEditText)

        builder.setTitle("Forgot Password")
            .setMessage("Enter your email to receive a password reset link.")
            .setView(dialogLayout)
            .setPositiveButton("Send") { _, _ ->
                val email = emailEditText.text.toString().trim()
                if (email.isNotEmpty()) {
                    auth.sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to send reset email: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
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

    private fun requestNotificationPermission() {
        // Only request on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }

}
