package io.legado.app.ui.config.theme.manage

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

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