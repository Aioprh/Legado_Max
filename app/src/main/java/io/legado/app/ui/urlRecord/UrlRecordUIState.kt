package io.legado.app.ui.urlRecord

import io.legado.app.data.entities.UrlRecord

sealed class UrlRecordUIState {
    object Loading : UrlRecordUIState()
    data class Success(val records: List<UrlRecord>) : UrlRecordUIState()
    data class Error(val message: String) : UrlRecordUIState()
    object Empty : UrlRecordUIState()
}
