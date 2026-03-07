package com.clubs.notification

data class QueuedNotification(
    val chatId: Long = 0,
    val text: String = "",
    val buttonText: String? = null,
    val buttonUrl: String? = null
)
