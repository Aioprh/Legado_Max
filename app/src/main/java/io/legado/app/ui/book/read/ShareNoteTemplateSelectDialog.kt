package io.legado.app.ui.book.read

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.config.ShareNoteTemplateManager
import io.legado.app.ui.config.ShareNoteTemplatePreview
import io.legado.app.ui.widget.components.dialog.BaseComposeDialogFragment
import io.legado.app.utils.dpToPx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 摘录分享模板选择对话框。
 *
 * 列出所有可用模板（内置 + 本地），并渲染每个模板的头部预览图，
 * 点击选择后回调，也可跳转到模板管理页。
 */
class ShareNoteTemplateSelectDialog : BaseComposeDialogFragment() {

    private var onSelected: ((ShareNoteTemplateManager.Entry) -> Unit)? = null
    private var onManage: (() -> Unit)? = null

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.setGravity(Gravity.BOTTOM)
            window.attributes = window.attributes.apply {
                height = (resources.displayMetrics.heightPixels * 0.72f).toInt().coerceAtLeast(480.dpToPx())
            }
        }
    }

    @Composable
    override fun DialogContent() {
        var entries by remember { mutableStateOf<List<ShareNoteTemplateManager.Entry>>(emptyList()) }
        var previews by remember { mutableStateOf<Map<String, File>>(emptyMap()) }
        LaunchedEffect(Unit) {
            val loaded = try {
                withContext(Dispatchers.IO) { ShareNoteTemplateManager.loadEntries() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.put("Share note template select load failed\n${e.localizedMessage}", e)
                return@LaunchedEffect
            }
            val last = ShareNoteTemplateManager.lastDirName()
            entries = loaded.sortedBy { if (it.dirName == last) 0 else 1 }
            loaded.forEach { entry ->
                val file = try {
                    ShareNoteImageRenderer.renderPreview(requireContext().applicationContext, entry)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.put(
                        "Share note template select preview failed: ${entry.dirName}\n${e.localizedMessage}",
                        e
                    )
                    null
                }
                file?.let {
                    previews = previews + (entry.dirName to it)
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "选择分享模板",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(0.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(entries, key = { it.dirName }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    dismissAllowingStateLoss()
                                    ShareNoteTemplateManager.rememberLast(entry)
                                    onSelected?.invoke(entry)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ShareNoteTemplatePreview(
                                previewFile = previews[entry.dirName],
                                modifier = Modifier
                                    .width(60.dp)
                                    .heightIn(min = 84.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.meta.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${entry.meta.canvasLabel()} · ${entry.meta.sizeLabel()}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        dismissAllowingStateLoss()
                        onManage?.invoke()
                    }) {
                        Text("管理模板")
                    }
                    TextButton(onClick = { dismissAllowingStateLoss() }) {
                        Text(getString(R.string.cancel))
                    }
                }
            }
        }
    }

    companion object {
        fun create(
            onSelected: (ShareNoteTemplateManager.Entry) -> Unit,
            onManage: () -> Unit
        ): ShareNoteTemplateSelectDialog {
            return ShareNoteTemplateSelectDialog().apply {
                this.onSelected = onSelected
                this.onManage = onManage
            }
        }
    }
}
