package com.group_12.backstage.UserAccountData

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val profileImageUrl: String = "",
    val bio: String = "",
    val receiveNotifications: Boolean = false,
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val locationBasedContent: Boolean = false,
    var lastMessageTimestamp: Long = 0L // Add this line
)
