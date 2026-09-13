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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
import splitties.init.appCtx
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Persistent audio downloader, separate from ExoPlayer's transient cache. */
object AudioDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val batchJobs = ConcurrentHashMap<String, Job>()
    private val root: File by lazy { File(appCtx.getExternalFilesDir("Music"), "LegadoAudio").apply { mkdirs() } }

    data class DownloadedAudio(val file: File, val title: String) {
        val size: Long get() = file.length()
    }

    fun download(context: Context, url: String, title: String, onResult: (Boolean, File?) -> Unit = { _, _ -> }) {
        val urls = parseUrls(url)
        if (urls.isEmpty()) { onResult(false, null); return }
        val key = sha1(url)
        if (jobs.containsKey(key)) { onResult(false, null); return }
        val job = scope.launch {
            try {
                val target = if (urls.size == 1) {
                    downloadOne(urls[0], safeName(title, key))
                } else {
                    val dir = File(root, safeName(title, key)).apply { mkdirs() }
                    val files = urls.mapIndexed { index, item -> downloadOne(item, "%03d".format(index + 1), dir) }
                    File(dir, "playlist.m3u8").apply {
                        writeText(buildString {
                            append("#EXTM3U\n")
                            files.forEach { file -> append("#EXTINF:-1,\n"); append(file.absolutePath); append('\n') }
                        })
                    }
                    dir
                }
                launch(Dispatchers.Main) { onResult(true, target) }
            } catch (_: Exception) {
                launch(Dispatchers.Main) { onResult(false, null) }
            } finally { jobs.remove(key) }
        }
        jobs[key] = job
    }

    /** Sequentially resolves and downloads chapters, avoiding a burst of source requests. */
    fun downloadChapters(
        context: Context,
        book: Book,
        source: BookSource,
        chapters: List<BookChapter>,
        onProgress: (current: Int, total: Int, title: String, success: Boolean) -> Unit = { _, _, _, _ -> },
        onFinished: () -> Unit = {}
    ) {
        if (chapters.isEmpty()) { onFinished(); return }
        val key = "chapters:${book.bookUrl}"
        // 取消上一次未完成的批量任务，避免重复排队
        batchJobs.remove(key)?.cancel()
        val job = scope.launch {
            fun next(position: Int) {
                if (!isActive) { onFinished(); return }
                if (position >= chapters.size) { onFinished(); return }
                val chapter = chapters[position]
                WebBook.getContent(this, source, book, chapter)
                    .onSuccess { content ->
                        val value = content.trim()
                        if (value.isBlank()) {
                            onProgress(position + 1, chapters.size, chapter.title, false)
                            next(position + 1)
                        } else {
                            download(context, value, "${book.name}_${chapter.title}") { ok, _ ->
                                onProgress(position + 1, chapters.size, chapter.title, ok)
                                next(position + 1)
                            }
                        }
                    }
                    .onError {
                        onProgress(position + 1, chapters.size, chapter.title, false)
                        next(position + 1)
                    }
            }
            next(0)
        }
        batchJobs[key] = job
    }

    /** 取消指定书籍的批量下载任务 */
    fun cancelDownloading(bookUrl: String) {
        batchJobs.remove("chapters:$bookUrl")?.cancel()
    }

    /** 取消所有批量下载任务 */
    fun cancelAllDownloading() {
        batchJobs.keys.toList().forEach { batchJobs.remove(it)?.cancel() }
    }

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

    private fun downloadOne(url: String, baseName: String, dir: File = root): File {
        val uri = Uri.parse(url)
        val ext = uri.lastPathSegment?.substringAfterLast('.', "mp3")?.takeIf { it.length in 2..5 } ?: "mp3"
        val file = File(dir, "$baseName.$ext")
        if (file.exists() && file.length() > 0) return file
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("empty audio body")
            file.outputStream().use { output -> body.byteStream().use { input -> input.copyTo(output, 64 * 1024) } }
        }
        return file
    }

    private fun parseUrls(value: String): List<String> = runCatching {
        val array = JSONArray(value)
        List(array.length()) { array.optString(it) }.filter { it.startsWith("http://") || it.startsWith("https://") }
    }.getOrElse { listOf(value).filter { it.startsWith("http://") || it.startsWith("https://") } }

    private fun safeName(value: String, key: String): String = value.replace(Regex("[\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "audio_$key" } + "_$key"
    private fun sha1(value: String): String = MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
}
