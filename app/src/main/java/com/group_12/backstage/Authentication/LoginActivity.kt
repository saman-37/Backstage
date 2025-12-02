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
import androidx.core.content.ContextCompat
import com.group_12.backstage.MyAccount.LocationHelper
import com.group_12.backstage.notifications.FcmTokenManager

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val email = findViewById<EditText>(R.id.emailLoginEditText)
        val password = findViewById<EditText>(R.id.passwordLoginEditText)
        val loginBtn = findViewById<Button>(R.id.loginButton)

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

        val tvSignUp = findViewById<TextView>(R.id.tvSignUp)
        tvSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
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
