package io.legado.app.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Size
import androidx.collection.LruCache
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import io.legado.app.R
import io.legado.app.constant.AppLog.putDebug
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isEpub
import io.legado.app.help.book.isMobi
import io.legado.app.help.book.isPdf
import io.legado.app.help.config.AppConfig
import io.legado.app.model.localBook.EpubFile
import io.legado.app.model.localBook.MobiFile
import io.legado.app.model.localBook.PdfFile
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.SvgUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

object ImageProvider {

    private val errorBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(appCtx.resources, R.drawable.image_loading_error)
    }

    private val gifFileCache = ConcurrentHashMap<String, Boolean>()

    /**
     * 缓存bitmap LruCache实现
     * filePath bitmap
     */
    private const val M = 1024 * 1024
    val cacheSize: Int
        get() {
            if (AppConfig.bitmapCacheSize !in 1..1024) {
                AppConfig.bitmapCacheSize = 50
            }
            return AppConfig.bitmapCacheSize * M
        }

    val bitmapLruCache = BitmapLruCache()

    class BitmapLruCache : LruCache<String, Bitmap>(cacheSize) {

        private var removeCount = 0

        val count get() = putCount() + createCount() - evictionCount() - removeCount

        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (!evicted) {
                synchronized(this) {
                    removeCount++
                }
            }
            //错误图片不能释放,占位用,防止一直重复获取图片
            if (oldValue != errorBitmap) {
                oldValue.recycle()
            }
        }

    }

    fun put(key: String, bitmap: Bitmap) {
        ensureLruCacheSize(bitmap)
        bitmapLruCache.put(key, bitmap)
    }

    fun get(key: String): Bitmap? {
        return bitmapLruCache[key]
    }

    fun remove(key: String): Bitmap? {
        return bitmapLruCache.remove(key)
    }

    private fun getNotRecycled(key: String): Bitmap? {
        val bitmap = bitmapLruCache[key] ?: return null
        if (bitmap.isRecycled) {
            bitmapLruCache.remove(key)
            return null
        }
        return bitmap
    }

    private fun ensureLruCacheSize(bitmap: Bitmap) {
        val lruMaxSize = bitmapLruCache.maxSize()
        val lruSize = bitmapLruCache.size()
        val byteCount = bitmap.byteCount
        val size = if (byteCount > lruMaxSize) {
            min(256 * M, (byteCount * 1.3).toInt())
        } else if (lruSize + byteCount > lruMaxSize && bitmapLruCache.count < 5) {
            min(256 * M, (lruSize + byteCount * 1.3).toInt())
        } else {
            lruMaxSize
        }
        if (size > lruMaxSize) {
            bitmapLruCache.resize(size)
        }
    }

    suspend fun cacheImage(
        book: Book,
        src: String,
        bookSource: BookSource?
    ): File {
        return withContext(IO) {
            val vFile = BookHelp.getImage(book, src)
            if (!BookHelp.isImageExist(book, src)) {
                val inputStream = when {
                    book.isEpub -> EpubFile.getImage(book, src)
                    book.isPdf -> PdfFile.getImage(book, src)
                    book.isMobi -> MobiFile.getImage(book, src)
                    else -> {
                        BookHelp.saveImage(bookSource, book, src)
                        null
                    }
                }
                inputStream?.use { input ->
                    val newFile = FileUtils.createFileIfNotExist(vFile.absolutePath)
                    FileOutputStream(newFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return@withContext vFile
        }
    }

    suspend fun getImageSize(
        book: Book,
        src: String,
        bookSource: BookSource?
    ): Size {
        val bubbleSrc = normalizeBubbleSrc(src)
        if (ParagraphBubbleRenderer.isBubbleSrc(bubbleSrc)) {
            return ParagraphBubbleRenderer.getSize(bubbleSrc)
        }
        inlineSvgSize(src)?.let { return it }
        val file = cacheImage(book, src, bookSource)
        val op = BitmapFactory.Options()
        op.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, op)
        if (op.outWidth < 1 && op.outHeight < 1) {
            val size = SvgUtils.getSize(file.absolutePath)
            if (size != null) return size
            putDebug("ImageProvider: $src Unsupported image type")
            return Size(errorBitmap.width, errorBitmap.height)
        }
        return Size(op.outWidth, op.outHeight)
    }

    suspend fun isGif(
        book: Book,
        src: String,
        bookSource: BookSource?
    ): Boolean {
        val file = cacheImage(book, src, bookSource)
        return isGifFile(file)
    }

    fun isGifFile(file: File): Boolean {
        return gifFileCache.getOrPut(file.absolutePath) {
            if (!file.exists() || file.length() < 6) {
                return@getOrPut false
            }
            kotlin.runCatching {
                FileInputStream(file).use { input ->
                    val header = ByteArray(6)
                    if (input.read(header) != header.size) {
                        false
                    } else {
                        val signature = String(header, Charsets.US_ASCII)
                        signature == "GIF87a" || signature == "GIF89a"
                    }
                }
            }.getOrDefault(false)
        }
    }

    suspend fun getGifDrawable(
        book: Book,
        src: String,
        width: Int,
        height: Int,
        bookSource: BookSource?
    ): GifDrawable? = withContext(IO) {
        val vFile = cacheImage(book, src, bookSource)
        if (!isGifFile(vFile)) {
            return@withContext null
        }
        kotlin.runCatching {
            Glide.with(appCtx)
                .asGif()
                .load(vFile)
                .submit(width.coerceAtLeast(1), height.coerceAtLeast(1))
                .get()
        }.getOrNull()
    }

    fun getImage(
        book: Book,
        src: String,
        width: Int,
        height: Int? = null
    ): Bitmap {
        val bubbleSrc = normalizeBubbleSrc(src)
        if (ParagraphBubbleRenderer.isBubbleSrc(bubbleSrc)) {
            val cacheKey = ParagraphBubbleRenderer.cacheKey(bubbleSrc, width, height)
            getNotRecycled(cacheKey)?.let { return it }
            return kotlin.runCatching {
                ParagraphBubbleRenderer.render(bubbleSrc, width, height)
                    ?: throw NoStackTraceException(appCtx.getString(R.string.error_decode_bitmap))
            }.onSuccess {
                put(cacheKey, it)
            }.onFailure {
                put(cacheKey, errorBitmap)
            }.getOrDefault(errorBitmap)
        }

        // 番茄四合一等书源会直接返回 data:image/svg+xml;base64,...,{options}。
        // options 中的 click 仍由正文图片点击链路读取，这里只负责把内联 SVG 正确绘制出来。
        inlineSvgBitmap(src, width, height)?.let { bitmap ->
            val cacheKey = "inline-svg:$src#$width#$height"
            getNotRecycled(cacheKey)?.let { return it }
            put(cacheKey, bitmap)
            return bitmap
        }

        if (book.getUseReplaceRule() && src.isBlank()) {
            book.setUseReplaceRule(false)
            appCtx.toastOnUi(R.string.error_image_url_empty)
        }
        val vFile = BookHelp.getImage(book, src)
        if (!vFile.exists()) return errorBitmap
        val cacheBitmap = getNotRecycled(vFile.absolutePath)
        if (cacheBitmap != null) return cacheBitmap
        return kotlin.runCatching {
            val bitmap = BitmapUtils.decodeBitmap(vFile.absolutePath, width, height)
                ?: SvgUtils.createBitmap(vFile.absolutePath, width, height)
                ?: throw NoStackTraceException(appCtx.getString(R.string.error_decode_bitmap))
            put(vFile.absolutePath, bitmap)
            bitmap
        }.onFailure {
            put(vFile.absolutePath, errorBitmap)
        }.getOrDefault(errorBitmap)
    }

    /**
     * 将 dp: 段评气泡协议，以及番茄四合一的 fqWrapper 内联气泡，统一归一化为
     * bubble://paragraph。正文中的原始 data URI 不会被替换，因此其中携带的
     * click JSON 仍可由 ReadBookActivity 的图片点击链路读取。
     */
    private fun normalizeBubbleSrc(src: String): String {
        if (src.startsWith("dp:", ignoreCase = true)) {
            val payload = src.substring(3).trim()
            val optionIndex = payload.indexOf(",{")
            val count = if (optionIndex >= 0) payload.substring(0, optionIndex) else payload
            val option = if (optionIndex >= 0) {
                GSON.fromJsonObject<Map<String, String>>(payload.substring(optionIndex + 1))
                    .getOrNull().orEmpty()
            } else emptyMap()
            return bubbleUrl(
                option["displayText"]?.takeIf { it.isNotBlank() } ?: count.trim(),
                option["status"]?.takeIf { it.isNotBlank() } ?: "normal",
                option["displayColor"]
            )
        }

        val inline = decodeInlineSvg(src) ?: return src
        val options = inline.options
        val marker = options["marker"].orEmpty()
        if (!marker.startsWith("fqWrapper:", ignoreCase = true)) return src
        val count = Regex("<text\\b[^>]*>\\s*([^<]+?)\\s*</text>", RegexOption.IGNORE_CASE)
            .find(inline.svg)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return src
        return bubbleUrl(
            count,
            options["status"]?.takeIf { it.isNotBlank() } ?: "normal",
            options["displayColor"]
        )
    }

    private fun bubbleUrl(text: String, status: String, color: String?): String {
        val colorQuery = color?.takeIf { it.isNotBlank() }?.let { "&displayColor=${Uri.encode(it)}" }.orEmpty()
        return buildString {
            append("bubble://paragraph")
            append("?displayText=").append(Uri.encode(text))
            append("&num=").append(Uri.encode(text))
            append("&status=").append(Uri.encode(status))
            append(colorQuery)
        }
    }

    private data class InlineSvg(
        val svg: String,
        val options: Map<String, String>
    )

    private fun decodeInlineSvg(src: String): InlineSvg? {
        val prefix = "data:image/svg+xml;base64,"
        if (!src.startsWith(prefix, ignoreCase = true)) return null
        val payload = src.substring(prefix.length)
        val separator = payload.indexOf(',')
        val encoded = if (separator >= 0) payload.substring(0, separator) else payload
        if (encoded.isBlank()) return null
        return kotlin.runCatching {
            val svg = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
            val options = if (separator >= 0) {
                GSON.fromJsonObject<Map<String, String>>(payload.substring(separator + 1))
                    .getOrNull().orEmpty()
            } else emptyMap()
            InlineSvg(svg, options)
        }.getOrNull()
    }

    private fun inlineSvgSize(src: String): Size? {
        val inline = decodeInlineSvg(src) ?: return null
        val width = Regex("\\bwidth\\s*=\\s*[\\\"']([0-9.]+)", RegexOption.IGNORE_CASE)
            .find(inline.svg)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        val height = Regex("\\bheight\\s*=\\s*[\\\"']([0-9.]+)", RegexOption.IGNORE_CASE)
            .find(inline.svg)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        if (width != null && height != null) {
            return Size(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1))
        }
        val viewBox = Regex("\\bviewBox\\s*=\\s*[\\\"']\\s*[-0-9.]+\\s+[-0-9.]+\\s+([0-9.]+)\\s+([0-9.]+)", RegexOption.IGNORE_CASE)
            .find(inline.svg)
        if (viewBox != null) {
            val w = viewBox.groupValues[1].toFloatOrNull()
            val h = viewBox.groupValues[2].toFloatOrNull()
            if (w != null && h != null) return Size(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
        }
        return null
    }

    private fun inlineSvgBitmap(src: String, width: Int, height: Int?): Bitmap? {
        val inline = decodeInlineSvg(src) ?: return null
        return kotlin.runCatching {
            SvgUtils.createBitmap(
                ByteArrayInputStream(inline.svg.toByteArray(Charsets.UTF_8)),
                width.coerceAtLeast(1),
                height
            )
        }.getOrNull()
    }

    fun clear() {
        bitmapLruCache.evictAll()
    }

}
