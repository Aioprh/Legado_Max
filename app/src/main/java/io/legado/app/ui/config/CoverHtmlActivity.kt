package io.legado.app.ui.config

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.legado.app.base.BaseComposeActivity
import io.legado.app.help.config.CoverHtmlTemplateConfig

class CoverHtmlActivity : BaseComposeActivity() {

    private var mode: Int = MODE_TEMPLATE_LIST
    private var templateId: String? = null
    private var isNew: Boolean = false

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        mode = intent.getIntExtra(EXTRA_MODE, MODE_TEMPLATE_LIST)
        templateId = intent.getStringExtra(EXTRA_TEMPLATE_ID)
        isNew = intent.getBooleanExtra(EXTRA_IS_NEW, false)
    }

    @Composable
    override fun ComposeContent() {
        CoverHtmlContent(
            mode = mode,
            templateId = templateId,
            isNew = isNew,
            onBackClick = { finish() }
        )
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_TEMPLATE_ID = "templateId"
        private const val EXTRA_IS_NEW = "isNew"

        const val MODE_TEMPLATE_LIST = 0
        const val MODE_EDIT_TEMPLATE = 1

        fun startTemplateList(context: Context) {
            val intent = Intent(context, CoverHtmlActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_TEMPLATE_LIST)
            }
            context.startActivity(intent)
        }

        fun startEditTemplate(context: Context, templateId: String? = null, isNew: Boolean = false) {
            val intent = Intent(context, CoverHtmlActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_EDIT_TEMPLATE)
                putExtra(EXTRA_TEMPLATE_ID, templateId)
                putExtra(EXTRA_IS_NEW, isNew)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun CoverHtmlContent(
    mode: Int,
    templateId: String?,
    isNew: Boolean,
    onBackClick: () -> Unit
) {
    var currentMode by remember { mutableStateOf(mode) }
    var currentTemplateId by remember { mutableStateOf(templateId) }
    var currentIsNew by remember { mutableStateOf(isNew) }

    when (currentMode) {
        CoverHtmlActivity.MODE_TEMPLATE_LIST -> {
            CoverHtmlTemplateListScreen(
                onBackClick = onBackClick,
                onEditTemplate = { template ->
                    if (template == null) {
                        currentMode = CoverHtmlActivity.MODE_EDIT_TEMPLATE
                        currentIsNew = true
                        currentTemplateId = null
                    } else {
                        currentMode = CoverHtmlActivity.MODE_EDIT_TEMPLATE
                        currentIsNew = false
                        currentTemplateId = template.id
                    }
                }
            )
        }

        CoverHtmlActivity.MODE_EDIT_TEMPLATE -> {
            val template = currentTemplateId?.let {
                CoverHtmlTemplateConfig.getTemplateById(it)
            }
            CoverHtmlCodeScreen(
                template = template,
                isNewTemplate = currentIsNew,
                onBackClick = {
                    currentMode = CoverHtmlActivity.MODE_TEMPLATE_LIST
                },
                onShowTemplateList = {
                    currentMode = CoverHtmlActivity.MODE_TEMPLATE_LIST
                }
            )
        }
    }
}
