package com.group_12.backstage.Explore

import android.graphics.PorterDuff
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.group_12.backstage.R
import kotlinx.parcelize.Parcelize

@Parcelize
data class Event(
    val id: String,
    val name: String,
    val date: String,
    val venue: String,
    val imageUrl: String,
    val genre: String,
    val ticketUrl: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) : Parcelable

class EventsAdapter(
    events: List<Event>,
    private val onInterestedClick: (Event) -> Unit,
    private val onGoingClick: (Event) -> Unit,
    private val onItemClick: (Event) -> Unit
) : RecyclerView.Adapter<EventsAdapter.EventViewHolder>() {

    private val eventList = events.toMutableList()
    private val interestedState = mutableSetOf<String>()
    private val goingState = mutableSetOf<String>()

    fun updateEvents(newEvents: List<Event>) {
        eventList.clear()
        eventList.addAll(newEvents)
        notifyDataSetChanged()
    }

    fun setEventStatuses(interested: Set<String>, going: Set<String>) {
        interestedState.clear()
        interestedState.addAll(interested)
        goingState.clear()
        goingState.addAll(going)
        notifyDataSetChanged()
    }

    inner class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.eventImage)
        val name: TextView = view.findViewById(R.id.eventName)
        val venue: TextView = view.findViewById(R.id.eventVenue)
        val date: TextView = view.findViewById(R.id.eventDate)
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
        val context = holder.itemView.context

        holder.name.text = event.name
        holder.venue.text = event.venue
        holder.date.text = event.date
        Glide.with(context).load(event.imageUrl).into(holder.image)

        holder.itemView.setOnClickListener {
            onItemClick(event)
        }

        val activeColor = ContextCompat.getColor(context, R.color.colorSecondary)
        val inactiveColor = ContextCompat.getColor(context, R.color.colorOutline)

        holder.btnInterested.setColorFilter(
            if (interestedState.contains(event.id)) activeColor else inactiveColor,
            PorterDuff.Mode.SRC_IN
        )
        holder.btnGoing.setColorFilter(
            if (goingState.contains(event.id)) activeColor else inactiveColor,
            PorterDuff.Mode.SRC_IN
        )

        holder.btnInterested.setOnClickListener {
            if (interestedState.contains(event.id)) {
                interestedState.remove(event.id)
            } else {
                interestedState.add(event.id)
                goingState.remove(event.id)
            }
            notifyItemChanged(position)
            onInterestedClick(event)
        }

        holder.btnGoing.setOnClickListener {
            if (goingState.contains(event.id)) {
                goingState.remove(event.id)
            } else {
                goingState.add(event.id)
                interestedState.remove(event.id)
            }
            notifyItemChanged(position)
            onGoingClick(event)
        }
    }

    override fun getItemCount(): Int = eventList.size
}
