package com.group_12.backstage.Chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.with
import androidx.compose.ui.semantics.error
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.group_12.backstage.R
import com.group_12.backstage.util.NotificationHelper
import de.hdodenhof.circleimageview.CircleImageView

class DirectMessageActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var emptyChatTextView: TextView
    private val messages = mutableListOf<Message>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var listener: ListenerRegistration? = null

    private var targetUserId: String? = null
    private var targetUserName: String? = null
    private var chatId: String? = null

    private lateinit var toolbarUserName: TextView
    private lateinit var backButton: ImageView

    private var targetUserProfileImage: String? = null
    private lateinit var toolbarProfileImage: CircleImageView


    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_direct_message)

        targetUserId = intent.getStringExtra("targetUserId")
        targetUserName = intent.getStringExtra("targetUserName")
        targetUserProfileImage = intent.getStringExtra("targetUserProfileImage")

        recyclerView = findViewById(R.id.messagesRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        emptyChatTextView = findViewById(R.id.emptyChatTextView)

        toolbarUserName = findViewById(R.id.toolbarUserName)
        backButton = findViewById(R.id.backButton)
        toolbarProfileImage = findViewById(R.id.toolbarProfileImage)

        toolbarUserName.text = targetUserName ?: "Chat"
        backButton.setOnClickListener { onBackPressed() }

        // *** LOAD THE IMAGE WITH GLIDE ***
        if (!targetUserProfileImage.isNullOrEmpty()) {
            Glide.with(this)
                .load(targetUserProfileImage)
                .placeholder(R.drawable.ic_person) // Show placeholder while loading
                .error(R.drawable.ic_person)       // Show error placeholder if it fails
                .into(toolbarProfileImage)
        } else {
            // Set a default image if the URL is null or empty
            toolbarProfileImage.setImageResource(R.drawable.ic_person)
        }


        setupRecyclerView()
        setupChatId()
        markChatAsRead() // Mark chat as read when activity opens
        setupSendButton()
        listenForMessages()

        // Request notification permission when the activity is created
        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        // Permission is only needed for Android 13 (API 33) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                // If permission is not granted, request it.
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun markChatAsRead() {
        val currentUserId = auth.currentUser?.uid
        if (chatId != null && currentUserId != null) {
            db.collection("chats").document(chatId!!)
                .set(mapOf("isRead_${currentUserId}" to true), SetOptions.merge())
        }
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(messages)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
    }

    private fun setupChatId() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null && targetUserId != null) {
            val userList = listOf(currentUserId, targetUserId!!).sorted()
            chatId = "chat_${userList[0]}_${userList[1]}"
        }
    }

    private fun setupSendButton() {
        sendButton.setOnClickListener {
            val text = messageEditText.text.toString()
            if (text.isNotBlank()) {
                val currentUser = auth.currentUser ?: return@setOnClickListener
                sendMessage(currentUser.uid, text)
                messageEditText.text.clear()
            }
        }
    }

    private fun sendMessage(senderId: String, message: String) {
        if (chatId == null) return

        val currentUser = auth.currentUser ?: return
        val senderName = currentUser.displayName ?: "Anonymous"
        val receiverId = targetUserId ?: return

        val timestamp = System.currentTimeMillis()

        val msg = hashMapOf(
            "senderId" to senderId,
            "senderName" to senderName, // Important for notifications
            "text" to message,
            "timestamp" to timestamp
        )

        val chatDocRef = db.collection("chats").document(chatId!!)

        chatDocRef.collection("messages")
            .add(msg)
            .addOnSuccessListener {
                // Update chat metadata for sorting and unread status
                val chatUpdates = mapOf(
                    "lastMessageTimestamp" to timestamp,
                    "lastMessageSenderId" to senderId,
                    "lastMessageText" to message, // Add this for the preview
                    "isRead_${receiverId}" to false, // Mark as unread for the receiver
                    "isRead_${senderId}" to true      // Mark as read for the sender
                )
                chatDocRef.set(chatUpdates, SetOptions.merge())
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenForMessages() {
        if (chatId == null) return
        val currentUserId = auth.currentUser?.uid ?: ""

        listener = db.collection("chats")
            .document(chatId!!)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshots, e ->
                if (e != null) { return@addSnapshotListener }
                if (snapshots == null) { return@addSnapshotListener }

                val newMessages = mutableListOf<Message>()
                for (docChange in snapshots.documentChanges) {
                    if (docChange.type == DocumentChange.Type.ADDED) {
                        val messageDoc = docChange.document
                        val senderId = messageDoc.getString("senderId") ?: ""
                        val text = messageDoc.getString("text") ?: ""
                        val timestamp = messageDoc.getLong("timestamp") ?: 0L
                        val isSentByCurrentUser = senderId == currentUserId

                        newMessages.add(Message(senderId, text, timestamp, isSentByCurrentUser))

                        // --- NOTIFICATION LOGIC ---
                        // Show notification if the message is from another user AND the chat screen isn't active.
                        if (!isSentByCurrentUser && lifecycle.currentState != Lifecycle.State.RESUMED) {
                            if (checkNotificationPermission()) {
                                val senderName = messageDoc.getString("senderName") ?: "Someone"
                                NotificationHelper.showNewMessageNotification(this, senderName, text)
                            }
                        }
                    }
                }

                if (newMessages.isNotEmpty()) {
                    messages.addAll(newMessages)
                    messages.sortBy { it.timestamp }
                    adapter.notifyDataSetChanged()
                    emptyChatTextView.isVisible = messages.isEmpty()
                    recyclerView.isVisible = messages.isNotEmpty()
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            }
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            // For older Android versions, permission is implicitly granted.
            true
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
                    "Notifications disabled. You won't get message alerts.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }
}
