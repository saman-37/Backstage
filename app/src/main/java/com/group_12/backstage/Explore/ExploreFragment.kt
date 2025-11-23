package com.group_12.backstage.Explore

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.group_12.backstage.R

class ExploreFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EventsAdapter
    private val events = mutableListOf<Event>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_explore, container, false)

        recyclerView = view.findViewById(R.id.recyclerEvents)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = EventsAdapter(events)
        recyclerView.adapter = adapter

        fetchEvents() // loads concerts by default when Explore opens



        return view
    }

    private fun fetchEvents(query: String = "") {
        val apiKey = "A9nGiKzxYRZTGQfiUG0lK0JlNkZJ8FXx"
        val baseUrl = "https://app.ticketmaster.com/discovery/v2/events.json"

        // "segmentName=Music" restricts results to concerts/music events
        val encodedQuery = Uri.encode(query)
        val url = "$baseUrl?apikey=$apiKey&countryCode=CA&segmentName=Music&keyword=$encodedQuery"

        val request = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                events.clear()
                try {
                    val embedded = response.optJSONObject("_embedded") ?: return@JsonObjectRequest
                    val eventsArray = embedded.optJSONArray("events") ?: return@JsonObjectRequest

                    for (i in 0 until eventsArray.length()) {
                        val e = eventsArray.getJSONObject(i)
                        val name = e.optString("name", "Untitled")
                        val images = e.optJSONArray("images")
                        val image = images?.getJSONObject(0)?.optString("url", "")
                        val venues = e.optJSONObject("_embedded")?.optJSONArray("venues")
                        val venue = venues?.getJSONObject(0)?.optString("name", "Unknown Venue") ?: "Unknown Venue"
                        events.add(Event(name, image ?: "", venue))
                    }
                    adapter.notifyDataSetChanged()
                } catch (ex: Exception) {
                    Log.e("API_PARSE", "Error parsing events: ${ex.message}")
                }
            },
            { error ->
                Log.e("API_ERROR", "Error fetching concerts: $error")
            }
        )

        Volley.newRequestQueue(requireContext()).add(request)
    }

}

private fun ImageButton.setOnClickListener(function: () -> Unit) {
    TODO("Not yet implemented")
}
