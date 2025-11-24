package com.group_12.backstage.MyInterests

import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.group_12.backstage.R

class MyInterestsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MyInterestsAdapter
    private lateinit var emptyTextView: TextView
    private lateinit var searchView: SearchView
    private lateinit var chipGroup: ChipGroup
    private lateinit var progressBar: ProgressBar

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val eventList = mutableListOf<Event>()        // all events
    private val filteredList = mutableListOf<Event>()     // filtered events

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_my_interests, container, false)

        recyclerView = view.findViewById(R.id.recyclerViewInterests)
        searchView = view.findViewById(R.id.searchBar)
        emptyTextView = view.findViewById(R.id.emptyTextView)
        chipGroup = view.findViewById(R.id.filterChipGroup)
        progressBar = view.findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        // Pass a lambda for item clicks
        adapter = MyInterestsAdapter(filteredList) { event, imageView ->
             navigateToEventDetails(event, imageView)
        }
        recyclerView.adapter = adapter

        setupSearch()
        setupFilterChips()
        setupSwipeToDelete()
        listenForUserEvents()

        return view
    }

    private fun navigateToEventDetails(event: Event, imageView: ImageView) {
        // Create unique transition name
        val transitionName = "event_image_${event.id}"
        
        val extras = FragmentNavigatorExtras(
            imageView to transitionName
        )

        val bundle = bundleOf(
            "eventId" to event.id,
            "title" to event.title,
            "date" to event.date,
            "location" to event.location,
            "imageUrl" to event.imageUrl,
            "ticketUrl" to event.ticketUrl
        )

        findNavController().navigate(
            R.id.action_myInterests_to_eventDetails,
            bundle,
            null,
            extras
        )
    }

    private fun setupFilterChips() {
        chipGroup.setOnCheckedChangeListener { group, checkedId ->
            val query = searchView.query.toString().trim().lowercase()
            filterList(query)
        }
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val eventToDelete = filteredList[position]
                deleteEvent(eventToDelete, position)
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if (viewHolder != null) {
                    val foregroundView = viewHolder.itemView.findViewById<View>(R.id.view_foreground)
                    getDefaultUIUtil().onSelected(foregroundView)
                }
            }

            override fun onChildDrawOver(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder?,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (viewHolder != null) {
                    val foregroundView = viewHolder.itemView.findViewById<View>(R.id.view_foreground)
                    getDefaultUIUtil().onDrawOver(c, recyclerView, foregroundView, dX, dY, actionState, isCurrentlyActive)
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                val foregroundView = viewHolder.itemView.findViewById<View>(R.id.view_foreground)
                getDefaultUIUtil().clearView(foregroundView)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val foregroundView = viewHolder.itemView.findViewById<View>(R.id.view_foreground)
                getDefaultUIUtil().onDraw(c, recyclerView, foregroundView, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun deleteEvent(event: Event, position: Int) {
        // 1. Remove from lists locally to reflect swipe immediately
        filteredList.removeAt(position)
        eventList.remove(event)
        adapter.notifyItemRemoved(position)
        
        // Check if list is empty to show empty view
        if (filteredList.isEmpty()) {
            emptyTextView.visibility = View.VISIBLE
        }

        // 2. Show Snackbar with Undo
        Snackbar.make(recyclerView, "${event.title} removed", Snackbar.LENGTH_LONG)
            .setAction("UNDO") {
                // Undo Action: Restore item
                filteredList.add(position, event)
                eventList.add(event)
                adapter.notifyItemInserted(position)
                emptyTextView.visibility = View.GONE
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, eventId: Int) {
                    // If not dismissed via UNDO action, proceed with actual deletion
                    if (eventId != DISMISS_EVENT_ACTION) {
                        permanentlyDeleteEvent(event)
                    }
                }
            })
            .show()
    }

    private fun permanentlyDeleteEvent(event: Event) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
             if (event.id.isNotEmpty()) {
                 db.collection("users")
                    .document(currentUser.uid)
                    .collection("my_events")
                    .document(event.id)
                    .delete()
                    .addOnFailureListener { e ->
                         Toast.makeText(context, "Failed to delete from server: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
             } else {
                 // Fallback to title
                  db.collection("users")
                    .document(currentUser.uid)
                    .collection("my_events")
                    .whereEqualTo("title", event.title)
                    .get()
                    .addOnSuccessListener { documents ->
                        for (document in documents) {
                            document.reference.delete()
                        }
                    }
             }
        }
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText?.trim()?.lowercase() ?: ""
                filterList(query)
                return true
            }
        })
    }

    private fun filterList(query: String) {
        val checkedChipId = chipGroup.checkedChipId
        val filterStatus = when (checkedChipId) {
            R.id.chipGoing -> "going"
            R.id.chipInterested -> "interested"
            else -> "all" // Default to all
        }

        filteredList.clear()
        
        val searchFiltered = if (query.isEmpty()) {
            eventList
        } else {
            eventList.filter {
                it.title.lowercase().contains(query) ||
                        it.location.lowercase().contains(query) ||
                        it.date.lowercase().contains(query)
            }
        }

        val finalFiltered = if (filterStatus == "all") {
            searchFiltered
        } else {
            searchFiltered.filter { it.status.equals(filterStatus, ignoreCase = true) }
        }

        filteredList.addAll(finalFiltered)
        adapter.notifyDataSetChanged()
        emptyTextView.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun listenForUserEvents() {
        progressBar.visibility = View.VISIBLE 
        
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d("MyInterests", "No user logged in. Loading dummy data.")
            loadDummyData()
            return
        }

        Log.d("MyInterests", "Fetching events for User UID: ${currentUser.uid}")
        Toast.makeText(context, "Checking DB for User: ${currentUser.uid.take(5)}...", Toast.LENGTH_SHORT).show()

        db.collection("users")
            .document(currentUser.uid)
            .collection("my_events")
            .addSnapshotListener { snapshots, e ->
                progressBar.visibility = View.GONE 
                
                if (e != null) {
                    Log.e("MyInterests", "Listen failed.", e)
                    Toast.makeText(context, "Error loading events: ${e.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    Log.d("MyInterests", "Snapshot received! Count: ${snapshots.size()}")
                    if (snapshots.isEmpty) {
                         Log.d("MyInterests", "Snapshot is empty.")
                         // Toast.makeText(context, "No events found in DB", Toast.LENGTH_SHORT).show()
                    }

                    eventList.clear()
                    for (doc in snapshots) {
                        try {
                            val event = doc.toObject(Event::class.java).copy(id = doc.id)
                            Log.d("MyInterests", "Loaded event: ${event.title}")
                            eventList.add(event)
                        } catch (e: Exception) {
                            Log.e("MyInterests", "Error parsing document: ${doc.id}", e)
                        }
                    }
                    
                    // Re-apply filter
                    val currentQuery = searchView.query.toString().trim().lowercase()
                    filterList(currentQuery)
                } else {
                    Log.d("MyInterests", "Current data: null")
                }
            }
    }

    private fun loadDummyData() {
        eventList.clear()
        eventList.add(Event(
            id = "1",
            title = "Taylor Swift | The Eras Tour",
            date = "Dec 06, 2024",
            location = "BC Place, Vancouver",
            imageUrl = "https://s1.ticketm.net/dam/a/263/e3ae5095-f7c0-4b21-9f23-106040627263_1830261_TABLET_LANDSCAPE_LARGE_16_9.jpg",
            status = "going",
            ticketUrl = "https://www.ticketmaster.ca/event/11005F6EC6B54372"
        ))
        eventList.add(Event(
            id = "2",
            title = "Coldplay: Music Of The Spheres",
            date = "Sep 22, 2025",
            location = "Rogers Arena",
            imageUrl = "",
            status = "interested",
            ticketUrl = "https://www.ticketmaster.com/search?q=Coldplay+Rogers+Arena"
        ))
        eventList.add(Event(
            id = "3",
            title = "Ed Sheeran: +-=÷x Tour",
            date = "Aug 15, 2024",
            location = "BC Place",
            imageUrl = "",
            status = "interested",
            ticketUrl = "https://www.ticketmaster.com/search?q=Ed+Sheeran+BC+Place"
        ))
        
        filterList(searchView.query.toString())
        progressBar.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        searchView.clearFocus()
    }
}