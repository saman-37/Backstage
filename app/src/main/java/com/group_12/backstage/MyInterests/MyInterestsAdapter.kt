package com.group_12.backstage.MyInterests

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.group_12.backstage.R

class MyInterestsAdapter(
    private val events: MutableList<Event>,
    private val onItemClick: (Event, ImageView) -> Unit // Updated callback to include ImageView
) : RecyclerView.Adapter<MyInterestsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val eventImage: ImageView = itemView.findViewById(R.id.eventImage)
        val eventName: TextView = itemView.findViewById(R.id.eventName)
        val eventVenue: TextView = itemView.findViewById(R.id.eventVenue)
        val eventStatus: TextView = itemView.findViewById(R.id.eventStatus)
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
        holder.eventStatus.text = event.status.replaceFirstChar { it.uppercase() }
        if (event.status.equals("going", ignoreCase = true)) {
            holder.eventStatus.setTextColor(Color.parseColor("#008000")) // Green for Going
        } else {
            holder.eventStatus.setTextColor(Color.parseColor("#009688")) // Teal for Interested
        }

        // Load image using Glide
        if (event.imageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(event.imageUrl)
                .placeholder(R.drawable.sebastian_unsplash) // Use a placeholder
                .error(R.drawable.sebastian_unsplash) // Use a fallback
                .into(holder.eventImage)
        } else {
             holder.eventImage.setImageResource(R.drawable.sebastian_unsplash)
        }

        // Set click listener on the card
        holder.itemView.setOnClickListener {
            onItemClick(event, holder.eventImage)
        }
    }

    override fun getItemCount(): Int = events.size
}
