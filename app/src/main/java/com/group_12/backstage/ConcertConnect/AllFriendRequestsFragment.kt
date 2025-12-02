package com.group_12.backstage.ConcertConnect

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.group_12.backstage.UserAccountData.User
import com.group_12.backstage.databinding.FragmentAllFriendRequestsBinding
import com.group_12.backstage.notifications.NotificationHelper

class AllFriendRequestsFragment : Fragment() {

    private var _binding: FragmentAllFriendRequestsBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var requestsAdapter: FindFriendsAdapter
    private val friendStatuses = mutableMapOf<String, String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllFriendRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupAdapter()

        val requestUids = arguments?.getStringArray("REQUEST_UIDS")?.toList() ?: emptyList()
        fetchRequestProfiles(requestUids)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupAdapter() {
        requestsAdapter = FindFriendsAdapter(
            onAddClick = { /* Should not happen */ },
            onDeclineClick = { user -> declineRequest(user) },
            onAcceptClick = { user -> acceptRequest(user) }
        )
        binding.requestsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.requestsRecyclerView.adapter = requestsAdapter
    }

    private fun fetchRequestProfiles(uids: List<String>) {
        if (_binding == null) return
        if (uids.isEmpty()) {
            binding.emptyRequestsText.isVisible = true
            binding.progressBar.isVisible = false
            return
        }

        binding.progressBar.isVisible = true

        // Mark all as "received" status
        uids.forEach { friendStatuses[it] = "received" }

        // Limit to 10 to match Firestore whereIn limit
        db.collection("users").whereIn("uid", uids.take(10)).get()
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                binding.progressBar.isVisible = false
                val users = it.toObjects(User::class.java).filter { user -> user.uid.isNotEmpty() }

                if (users.isEmpty()) {
                    binding.emptyRequestsText.isVisible = true
                } else {
                    binding.emptyRequestsText.isVisible = false
                    requestsAdapter.submitList(users, friendStatuses)
                }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                binding.progressBar.isVisible = false
                binding.emptyRequestsText.isVisible = true
                Log.e("AllFriendRequests", "Failed to fetch requests", it)
            }
    }

    private fun acceptRequest(user: User) {
        val myUid = auth.currentUser?.uid ?: return
        if (user.uid.isEmpty()) return

        db.collection("users").document(myUid).collection("friends").document(user.uid)
            .update("status", "friend")
        db.collection("users").document(user.uid).collection("friends").document(myUid)
            .update("status", "friend")
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(context, "You are now friends with ${user.name}", Toast.LENGTH_SHORT).show()

                // Send push notification
                val currentUser = auth.currentUser
                val accepterName = currentUser?.displayName ?: currentUser?.email?.substringBefore("@") ?: "Someone"
                NotificationHelper.sendFriendRequestAcceptedNotification(user.uid, accepterName, myUid)

                // Navigate back to refresh the main friends list
                findNavController().navigateUp()
            }
    }

    private fun declineRequest(user: User) {
        val myUid = auth.currentUser?.uid ?: return
        if (user.uid.isEmpty()) return

        db.collection("users").document(myUid).collection("friends").document(user.uid).delete()
        db.collection("users").document(user.uid).collection("friends").document(myUid).delete()
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(context, "Request declined", Toast.LENGTH_SHORT).show()
                // Remove from local list
                val currentList = (requestsAdapter as? FindFriendsAdapter)?.let {
                    // Refresh by removing the declined user
                    val uids = arguments?.getStringArray("REQUEST_UIDS")?.toList()?.filter { it != user.uid } ?: emptyList()
                    fetchRequestProfiles(uids)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
