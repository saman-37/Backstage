package com.group_12.backstage.MyInterests

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.transition.TransitionInflater
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.group_12.backstage.R
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

class EventDetailsFragment : Fragment() {

    private var eventId: String? = null
    private var title: String? = null
    private var date: String? = null
    private var location: String? = null
    private var imageUrl: String? = null
    private var ticketUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            eventId = it.getString("eventId")
            title = it.getString("title")
            date = it.getString("date")
            location = it.getString("location")
            imageUrl = it.getString("imageUrl")
            ticketUrl = it.getString("ticketUrl")
        }

        // Set shared element transition
        sharedElementEnterTransition = TransitionInflater.from(context).inflateTransition(android.R.transition.move)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_event_details, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val eventImage: ImageView = view.findViewById(R.id.detailEventImage)
        val eventTitle: TextView = view.findViewById(R.id.detailEventTitle)
        val eventDate: TextView = view.findViewById(R.id.detailEventDate)
        val eventVenue: TextView = view.findViewById(R.id.detailEventVenue)
        val btnAddToCalendar: Button = view.findViewById(R.id.btnAddToCalendar)
        val btnShare: Button = view.findViewById(R.id.btnShare)
        val btnBuyTickets: Button = view.findViewById(R.id.btnBuyTickets)
        val scrollView: ScrollView = view.findViewById(R.id.detailsScrollView)
        val rootLayout: View = view.findViewById(R.id.detailsRootLayout)
        val btnBack: ImageView = view.findViewById(R.id.btnBack)

        // Set transition name for the image view to match the one from the list
        if (eventId != null) {
            eventImage.transitionName = "event_image_$eventId"
        }

        // Back Button Logic
        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Populate Data
        eventTitle.text = title
        eventDate.text = date
        eventVenue.text = location

        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.sebastian_unsplash)
                .error(R.drawable.sebastian_unsplash)
                .into(eventImage)
        } else {
            eventImage.setImageResource(R.drawable.sebastian_unsplash)
        }

        // Logic for Buttons
        btnAddToCalendar.setOnClickListener {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                putExtra(CalendarContract.Events.DESCRIPTION, "Check out this event: $ticketUrl")
                try {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
                    val parsedDate = dateFormat.parse(date ?: "")
                    if (parsedDate != null) {
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, parsedDate.time)
                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, parsedDate.time + 7200000)
                    }
                } catch (e: Exception) {
                    Log.e("EventDetails", "Date parse error", e)
                }
            }
            startActivity(intent)
        }

        btnShare.setOnClickListener {
            val shareText = "I'm going to $title at $location! Join me: $ticketUrl"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, "Share Event"))
        }

        btnBuyTickets.setOnClickListener {
            if (!ticketUrl.isNullOrEmpty()) {
                MaterialAlertDialogBuilder(requireContext(), R.style.App_MaterialAlertDialog)
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
                Toast.makeText(context, "Ticket link not available", Toast.LENGTH_SHORT).show()
            }
        }

        // Swipe down to dismiss logic
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (abs(diffY) > abs(diffX) && abs(diffY) > 100 && velocityY > 100) {
                    if (diffY > 0) {
                        // Swipe Down detected
                        if (scrollView.scrollY == 0) {
                            findNavController().navigateUp()
                            return true
                        }
                    }
                }
                return false
            }
        })

        // Attach touch listener to root layout and image
        val touchListener = View.OnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        rootLayout.setOnTouchListener(touchListener)
        eventImage.setOnTouchListener(touchListener)
        
        scrollView.setOnTouchListener { v, event ->
             if (v.scrollY == 0) {
                 gestureDetector.onTouchEvent(event)
             }
             false
        }
    }
}
