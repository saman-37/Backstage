package com.group_12.backstage.MyInterests

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.group_12.backstage.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MyInterestsAdapter
    private lateinit var emptyView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val historyList = mutableListOf<Event>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)

        recyclerView = view.findViewById(R.id.recyclerHistory)
        emptyView = view.findViewById(R.id.emptyHistoryView)
        progressBar = view.findViewById(R.id.progressBar)
        btnBack = view.findViewById(R.id.btnBack)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = MyInterestsAdapter(
            historyList,
            onItemClick = { event, imageView ->
                navigateToEventDetails(event, imageView)
            },
            onStatusChange = { event, newStatus ->
                updateEventStatus(event, newStatus)
            }
        )
        recyclerView.adapter = adapter

        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        fetchHistoryEvents()

        return view
    }

    private fun fetchHistoryEvents() {
        val currentUser = auth.currentUser ?: return
        progressBar.visibility = View.VISIBLE

        db.collection("users")
            .document(currentUser.uid)
            .collection("my_events")
            .get()
            .addOnSuccessListener { result ->
                progressBar.visibility = View.GONE
                historyList.clear()
                
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                val today = Date()

                for (document in result) {
                    try {
                        val event = document.toObject(Event::class.java).copy(id = document.id)
                        
                        // Filter for Past Events
                        if (event.date.isNotEmpty()) {
                            val eventDate = dateFormat.parse(event.date)
                            if (eventDate != null && eventDate.before(today)) {
                                historyList.add(event)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("HistoryFragment", "Error parsing event", e)
                    }
                }

                adapter.notifyDataSetChanged()
                emptyView.visibility = if (historyList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { exception ->
                progressBar.visibility = View.GONE
                Log.e("HistoryFragment", "Error getting documents.", exception)
                Toast.makeText(context, "Failed to load history", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateEventStatus(event: Event, newStatus: String) {
        // Allow updating status even in history (e.g. if they changed their mind about 'going' to a past event)
        val currentUser = auth.currentUser ?: return
        db.collection("users")
            .document(currentUser.uid)
            .collection("my_events")
            .document(event.id)
            .update("status", newStatus)
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to update status", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToEventDetails(event: Event, imageView: ImageView) {
        val transitionName = "event_image_${event.id}"
        val extras = FragmentNavigatorExtras(imageView to transitionName)

        val bundle = bundleOf(
            "eventId" to event.id,
            "title" to event.title,
            "date" to event.date,
            "location" to event.location,
            "imageUrl" to event.imageUrl,
            "ticketUrl" to event.ticketUrl
        )

        findNavController().navigate(
            R.id.action_historyFragment_to_eventDetailsFragment, // Need to add this action or use global action
            bundle,
            null,
            extras
        )
    }
}
