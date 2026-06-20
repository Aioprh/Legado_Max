package io.legado.app.ui.main.homepage.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.legado.app.model.BookCover

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomepageBookCover(
    name: String,
    author: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    val displayCover = coverUrl ?: BookCover.getGalleryDefaultCover()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (displayCover != null) {
            GlideImage(
                model = displayCover,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
