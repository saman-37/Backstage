package com.group_12.backstage.Explore

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContentProviderCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.group_12.backstage.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExploreFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EventsAdapter
    private val events = mutableListOf<Event>()
    private lateinit var btnFilterDate: Button
    private lateinit var genreChipGroup: ChipGroup

    private var startDate: String? = null
    private var endDate: String? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var searchJob: Runnable? = null





    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_explore, container, false)

        recyclerView = view.findViewById(R.id.recyclerEvents)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = EventsAdapter(events,
            onInterestedClick = { event -> markEventStatus(event, "interested") },
            onGoingClick = { event -> markEventStatus(event, "going") }
        )
        recyclerView.adapter = adapter



        val searchView = view.findViewById<SearchView>(R.id.searchBar)
        // Listen for search queries
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                fetchEvents(query)  // fetch events matching the query
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText ?: ""

                // Cancel previous jobs
                searchJob?.let { handler.removeCallbacks(it) }

                if (query.isEmpty()) {
                    events.clear()
                    adapter.updateEvents(events.toMutableList())
                    fetchEvents()
                }

                // Debounce: run 350ms after user stops typing
                searchJob = Runnable {
                    fetchEvents(
                        query = query,
                        genre = getSelectedGenre(),
                        startDate = startDate,
                        endDate = endDate
                    )
                }

                handler.postDelayed(searchJob!!, 350)

                return true
            }


        })

        genreChipGroup = view.findViewById<ChipGroup>(R.id.genreChipGroup)
        genreChipGroup.setOnCheckedChangeListener { group, checkedId ->
            val genreFilter = when (checkedId) {
                R.id.chipPop -> "Pop"
                R.id.chipRock -> "Rock"
                R.id.chipHipHop -> "Hip-Hop"
                R.id.chipCountry -> "Country"
                R.id.chipJazz -> "Jazz"
                R.id.chipElectronic -> "Electronic"
                else -> null  // "All" selected or no filter
            }
            // Fetch events using current search query and selected genre
            val currentQuery = searchView.query.toString()
            fetchEvents(currentQuery, genreFilter)
        }



        btnFilterDate = view.findViewById(R.id.btnFilterDate)
        btnFilterDate.setOnClickListener()
        {
            val dateButton = view.findViewById<Button>(R.id.btnFilterDate)
            dateButton.setOnClickListener {
                // Build a date range picker dialog
                val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker().setTitleText("Select Date Range").build()
                dateRangePicker.show(parentFragmentManager, "datePicker")
                dateRangePicker.addOnPositiveButtonClickListener { selection ->
                    // selection is a Pair<Long, Long> for start and end time in UTC milliseconds
                    val startMillis = selection.first
                    val endMillis = selection.second ?: selection.first  // in case only one date selected
                    // Format to YYYY-MM-DD (the format Ticketmaster API expects)
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    startDate = dateFormat.format(Date(startMillis))
                    endDate = dateFormat.format(Date(endMillis))
                    // Call fetchEvents with the selected date range
                    val currentQuery = searchView.query.toString()
                    fetchEvents(currentQuery, getSelectedGenre(), startDate, endDate)
                }
            }

        }
        fetchEvents() // loads concerts by default when Explore opens



        return view
    }
    private fun getSelectedGenre(): String? {
        val checkedChipId = genreChipGroup.checkedChipId
        return if (checkedChipId != View.NO_ID) {
            val selectedChip = genreChipGroup.findViewById<Chip>(checkedChipId)
            selectedChip.text.toString()
        } else {
            null
        }
    }
    private fun fetchEvents(
        query: String = "",
        genre: String? = null,
        startDate: String? = null,
        endDate: String? = null
    ) {
        val apiKey = "A9nGiKzxYRZTGQfiUG0lK0JlNkZJ8FXx"
        val baseUrl = "https://app.ticketmaster.com/discovery/v2/events.json"

        val encodedQuery = Uri.encode(query)

        // Base
        var url = "$baseUrl?apikey=$apiKey&countryCode=CA&segmentName=Music"

        // If user typed something → search
        if (query.isNotEmpty()) {
            url += "&keyword=${Uri.encode(query)}"
        } else {
            // User cleared search → show popular/default events
            url += "&size=50"
        }



        // Genre filter
        if (genre != null) {
            url += "&classificationName=${Uri.encode(genre)}"
        }

        // Date filter
        if (startDate != null) {
            val end = endDate ?: startDate
            url += "&startDateTime=${startDate}T00:00:00Z&endDateTime=${end}T23:59:59Z"
        }

        Log.d("API_URL", url)


        val request = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                try {
                events.clear()

                val embedded = response.optJSONObject("_embedded") ?: return@JsonObjectRequest
                val eventsArray = embedded.optJSONArray("events") ?: return@JsonObjectRequest

                for (i in 0 until eventsArray.length()) {
                    val e = eventsArray.getJSONObject(i)
                    val id = e.optString("id")
                    val name = e.optString("name", "Untitled")

                    val images = e.optJSONArray("images")
                    val imageUrl = images?.getJSONObject(0)?.optString("url", "") ?: ""

                    val venue = e.optJSONObject("_embedded")
                        ?.optJSONArray("venues")
                        ?.getJSONObject(0)
                        ?.optString("name", "Unknown Venue")
                        ?: "Unknown Venue"

                    // date parsing
                    var formattedDate = ""
                    e.optJSONObject("dates")?.optJSONObject("start")?.let { startObj ->
                        val localDate = startObj.optString("localDate")
                        if (localDate.isNotEmpty()) {
                            try {
                                val inputFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val outputFmt = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                                formattedDate = outputFmt.format(inputFmt.parse(localDate)!!)
                            } catch (ex: Exception) {
                                formattedDate = localDate
                            }
                        }
                    }

                    // genre parsing
                    var genre = ""
                    val classifications = e.optJSONArray("classifications")
                    if (classifications != null && classifications.length() > 0) {
                        genre = classifications.getJSONObject(0)
                            .optJSONObject("genre")
                            ?.optString("name", "") ?: ""
                    }

                    events.add(Event(id, name, formattedDate, venue, imageUrl, genre))
                }

                // update adapter
                adapter.updateEvents(events.toMutableList())


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
    private fun markEventStatus(event: Event, status: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Please log in to save events", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = user.uid

        val firestore = FirebaseFirestore.getInstance()

        val eventData = mapOf(
            "id" to event.id,
            "title" to event.name,            // MyInterests expects "title"
            "date" to event.date,
            "location" to event.venue,         // MyInterests expects "location"
            "imageUrl" to event.imageUrl,      // MyInterests expects "imageUrl"
            "genre" to event.genre,
            "status" to status,
            "ticketUrl" to ""                  // optional for now
        )

        firestore.collection("users")
            .document(uid)
            .collection("my_events")
            .document(event.id)
            .set(eventData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Marked as $status", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("Firebase", "Error writing event", e)
            }
    }



}



