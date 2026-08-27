package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.isNotShelf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.min
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 轻量书架键数据类
 *
 * 仅包含书架匹配所需的三个字段，用于替代全量 [Book] 查询，
 * 减少 Room 查询的内存开销（从 20+ 字段降为 3 个 String）。
 */
data class ShelfKey(
    val name: String,
    val author: String,
    val bookUrl: String
)

/**
 * 轻量书架展示数据类
 *
 * 仅包含书架 UI 展示所需的字段，排除 [Book] 中不在书架界面使用的字段
 * （tocUrl、charset、variable、syncTime、durVolumeIndex、chapterInVolumeIndex、
 * durChapterPos、originOrder、lastCheckTime），减少 Room 查询的内存开销。
 *
 * SQL 层面已包含 `WHERE (type & notShelf) = 0` 过滤和 `ORDER BY` 排序，
 * 避免在内存中做过滤和排序。
 *
 * @see BookDao.flowShelfByGroup
 */
@TypeConverters(Book.Converters::class)
data class BookShelfDisplay(
    val bookUrl: String,
    val origin: String,
    val originName: String,
    val name: String,
    val author: String,
    val kind: String?,
    val customTag: String?,
    val intro: String?,
    val customIntro: String?,
    val coverUrl: String?,
    val customCoverUrl: String?,
    val wordCount: String?,
    val type: Int,
    val group: Long,
    val latestChapterTitle: String?,
    val latestChapterTime: Long,
    val lastCheckCount: Int,
    val totalChapterNum: Int,
    val durChapterTitle: String?,
    val durChapterIndex: Int,
    val durChapterTime: Long,
    val canUpdate: Boolean,
    val order: Int,
    val readConfig: Book.ReadConfig?
) {
    val isLocal: Boolean
        get() {
            if (type == 0) {
                return origin == BookType.localTag || origin.startsWith(BookType.webDavTag)
            }
            return type and BookType.local > 0
        }

    val isAudio: Boolean
        get() = type and BookType.audio > 0

    val isVideo: Boolean
        get() = type and BookType.video > 0

    val isImage: Boolean
        get() = type and BookType.image > 0

    fun getDisplayCover(): String? {
        return if (customCoverUrl.isNullOrEmpty()) coverUrl else customCoverUrl
    }

    fun getDisplayIntro(): String? {
        return if (customIntro.isNullOrEmpty()) intro else customIntro
    }

    /**
     * 获取简介的纯文本内容，用于书架列表等仅支持纯文本的场景。
     * 处理带格式标记的简介：<usehtml>、<md>、<useweb>
     */
    fun getDisplayIntroPlainText(): String {
        val displayIntro = getDisplayIntro() ?: return ""
        return when {
            displayIntro.startsWith("<useweb>") -> {
                val html = displayIntro.removePrefix("<useweb>")
                io.legado.app.utils.HtmlFormatter.format(html)
            }
            displayIntro.startsWith("<usehtml>") -> {
                val html = displayIntro.removePrefix("<usehtml>")
                io.legado.app.utils.HtmlFormatter.format(html)
            }
            displayIntro.startsWith("<md>") -> {
                val md = displayIntro.removePrefix("<md>")
                io.legado.app.utils.StringUtils.removeMdFormat(md)
            }
            else -> displayIntro
        }
    }

    fun getUnreadChapterNum(): Int {
        return max(simulatedTotalChapterNum() - durChapterIndex - 1, 0)
    }

    private fun simulatedTotalChapterNum(): Int {
        return if (readSimulating()) {
            val currentDate = LocalDate.now()
            val daysPassed = ChronoUnit.DAYS.between(
                readConfig?.startDate ?: return totalChapterNum,
                currentDate
            ).toInt() + 1
            val chaptersToUnlock =
                max(0, (readConfig?.startChapter ?: 0) + (daysPassed * (readConfig?.dailyChapters ?: 0)))
            min(totalChapterNum, chaptersToUnlock)
        } else {
            totalChapterNum
        }
    }

    private fun readSimulating(): Boolean {
        return readConfig?.readSimulating == true
    }

    /**
     * 转换为最小化 [Book] 对象，用于书架点击打开阅读界面。
     *
     * 包含 [startActivityForBook] 所需的全部字段（type、origin、bookUrl），
     * 但省略了 tocUrl、charset 等阅读界面内部会自行加载的字段。
     */
    fun toMinimalBook(): Book {
        return Book(
            bookUrl = bookUrl,
            origin = origin,
            originName = originName,
            name = name,
            author = author,
            kind = kind,
            customTag = customTag,
            intro = intro,
            customIntro = customIntro,
            coverUrl = coverUrl,
            customCoverUrl = customCoverUrl,
            wordCount = wordCount,
            type = type,
            group = group,
            latestChapterTitle = latestChapterTitle,
            latestChapterTime = latestChapterTime,
            lastCheckCount = lastCheckCount,
            totalChapterNum = totalChapterNum,
            durChapterTitle = durChapterTitle,
            durChapterIndex = durChapterIndex,
            durChapterTime = durChapterTime,
            canUpdate = canUpdate,
            order = order,
            readConfig = readConfig
        )
    }
}

