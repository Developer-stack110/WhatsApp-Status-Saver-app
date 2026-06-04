package com.example.model

data class StatusItem(
    val id: String,
    val uriString: String,
    val fileName: String,
    val isVideo: Boolean,
    val fileSize: Long,
    val isSaved: Boolean = false,
    val isBusiness: Boolean = false,
    val fallbackUrl: String? = null
)
