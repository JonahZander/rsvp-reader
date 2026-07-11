package com.jonahzander.rsvpreader.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Chapter(
    val title: String,
    val text: String,
)

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String? = null,
    val chapters: List<Chapter>,
)
