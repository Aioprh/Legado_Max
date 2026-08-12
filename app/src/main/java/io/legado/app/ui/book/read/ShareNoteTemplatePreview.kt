package io.legado.app.ui.book.read

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import java.io.File

/**
 * 摘录分享模板头部预览图。
 *
 * 预览文件由 [ShareNoteImageRenderer.renderPreview] 生成，缓存于模板目录 .preview 下。
 * 模板选择对话框与模板管理页共用。
 */
@Composable
internal fun ShareNoteTemplatePreview(
    modifier: Modifier = Modifier,
    previewFile: File?
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (previewFile != null && previewFile.exists() && previewFile.length() > 0L) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    Glide.with(imageView)
                        .load(previewFile)
                        .signature(
                            ObjectKey("${previewFile.absolutePath}:${previewFile.length()}:${previewFile.lastModified()}")
                        )
                        .centerCrop()
                        .into(imageView)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = stringResource(R.string.share_note_preview_label),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(name = "Empty", locale = "zh")
@Composable
private fun ShareNoteTemplatePreviewEmpty() {
    LegadoTheme {
        ShareNoteTemplatePreview(
            modifier = Modifier,
            previewFile = null
        )
    }
}

@Preview(name = "Filled", locale = "zh")
@Composable
private fun ShareNoteTemplatePreviewFilled() {
    LegadoTheme {
        ShareNoteTemplatePreview(
            modifier = Modifier,
            previewFile = null
        )
    }
}
