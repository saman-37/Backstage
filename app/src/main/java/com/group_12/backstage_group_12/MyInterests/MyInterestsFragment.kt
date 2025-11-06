package com.group_12.backstage_group_12.MyInterests


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.group_12.backstage_group_12.R
import com.google.firebase.firestore.FirebaseFirestore

class MyInterestsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MyInterestsAdapter
    private lateinit var emptyTextView: TextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val eventList = mutableListOf<Event>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_my_interests, container, false)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.myInterestsToolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        recyclerView = view.findViewById(R.id.recyclerViewInterests)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = MyInterestsAdapter(eventList)
        recyclerView.adapter = adapter

        emptyTextView = view.findViewById(R.id.emptyTextView)

        loadInterests()
        return view
    }

    private fun loadInterests() {
//        val userId = auth.currentUser?.uid ?: return
//        db.collection("users")
//            .document(userId)
//            .collection("interests")
//            .get()
//            .addOnSuccessListener { result ->
//                eventList.clear()
//                for (doc in result) {
//                    val event = doc.toObject(Event::class.java)
//                    eventList.add(event)
//                }
//                adapter.notifyDataSetChanged()
//
//                emptyTextView.visibility =
//                    if (eventList.isEmpty()) View.VISIBLE else View.GONE
//            }
//            .addOnFailureListener {
//                emptyTextView.text = "Failed to load data."
//                emptyTextView.visibility = View.VISIBLE
//            }

        eventList.clear()
        eventList.add(Event("Coldplay Concert", "BC Place Stadium", "2025-07-10"))
        eventList.add(Event("Drake Tour", "Rogers Arena", "2025-08-15"))
        eventList.add(Event("Taylor Swift Eras Tour", "BC Place", "2025-09-01"))
        eventList.add(Event("Jazz Festival", "Granville Island", "2025-06-20"))

        adapter.notifyDataSetChanged()
        emptyTextView.visibility = View.GONE

    }
}