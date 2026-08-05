package io.legado.app.ui.config.coverhtml

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

/**
 * 封面HTML模板管理Activity
 * 
 * 提供封面HTML模板列表展示和代码编辑功能
 * 包含两种模式：模板列表模式(MODE_TEMPLATE_LIST)和编辑模板模式(MODE_EDIT_TEMPLATE)
 * 
 * @property mode 当前显示模式
 * @property templateId 当前编辑的模板ID
 * @property isNew 是否为新建模板
 */
class CoverHtmlActivity : BaseComposeActivity() {

    /** 当前显示模式 */
    private var mode: Int = MODE_TEMPLATE_LIST
    /** 当前编辑的模板ID */
    private var templateId: String? = null
    /** 是否为新建模板 */
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
        //region Intent Extra Keys
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_TEMPLATE_ID = "templateId"
        private const val EXTRA_IS_NEW = "isNew"
        //endregion

        //region Mode Constants
        /** 模板列表模式 */
        const val MODE_TEMPLATE_LIST = 0
        /** 编辑模板模式 */
        const val MODE_EDIT_TEMPLATE = 1
        //endregion

        //region 启动方法

        /**
         * 启动封面HTML模板列表页面
         * 
         * @param context 上下文
         */
        fun startTemplateList(context: Context) {
            val intent = Intent(context, CoverHtmlActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_TEMPLATE_LIST)
            }
            context.startActivity(intent)
        }

        /**
         * 启动封面HTML模板编辑页面
         * 
         * @param context 上下文
         * @param templateId 模板ID，为空则使用当前选中的模板
         * @param isNew 是否为新建模板
         */
        fun startEditTemplate(context: Context, templateId: String? = null, isNew: Boolean = false) {
            val intent = Intent(context, CoverHtmlActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_EDIT_TEMPLATE)
                putExtra(EXTRA_TEMPLATE_ID, templateId)
                putExtra(EXTRA_IS_NEW, isNew)
            }
            context.startActivity(intent)
        }
        //endregion
    }
}

/**
 * 封面HTML内容组件
 * 
 * 根据当前模式显示模板列表或模板编辑器
 * 管理模式切换状态
 * 
 * @param mode 初始显示模式
 * @param templateId 初始模板ID
 * @param isNew 是否为新建模板
 * @param onBackClick 返回点击回调
 */
@Composable
fun CoverHtmlContent(
    mode: Int,
    templateId: String?,
    isNew: Boolean,
    onBackClick: () -> Unit
) {
    //region 状态管理
    var currentMode by remember { mutableStateOf(mode) }
    var currentTemplateId by remember { mutableStateOf(templateId) }
    var currentIsNew by remember { mutableStateOf(isNew) }
    //endregion

    when (currentMode) {
        CoverHtmlActivity.MODE_TEMPLATE_LIST -> {
            //region 模板列表模式
            CoverHtmlTemplateListScreen(
                onBackClick = onBackClick,
                onEditTemplate = { template ->
                    if (template == null) {
                        // 新建模板
                        currentMode = CoverHtmlActivity.MODE_EDIT_TEMPLATE
                        currentIsNew = true
                        currentTemplateId = null
                    } else {
                        // 编辑已有模板
                        currentMode = CoverHtmlActivity.MODE_EDIT_TEMPLATE
                        currentIsNew = false
                        currentTemplateId = template.id
                    }
                }
            )
            //endregion
        }

        CoverHtmlActivity.MODE_EDIT_TEMPLATE -> {
            //region 编辑模板模式
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
            //endregion
        }
    }
}
