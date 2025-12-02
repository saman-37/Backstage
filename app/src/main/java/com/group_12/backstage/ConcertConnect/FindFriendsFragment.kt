package com.group_12.backstage.ConcertConnect

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.group_12.backstage.R
import com.group_12.backstage.UserAccountData.User
import com.group_12.backstage.databinding.FragmentFindFriendsBinding
import com.group_12.backstage.notifications.NotificationHelper

class FindFriendsFragment : Fragment() {

    private var _binding: FragmentFindFriendsBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var requestsAdapter: FindFriendsAdapter
    private lateinit var searchAdapter: FindFriendsAdapter
    private lateinit var friendsAdapter: FriendsListAdapter

    // Local cache of friend statuses: uid -> status ("sent", "received", "friend")
    private val friendStatuses = mutableMapOf<String, String>()

    // Store all friend requests
    private var allFriendRequests = listOf<User>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFindFriendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupSearch()
        
        listenToFriendStatuses()
    }

    private fun setupAdapters() {
        // Adapter for Friend Requests List
        requestsAdapter = FindFriendsAdapter(
            onAddClick = { /* Should not happen */ },
            onDeclineClick = { user -> declineRequest(user) },
            onAcceptClick = { user -> acceptRequest(user) }
        )
        binding.requestsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.requestsRecyclerView.adapter = requestsAdapter

        // Adapter for Friends List
        friendsAdapter = FriendsListAdapter { friend ->
            val intent = Intent(requireContext(), FriendProfileActivity::class.java)
            intent.putExtra("FRIEND_ID", friend.uid)
            intent.putExtra("FRIEND_NAME", friend.name)
            startActivity(intent)
        }
        binding.friendsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.friendsRecyclerView.adapter = friendsAdapter

        // Adapter for Search Results
        searchAdapter = FindFriendsAdapter(
            onAddClick = { user -> sendFriendRequest(user) },
            onDeclineClick = { user -> declineRequest(user) },
            onAcceptClick = { user -> acceptRequest(user) }
        )
        binding.searchResultsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.searchResultsRecyclerView.adapter = searchAdapter

        // "See all" button click listener
        binding.seeAllRequestsButton.setOnClickListener {
            navigateToAllRequests()
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    binding.mainContentContainer.isVisible = false
                    binding.searchResultsRecyclerView.isVisible = true
                    searchUsers(query)
                } else {
                    binding.mainContentContainer.isVisible = true
                    binding.searchResultsRecyclerView.isVisible = false
                    searchAdapter.submitList(emptyList(), emptyMap())
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun listenToFriendStatuses() {
        val myUid = auth.currentUser?.uid ?: return

        db.collection("users").document(myUid).collection("friends")
            .addSnapshotListener { snapshots, e ->
                if (_binding == null) return@addSnapshotListener
                
                if (e != null || snapshots == null) {
                    Log.w("FindFriends", "Listen failed.", e)
                    return@addSnapshotListener
                }

                friendStatuses.clear()
                val requestUids = mutableListOf<String>()
                val friendUids = mutableListOf<String>()

                for (doc in snapshots) {
                    val status = doc.getString("status")
                    when (status) {
                        "received" -> {
                            friendStatuses[doc.id] = status
                            requestUids.add(doc.id)
                        }
                        "friend" -> {
                            friendStatuses[doc.id] = status
                            friendUids.add(doc.id)
                        }
                        "sent" -> {
                             friendStatuses[doc.id] = status
                        }
                    }
                }

                fetchRequestProfiles(requestUids)
                fetchFriendProfiles(friendUids)
                
                // Refresh search results if visible
                if (::searchAdapter.isInitialized) {
                    searchAdapter.notifyDataSetChanged()
                }
            }
    }

    private fun fetchRequestProfiles(uids: List<String>) {
        if (_binding == null) return
        if (uids.isEmpty()) {
            binding.emptyRequestsText.isVisible = true
            binding.seeAllRequestsButton.isVisible = false
            allFriendRequests = emptyList()
            requestsAdapter.submitList(emptyList(), emptyMap())
            return
        }

        // Limit to 10 to match Firestore whereIn limit
        db.collection("users").whereIn("uid", uids.take(10)).get()
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                binding.emptyRequestsText.isVisible = false
                val users = it.toObjects(User::class.java).filter { user -> user.uid.isNotEmpty() }
                allFriendRequests = users

                // Show max 3 requests
                val displayedUsers = users.take(3)
                requestsAdapter.submitList(displayedUsers, friendStatuses)

                // Show "See all" button if there are more than 3 requests
                binding.seeAllRequestsButton.isVisible = users.size > 3
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                binding.emptyRequestsText.isVisible = true
                binding.seeAllRequestsButton.isVisible = false
            }
    }

    private fun fetchFriendProfiles(uids: List<String>) {
        if (_binding == null) return
        if (uids.isEmpty()) {
            binding.emptyFriendsText.isVisible = true
            friendsAdapter.submitList(emptyList())
            return
        }

        // Firestore 'whereIn' supports max 10 values. 
        // Using take(10) prevents crash. Pagination or batching needed for > 10.
        db.collection("users").whereIn("uid", uids.take(10)).get()
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                binding.emptyFriendsText.isVisible = false
                val users = it.toObjects(User::class.java).filter { user -> user.uid.isNotEmpty() }
                friendsAdapter.submitList(users)
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                binding.emptyFriendsText.isVisible = true
            }
    }

    private fun searchUsers(query: String) {
        binding.progressBar.isVisible = true
        
        db.collection("users")
            .orderBy("name")
            .startAt(query)
            .endAt(query + "\uf8ff")
            .limit(20)
            .get()
            .addOnSuccessListener { documents ->
                if (_binding == null) return@addOnSuccessListener
                binding.progressBar.isVisible = false
                val myUid = auth.currentUser?.uid
                val users = documents.toObjects(User::class.java).filter { it.uid.isNotEmpty() && it.uid != myUid }
                searchAdapter.submitList(users, friendStatuses)
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                binding.progressBar.isVisible = false
            }
    }

    private fun sendFriendRequest(user: User) {
        val myUid = auth.currentUser?.uid ?: return
        if (user.uid.isEmpty()) return

        db.collection("users").document(myUid).collection("friends").document(user.uid)
            .set(mapOf("status" to "sent"))

        db.collection("users").document(user.uid).collection("friends").document(myUid)
            .set(mapOf("status" to "received"))
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(context, "Request sent to ${user.name}", Toast.LENGTH_SHORT).show()

                // Send push notification
                val currentUser = auth.currentUser
                val senderName = currentUser?.displayName ?: currentUser?.email?.substringBefore("@") ?: "Someone"
                NotificationHelper.sendFriendRequestNotification(user.uid, senderName, myUid)
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
            }
    }

    private fun declineRequest(user: User) {
        val myUid = auth.currentUser?.uid ?: return
        if (user.uid.isEmpty()) return

        db.collection("users").document(myUid).collection("friends").document(user.uid).delete()
        db.collection("users").document(user.uid).collection("friends").document(myUid).delete()
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(context, "Request declined", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToAllRequests() {
        val requestUids = allFriendRequests.map { it.uid }.toTypedArray()
        val bundle = Bundle().apply {
            putStringArray("REQUEST_UIDS", requestUids)
        }
        findNavController().navigate(R.id.action_friends_to_allRequests, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
