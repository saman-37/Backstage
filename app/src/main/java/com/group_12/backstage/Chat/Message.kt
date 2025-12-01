package com.group_12.backstage.Chat

import com.google.firebase.Timestamp

data class Message(
    val sender: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val isSentByCurrentUser: Boolean = false,
    var previewMessage: String = ""
)
