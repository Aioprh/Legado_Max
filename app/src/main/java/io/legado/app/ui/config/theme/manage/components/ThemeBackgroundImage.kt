package io.legado.app.ui.config.theme.manage.components

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

/**
 * 基于 Glide 的 Compose 主题背景图片加载。
 *
 * 为什么不用原生的 produceState 读磁盘：
 * 列表滚动时会频繁触发 Compose 重组。如果在这里用协程+文件 IO 解码位图，
 * 必然会导致不可挽回的列表掉帧和内存泄漏。
 * 改造为 AndroidView 包裹 ImageView，交由项目已有的 Glide 接管后，
 * 借由 Glide 底层的内存/磁盘多级缓存机制与 Bitmap 复用池，彻底根治列表滑动时的卡顿顽疾。
 */
@Composable
fun ThemeBackgroundImage(
    path: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        update = { imageView ->
            if (path.isNullOrBlank()) {
                // 清理旧请求，防止列表复用时残留 Glide 加载回调
                Glide.with(context).clear(imageView)
                imageView.setImageDrawable(null)
            } else {
                Glide.with(context)
                    .load(path)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imageView)
            }
        }
    )
}
