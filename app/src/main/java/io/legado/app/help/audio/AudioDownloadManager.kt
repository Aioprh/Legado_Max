package io.legado.app.help.audio

import android.content.Context
import android.net.Uri
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
import splitties.init.appCtx
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent audio cache manager.
 *
 * Audio files live in a book-specific directory so the existing offline-cache
 * page can report and clear them together with normal text caches.
 */
object AudioDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val root: File by lazy {
        File(appCtx.getExternalFilesDir("Music"), "LegadoAudio").apply { mkdirs() }
    }

    data class DownloadedAudio(val file: File, val title: String) {
        val size: Long get() = file.length()
    }

    data class CacheStats(val count: Int, val size: Long)

    fun download(
        context: Context,
        url: String,
        title: String,
        onResult: (Boolean, File?) -> Unit = { _, _ -> }
    ) {
        val urls = parseUrls(url)
        if (urls.isEmpty()) {
            onResult(false, null)
            return
        }
        val key = sha1(url)
        if (jobs.containsKey(key)) return
        val job = scope.launch {
            try {
                val target = if (urls.size == 1) {
                    downloadOne(urls[0], safeName(title, key))
                } else {
                    val dir = File(root, safeName(title, key)).apply { mkdirs() }
                    val files = urls.mapIndexed { index, item ->
                        downloadOne(item, "%03d".format(index + 1), dir)
                    }
                    File(dir, "playlist.m3u8").apply {
                        writeText(buildString {
                            append("#EXTM3U\n")
                            files.forEach { file ->
                                append("#EXTINF:-1,\n")
                                append(file.absolutePath)
                                append('\n')
                            }
                        })
                    }
                    dir
                }
                launch(Dispatchers.Main) { onResult(true, target) }
            } catch (_: Exception) {
                launch(Dispatchers.Main) { onResult(false, null) }
            } finally {
                jobs.remove(key)
            }
        }
        jobs[key] = job
    }

    /**
     * Starts one unified audio-cache task for a book. Existing chapters are
     * skipped, so pressing resume continues from the first missing chapter.
     */
    fun startChapters(
        context: Context,
        book: Book,
        source: BookSource,
        chapters: List<BookChapter>,
        onProgress: (current: Int, total: Int, title: String, success: Boolean) -> Unit = { _, _, _, _ -> },
        onFinished: () -> Unit = {}
    ) {
        if (chapters.isEmpty()) {
            onFinished()
            return
        }
        if (jobs[book.bookUrl]?.isActive == true) return

        val job = scope.launch {
            try {
                for ((position, chapter) in chapters.withIndex()) {
                    if (!isActiveJob(book.bookUrl)) break
                    if (isChapterDownloaded(book, chapter)) {
                        onMainProgress(onProgress, position + 1, chapters.size, chapter.title, true)
                        continue
                    }
                    val content = try {
                        WebBook.getContent(scope, source, book, chapter).await()
                    } catch (_: Exception) {
                        null
                    }
                    val value = content?.trim().orEmpty()
                    if (value.isBlank()) {
                        onMainProgress(onProgress, position + 1, chapters.size, chapter.title, false)
                        continue
                    }
                    val ok = runCatching {
                        val urls = parseUrls(value)
                        if (urls.isEmpty()) false
                        else {
                            val dir = bookDir(book)
                            dir.mkdirs()
                            if (urls.size == 1) {
                                downloadOne(
                                    urls[0],
                                    chapterFileName(chapter, urls[0]),
                                    dir
                                )
                            } else {
                                val chapterDir = File(dir, safeName(chapter.title, sha1(chapter.url)))
                                    .apply { mkdirs() }
                                urls.forEachIndexed { index, url ->
                                    downloadOne(url, "%03d_${sha1(url)}".format(index + 1), chapterDir)
                                }
                                File(chapterDir, "playlist.m3u8").writeText(
                                    buildString {
                                        append("#EXTM3U\n")
                                        chapterDir.listFiles()
                                            ?.filter { it.isFile && it.name != "playlist.m3u8" }
                                            ?.sortedBy { it.name }
                                            ?.forEach { file ->
                                                append("#EXTINF:-1,\n")
                                                append(file.name)
                                                append('\n')
                                            }
                                    }
                                )
                            }
                            true
                        }
                    }.getOrDefault(false)
                    onMainProgress(onProgress, position + 1, chapters.size, chapter.title, ok)
                }
            } finally {
                jobs.remove(book.bookUrl)
                launch(Dispatchers.Main) { onFinished() }
            }
        }
        jobs[book.bookUrl] = job
    }

    /** Backward-compatible entry point used by the audio player. */
    fun downloadChapters(
        context: Context,
        book: Book,
        source: BookSource,
        chapters: List<BookChapter>,
        onProgress: (current: Int, total: Int, title: String, success: Boolean) -> Unit = { _, _, _, _ -> },
        onFinished: () -> Unit = {}
    ) = startChapters(context, book, source, chapters, onProgress, onFinished)

    fun stop(bookUrl: String): Boolean = jobs.remove(bookUrl)?.let {
        it.cancel()
        true
    } ?: false

    fun isRunning(bookUrl: String): Boolean = jobs[bookUrl]?.isActive == true

    fun hasRunningTasks(): Boolean = jobs.values.any { it.isActive }

    fun getCacheStats(book: Book): CacheStats {
        val dir = bookDir(book)
        if (!dir.exists()) return CacheStats(0, 0L)
        val files = dir.walkTopDown()
            .filter { it.isFile && it.name != "playlist.m3u8" }
            .toList()
        return CacheStats(files.size, files.sumOf { it.length() })
    }

    fun clearBook(book: Book): Boolean = runCatching {
        bookDir(book).deleteRecursively()
    }.getOrDefault(false)

    fun listDownloaded(): List<DownloadedAudio> = root.walkTopDown()
        .filter { it.isFile && it.name != "playlist.m3u8" }
        .map { file -> DownloadedAudio(file, file.nameWithoutExtension) }
        .sortedByDescending { it.file.lastModified() }
        .toList()

    fun delete(file: File): Boolean = runCatching {
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }.getOrDefault(false)

    fun isDownloaded(url: String): Boolean {
        val key = sha1(url)
        return root.walkTopDown().any { it.isFile && it.name.contains(key) }
    }

    /** Adaptive URL preloading: faster networks get more chapters, while mobile/slow links stay conservative. */
    fun smartCount(context: Context, speed: Float): Int {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val wifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        val cellular = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
        return when {
            wifi && speed <= 1.5f -> 4
            wifi -> 3
            cellular && speed <= 1.25f -> 2
            cellular -> 1
            else -> 1
        }
    }

    private fun bookDir(book: Book): File =
        File(root, "${safeName(book.name, sha1(book.bookUrl))}_${sha1(book.bookUrl)}")

    private fun chapterFileName(chapter: BookChapter, url: String): String =
        "${safeName(chapter.title, sha1(chapter.url))}_${sha1(url)}.${extension(url)}"

    private fun extension(url: String): String =
        Uri.parse(url).lastPathSegment?.substringAfterLast('.', "mp3")
            ?.takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) } ?: "mp3"

    private fun downloadOne(url: String, baseName: String, dir: File = root): File {
        val file = File(dir, baseName)
        if (file.exists() && file.length() > 0) return file
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("empty audio body")
            file.outputStream().use { output ->
                body.byteStream().use { input -> input.copyTo(output, 64 * 1024) }
            }
        }
        return file
    }

    private fun isChapterDownloaded(book: Book, chapter: BookChapter): Boolean {
        val dir = bookDir(book)
        if (!dir.exists()) return false
        val chapterKey = sha1(chapter.url)
        return dir.walkTopDown().any {
            it.isFile && it.name != "playlist.m3u8" && it.name.contains(chapterKey)
        }
    }

    private fun isActiveJob(bookUrl: String): Boolean =
        jobs[bookUrl]?.isActive == true

    private fun onMainProgress(
        callback: (Int, Int, String, Boolean) -> Unit,
        current: Int,
        total: Int,
        title: String,
        success: Boolean
    ) {
        scope.launch(Dispatchers.Main) { callback(current, total, title, success) }
    }

    private fun parseUrls(value: String): List<String> = runCatching {
        val array = JSONArray(value)
        List(array.length()) { array.optString(it) }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
    }.getOrElse {
        listOf(value).filter { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun safeName(value: String, key: String): String =
        value.replace(Regex("[\\/:*?\"<>|]"), "_")
            .trim()
            .take(80)
            .ifBlank { "audio_$key" } + "_$key"

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(12)
}
