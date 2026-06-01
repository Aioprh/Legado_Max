package io.legado.app.ui.module

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModuleStatusViewModel : ViewModel() {

    private val _modules = MutableStateFlow(ModuleStatusProvider.snapshot())
    val modules: StateFlow<List<ModuleStatusItem>> = _modules.asStateFlow()

    fun refresh() {
        _modules.value = ModuleStatusProvider.snapshot()
    }
}
