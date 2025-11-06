package com.group_12.backstage_group_12

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.group_12.backstage_group_12.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = ContextCompat.getColor(this, R.color.purple_40)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.purple_40)

        val navController = findNavController(R.id.nav_host_fragment)
        binding.bottomNavigationView.setupWithNavController(navController)

        binding.bottomNavigationView.setBackgroundColor(
            ContextCompat.getColor(this, R.color.purple_80)
        )
        binding.bottomNavigationView.itemRippleColor =
            ContextCompat.getColorStateList(this, R.color.nav_ripple)
    }
}