/**
 * 书数据访问接口
 */
@Dao
interface BookDao {

    /**
     * 轻量书架键查询：只返回 name/author/bookUrl 三个字段，排除 notShelf 类型。
     *
     * 供 [io.legado.app.help.book.BookshelfMatcher] 使用，避免加载完整 [Book] 实体。
     */
    @Query(
        """SELECT name, author, bookUrl FROM books 
        WHERE type & ${BookType.notShelf} = 0 ORDER BY durChapterTime DESC"""
    )
    fun flowShelfKeys(): Flow<List<ShelfKey>>

    fun flowByGroup(groupId: Long): Flow<List<Book>> {
        return when (groupId) {
            BookGroup.IdRoot -> flowRoot()
            BookGroup.IdAll -> flowAll()
            BookGroup.IdLocal -> flowLocal()
            BookGroup.IdAudio -> flowAudio()
            BookGroup.IdNetNone -> flowNetNoGroup()
            BookGroup.IdLocalNone -> flowLocalNoGroup()
            BookGroup.IdVideo -> flowVideo()
            BookGroup.IdError -> flowUpdateError()
            else -> flowByUserGroup(groupId)
        }.map { list ->
            list.filterNot { it.isNotShelf }
        }
    }

    /**
     * 轻量书架展示查询：只返回 [BookShelfDisplay] 所需字段，
     * SQL 层面直接过滤 notShelf 类型并按 durChapterTime DESC 排序，
     * 避免在内存中做全量加载 + 过滤 + 排序。
     *
     * 排序由调用方在内存中完成（因为部分排序方式如中文名排序无法用 SQL 表达），
     * 但由于排除了不必要的大字段，内存开销大幅降低。
     */
    fun flowShelfByGroup(groupId: Long): Flow<List<BookShelfDisplay>> {
        return when (groupId) {
            BookGroup.IdRoot -> flowShelfRoot()
            BookGroup.IdAll -> flowShelfAll()
            BookGroup.IdLocal -> flowShelfLocal()
            BookGroup.IdAudio -> flowShelfAudio()
            BookGroup.IdNetNone -> flowShelfNetNoGroup()
            BookGroup.IdLocalNone -> flowShelfLocalNoGroup()
            BookGroup.IdVideo -> flowShelfVideo()
            BookGroup.IdError -> flowShelfUpdateError()
            else -> flowShelfByUserGroup(groupId)
        }
    }

    @Query(
        """
        SELECT bookUrl, origin, originName, name, author, kind, customTag, intro, customIntro,
        coverUrl, customCoverUrl, wordCount, type, `group`, latestChapterTitle, latestChapterTime,
        lastCheckCount, totalChapterNum, durChapterTitle, durChapterIndex, durChapterTime,
        canUpdate, `order`, readConfig
        FROM books
        WHERE (type & ${BookType.notShelf}) = 0
        ORDER BY durChapterTime DESC
        """
    )
    fun flowShelfAll(): Flow<List<BookShelfDisplay>>

    @Query(
        """
        SELECT bookUrl, origin, originName, name, author, kind, customTag, intro, customIntro,
        coverUrl, customCoverUrl, wordCount, type, `group`, latestChapterTitle, latestChapterTime,
        lastCheckCount, totalChapterNum, durChapterTitle, durChapterIndex, durChapterTime,
        canUpdate, `order`, readConfig
        FROM books
        WHERE (type & ${BookType.notShelf}) = 0
        AND (type & ${BookType.text}) > 0
        AND (type & ${BookType.local}) = 0
        AND ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
        AND (select show from book_groups where groupId = ${BookGroup.IdNetNone}) != 1
        ORDER BY durChapterTime DESC
        """
    )
    fun flowShelfRoot(): Flow<List<BookShelfDisplay>>

    @Query(
        """
        SELECT bookUrl, origin, originName, name, author, kind, customTag, intro, customIntro,
        coverUrl, customCoverUrl, wordCount, type, `group`, latestChapterTitle, latestChapterTime,
        lastCheckCount, totalChapterNum, durChapterTitle, durChapterIndex, durChapterTime,
        canUpdate, `order`, readConfig
        FROM books
        WHERE (type & ${BookType.notShelf}) = 0
        AND (type & ${BookType.audio}) > 0
        ORDER BY durChapterTime DESC
        """
    )
    fun flowShelfAudio(): Flow<List<BookShelfDisplay>>

