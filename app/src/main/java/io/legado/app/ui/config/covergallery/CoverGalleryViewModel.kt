package io.legado.app.ui.config.covergallery

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.CoverGalleryGroupWithImages
import io.legado.app.data.repository.CoverGalleryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CoverGalleryViewModel : ViewModel() {

    private val repository = CoverGalleryRepository()
    private val searchQuery = MutableStateFlow("")

    val groups: StateFlow<List<CoverGalleryGroupWithImages>> = searchQuery
        .flatMapLatest { repository.flowGroupsWithImages(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun addGroup(name: String) {
        val realName = name.trim()
        if (realName.isBlank()) return
        viewModelScope.launch {
            repository.addGroup(realName)
        }
    }

    fun renameGroup(groupId: Long, name: String) {
        val realName = name.trim()
        if (realName.isBlank()) return
        viewModelScope.launch {
            repository.renameGroup(groupId, realName)
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
        }
    }

    fun addImage(context: Context, groupId: Long, uri: Uri) {
        viewModelScope.launch {
            repository.addImage(context.applicationContext, groupId, uri)
        }
    }

    fun deleteImage(imageId: Long) {
        viewModelScope.launch {
            repository.deleteImage(imageId)
        }
    }

    fun setDefaultGroup(groupId: Long) {
        viewModelScope.launch {
            repository.setDefaultGroup(groupId)
        }
    }

    fun unsetDefaultGroup(groupId: Long) {
        viewModelScope.launch {
            repository.unsetDefaultGroup(groupId)
        }
    }
}
