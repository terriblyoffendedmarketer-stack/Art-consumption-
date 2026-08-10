package com.artconsumption.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "art_posts")
data class ArtPost(
    @PrimaryKey val shortcode: String,
    val handle: String,
    val date: String,
    val caption: String,
    val slideCount: Int,
    val directoryPath: String,
    val lastShownAt: Long = 0,
)