    @Query(
        """
        SELECT bookUrl, origin, originName, name, author, kind, customTag, intro, customIntro,
        coverUrl, customCoverUrl, wordCount, type, `group`, latestChapterTitle, latestChapterTime,
        lastCheckCount, totalChapterNum, durChapterTitle, durChapterIndex, durChapterTime,
        canUpdate, `order`, readConfig
        FROM books
        WHERE (type & ${BookType.notShelf}) = 0
        AND (type & ${BookType.video}) > 0
        ORDER BY durChapterTime DESC
        """
    )
    fun flowShelfVideo(): Flow<List<BookShelfDisplay>>

    @Query(
        """
        SELECT bookUrl, origin, originName, name, author, kind, customTag, intro, customIntro,
        coverUrl, customCoverUrl, wordCount, type, `group`, latestChapterTitle, latestChapterTime,
        lastCheckCount, totalChapterNum, durChapterTitle, durChapterIndex, durChapterTime,
        canUpdate, `order`, readConfig
        FROM books
        WHERE (type & ${BookType.notShelf}) = 0
        AND (type & ${BookType.local}) > 0
        ORDER BY durChapterTime DESC
        """
    )
    fun flowShelfLocal(): Flow<List<BookShelfDisplay>>

    @Query(
        """
        SELECT bookUrl, origin, originName, name, author, kind, customTag, intro, customIntro,
        coverUrl, customCoverUrl, wordCount, type, `group`, latestChapterTitle, latestChapterTime,
        lastCheckCount, totalChapterNum, durChapterTitle, durChapterIndex, durChapterTime,
        canUpdate, `order`, readConfig
        FROM books
        WHERE (type & ${BookType.notShelf}) = 0
        AND (type & ${BookType.audio}) = 0
        AND (type & ${BookType.local}) = 0
        AND (type & ${BookType.video}) = 0
        AND ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
        ORDER BY durChapterTime DESC
        """
    )
    fun flowShelfNetNoGroup(): Flow<List<BookShelfDisplay>>

    @Query(
        """
        SELECT bookUrl, origin, originName, name, author, kind, customTag, intro, customIntro,
        coverUrl, customCoverUrl, wordCount, type, `group`, latestChapterTitle, latestChapterTime,
        lastCheckCount, totalChapterNum, durChapterTitle, durChapterIndex, durChapterTime,
        canUpdate, `order`, readConfig
        FROM books
        WHERE (type & ${BookType.notShelf}) = 0
        AND (type & ${BookType.local}) > 0
        AND ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
        ORDER BY durChapterTime DESC
        """
    )
    fun flowShelfLocalNoGroup(): Flow<List<BookShelfDisplay>>

    @Query(
        """
        SELECT bookUrl, origin, originName, name, author, kind, customTag, intro, customIntro,
        coverUrl, customCoverUrl, wordCount, type, `group`, latestChapterTitle, latestChapterTime,
        lastCheckCount, totalChapterNum, durChapterTitle, durChapterIndex, durChapterTime,
        canUpdate, `order`, readConfig
        FROM books
        WHERE (type & ${BookType.notShelf}) = 0
        AND (`group` & :group) > 0
        ORDER BY durChapterTime DESC
        """
    )
    fun flowShelfByUserGroup(group: Long): Flow<List<BookShelfDisplay>>

    @Query(
        """
        SELECT bookUrl, origin, originName, name, author, kind, customTag, intro, customIntro,
        coverUrl, customCoverUrl, wordCount, type, `group`, latestChapterTitle, latestChapterTime,
        lastCheckCount, totalChapterNum, durChapterTitle, durChapterIndex, durChapterTime,
        canUpdate, `order`, readConfig
        FROM books
        WHERE (type & ${BookType.notShelf}) = 0
        AND (type & ${BookType.updateError}) > 0
        ORDER BY durChapterTime DESC
        """
    )
    fun flowShelfUpdateError(): Flow<List<BookShelfDisplay>>

    @Query(
        """
        select * from books where type & ${BookType.text} > 0
        and type & ${BookType.local} = 0
        and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
        and (select show from book_groups where groupId = ${BookGroup.IdNetNone}) != 1
        """
    )
    fun flowRoot(): Flow<List<Book>>

