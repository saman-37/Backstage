package com.group_12.backstage.Chat

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.group_12.backstage.R
import com.group_12.backstage.util.NotificationHelper

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_direct_message)

        targetUserId = intent.getStringExtra("targetUserId")
        targetUserName = intent.getStringExtra("targetUserName")

        recyclerView = findViewById(R.id.messagesRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        emptyChatTextView = findViewById(R.id.emptyChatTextView)

        toolbarUserName = findViewById(R.id.toolbarUserName)
        backButton = findViewById(R.id.backButton)

        toolbarUserName.text = targetUserName ?: "Chat"
        backButton.setOnClickListener { onBackPressed() }

        setupRecyclerView()
        setupChatId()
        setupSendButton()
        listenForMessages()
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
                // *** FIX: Get the current user's UID ***
                val currentUser = auth.currentUser ?: return@setOnClickListener
                // *** FIX: Pass the UID to sendMessage ***
                sendMessage(currentUser.uid, text)
                messageEditText.text.clear()
            }
        }
    }

    // In DirectMessageActivity.kt

    private fun listenForMessages() {
        if (chatId == null) return
        val currentUserId = auth.currentUser?.uid ?: ""

        // This listener will now only be responsible for ADDING new messages
        // We will load the initial history separately.
        listener = db.collection("chats")
            .document(chatId!!)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    // Handle error, maybe log it or show a Toast
                    return@addSnapshotListener
                }
                if (snapshots == null) {
                    return@addSnapshotListener
                }

                // --- Consolidated Logic for NEW Messages ---
                val newMessages = mutableListOf<Message>()
                for (docChange in snapshots.documentChanges) {

                    // We only care about documents that were newly added since the last update
                    if (docChange.type == DocumentChange.Type.ADDED) {
                        val messageDoc = docChange.document
                        val senderId = messageDoc.getString("senderId") ?: ""
                        val text = messageDoc.getString("text") ?: ""
                        val timestamp = messageDoc.getLong("timestamp") ?: 0L
                        val isSentByCurrentUser = senderId == currentUserId

                        newMessages.add(Message(senderId, text, timestamp, isSentByCurrentUser))

                        // --- Notification Logic ---
                        // Only show a notification if the message is NOT from the current user
                        // AND the chat screen is not currently visible (app is in background).
                        if (!isSentByCurrentUser && lifecycle.currentState != Lifecycle.State.RESUMED) {
                            val senderName = messageDoc.getString("senderName") ?: "Someone"
                            NotificationHelper.showNewMessageNotification(this, senderName, text)
                        }
                    }
                }

                // --- UI Update ---
                if (newMessages.isNotEmpty()) {
                    // Add the new messages to our main list
                    messages.addAll(newMessages)
                    // Sort by timestamp to handle any out-of-order delivery
                    messages.sortBy { it.timestamp }

                    // Notify the adapter of the changes
                    adapter.notifyDataSetChanged()

                    // Update UI visibility and scroll to the newest message at the bottom
                    emptyChatTextView.isVisible = messages.isEmpty()
                    recyclerView.isVisible = messages.isNotEmpty()
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            }
    }


    private fun sendMessage(senderId: String, message: String) { // *** FIX: Changed parameter to senderId ***
        if (chatId == null) return

        val currentUser = auth.currentUser
        // We get the sender's display name here to store it with the message
        val senderName = currentUser?.displayName ?: "Anonymous"

        val msg = hashMapOf(
            "senderId" to senderId,       // The user's unique ID
            "senderName" to senderName,   // The user's display name for notifications
            "text" to message,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("chats")
            .document(chatId!!)
            .collection("messages")
            .add(msg)
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
            }
    }



    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }
}
