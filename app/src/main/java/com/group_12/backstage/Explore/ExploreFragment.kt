package com.group_12.backstage.Explore

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.group_12.backstage.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ExploreFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EventsAdapter
    private lateinit var tvNoResults: TextView
    private val events = mutableListOf<Event>()
    private lateinit var btnFilterDate: Button
    private lateinit var genreChipGroup: ChipGroup
    private lateinit var sortChipGroup: ChipGroup
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var startDate: String? = null
    private var endDate: String? = null
    private var currentSort = "distance,asc"
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var searchJob: Runnable? = null
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_explore, container, false)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        recyclerView = view.findViewById(R.id.recyclerEvents)
        tvNoResults = view.findViewById(R.id.tvNoResults)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = EventsAdapter(events,
            onInterestedClick = { event -> markEventStatus(event, "interested") },
            onGoingClick = { event -> markEventStatus(event, "going") },
            onItemClick = { event ->
                val intent = Intent(requireContext(), EventDetailActivity::class.java)
                intent.putExtra("eventId", event.id)
                startActivity(intent)
            }
        )
        recyclerView.adapter = adapter

        fetchUserEventStatuses()

        val searchView = view.findViewById<SearchView>(R.id.searchBar)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                if (currentSort == "distance,asc") {
                    requestLocationAndFetchEvents()
                } else {
                    fetchEvents(query, getSelectedGenre(), startDate, endDate, sort = currentSort)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText ?: ""
                searchJob?.let { handler.removeCallbacks(it) }

                if (query.isEmpty()) {
                    events.clear()
                    adapter.updateEvents(events.toMutableList())
                    if (currentSort == "distance,asc") {
                        requestLocationAndFetchEvents()
                    } else {
                        fetchEvents(query = "", genre = getSelectedGenre(), startDate = startDate, endDate = endDate, sort = currentSort)
                    }
                }

                searchJob = Runnable {
                    if (currentSort == "distance,asc") {
                        requestLocationAndFetchEvents()
                    } else {
                        fetchEvents(
                            query = query,
                            genre = getSelectedGenre(),
                            startDate = startDate,
                            endDate = endDate,
                            sort = currentSort
                        )
                    }
                }
                handler.postDelayed(searchJob!!, 350)
                return true
            }
        })

        genreChipGroup = view.findViewById<ChipGroup>(R.id.genreChipGroup)
        genreChipGroup.setOnCheckedChangeListener { _, checkedId ->
            val currentQuery = searchView.query.toString()

            if (currentSort == "distance,asc") {
                requestLocationAndFetchEvents()
            } else {
                if (checkedId == R.id.chipAll) {
                    // Reset ALL filters including Date
                    startDate = null
                    endDate = null
                    btnFilterDate.text = "Filter by Date" // Reset button text
                    fetchEvents(currentQuery, null, null, null, sort = currentSort)
                    Toast.makeText(context, "Showing all events", Toast.LENGTH_SHORT).show()
                } else {
                    val genreFilter = when (checkedId) {
                        R.id.chipPop -> "Pop"
                        R.id.chipRock -> "Rock"
                        R.id.chipHipHop -> "Hip-Hop"
                        R.id.chipCountry -> "Country"
                        R.id.chipJazz -> "Jazz"
                        R.id.chipElectronic -> "Electronic"
                        else -> null
                    }
                    // Pass current date filters so they aren't lost
                    fetchEvents(currentQuery, genreFilter, startDate, endDate, sort = currentSort)
                }
            }
        }

        sortChipGroup = view.findViewById(R.id.sortChipGroup)
        sortChipGroup.setOnCheckedChangeListener { _, checkedId ->
            val currentQuery = searchView.query.toString()
            val currentGenre = getSelectedGenre()
            when (checkedId) {
                R.id.chipSortDate -> {
                    currentSort = "date,asc"
                    fetchEvents(currentQuery, currentGenre, startDate, endDate, sort = currentSort)
                }
                R.id.chipSortPopularity -> {
                    currentSort = "relevance,desc"
                    fetchEvents(currentQuery, currentGenre, startDate, endDate, sort = currentSort)
                }
                R.id.chipSortNearby -> {
                    currentSort = "distance,asc"
                    requestLocationAndFetchEvents()
                }
            }
        }

        // Handle click on "All" chip specifically for when it is ALREADY selected
        val chipAll = view.findViewById<Chip>(R.id.chipAll)
        chipAll.setOnClickListener {
            // If date filters are active, clear them
            if (startDate != null || endDate != null) {
                startDate = null
                endDate = null
                btnFilterDate.text = "Filter by Date"
                val currentQuery = searchView.query.toString()
                fetchEvents(currentQuery, null, null, null, sort = currentSort)
                Toast.makeText(context, "Date filter cleared", Toast.LENGTH_SHORT).show()
            }
        }

        btnFilterDate = view.findViewById(R.id.btnFilterDate)
        btnFilterDate.setOnClickListener {
            val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Select Date Range")
                .setTheme(R.style.Theme_Backstage_DatePicker)
                .build()

            dateRangePicker.show(parentFragmentManager, "datePicker")

            dateRangePicker.addOnPositiveButtonClickListener { selection ->
                val startMillis = selection.first
                val endMillis = selection.second ?: selection.first
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                
                startDate = dateFormat.format(Date(startMillis))
                endDate = dateFormat.format(Date(endMillis))
                
                if (startDate == endDate) {
                    btnFilterDate.text = startDate
                } else {
                    btnFilterDate.text = "$startDate to $endDate"
                }

                val currentQuery = searchView.query.toString()
                if (currentSort == "distance,asc") {
                    requestLocationAndFetchEvents()
                } else {
                    fetchEvents(currentQuery, getSelectedGenre(), startDate, endDate, sort = currentSort)
                }
            }

            dateRangePicker.addOnNegativeButtonClickListener {
                startDate = null
                endDate = null
                btnFilterDate.text = "Filter by Date"
                val currentQuery = searchView.query.toString()
                if (currentSort == "distance,asc") {
                    requestLocationAndFetchEvents()
                } else {
                    fetchEvents(currentQuery, getSelectedGenre(), null, null, sort = currentSort)
                }
                Toast.makeText(context, "Date filter cleared", Toast.LENGTH_SHORT).show()
            }
        }
        requestLocationAndFetchEvents() // loads concerts by default when Explore opens

        return view
    }

    private fun fetchUserEventStatuses() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("users").document(user.uid).collection("my_events")
            .get()
            .addOnSuccessListener { documents ->
                val interested = mutableSetOf<String>()
                val going = mutableSetOf<String>()
                for (document in documents) {
                    val status = document.getString("status")
                    val id = document.id
                    if (status == "interested") {
                        interested.add(id)
                    } else if (status == "going") {
                        going.add(id)
                    }
                }
                adapter.setEventStatuses(interested, going)
            }
            .addOnFailureListener { e ->
                Log.e("ExploreFragment", "Error fetching user event statuses", e)
            }
    }

    private fun getSelectedGenre(): String? {
        val checkedChipId = genreChipGroup.checkedChipId
        // If "All" is checked, return null for genre
        if (checkedChipId == R.id.chipAll) return null
        
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
        endDate: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        sort: String = "distance,asc"
    ) {
        val apiKey = "A9nGiKzxYRZTGQfiUG0lK0JlNkZJ8FXx"
        val baseUrl = "https://app.ticketmaster.com/discovery/v2/events.json"

        var url = "$baseUrl?apikey=$apiKey&countryCode=CA&segmentName=Music&sort=$sort"

        if (query.isNotEmpty()) {
            url += "&keyword=${Uri.encode(query)}"
        } else {
            url += "&size=50"
        }

        if (genre != null) {
            url += "&classificationName=${Uri.encode(genre)}"
        }

        if (latitude != null && longitude != null) {
            url += "&latlong=${latitude},${longitude}"
        }

        if (startDate != null) {
            // Logic to handle Timezones correctly using standard startDateTime/endDateTime
            // We will extend the end date by 1 day in UTC to ensure we catch evening concerts
            // that occur in Pacific/Eastern time (which is next day UTC)
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                parser.timeZone = TimeZone.getTimeZone("UTC")
                
                val endStr = endDate ?: startDate
                val parsedEndDate = parser.parse(endStr)
                
                // Add 1 day to end date to create a safe buffer
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                calendar.time = parsedEndDate!!
                calendar.add(Calendar.DAY_OF_MONTH, 1) 
                
                val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                formatter.timeZone = TimeZone.getTimeZone("UTC")
                val safeEndDate = formatter.format(calendar.time)

                // UTC Format: yyyy-MM-ddTHH:mm:ssZ
                val startDateTime = "${startDate}T00:00:00Z"
                val endDateTime = "${safeEndDate}T23:59:59Z" // End of the NEXT day to be safe
                
                url += "&startDateTime=$startDateTime&endDateTime=$endDateTime"
                
            } catch (e: Exception) {
                Log.e("ExploreFragment", "Date format error", e)
            }
        }
        
        Log.d("ExploreFragment", "Fetching: $url")

        val request = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                try {
                    events.clear()
                    val embedded = response.optJSONObject("_embedded")
                    
                    if (embedded == null) {
                        adapter.updateEvents(events.toMutableList())
                        tvNoResults.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                        return@JsonObjectRequest
                    }
                    
                    val eventsArray = embedded.optJSONArray("events")
                    
                    if (eventsArray == null || eventsArray.length() == 0) {
                         adapter.updateEvents(events.toMutableList())
                        tvNoResults.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                        return@JsonObjectRequest
                    }
                    
                    tvNoResults.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE

                    for (i in 0 until eventsArray.length()) {
                        val e = eventsArray.getJSONObject(i)
                        val id = e.optString("id")
                        val name = e.optString("name", "Untitled")
                        val ticketUrl = e.optString("url", "")

                        val images = e.optJSONArray("images")
                        val imageUrl = images?.getJSONObject(0)?.optString("url", "") ?: ""

                        val venueObj = e.optJSONObject("_embedded")
                            ?.optJSONArray("venues")
                            ?.getJSONObject(0)

                        var venueName = venueObj?.optString("name", "Unknown Venue") ?: "Unknown Venue"
                        val city = venueObj?.optJSONObject("city")?.optString("name")
                        if (!city.isNullOrEmpty()) {
                            venueName += ", $city"
                        }

                        var lat = 0.0
                        var lng = 0.0
                        try {
                            val location = venueObj?.optJSONObject("location")
                            if (location != null) {
                                lat = location.optString("latitude", "0.0").toDouble()
                                lng = location.optString("longitude", "0.0").toDouble()
                            }
                        } catch (e: Exception) {
                            Log.e("Explore", "Error parsing location", e)
                        }

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

                        var genreName = ""
                        val classifications = e.optJSONArray("classifications")
                        if (classifications != null && classifications.length() > 0) {
                            genreName = classifications.getJSONObject(0)
                                .optJSONObject("genre")
                                ?.optString("name", "") ?: ""
                        }

                        events.add(Event(id, name, formattedDate, venueName, imageUrl, genreName, ticketUrl, lat, lng))
                    }
                    adapter.updateEvents(events.toMutableList())
                } catch (ex: Exception) {
                    Log.e("API_PARSE", "Error parsing events: ${ex.message}")
                }
            },
            { error ->
                Log.e("API_ERROR", "Error fetching concerts: $error")
                Toast.makeText(context, "Error loading events", Toast.LENGTH_SHORT).show()
            }
        )
        Volley.newRequestQueue(requireContext()).add(request)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                fetchNearbyEvents()
            } else {
                Toast.makeText(
                    context,
                    "Location permission is required for this sort option.",
                    Toast.LENGTH_SHORT
                ).show()
                // Revert to date sort if permission is denied
                sortChipGroup.check(R.id.chipSortDate)
            }
        }
    }

    private fun requestLocationAndFetchEvents() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            fetchNearbyEvents()
        }
    }

    private fun fetchNearbyEvents() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude
                fetchEvents(
                    query = (view?.findViewById<SearchView>(R.id.searchBar)?.query ?: "").toString(),
                    genre = getSelectedGenre(),
                    startDate = startDate,
                    endDate = endDate,
                    latitude = lat,
                    longitude = lon,
                    sort = currentSort
                )
            } else {
                Toast.makeText(context, "Could not get location. Please ensure location is enabled.", Toast.LENGTH_LONG).show()
                sortChipGroup.check(R.id.chipSortDate)
            }
        }
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
            "title" to event.name,
            "date" to event.date,
            "location" to event.venue,
            "imageUrl" to event.imageUrl,
            "genre" to event.genre,
            "status" to status,
            "ticketUrl" to event.ticketUrl,
            "latitude" to event.latitude,
            "longitude" to event.longitude
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
