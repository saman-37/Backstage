package com.group_12.backstage.Chat

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.group_12.backstage.R
import com.group_12.backstage.UserAccountData.User

class ChatFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var adapter: UserAdapter
    private var userList = mutableListOf<User>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val allUsers = mutableListOf<User>()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)
        recyclerView = view.findViewById(R.id.usersRecyclerView)
        searchEditText = view.findViewById(R.id.searchEditText)

        setupRecyclerView()
        setupSearch()

        return view
    }

    private fun setupRecyclerView() {
        adapter = UserAdapter(userList) { user ->
            val intent = Intent(requireContext(), DirectMessageActivity::class.java)
            intent.putExtra("targetUserId", user.uid)
            intent.putExtra("targetUserName", user.name)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun fetchUsersAndListenForUpdates() {
        val currentUserId = auth.currentUser?.uid ?: return
        listeners.forEach { it.remove() }
        listeners.clear()

        db.collection("users").get().addOnSuccessListener { userDocuments ->
            allUsers.clear()
            for (doc in userDocuments) {
                if (doc.id != currentUserId) {
                    allUsers.add(doc.toObject(User::class.java))
                }
            }

            allUsers.forEach { user ->
                val userIds = listOf(currentUserId, user.uid).sorted()
                val chatId = "chat_${userIds[0]}_${userIds[1]}"

                val listener = db.collection("chats").document(chatId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.w("ChatFragment", "Listen failed.", e)
                            return@addSnapshotListener
                        }

                        if (snapshot != null && snapshot.exists()) {
                            val timestamp = snapshot.getLong("lastMessageTimestamp") ?: 0L
                            val userToUpdate = allUsers.find { it.uid == user.uid }
                            userToUpdate?.lastMessageTimestamp = timestamp
                        }

                        // Sort and update the list
                        val sortedUsers = allUsers.sortedByDescending { it.lastMessageTimestamp }
                        userList.clear()
                        userList.addAll(sortedUsers)
                        filter(searchEditText.text.toString())
                    }
                listeners.add(listener)
            }
        }
    }


    override fun onResume() {
        super.onResume()
        fetchUsersAndListenForUpdates()
    }

    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filter(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun filter(text: String) {
        val filteredList = if (text.isBlank()) {
            userList
        } else {
            userList.filter {
                it.name.contains(text, true)
            }
        }
        adapter.updateList(filteredList)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        // Remove all Firestore listeners
        listeners.forEach { it.remove() }
        listeners.clear()
    }
}

