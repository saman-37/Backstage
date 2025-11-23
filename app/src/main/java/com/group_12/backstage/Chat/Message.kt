package com.group_12.backstage.Chat

data class Message(
    val name: String,
    val lastMessage: String,
    val time: String,
    val isNew: Boolean = false
)
