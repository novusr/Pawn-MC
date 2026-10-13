package com.pocketsamp.server.crash

data class CrashReport(
    val id: String,
    val timestamp: Long,
    val formattedDate: String,
    val exceptionClass: String,
    val message: String?,
    val threadName: String,
    val deviceInfo: String,
    val stackTrace: String
)
