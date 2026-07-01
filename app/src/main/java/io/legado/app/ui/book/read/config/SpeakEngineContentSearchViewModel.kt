package io.legado.app.ui.book.read.config

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.writeToOutputStream
import java.io.File

class SpeakEngineContentSearchViewModel(application: Application) : BaseViewModel(application) {

    fun loadEngines(callback: (List<HttpTTS>) -> Unit) {
        execute {
            appDb.httpTTSDao.all
        }.onSuccess {
            callback(it ?: emptyList())
        }.onError {
            callback(emptyList())
        }
    }

    fun exportEngines(engineIds: List<Long>, success: (File) -> Unit) {
        execute {
            val engines = appDb.httpTTSDao.all.filter { it.id in engineIds }
            val path = "${context.filesDir}/shareHttpTts.json"
            FileUtils.delete(path)
            val file = FileUtils.createFileWithReplace(path)
            file.outputStream().buffered().use { out ->
                GSON.writeToOutputStream(out, engines)
            }
            file
        }.onSuccess {
            if (it != null) success(it)
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }
}
