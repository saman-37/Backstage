package com.group_12.backstage_group_12.MyInterests

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.group_12.backstage_group_12.R

class MyInterestsAdapter(
    private var events: List<Event>
) : RecyclerView.Adapter<MyInterestsAdapter.EventViewHolder>() {

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val eventImage: ImageView = itemView.findViewById(R.id.eventImage)
        val eventTitle: TextView = itemView.findViewById(R.id.eventTitle)
        val eventDate: TextView = itemView.findViewById(R.id.eventDate)
        val eventLocation: TextView = itemView.findViewById(R.id.eventLocation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_interest_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.eventTitle.text = event.title
        holder.eventDate.text = event.date
        holder.eventLocation.text = event.location
        Glide.with(holder.itemView.context)
            .load(event.imageUrl)
            .placeholder(R.drawable.sebastian_unsplash)
            .into(holder.eventImage)
    }

    override fun getItemCount(): Int = events.size
}