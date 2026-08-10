package com.artconsumption.data

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * Scans the content directory for scraped Instagram posts and indexes them into the database.
 *
 * Expected directory structure:
 *   ArtConsumption/content/<handle>/<shortcode>/
 *     metadata.json   — { shortcode, date, caption, slide_count, handle }
 *     01.jpg, 02.jpg, ...
 */
class ContentScanner(private val context: Context) {

    private val dao = ArtDatabase.get(context).artDao()

    private fun contentRoots(): List<File> {
        val roots = mutableListOf<File>()

        val external = File(
            Environment.getExternalStorageDirectory(),
            "ArtConsumption/content"
        )
        if (external.isDirectory) roots.add(external)

        val internal = File(context.filesDir, "content")
        if (internal.isDirectory) roots.add(internal)

        return roots
    }

    suspend fun scan(): Int {
        var imported = 0

        for (root in contentRoots()) {
            val accountDirs = root.listFiles { f -> f.isDirectory } ?: continue

            for (accountDir in accountDirs) {
                val handle = accountDir.name
                val postDirs = accountDir.listFiles { f ->
                    f.isDirectory && File(f, "metadata.json").exists()
                } ?: continue

                val posts = postDirs.mapNotNull { postDir ->
                    parsePost(postDir, handle)
                }

                if (posts.isNotEmpty()) {
                    dao.insertAll(posts)
                    imported += posts.size
                }
            }
        }

        return imported
    }

    private fun parsePost(postDir: File, fallbackHandle: String): ArtPost? {
        val metaFile = File(postDir, "metadata.json")
        if (!metaFile.exists()) return null

        return try {
            val json = JSONObject(metaFile.readText())
            val shortcode = json.optString("shortcode", postDir.name)
            val slideFiles = postDir.listFiles { f ->
                f.isFile && f.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
            }

            if (slideFiles.isNullOrEmpty()) return null

            ArtPost(
                shortcode = shortcode,
                handle = json.optString("handle", fallbackHandle),
                date = json.optString("date", ""),
                caption = json.optString("caption", ""),
                slideCount = slideFiles.size,
                directoryPath = postDir.absolutePath,
            )
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun getSlidesForPost(post: ArtPost): List<File> {
            val dir = File(post.directoryPath)
            if (!dir.isDirectory) return emptyList()

            return dir.listFiles { f ->
                f.isFile && f.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
            }?.sortedBy { it.name } ?: emptyList()
        }
    }
}
