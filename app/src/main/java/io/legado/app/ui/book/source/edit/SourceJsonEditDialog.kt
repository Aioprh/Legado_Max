package io.legado.app.ui.book.source.edit

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.ui.widget.dialog.BaseContentEditDialog

class SourceJsonEditDialog(
    private val sourceJson: String,
    private val onSave: (String) -> Unit
) : BaseContentEditDialog() {

    private var formattedJson: String = ""

    override fun getTitle(): CharSequence = getString(R.string.edit_content)

    override fun getSourceType(): String = "bookSourceJson"

    override fun setupContentView() {
        binding.contentView.addLegadoPattern()
        binding.contentView.addJsonPattern()
    }

    override fun onContentReady() {
        loadJson()
    }

    override fun onSave(content: String): Boolean {
        return try {
            JsonParser.parseString(content)
            onSave(content)
            true
        } catch (e: Exception) {
            alert(R.string.error) {
                setMessage("${getString(R.string.json_format)}\n${e.message}")
                positiveButton(R.string.confirm)
            }
            false
        }
    }

    override fun onReset() {
        loadJson()
    }

    private fun loadJson() {
        formattedJson = try {
            val jsonElement = JsonParser.parseString(sourceJson)
            Gson().toJson(jsonElement)
        } catch (e: Exception) {
            sourceJson
        }
        binding.contentView.setText(formattedJson)
        originalContent = null
    }
}
