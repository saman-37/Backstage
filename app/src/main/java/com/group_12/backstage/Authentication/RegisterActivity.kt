package com.group_12.backstage.Authentication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.group_12.backstage.R

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

            // Create Firebase Auth account
            auth.createUserWithEmailAndPassword(emailText, passText)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser!!.uid

                        // Show toast and go to LoginActivity immediately
                        Toast.makeText(this, "Registered!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()

                        // Save user data in Firestore in the background
                        val userMap = hashMapOf(
                            "uid" to uid,
                            "name" to nameText,
                            "email" to emailText,
                            "profileImageUrl" to "",
                            "bio" to ""
                        )
                        db.collection("users").document(uid).set(userMap)
                            .addOnFailureListener {
                                // Optional: log the error, but user is already navigated
                                it.printStackTrace()
                            }

                    } else {
                        Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // Login link click (already handled)
        loginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
