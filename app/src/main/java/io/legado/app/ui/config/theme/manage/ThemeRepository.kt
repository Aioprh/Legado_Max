package io.legado.app.ui.config.theme.manage

import io.legado.app.help.config.ThemeConfig

/**
 * 主题数据源抽象层
 * 
 * 为什么抽象出 Repository：
 * 隔离上层 ViewModel 与底层 ThemeConfig (基于静态单例和磁盘 IO) 的强耦合。
 * 如果未来需要接入网络同步或者换用 Room 数据库，直接替换这里的实现即可，
 * 避免 ViewModel 层因为数据源变更而伤筋动骨。
 */
interface ThemeRepository {
    fun getThemes(): List<ThemeConfig.Config>
    fun saveTheme(config: ThemeConfig.Config, index: Int)
    fun applyTheme(config: ThemeConfig.Config)
    fun deleteConfig(index: Int)
    fun toTopConfigs(positions: List<Int>)
    fun addConfig(json: String): Int
    fun getDurConfig(): ThemeConfig.Config
}

/**
 * 主题数据源默认实现
 * 
 * 为什么这样写：
 * 代理并收拢对全局单例 ThemeConfig 的所有直接访问。
 * 边界条件与风险警告：ThemeConfig 内部的方法大量涉及同步的磁盘读写，
 * 因此 ViewModel 调用这些方法时，务必保证协程上下文已切换到 IO 线程，严禁在主线程直接调用！
 */
class ThemeRepositoryImpl(
    private val context: android.content.Context
) : ThemeRepository {
    override fun getThemes(): List<ThemeConfig.Config> = ThemeConfig.configList.toList()

    override fun saveTheme(config: ThemeConfig.Config, index: Int) {
        if (index >= 0) {
            ThemeConfig.configList[index] = config
        } else {
            ThemeConfig.configList.add(config)
        }
        ThemeConfig.save()
    }

    override fun applyTheme(config: ThemeConfig.Config) {
        ThemeConfig.applyConfig(context, config)
    }

    override fun deleteConfig(index: Int) {
        ThemeConfig.delConfig(index)
    }

    override fun toTopConfigs(positions: List<Int>) {
        ThemeConfig.toTopConfigs(positions)
    }

    override fun addConfig(json: String): Int {
        return ThemeConfig.addConfig(json)
    }

    override fun getDurConfig(): ThemeConfig.Config {
        return ThemeConfig.getDurConfig(context)
    }
}
