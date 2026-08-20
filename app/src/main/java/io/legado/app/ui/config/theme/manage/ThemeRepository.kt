package io.legado.app.ui.config.theme.manage

import android.content.Context
import io.legado.app.help.config.ThemeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 主题数据源抽象。IO 和全局可变列表的并发控制统一收口在这里。 */
interface ThemeRepository {
    suspend fun getThemes(): List<ThemeConfig.Config>
    suspend fun saveTheme(config: ThemeConfig.Config, original: ThemeConfig.Config?)
    fun applyTheme(config: ThemeConfig.Config)
    suspend fun deleteConfig(config: ThemeConfig.Config)
    suspend fun toTopConfigs(configs: List<ThemeConfig.Config>)
    suspend fun addConfig(json: String): Int
    suspend fun getDurConfig(): ThemeConfig.Config
}

/**
 * 主题配置的默认数据源实现。
 *
 * 通过 IO 调度和写锁隔离 ThemeConfig 的磁盘访问与全局可变列表，避免管理页面直接承担并发控制。
 */
class ThemeRepositoryImpl(context: Context) : ThemeRepository {
    private val appContext = context.applicationContext
    private val writeMutex = Mutex()

    override suspend fun getThemes(): List<ThemeConfig.Config> = withContext(Dispatchers.IO) {
        ThemeConfig.configList.map { it.copy() }
    }

    override suspend fun saveTheme(config: ThemeConfig.Config, original: ThemeConfig.Config?) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val index = original?.let { ThemeConfig.configList.indexOf(it) } ?: -1
                if (index >= 0) ThemeConfig.configList[index] = config else ThemeConfig.configList.add(config)
                ThemeConfig.save()
            }
        }
    }

    // applyConfig() 更新全局主题并触发夜间模式切换，必须在主线程完成。
    override fun applyTheme(config: ThemeConfig.Config) {
        ThemeConfig.applyConfig(appContext, config)
    }

    override suspend fun deleteConfig(config: ThemeConfig.Config) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                ThemeConfig.configList.indexOf(config).takeIf { it >= 0 }?.let { index ->
                    ThemeConfig.delConfig(index)
                }
            }
        }
    }

    override suspend fun toTopConfigs(configs: List<ThemeConfig.Config>) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val positions = configs.mapNotNull { config ->
                    ThemeConfig.configList.indexOf(config).takeIf { it >= 0 }
                }.distinct()
                if (positions.isNotEmpty()) ThemeConfig.toTopConfigs(positions.sorted())
            }
        }
    }

    override suspend fun addConfig(json: String): Int = writeMutex.withLock {
        withContext(Dispatchers.IO) { ThemeConfig.addConfig(json) }
    }

    override suspend fun getDurConfig(): ThemeConfig.Config = withContext(Dispatchers.IO) {
        ThemeConfig.getDurConfig(appContext).copy()
    }
}
