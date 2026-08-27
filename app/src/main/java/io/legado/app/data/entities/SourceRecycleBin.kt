package io.legado.app.data.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// 无可变集合字段，标注 @Immutable 让 Compose 跳过重组判断（纯编译期/运行时提示，不影响 Room）
@Immutable
@Entity(
    tableName = "source_recycle_bin",
    indices = [
        Index(value = ["type"]),
        Index(value = ["key"]),
        Index(value = ["expireAt"])
    ]
)
data class SourceRecycleBin(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var type: String = "",
    var key: String = "",
    var name: String = "",
    var groupName: String? = null,
    var payload: String = "",
    var deletedAt: Long = 0,
    var expireAt: Long = 0
)

