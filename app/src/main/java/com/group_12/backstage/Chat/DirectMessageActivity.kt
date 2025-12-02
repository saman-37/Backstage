package com.group_12.backstage.Chat

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.group_12.backstage.R

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
                val currentUser = auth.currentUser
                val senderName = currentUser?.displayName ?: currentUser?.email ?: "Anonymous"
                sendMessage(senderName, text)
                messageEditText.text.clear()
            }
        }
    }

    private fun sendMessage(sender: String, message: String) {
        if (chatId == null) return
        val timestamp = System.currentTimeMillis()

        val msg = hashMapOf(
            "senderId" to sender,
            "text" to message,
            "timestamp" to timestamp
        )

        val chatDocRef = db.collection("chats").document(chatId!!)

        chatDocRef.collection("messages")
            .add(msg)
            .addOnSuccessListener {
                // Update the last message timestamp for the chat
                chatDocRef.set(mapOf("lastMessageTimestamp" to timestamp), com.google.firebase.firestore.SetOptions.merge())
            }
            .addOnFailureListener { Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenForMessages() {
        if (chatId == null) return
        val currentUserEmail = auth.currentUser?.email ?: ""

        listener = db.collection("chats")
            .document(chatId!!)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                if (snapshots != null) {
                    messages.clear()
                    for (doc in snapshots.documents) {
                        val sender = doc.getString("senderId") ?: ""
                        val text = doc.getString("text") ?: ""
                        val isSent = sender == currentUserEmail
                        val timestamp = doc.getLong("timestamp") ?: 0L
                        messages.add(Message(sender, text, timestamp,isSentByCurrentUser = isSent))
                    }
                    adapter.notifyDataSetChanged()
                    if (messages.isEmpty()) {
                        emptyChatTextView.isVisible = true
                        recyclerView.isVisible = false
                    } else {
                        emptyChatTextView.isVisible = false
                        recyclerView.isVisible = true
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }
}
