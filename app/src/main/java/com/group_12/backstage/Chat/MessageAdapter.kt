package com.group_12.backstage.Chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.group_12.backstage.R


class MessageAdapter(
    private val messages: List<Message>
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isSentByCurrentUser) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == TYPE_SENT) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_recieved
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view)
    }


    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.messageTextView.text = messages[position].message
        val msg = messages[position]

        // Format timestamp like iMessage
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        val formatted = sdf.format(java.util.Date(msg.timestamp))

        holder.timestampTextView.text = formatted
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        val timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)

    }
}
