package com.group_12.backstage.ConcertConnect

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.group_12.backstage.R
import com.group_12.backstage.UserAccountData.User

class FriendsListAdapter(
    private val onFriendClick: (User) -> Unit
) : RecyclerView.Adapter<FriendsListAdapter.FriendViewHolder>() {

    private var friends = listOf<User>()

    fun submitList(newFriends: List<User>) {
        friends = newFriends
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friends[position]
        holder.bind(friend)
    }

    override fun getItemCount(): Int = friends.size

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ShapeableImageView = itemView.findViewById(R.id.userProfileImage)
        private val name: TextView = itemView.findViewById(R.id.userName)
        private val email: TextView = itemView.findViewById(R.id.userEmail)

        fun bind(user: User) {
            name.text = user.name.ifEmpty { "Unknown" }
            email.text = user.email

            if (user.profileImageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(user.profileImageUrl)
                    .placeholder(R.drawable.ic_account_circle)
                    .error(R.drawable.ic_account_circle)
                    .centerCrop()
                    .into(image)
            } else {
                image.setImageResource(R.drawable.ic_account_circle)
            }

            itemView.setOnClickListener {
                onFriendClick(user)
            }
        }
    }
}
