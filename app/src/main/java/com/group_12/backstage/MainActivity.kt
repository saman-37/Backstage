package com.group_12.backstage

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.group_12.backstage.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Switch back to the main app theme before super.onCreate
        setTheme(R.style.Theme_Backstage_group_12)
        
        super.onCreate(savedInstanceState)
        
        // Force White Status Bar with Dark Icons Programmatically
        // This ensures the status bar is white regardless of theme defaults
        window.statusBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        FirebaseApp.initializeApp(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment)
        binding.bottomNavigationView.setupWithNavController(navController)

        binding.bottomNavigationView.setBackgroundColor(
            ContextCompat.getColor(this, R.color.dark_blue)
        )
        binding.bottomNavigationView.itemRippleColor =
            ContextCompat.getColorStateList(this, R.color.nav_ripple)


        val db = FirebaseFirestore.getInstance()

        db.collection("test").document("hello")
            .set(mapOf("msg" to "Hello Firestore"))
            .addOnSuccessListener { Log.d("Firestore", "Success") }
            .addOnFailureListener { e -> Log.e("Firestore", "Fail", e) }
    }
}
