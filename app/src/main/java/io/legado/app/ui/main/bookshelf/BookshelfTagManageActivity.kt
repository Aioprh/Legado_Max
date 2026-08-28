package io.legado.app.ui.main.bookshelf

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityBookshelfTagManageBinding
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 书架标签管理宿主 Activity。
 *
 * 传入 `groupId`（LongExtra）指定要聚焦的分组。
 */
class BookshelfTagManageActivity : BaseActivity<ActivityBookshelfTagManageBinding>() {

    override val binding by viewBinding(ActivityBookshelfTagManageBinding::inflate)
    private val viewModel: BookshelfTagManageViewModel by viewModels()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val focusGroupId = intent.getLongExtra("groupId", -1L)
        viewModel.setFocusGroupId(focusGroupId)
        binding.composeRoot.setContent {
            LegadoTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                BookshelfTagManageScreen(
                    state = uiState,
                    callbacks = BookshelfTagManageCallbacks(
                        onBack = ::finish,
                        onShowAddTagDialog = viewModel::showAddTagDialog,
                        onAddTags = viewModel::addTags,
                        onTagVisibilityChange = viewModel::setTagVisible,
                        onManageBooks = viewModel::startManageBooks,
                        onRequestDelete = viewModel::confirmDeleteTag,
                        onConfirmDelete = viewModel::executeDeleteTag,
                        onDismissDialog = viewModel::dismissDialog,
                        onSaveAssignment = viewModel::saveAssignment,
                        onRequestRename = viewModel::confirmRenameTag,
                        onRenameTag = viewModel::executeRenameTag,
                        onReorderTags = viewModel::reorderTags
                    )
                )
            }
        }
    }
}
