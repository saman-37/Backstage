package com.group_12.backstage.Explore

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.text.LinkAnnotation
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.group_12.backstage.R

data class Event(
    val id: String,
    val name: String,
    val date: String,       // e.g. "Jan 05, 2024"
    val venue: String,      // venue name
    val imageUrl: String,
    val genre: String,       // e.g. "Rock", "Pop"
//    val ticketUrl: String
)

class EventsAdapter(
events: List<Event>,
private val onInterestedClick: (Event) -> Unit,
private val onGoingClick: (Event) -> Unit
) : RecyclerView.Adapter<EventsAdapter.EventViewHolder>() {

    private val eventList = events.toMutableList()   // internal safe copy

    fun updateEvents(newEvents: List<Event>) {
        eventList.clear()
        eventList.addAll(newEvents)
        notifyDataSetChanged()
    }

    inner class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.eventImage)
        val name: TextView = view.findViewById(R.id.eventName)
        val venue: TextView = view.findViewById(R.id.eventVenue)
        val btnInterested: ImageButton = view.findViewById(R.id.btnInterested)
        val btnGoing: ImageButton = view.findViewById(R.id.btnGoing)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = eventList[position]

        holder.name.text = event.name
        holder.venue.text = event.venue
        Glide.with(holder.itemView.context).load(event.imageUrl).into(holder.image)

        holder.btnInterested.setOnClickListener { onInterestedClick(event) }
        holder.btnGoing.setOnClickListener { onGoingClick(event) }
    }

    override fun getItemCount(): Int = eventList.size

}
