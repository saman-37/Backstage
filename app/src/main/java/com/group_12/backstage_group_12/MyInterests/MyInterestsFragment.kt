package com.group_12.backstage_group_12.MyInterests


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.group_12.backstage_group_12.R
import com.google.firebase.firestore.FirebaseFirestore

class MyInterestsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MyInterestsAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val eventList = mutableListOf<Event>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_my_interests, container, false)
        recyclerView = view.findViewById(R.id.recyclerViewInterests)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = MyInterestsAdapter(eventList)
        recyclerView.adapter = adapter
        loadInterests()
        return view
    }

    private fun loadInterests() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users")
            .document(userId)
            .collection("interests")
            .get()
            .addOnSuccessListener { result ->
                eventList.clear()
                for (doc in result) {
                    val event = doc.toObject(Event::class.java)
                    eventList.add(event)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
            }
    }
}