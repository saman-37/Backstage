package com.group_12.backstage_group_12.Chat

data class Message(
    val name: String,
    val lastMessage: String,
    val time: String,
    val isNew: Boolean = false
)
