package com.artconsumption.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtDao {
    @Query("SELECT * FROM art_posts ORDER BY date DESC")
    fun getAllPosts(): Flow<List<ArtPost>>

    @Query("SELECT * FROM art_posts ORDER BY date DESC")
    suspend fun getAllPostsOnce(): List<ArtPost>

    @Query("SELECT * FROM art_posts ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomPost(): ArtPost?

    @Query("SELECT * FROM art_posts WHERE shortcode = :shortcode")
    suspend fun getPost(shortcode: String): ArtPost?

    @Query("SELECT * FROM art_posts ORDER BY lastShownAt ASC LIMIT 1")
    suspend fun getLeastRecentlyShown(): ArtPost?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(posts: List<ArtPost>)

    @Query("UPDATE art_posts SET lastShownAt = :timestamp WHERE shortcode = :shortcode")
    suspend fun markShown(shortcode: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM art_posts")
    suspend fun getPostCount(): Int

    @Query("SELECT DISTINCT handle FROM art_posts ORDER BY handle")
    fun getAccounts(): Flow<List<String>>
}