    @Query("SELECT * FROM books order by durChapterTime desc")
    fun flowAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE type & ${BookType.audio} > 0")
    fun flowAudio(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE type & ${BookType.video} > 0")
    fun flowVideo(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE type & ${BookType.local} > 0")
    fun flowLocal(): Flow<List<Book>>

    @Query(
        """
        select * from books where type & ${BookType.audio} = 0 and type & ${BookType.local} = 0 and type & ${BookType.video} = 0
        and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
        """
    )
    fun flowNetNoGroup(): Flow<List<Book>>

    @Query(
        """
        select * from books where type & ${BookType.local} > 0
        and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
        """
    )
    fun flowLocalNoGroup(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE (`group` & :group) > 0")
    fun flowByUserGroup(group: Long): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE name like '%'||:key||'%' or author like '%'||:key||'%'")
    fun flowSearch(key: String): Flow<List<Book>>

    @Query("SELECT * FROM books where type & ${BookType.updateError} > 0 order by durChapterTime desc")
    fun flowUpdateError(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE (`group` & :group) > 0")
    fun getBooksByGroup(group: Long): List<Book>

    @Query("SELECT * FROM books WHERE `name` in (:names)")
    fun findByName(vararg names: String): List<Book>

    @Query("select * from books where originName = :fileName")
    fun getBookByFileName(fileName: String): Book?

    @Query("SELECT * FROM books WHERE bookUrl = :bookUrl")
    fun getBook(bookUrl: String): Book?

    @Query("SELECT * FROM books WHERE name = :name and author = :author")
    fun getBook(name: String, author: String): Book?

    @Query("SELECT * FROM books WHERE name = :name LIMIT 1")
    fun getBookByName(name: String): Book?

    @Query("SELECT * FROM books WHERE name = :name and author = :author")
    fun findByNameAndAuthor(name: String, author: String): Flow<Book?>

    @Query("""select distinct bs.* from books, book_sources bs 
        where origin == bookSourceUrl and origin not like '${BookType.localTag}%' 
        and origin not like '${BookType.webDavTag}%'""")
    fun getAllUseBookSource(): List<BookSource>

    @Query("SELECT * FROM books WHERE name = :name and origin = :origin")
    fun getBookByOrigin(name: String, origin: String): Book?

    @Query("select exists(select 1 from books where origin = :origin)")
    fun hasBookByOrigin(origin: String): Boolean

    @Query("update books set origin = :newUrl where origin = :oldUrl")
    fun updateOrigin(oldUrl: String, newUrl: String): Int

    @get:Query("select count(bookUrl) from books where (SELECT sum(groupId) FROM book_groups)")
    val noGroupSize: Int

    @get:Query("SELECT * FROM books where type & ${BookType.local} = 0")
    val webBooks: List<Book>

    @get:Query("SELECT * FROM books where type & ${BookType.local} = 0 and canUpdate = 1")
    val hasUpdateBooks: List<Book>

    @get:Query("SELECT * FROM books")
    val all: List<Book>

    @Query("SELECT * FROM books where type & :type > 0 and type & ${BookType.local} = 0")
    fun getByTypeOnLine(type: Int): List<Book>

    @get:Query("SELECT * FROM books where type & ${BookType.text} > 0 ORDER BY durChapterTime DESC limit 1")
    val lastReadBook: Book?

    @get:Query("SELECT bookUrl FROM books")
    val allBookUrls: List<String>

    @get:Query("SELECT bookUrl, name, author, customTag, type, `group` FROM books")
    val allTagInfos: List<BookTagInfo>

    @get:Query("SELECT COUNT(*) FROM books")
    val allBookCount: Int

    @get:Query("select min(`order`) from books")
    val minOrder: Int

    @get:Query("select max(`order`) from books")
    val maxOrder: Int

    @Query("select exists(select 1 from books where bookUrl = :bookUrl)")
    fun has(bookUrl: String): Boolean

    @Query("select exists(select 1 from books where name = :name and author = :author)")
    fun has(name: String, author: String): Boolean

    @Query(
        """select exists(select 1 from books where type & ${BookType.local} > 0 
        and (originName = :fileName or (origin != '${BookType.localTag}' and origin like '%' || :fileName)))"""
    )
    fun hasFile(fileName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg book: Book)

    @Update
    fun update(vararg book: Book)

    @Delete
    fun delete(vararg book: Book)

    @Query("delete from books")
    fun deleteAll()

    @Transaction
    fun replace(oldBook: Book, newBook: Book) {
        delete(oldBook)
        insert(newBook)
    }

    @Query("update books set durChapterPos = :pos where bookUrl = :bookUrl")
    fun upProgress(bookUrl: String, pos: Int)

    @Query("update books set customTag = :customTag where bookUrl = :bookUrl")
    fun updateCustomTag(bookUrl: String, customTag: String?)

    @Query("update books set `group` = :newGroupId where `group` = :oldGroupId")
    fun upGroup(oldGroupId: Long, newGroupId: Long)

    @Query("update books set `group` = `group` - :group where `group` & :group > 0")
    fun removeGroup(group: Long)

    @Query("delete from books where type & ${BookType.notShelf} > 0")
    fun deleteNotShelfBook()
}

/**
 * 轻量标签查询数据类，仅包含标签管理所需字段。
 */
data class BookTagInfo(
    val bookUrl: String,
    val name: String,
    val author: String,
    val customTag: String?,
    val type: Int,
    val group: Long
)
