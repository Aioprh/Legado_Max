package io.legado.app.ui.book.readRecord

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.data.entities.readRecord.ReadRecordTimelineDay
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.formatReadDuration
import io.legado.app.utils.fullScreen
import io.legado.app.utils.setNavigationBarColorAuto
import io.legado.app.utils.setStatusBarColorAuto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookReadRecordActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_NAME = "bookName"
        const val EXTRA_BOOK_AUTHOR = "bookAuthor"
    }

    private var bgDrawable: Drawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        loadBackgroundImage()
        enableEdgeToEdge()

        val bookName = intent.getStringExtra(EXTRA_BOOK_NAME).orEmpty()
        val bookAuthor = intent.getStringExtra(EXTRA_BOOK_AUTHOR).orEmpty()

        setContent {
            BookReadRecordContent(
                bgDrawable = bgDrawable,
                bookName = bookName,
                bookAuthor = bookAuthor,
                onBackClick = { finish() }
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun loadBackgroundImage() {
        try {
            val metrics = android.util.DisplayMetrics()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val windowMetrics = windowManager.currentWindowMetrics
                val bounds = windowMetrics.bounds
                metrics.widthPixels = bounds.width()
                metrics.heightPixels = bounds.height()
            } else {
                windowManager.defaultDisplay.getMetrics(metrics)
            }
            bgDrawable = ThemeConfig.getBgImage(this, metrics)
        } catch (_: Exception) {
            bgDrawable = null
        }
    }

    private fun initTheme() {
        val theme = ThemeConfig.getTheme()
        when (theme) {
            io.legado.app.constant.Theme.Dark -> {
                setTheme(io.legado.app.R.style.AppTheme_Dark)
            }

            io.legado.app.constant.Theme.Light -> {
                setTheme(io.legado.app.R.style.AppTheme_Light)
            }

            else -> {
                if (ColorUtils.isColorLight(primaryColor)) {
                    setTheme(io.legado.app.R.style.AppTheme_Light)
                } else {
                    setTheme(io.legado.app.R.style.AppTheme_Dark)
                }
            }
        }
    }

    private fun setupSystemBar() {
        fullScreen()
        val isTransparentStatusBar = AppConfig.isTransparentStatusBar
        val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
        setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, true)
        if (AppConfig.immNavigationBar) {
            setNavigationBarColorAuto(ThemeStore.navigationBarColor(this))
        } else {
            val nbColor = ColorUtils.darkenColor(ThemeStore.navigationBarColor(this))
            setNavigationBarColorAuto(nbColor)
        }
    }
}

