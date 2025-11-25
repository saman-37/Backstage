package com.group_12.backstage.Chat

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_direct_message)

        targetUserId = intent.getStringExtra("targetUserId")
        targetUserName = intent.getStringExtra("targetUserName")

        supportActionBar?.title = targetUserName ?: "Chat"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.messagesRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        emptyChatTextView = findViewById(R.id.emptyChatTextView)


        setupRecyclerView()
        setupChatId()
        setupSendButton()
        listenForMessages()
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(messages) {}
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
                val senderName = currentUser?.email ?: "Anonymous"
                sendMessage(senderName, text)
                messageEditText.text.clear()
            }
        }
    }

    private fun sendMessage(sender: String, message: String) {
        if (chatId == null) return

        val msg = hashMapOf(
            "senderId" to sender,
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

    private fun listenForMessages() {
        if (chatId == null) return

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
                        messages.add(Message(sender, text))
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }
}
