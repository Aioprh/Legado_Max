package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "url_records", indices = [Index(value = ["timestamp"]), Index(value = ["domain"])])
data class UrlRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val domain: String,
    val method: String,
    val sourceName: String? = null,
    val sourceUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val responseCode: Int = 0,
    val duration: Long = 0,
    val requestBody: String? = null,
    val errorMsg: String? = null
)
