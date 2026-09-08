package io.legado.app.model

import android.content.Context
import android.content.SharedPreferences
import io.legado.app.data.entities.Book
import org.json.JSONArray
import org.json.JSONObject
import splitties.init.appCtx

/** Persistent session/queue state for Max Audio System. */
object MaxAudioSession {
    private const val PREF = "max_audio_session"
    private const val KEY_QUEUE = "queue"
    private const val KEY_INDEX = "index"
    private const val KEY_POSITION = "position"
    private const val KEY_SPEED = "speed"
    private const val KEY_MODE = "mode"
    private const val KEY_BOOK = "book"

    private val prefs: SharedPreferences by lazy {
        appCtx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    data class SavedState(
        val queue: List<MaxAudioSystem.QueueItem>,
        val currentIndex: Int,
        val position: Int,
        val speed: Float,
        val playMode: AudioPlay.PlayMode,
        val currentBookUrl: String?
    )

    fun save(
        queue: List<MaxAudioSystem.QueueItem>,
        currentIndex: Int,
        position: Int,
        speed: Float,
        playMode: AudioPlay.PlayMode,
        book: Book?
    ) {
        val array = JSONArray()
        queue.forEach { item ->
            array.put(JSONObject().apply {
                put("url", item.bookUrl)
                put("title", item.title)
                put("author", item.author)
            })
        }
        prefs.edit()
            .putString(KEY_QUEUE, array.toString())
            .putInt(KEY_INDEX, currentIndex)
            .putInt(KEY_POSITION, position)
            .putFloat(KEY_SPEED, speed)
            .putInt(KEY_MODE, playMode.ordinal)
            .putString(KEY_BOOK, book?.bookUrl)
            .apply()
    }

    fun restore(): SavedState {
        val result = ArrayList<MaxAudioSystem.QueueItem>()
        runCatching {
            val array = JSONArray(prefs.getString(KEY_QUEUE, "[]"))
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = item.optString("url")
                if (url.isNotBlank()) result += MaxAudioSystem.QueueItem(
                    url,
                    item.optString("title"),
                    item.optString("author")
                )
            }
        }
        val mode = AudioPlay.PlayMode.entries.getOrNull(prefs.getInt(KEY_MODE, 0))
            ?: AudioPlay.PlayMode.LIST_END_STOP
        return SavedState(
            queue = result,
            currentIndex = prefs.getInt(KEY_INDEX, -1),
            position = prefs.getInt(KEY_POSITION, 0),
            speed = prefs.getFloat(KEY_SPEED, 1f),
            playMode = mode,
            currentBookUrl = prefs.getString(KEY_BOOK, null)
        )
    }

    fun clear() = prefs.edit().clear().apply()
}
