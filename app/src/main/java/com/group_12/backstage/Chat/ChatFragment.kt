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
import com.google.firebase.firestore.FirebaseFirestore
import com.group_12.backstage.R

class ChatFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var adapter: UserAdapter
    private var userList = mutableListOf<User>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)
        recyclerView = view.findViewById(R.id.usersRecyclerView)
        searchEditText = view.findViewById(R.id.searchEditText)

        setupRecyclerView()
        fetchUsers()
        setupSearch()

        return view
    }


    private fun setupRecyclerView() {
        adapter = UserAdapter(userList) { user ->
            // Open DirectMessageActivity with the selected user
            val intent = Intent(requireContext(), DirectMessageActivity::class.java)
            intent.putExtra("targetUserId", user.uid)
            intent.putExtra("targetUserName", user.name)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun fetchUsers() {
        db.collection("users").get()
            .addOnSuccessListener { documents ->
                val newList = mutableListOf<User>()
                val currentUserId = auth.currentUser?.uid

                for (doc in documents) {
                    val user = doc.toObject(User::class.java)
                    if (user.uid != currentUserId) newList.add(user)
                }

                userList = newList
                adapter.updateList(userList)
            }
    }



    override fun onResume() {
        super.onResume()
        fetchUsers()  // Reload user list every time you come back to this tab
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
        val filteredList = mutableListOf<User>()
        for (user in userList) {
            if (user.name.contains(text, true) || user.username.contains(text, true)) {
                filteredList.add(user)
            }
        }
        adapter.updateList(filteredList)
    }
}
