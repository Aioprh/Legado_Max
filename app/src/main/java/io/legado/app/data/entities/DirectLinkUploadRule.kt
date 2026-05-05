package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "direct_link_upload_rules",
    indices = [
        Index(value = ["sortOrder"]),
        Index(value = ["isDefault"])
    ]
)
data class DirectLinkUploadRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val uploadUrl: String,
    val downloadUrlRule: String,
    val summary: String,
    val compress: Boolean = false,
    
    val customHeaders: String? = null,
    val timeout: Long = 30000,
    val retryCount: Int = 3,
    
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val uploadCount: Int = 0,
    val lastUsedTime: Long = 0,
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
) {
    override fun toString(): String = summary
}
