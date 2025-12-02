package com.group_12.backstage.Chat

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
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
import com.google.firebase.firestore.SetOptions
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
        markChatAsRead() // Mark chat as read when activity opens
        setupSendButton()
        listenForMessages()

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

        // Check if chat document exists, create if not
        chatDocRef.get().addOnSuccessListener { documentSnapshot ->
            if (!documentSnapshot.exists()) {
                val participants = listOf(senderId, receiverId)
                val initialChatData = mapOf(
                    "participants" to participants,
                    "lastMessageTimestamp" to timestamp,
                    "lastMessageSenderId" to senderId,
                    "lastMessageText" to message,
                    "isRead_${receiverId}" to false,
                    "isRead_${senderId}" to true
                )
                chatDocRef.set(initialChatData)
                    .addOnSuccessListener { Log.d("DirectMessageActivity", "Chat document created.") }
                    .addOnFailureListener { e -> Log.e("DirectMessageActivity", "Error creating chat document", e) }
            } else {
                // If chat document exists, just update its metadata
                val chatUpdates = mapOf(
                    "lastMessageTimestamp" to timestamp,
                    "lastMessageSenderId" to senderId,
                    "lastMessageText" to message, // Add this for the preview
                    "isRead_${receiverId}" to false, // Mark as unread for the receiver
                    "isRead_${senderId}" to true      // Mark as read for the sender
                )
                chatDocRef.set(chatUpdates, SetOptions.merge())
                    .addOnSuccessListener { Log.d("DirectMessageActivity", "Chat document metadata updated.") }
                    .addOnFailureListener { e -> Log.e("DirectMessageActivity", "Error updating chat metadata", e) }
            }

            // Add message to subcollection regardless of whether chat document was new or existing
            chatDocRef.collection("messages")
                .add(msg)
                .addOnSuccessListener { Log.d("DirectMessageActivity", "Message sent.") }
                .addOnFailureListener { e -> Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show() }
        }.addOnFailureListener { e ->
            Log.e("DirectMessageActivity", "Error checking chat document existence", e)
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

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }
}
