package com.group_12.backstage

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.group_12.backstage.util.NotificationHelper

class MainActivity : AppCompatActivity() {

    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 102
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentUserId: String? = null
    private val chatIds = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navView: BottomNavigationView = findViewById(R.id.bottomNavigationView)
        val navController = findNavController(R.id.nav_host_fragment)

//        val appBarConfiguration = AppBarConfiguration(
//            setOf(
//                R.id.navigation_explore, R.id.navigation_my_interests, R.id.navigation_chat, R.id.navigation_my_account
//            )
//        )
//        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        currentUserId = auth.currentUser?.uid
        Log.d("MainActivity", "Current User ID: $currentUserId")

        requestNotificationPermission()
        listenForNewMessages()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Requesting POST_NOTIFICATIONS permission")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            } else {
                Log.d("MainActivity", "POST_NOTIFICATIONS permission already granted")
            }
        } else {
            Log.d("MainActivity", "POST_NOTIFICATIONS permission not needed below TIRAMISU")
        }
    }

    private fun listenForNewMessages() {
        currentUserId ?: run { Log.d("MainActivity", "No current user, not listening for new messages."); return }

        Log.d("MainActivity", "Setting up listener for chats involving $currentUserId")
        firestore.collection("chats")
            .whereArrayContains("participants", currentUserId!!)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("MainActivity", "Listen for chats failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) {
                    Log.d("MainActivity", "No chat documents found or snapshots are empty.")
                    return@addSnapshotListener
                }

                Log.d("MainActivity", "Received ${snapshots.size()} chat document changes.")
                for (doc in snapshots.documents) {
                    val chatId = doc.id
                    if (!chatIds.contains(chatId)) {
                        chatIds.add(chatId)
                        Log.d("MainActivity", "Found new chat: $chatId, setting up sub-listener.")
                        listenToChat(chatId)
                    } else {
                        Log.d("MainActivity", "Chat $chatId already being listened to.")
                    }
                }
            }
    }

    private fun listenToChat(chatId: String) {
        Log.d("MainActivity", "Setting up message listener for chat: $chatId")
        firestore.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp")
            .limitToLast(1)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("MainActivity", "Listen for messages in chat $chatId failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) {
                    Log.d("MainActivity", "No messages found in chat $chatId or snapshots are empty.")
                    return@addSnapshotListener
                }

                Log.d("MainActivity", "Received ${snapshots.size()} message document changes for chat $chatId.")
                for (doc in snapshots.documents) {
                    val message = doc.data
                    val senderId = message?.get("senderId") as? String
                    // Use a safe call with Elvis operator for currentUserId
                    val isRead = doc.getBoolean("isRead_${currentUserId ?: ""}") ?: true

                    Log.d("MainActivity", "Message from $senderId, isRead for current user: $isRead")

                    if (senderId != currentUserId && !isRead) {
                        val senderName = message?.get("senderName") as? String ?: "Someone"
                        val text = message?.get("text") as? String ?: ""
                        Log.d("MainActivity", "Showing notification for new message from $senderName: $text")
                        NotificationHelper.showNewMessageNotification(this, senderName, text)
                    } else if (senderId == currentUserId) {
                        Log.d("MainActivity", "Message is from current user, not showing notification.")
                    } else {
                        Log.d("MainActivity", "Message is already read for current user, not showing notification.")
                    }
                }
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(
                    this,
                    "Notifications disabled. You may miss new messages.",
                    Toast.LENGTH_LONG
                ).show()
                Log.d("MainActivity", "Notification permission denied.")
            } else if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Notification permission granted.")
            } else {
                Log.d("MainActivity", "Notification permission request result unknown.")
            }
        }
    }
}
