package com.group_12.backstage.MyInterests

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.group_12.backstage.R

class MyInterestsAdapter(
    private val events: MutableList<Event>,
    private val onItemClick: (Event, ImageView) -> Unit,
    private val onStatusChange: (Event, String) -> Unit // New callback for status updates
) : RecyclerView.Adapter<MyInterestsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val eventImage: ImageView = itemView.findViewById(R.id.eventImage)
        val eventName: TextView = itemView.findViewById(R.id.eventName)
        val eventVenue: TextView = itemView.findViewById(R.id.eventVenue)
        val eventStatus: TextView = itemView.findViewById(R.id.eventStatus)
        val btnInterested: ImageButton = itemView.findViewById(R.id.btnInterested)
        val btnGoing: ImageButton = itemView.findViewById(R.id.btnGoing)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        holder.eventName.text = event.title
        holder.eventVenue.text = event.location
        
        // Set unique transition name
        holder.eventImage.transitionName = "event_image_${event.id}"
        
        // Set status text and color
        val isGoing = event.status.equals("going", ignoreCase = true)
        holder.eventStatus.text = event.status.replaceFirstChar { it.uppercase() }
        if (isGoing) {
            holder.eventStatus.setTextColor(Color.parseColor("#008000")) // Green for Going
        } else {
            holder.eventStatus.setTextColor(Color.parseColor("#009688")) // Teal for Interested
        }

        // Update Button UI (Visual feedback)
        if (isGoing) {
            holder.btnGoing.setColorFilter(Color.parseColor("#008000"))
            holder.btnInterested.setColorFilter(Color.DKGRAY)
        } else {
            holder.btnGoing.setColorFilter(Color.DKGRAY)
            holder.btnInterested.setColorFilter(Color.parseColor("#009688"))
        }

        // Load image using Glide
        if (event.imageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(event.imageUrl)
                .placeholder(R.drawable.sebastian_unsplash)
                .error(R.drawable.sebastian_unsplash)
                .into(holder.eventImage)
        } else {
             holder.eventImage.setImageResource(R.drawable.sebastian_unsplash)
        }

        // Set click listener on the card
        holder.itemView.setOnClickListener {
            onItemClick(event, holder.eventImage)
        }

        // Button Listeners
        holder.btnInterested.setOnClickListener {
            onStatusChange(event, "interested")
        }

        holder.btnGoing.setOnClickListener {
            onStatusChange(event, "going")
        }
    }

    override fun getItemCount(): Int = events.size

    fun updateEvents(newEvents: List<Event>) {
        events.clear()
        events.addAll(newEvents)
        notifyDataSetChanged()
    }
}