@Composable
fun BookReadRecordContent(
    bgDrawable: Drawable?,
    bookName: String,
    bookAuthor: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val isNightTheme = AppConfig.isNightTheme
    val primaryColor = remember { ThemeStore.primaryColor(context) }
    val accentColor = remember { ThemeStore.accentColor(context) }
    val bgColor = remember { ThemeStore.backgroundColor(context) }
    val textPrimaryColor = remember { ThemeStore.textColorPrimary(context) }
    val textSecondaryColor = remember { ThemeStore.textColorSecondary(context) }

    val isLight = !isNightTheme && ColorUtils.isColorLight(bgColor)
    val background = remember(bgColor) { Color(bgColor) }
    val primary = remember(accentColor) { Color(accentColor) }
    val secondary = remember(primaryColor) { Color(primaryColor) }
    val onBackground = remember(textPrimaryColor) { Color(textPrimaryColor) }
    val onBackgroundVariant = remember(textSecondaryColor) { Color(textSecondaryColor) }

    val surface = remember(background, isLight) {
        lerp(background, Color.White, if (isLight) 0.04f else 0.10f)
    }
    val surfaceVariant = remember(background, onBackground, isLight) {
        lerp(background, onBackground, if (isLight) 0.05f else 0.14f)
    }
    val outline = remember(background, onBackground, isLight) {
        lerp(background, onBackground, if (isLight) 0.12f else 0.24f)
    }
    val pagePrimary = remember(primary, isLight) {
        if (isLight) primary else lerp(primary, Color.White, 0.20f)
    }
    val pageOnBackgroundVariant = remember(onBackgroundVariant, onBackground, isLight) {
        if (isLight) onBackgroundVariant else lerp(onBackgroundVariant, onBackground, 0.32f)
    }
    val pageSurfaceVariant = remember(surfaceVariant, onBackground, isLight) {
        if (isLight) surfaceVariant else lerp(surfaceVariant, onBackground, 0.08f)
    }

    val colorScheme = remember(
        isLight,
        pagePrimary,
        secondary,
        background,
        onBackground,
        pageOnBackgroundVariant,
        surface,
        pageSurfaceVariant,
        outline,
        accentColor,
        primaryColor
    ) {
        if (isLight) {
            lightColorScheme(
                primary = pagePrimary,
                secondary = secondary,
                tertiary = secondary,
                background = background,
                surface = surface,
                surfaceVariant = pageSurfaceVariant,
                secondaryContainer = pageSurfaceVariant,
                tertiaryContainer = pageSurfaceVariant,
                outline = outline,
                outlineVariant = outline.copy(alpha = 0.75f),
                onPrimary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
                onSecondary = if (ColorUtils.isColorLight(primaryColor)) Color.Black else Color.White,
                onBackground = onBackground,
                onSurface = onBackground,
                onSurfaceVariant = pageOnBackgroundVariant,
                error = Color(0xFFE53935),
                onError = Color.White
            )
        } else {
            darkColorScheme(
                primary = pagePrimary,
                secondary = secondary,
                tertiary = secondary,
                background = background,
                surface = surface,
                surfaceVariant = pageSurfaceVariant,
                secondaryContainer = pageSurfaceVariant,
                tertiaryContainer = pageSurfaceVariant,
                outline = outline,
                outlineVariant = outline.copy(alpha = 0.8f),
                onPrimary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
                onSecondary = if (ColorUtils.isColorLight(primaryColor)) Color.Black else Color.White,
                onBackground = onBackground,
                onSurface = onBackground,
                onSurfaceVariant = pageOnBackgroundVariant,
                error = Color(0xFFFF5252),
                onError = Color.Black
            )
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        BoxWithBackground(
            bgDrawable = bgDrawable,
            bgColor = background
        ) {
            BookReadRecordScreen(
                bookName = bookName,
                bookAuthor = bookAuthor,
                onBackClick = onBackClick
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReadRecordScreen(
    bookName: String,
    bookAuthor: String,
    onBackClick: () -> Unit
) {
    val repository = remember { ReadRecordRepository(appDb.readRecordDao) }
    val bookRepository = remember { BookRepository() }

    val rawSessions = repository.getBookSessions(bookName, bookAuthor)
        .collectAsStateWithLifecycle(emptyList())
        .value

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timelineDays = remember(rawSessions) {
        rawSessions
            .sortedBy { it.startTime }
            .fold(mutableListOf<io.legado.app.data.entities.readRecord.ReadRecordSession>()) { merged, session ->
                if (merged.isEmpty()) {
                    merged.add(session)
                } else {
                    val last = merged.last()
                    if (session.startTime - last.endTime <= 60_000L) {
                        merged[merged.lastIndex] = last.copy(
                            endTime = maxOf(last.endTime, session.endTime),
                            words = last.words + session.words
                        )
                    } else {
                        merged.add(session)
                    }
                }
                merged
            }
            .groupBy { dateFormat.format(Date(it.startTime)) }
            .toSortedMap(compareByDescending { it })
            .map { (date, daySessions) ->
                ReadRecordTimelineDay(
                    date = date,
                    sessions = daySessions.sortedByDescending { it.startTime }
                )
            }
    }

    val totalReadTime = repository.getBookReadTime(bookName, bookAuthor)
        .collectAsStateWithLifecycle(0L)
        .value

    var chapterTitle by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bookName, bookAuthor) {
        chapterTitle = bookRepository.getBookDurChapterTitle(bookName, bookAuthor)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = bookName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (timelineDays.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "暂无阅读记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val listState = rememberLazyListState()
                val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            val totalItems = listState.layoutInfo.totalItemsCount
                            if (totalItems > 0) {
                                val visibleItems = listState.layoutInfo.visibleItemsInfo.size
                                val fraction = visibleItems.toFloat() / totalItems.toFloat()
                                val barHeight = size.height * fraction.coerceIn(0.04f, 1f)
                                val progress = listState.firstVisibleItemIndex.toFloat()
                                    .coerceAtMost((totalItems - visibleItems).coerceAtLeast(0).toFloat())
                                val maxScroll = (totalItems - visibleItems).coerceAtLeast(1).toFloat()
                                val barY = (size.height - barHeight) * (progress / maxScroll).coerceIn(0f, 1f)
                                drawRoundRect(
                                    color = scrollbarColor,
                                    topLeft = Offset(size.width - 5.dp.toPx(), barY),
                                    size = Size(3.dp.toPx(), barHeight),
                                    cornerRadius = CornerRadius(1.5.dp.toPx())
                                )
                            }
                        },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "summary") {
                        SummaryHeader(
                            bookName = bookName,
                            bookAuthor = bookAuthor,
                            totalReadTime = totalReadTime,
                            dayCount = timelineDays.size,
                            sessionCount = timelineDays.sumOf { it.sessions.size }
                        )
                    }

                    items(
                        items = timelineDays,
                        key = { it.date }
                    ) { day ->
                        DaySection(day.date, day.sessions, chapterTitle)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(
    bookName: String,
    bookAuthor: String,
    totalReadTime: Long,
    dayCount: Int,
    sessionCount: Int
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, clip = false),
        shape = shape,
        color = surfaceColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = primaryColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bookName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (bookAuthor.isNotBlank()) {
                        Text(
                            text = bookAuthor,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "累计阅读 ${formatReadDuration(totalReadTime)} · $sessionCount 次 · 共 $dayCount 天",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DaySection(
    date: String,
    sessions: List<io.legado.app.data.entities.readRecord.ReadRecordSession>,
    chapterTitle: String?
) {
    var expanded by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dayTotal = remember(sessions) {
        sessions.sumOf { (it.endTime - it.startTime).coerceAtLeast(0L) }
    }

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape, clip = false),
        shape = shape,
        color = surfaceColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = primaryColor,
                        modifier = Modifier.size(8.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = date,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${sessions.size}次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatReadDuration(dayTotal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sessions.sortedByDescending { it.startTime }.forEach { session ->
                        val start = remember(session.startTime) { Date(session.startTime) }
                        val duration = remember(session.startTime, session.endTime) {
                            (session.endTime - session.startTime).coerceAtLeast(0L)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = timeFormat.format(start),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatReadDuration(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            chapterTitle?.let { title ->
                                Text(
                                    text = "·",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

