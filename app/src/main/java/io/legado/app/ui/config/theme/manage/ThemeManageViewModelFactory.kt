package io.legado.app.ui.config.theme.manage

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * 主题管理 ViewModel 的创建工厂。
 *
 * 在未引入依赖注入框架的现有入口中集中组装 Repository，保证 Activity 不直接持有数据源实现细节。
 */
class ThemeManageViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ThemeManageViewModel::class.java)) {
            val repository = ThemeRepositoryImpl(application)
            @Suppress("UNCHECKED_CAST")
            return ThemeManageViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}