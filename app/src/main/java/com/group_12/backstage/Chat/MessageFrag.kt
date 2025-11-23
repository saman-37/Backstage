// MessagesFrag.kt
package com.group_12.backstage.Chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.group_12.backstage.R

class MessagesFrag : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_messages, container, false)

        recyclerView = view.findViewById(R.id.messagesRecyclerView)
        setupRecyclerView()

        return view
    }

    private fun setupRecyclerView() {
        val messages = getSampleMessages()
        adapter = MessageAdapter(messages) { message ->
            // Handle message click
            onMessageClicked(message)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun onMessageClicked(message: Message) {
        // Navigate to chat detail screen
        // Example: findNavController().navigate(...)
    }

    private fun getSampleMessages(): List<Message> {
        return listOf(
            Message("Anna", "Yay!", "09:31"),
            Message("Lucy", "Cool!  I'll take them with...", "06:55"),
            Message("John", "I'll call her later! ", "Wed"),
            Message("Maya", "How about Friday next week?", "Wed"),
            Message("Sean", "It was awesome!!!!!", "Mon")
        )
    }
}