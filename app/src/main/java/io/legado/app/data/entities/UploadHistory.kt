package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "upload_histories",
    indices = [
        Index(value = ["uploadTime"]),
        Index(value = ["ruleId"]),
        Index(value = ["success"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = DirectLinkUploadRule::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UploadHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val fileName: String,
    val fileSize: Long,
    val contentType: String,
    
    val uploadTime: Long = System.currentTimeMillis(),
    val duration: Long,
    val downloadUrl: String,
    val expireTime: Long? = null,
    
    val ruleId: Long,
    val ruleSummary: String,
    
    val success: Boolean,
    val errorMsg: String? = null
)
