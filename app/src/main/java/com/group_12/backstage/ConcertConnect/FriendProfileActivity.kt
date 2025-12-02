package com.group_12.backstage.ConcertConnect

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.group_12.backstage.MyInterests.Event
import com.group_12.backstage.MyInterests.MyInterestsAdapter
import com.group_12.backstage.databinding.ActivityFriendProfileBinding

class FriendProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendProfileBinding
    private lateinit var adapter: MyInterestsAdapter
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val friendId = intent.getStringExtra("FRIEND_ID")
        val friendName = intent.getStringExtra("FRIEND_NAME")

        binding.tvProfileName.text = friendName ?: "Friend's Profile"
        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()

        if (friendId != null) {
            fetchFriendEvents(friendId)
        } else {
            binding.progressBar.visibility = View.GONE
            binding.tvNoEvents.visibility = View.VISIBLE
            binding.tvNoEvents.text = "Could not load friend details."
        }
    }

    private fun setupRecyclerView() {
        adapter = MyInterestsAdapter(mutableListOf(), { _, _ -> }, { _, _ -> })
        binding.recyclerViewEvents.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewEvents.adapter = adapter
    }

    private fun fetchFriendEvents(friendId: String) {
        binding.progressBar.visibility = View.VISIBLE

        db.collection("users").document(friendId).collection("my_events")
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE
                val events = documents.toObjects(Event::class.java)
                if (events.isNotEmpty()) {
                    adapter.updateEvents(events)
                } else {
                    binding.tvNoEvents.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                binding.tvNoEvents.visibility = View.VISIBLE
                binding.tvNoEvents.text = "Failed to load events."
            }
    }
}
