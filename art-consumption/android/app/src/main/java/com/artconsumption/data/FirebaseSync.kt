package com.artconsumption.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class FirebaseSync(private val context: Context) {

    private val dao = ArtDatabase.get(context).artDao()
    private val cacheDir = File(context.filesDir, "content")

    suspend fun sync(): Int {
        var total = 0
        for (handle in ACCOUNTS) {
            total += syncAccount(handle)
        }
        return total
    }

    private suspend fun syncAccount(handle: String): Int {
        val manifestUrl = firebaseUrl("content/$handle/manifest.json")
        val json = downloadText(manifestUrl) ?: return 0
        val manifest = JSONArray(json)

        val posts = (0 until manifest.length()).mapNotNull { i ->
            val obj = manifest.getJSONObject(i)
            val shortcode = obj.getString("shortcode")
            val slidesArr = obj.getJSONArray("slides")

            val existing = dao.getPost(shortcode)

            ArtPost(
                shortcode = shortcode,
                handle = obj.optString("handle", handle),
                date = obj.optString("date", ""),
                caption = obj.optString("caption", ""),
                slideCount = obj.optInt("slide_count", slidesArr.length()),
                directoryPath = File(cacheDir, "$handle/$shortcode").absolutePath,
                lastShownAt = existing?.lastShownAt ?: 0,
                slideBlobs = slidesArr.toString(),
            )
        }

        if (posts.isNotEmpty()) {
            dao.upsertAll(posts)
        }
        return posts.size
    }

    suspend fun preloadFirstSlides(count: Int) {
        val posts = dao.getAllPostsOnce()
            .filter { it.slideBlobs.isNotEmpty() }
            .sortedBy { it.lastShownAt }
            .take(count)

        for (post in posts) {
            val blobs = JSONArray(post.slideBlobs)
            if (blobs.length() == 0) continue
            val firstBlob = blobs.getString(0)
            val destDir = File(post.directoryPath)
            val destFile = File(destDir, firstBlob.substringAfterLast("/"))
            if (destFile.exists()) continue
            destDir.mkdirs()
            downloadFile(firebaseUrl(firstBlob), destFile)
        }
    }

    fun getCachedFirstSlide(post: ArtPost): File? {
        if (post.slideBlobs.isNotEmpty()) {
            val blobs = JSONArray(post.slideBlobs)
            if (blobs.length() > 0) {
                val firstBlobName = blobs.getString(0).substringAfterLast("/")
                val file = File(post.directoryPath, firstBlobName)
                if (file.exists()) return file
            }
        }
        return null
    }

    suspend fun downloadFirstSlideAsBitmap(post: ArtPost): Bitmap? {
        return downloadSlideAsBitmap(post, 0)
    }

    suspend fun downloadLastSlideAsBitmap(post: ArtPost): Bitmap? {
        if (post.slideBlobs.isEmpty()) return null
        val blobs = JSONArray(post.slideBlobs)
        if (blobs.length() == 0) return null
        return downloadSlideAsBitmap(post, blobs.length() - 1)
    }

    private suspend fun downloadSlideAsBitmap(post: ArtPost, index: Int): Bitmap? {
        if (post.slideBlobs.isEmpty()) return null
        val blobs = JSONArray(post.slideBlobs)
        if (index < 0 || index >= blobs.length()) return null

        val blobPath = blobs.getString(index)
        val destDir = File(post.directoryPath)
        destDir.mkdirs()
        val destFile = File(destDir, blobPath.substringAfterLast("/"))

        if (destFile.exists()) {
            return decodeSampledBitmap(destFile, 800, 800)
        }

        if (downloadFile(firebaseUrl(blobPath), destFile)) {
            return decodeSampledBitmap(destFile, 800, 800)
        }
        return null
    }

    private fun downloadText(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().readText()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadFile(url: String, dest: File): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            if (conn.responseCode == 200) {
                conn.inputStream.use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            dest.delete()
            false
        }
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    companion object {
        private const val BUCKET = "art-consumption.firebasestorage.app"
        private val ACCOUNTS = listOf("explainingpaintings")

        fun firebaseUrl(blobPath: String): String {
            val encoded = URLEncoder.encode(blobPath, "UTF-8")
            return "https://firebasestorage.googleapis.com/v0/b/$BUCKET/o/$encoded?alt=media"
        }

        fun getSlideUrls(post: ArtPost): List<String> {
            if (post.slideBlobs.isEmpty()) return emptyList()
            val blobs = JSONArray(post.slideBlobs)
            return (0 until blobs.length()).map { firebaseUrl(blobs.getString(it)) }
        }
    }
}
