package io.legado.app.ui.source.recycle

import io.legado.app.data.appDb
import io.legado.app.data.entities.SourceRecycleBin
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.SourceRecycleBinHelp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 回收站数据源抽象（testing.md §16：DAO/Help 依赖收敛为构造注入以便 Fake）
 * Default 实现封装 appDb / SourceRecycleBinHelp / AppConfig 静态依赖，
 * 阻塞操作统一切 IO 调度器，ViewModel 不再关心线程切换
 */
interface RecycleBinRepository {

    /** 回收站功能开关（持久化到偏好） */
    var enabled: Boolean

    fun flowAll(): Flow<List<SourceRecycleBin>>

    fun flowByType(type: String): Flow<List<SourceRecycleBin>>

    suspend fun cleanupExpired()

    suspend fun hasConflict(item: SourceRecycleBin): Boolean

    suspend fun restore(item: SourceRecycleBin, overwrite: Boolean)

    suspend fun delete(items: List<SourceRecycleBin>)

    suspend fun deleteAll()

    companion object Default : RecycleBinRepository {

        override var enabled: Boolean
            get() = AppConfig.sourceRecycleBinEnabled
            set(value) {
                AppConfig.sourceRecycleBinEnabled = value
            }

        override fun flowAll(): Flow<List<SourceRecycleBin>> =
            appDb.sourceRecycleBinDao.flowAll()

        override fun flowByType(type: String): Flow<List<SourceRecycleBin>> =
            appDb.sourceRecycleBinDao.flowByType(type)

        override suspend fun cleanupExpired() = withContext(Dispatchers.IO) {
            SourceRecycleBinHelp.cleanupExpired()
        }

        override suspend fun hasConflict(item: SourceRecycleBin): Boolean =
            withContext(Dispatchers.IO) {
                SourceRecycleBinHelp.hasConflict(item)
            }

        override suspend fun restore(item: SourceRecycleBin, overwrite: Boolean) =
            withContext(Dispatchers.IO) {
                SourceRecycleBinHelp.restore(item, overwrite)
            }

        override suspend fun delete(items: List<SourceRecycleBin>) = withContext(Dispatchers.IO) {
            appDb.sourceRecycleBinDao.delete(*items.toTypedArray())
        }

        override suspend fun deleteAll() = withContext(Dispatchers.IO) {
            appDb.sourceRecycleBinDao.deleteAll()
        }
    }
}
