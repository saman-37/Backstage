package com.group_12.backstage.Explore

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.group_12.backstage.R
import org.json.JSONObject

class EventDetailActivity : AppCompatActivity() {

    private var event: Event? = null
    private lateinit var btnInterested: Button
    private lateinit var btnGoing: Button
    private lateinit var eventId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        eventId = intent.getStringExtra("eventId")!!

        val backButton: ImageButton = findViewById(R.id.backButton)
        btnInterested = findViewById(R.id.btnInterested)
        btnGoing = findViewById(R.id.btnGoing)

        backButton.setOnClickListener {
            finish()
        }

        btnInterested.setOnClickListener {
            event?.let { markEventStatus("interested", it) }
        }

        btnGoing.setOnClickListener {
            event?.let { markEventStatus("going", it) }
        }

        fetchEventDetails(eventId)
        checkEventStatus()
    }

    private fun fetchEventDetails(eventId: String) {
        val apiKey = "A9nGiKzxYRZTGQfiUG0lK0JlNkZJ8FXx"
        val url = "https://app.ticketmaster.com/discovery/v2/events/$eventId.json?apikey=$apiKey"

        val request = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                try {
                    event = parseEventDetails(response)
                    updateUi(response)
                } catch (e: Exception) {
                    Log.e("EventDetailActivity", "Error parsing event details", e)
                }
            },
            { error ->
                Log.e("EventDetailActivity", "Error fetching event details", error)
            }
        )

        Volley.newRequestQueue(this).add(request)
    }

    private fun parseEventDetails(response: JSONObject): Event {
        val id = response.optString("id")
        val name = response.optString("name")
        val ticketUrl = response.optString("url")

        val images = response.optJSONArray("images")
        val imageUrl = images?.getJSONObject(0)?.optString("url", "") ?: ""

        val venueObj = response.optJSONObject("_embedded")
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
        response.optJSONObject("dates")?.optJSONObject("start")?.let { startObj ->
            val localDate = startObj.optString("localDate")
            if (localDate.isNotEmpty()) {
                try {
                    val inputFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val outputFmt = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
                    formattedDate = outputFmt.format(inputFmt.parse(localDate)!!)
                } catch (ex: Exception) {
                    formattedDate = localDate
                }
            }
        }

        var genreName = ""
        val classifications = response.optJSONArray("classifications")
        if (classifications != null && classifications.length() > 0) {
            genreName = classifications.getJSONObject(0)
                .optJSONObject("genre")
                ?.optString("name", "") ?: ""
        }
        return Event(id, name, formattedDate, venueName, imageUrl, genreName, ticketUrl, lat, lng)
    }

    private fun updateUi(response: JSONObject) {
        event?.let {
            val eventName: TextView = findViewById(R.id.eventName)
            val eventDate: TextView = findViewById(R.id.eventDate)
            val eventVenue: TextView = findViewById(R.id.eventVenue)
            val eventGenre: TextView = findViewById(R.id.eventGenre)
            val eventImage: ImageView = findViewById(R.id.eventImage)
            val btnBuyTickets: Button = findViewById(R.id.btnBuyTickets)
            val eventPrice: TextView = findViewById(R.id.eventPrice)
            val eventPromoter: TextView = findViewById(R.id.eventPromoter)
            val seatmapImage: ImageView = findViewById(R.id.seatmapImage)

            eventName.text = it.name
            eventDate.text = it.date
            eventVenue.text = it.venue
            eventGenre.text = it.genre
            val ticketUrl = it.ticketUrl

            Glide.with(this)
                .load(it.imageUrl)
                .into(eventImage)

            btnBuyTickets.setOnClickListener {
                 if (!ticketUrl.isNullOrEmpty()) {
                    MaterialAlertDialogBuilder(this, R.style.App_MaterialAlertDialog)
                        .setTitle("External Link")
                        .setMessage("You are about to be redirected to an external website to purchase tickets. Do you want to continue?")
                        .setPositiveButton("Continue") { dialog, _ ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ticketUrl))
                            startActivity(intent)
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                } else {
                    Toast.makeText(this, "Ticket link not available", Toast.LENGTH_SHORT).show()
                }
            }

            val priceRanges = response.optJSONArray("priceRanges")
            if (priceRanges != null && priceRanges.length() > 0) {
                val priceRange = priceRanges.getJSONObject(0)
                val min = priceRange.optDouble("min")
                val max = priceRange.optDouble("max")
                val currency = priceRange.optString("currency")
                eventPrice.text = "Price: $min - $max $currency"
                eventPrice.visibility = View.VISIBLE
            } else {
                eventPrice.visibility = View.GONE
            }

            val promoter = response.optJSONObject("promoter")?.optString("name")
            if (!promoter.isNullOrEmpty()) {
                eventPromoter.text = "Promoter: $promoter"
                eventPromoter.visibility = View.VISIBLE
            } else {
                eventPromoter.visibility = View.GONE
            }

            val seatmapUrl = response.optJSONObject("seatmap")?.optString("staticUrl")
            if (!seatmapUrl.isNullOrEmpty()) {
                seatmapImage.visibility = View.VISIBLE
                Glide.with(this)
                    .load(seatmapUrl)
                    .into(seatmapImage)
            } else {
                seatmapImage.visibility = View.GONE
            }
        }
    }


    private fun markEventStatus(status: String, eventToMark: Event) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Please log in to save events", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = user.uid
        val firestore = FirebaseFirestore.getInstance()

        val eventData = mapOf(
            "id" to eventToMark.id,
            "title" to eventToMark.name,
            "date" to eventToMark.date,
            "location" to eventToMark.venue,
            "imageUrl" to eventToMark.imageUrl,
            "genre" to eventToMark.genre,
            "status" to status,
            "ticketUrl" to eventToMark.ticketUrl,
            "latitude" to eventToMark.latitude,
            "longitude" to eventToMark.longitude
        )

        firestore.collection("users")
            .document(uid)
            .collection("my_events")
            .document(eventToMark.id)
            .set(eventData)
            .addOnSuccessListener {
                Toast.makeText(this, "Marked as $status", Toast.LENGTH_SHORT).show()
                updateButtonStates(status)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("Firebase", "Error writing event", e)
            }
    }

    private fun checkEventStatus() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            return
        }

        val uid = user.uid
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("users")
            .document(uid)
            .collection("my_events")
            .document(eventId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val status = document.getString("status")
                    updateButtonStates(status)
                }
            }
    }

    private fun updateButtonStates(status: String?) {
        val selectedColor = ContextCompat.getColor(this, R.color.colorSecondary)
        val defaultColor = ContextCompat.getColor(this, R.color.colorOutline)

        when (status) {
            "interested" -> {
                btnInterested.backgroundTintList = ColorStateList.valueOf(selectedColor)
                btnGoing.backgroundTintList = ColorStateList.valueOf(defaultColor)
            }
            "going" -> {
                btnInterested.backgroundTintList = ColorStateList.valueOf(defaultColor)
                btnGoing.backgroundTintList = ColorStateList.valueOf(selectedColor)
            }
            else -> {
                btnInterested.backgroundTintList = ColorStateList.valueOf(defaultColor)
                btnGoing.backgroundTintList = ColorStateList.valueOf(defaultColor)
            }
        }
    }
}
