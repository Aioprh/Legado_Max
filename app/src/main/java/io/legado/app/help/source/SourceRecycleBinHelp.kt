package io.legado.app.help.source

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.SourceRecycleBin
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import java.util.concurrent.TimeUnit

object SourceRecycleBinHelp {

    const val TYPE_BOOK_SOURCE = "book_source"
    const val TYPE_RSS_SOURCE = "rss_source"
    const val TYPE_REPLACE_RULE = "replace_rule"
    private const val RETENTION_DAYS = 7L

    fun recycleBookSources(bookSources: List<BookSource>, now: Long = System.currentTimeMillis()) {
        if (!AppConfig.sourceRecycleBinEnabled) return
        cleanupExpired(now)
        if (bookSources.isEmpty()) return
        val expireAt = now + TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        val items = bookSources.map {
            SourceRecycleBin(
                type = TYPE_BOOK_SOURCE,
                key = it.bookSourceUrl,
                name = it.bookSourceName,
                groupName = it.bookSourceGroup,
                payload = GSON.toJson(it),
                deletedAt = now,
                expireAt = expireAt
            )
        }
        appDb.sourceRecycleBinDao.insert(*items.toTypedArray())
    }

    fun recycleRssSources(rssSources: List<RssSource>, now: Long = System.currentTimeMillis()) {
        if (!AppConfig.sourceRecycleBinEnabled) return
        cleanupExpired(now)
        if (rssSources.isEmpty()) return
        val expireAt = now + TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        val items = rssSources.map {
            SourceRecycleBin(
                type = TYPE_RSS_SOURCE,
                key = it.sourceUrl,
                name = it.sourceName,
                groupName = it.sourceGroup,
                payload = GSON.toJson(it),
                deletedAt = now,
                expireAt = expireAt
            )
        }
        appDb.sourceRecycleBinDao.insert(*items.toTypedArray())
    }

    fun recycleReplaceRules(replaceRules: List<ReplaceRule>, now: Long = System.currentTimeMillis()) {
        if (!AppConfig.sourceRecycleBinEnabled) return
        cleanupExpired(now)
        if (replaceRules.isEmpty()) return
        val expireAt = now + TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        val items = replaceRules.map {
            SourceRecycleBin(
                type = TYPE_REPLACE_RULE,
                key = it.id.toString(),
                name = it.name,
                groupName = it.group,
                payload = GSON.toJson(it),
                deletedAt = now,
                expireAt = expireAt
            )
        }
        appDb.sourceRecycleBinDao.insert(*items.toTypedArray())
    }

    fun restore(item: SourceRecycleBin, overwrite: Boolean) {
        cleanupExpired()
        when (item.type) {
            TYPE_BOOK_SOURCE -> {
                val source = GSON.fromJsonObject<BookSource>(item.payload).getOrNull() ?: return
                if (!overwrite && appDb.bookSourceDao.has(source.bookSourceUrl)) return
                appDb.bookSourceDao.insert(source)
            }
            TYPE_RSS_SOURCE -> {
                val source = GSON.fromJsonObject<RssSource>(item.payload).getOrNull() ?: return
                if (!overwrite && appDb.rssSourceDao.has(source.sourceUrl)) return
                appDb.rssSourceDao.insert(source)
            }
            TYPE_REPLACE_RULE -> {
                val rule = GSON.fromJsonObject<ReplaceRule>(item.payload).getOrNull() ?: return
                if (!overwrite && appDb.replaceRuleDao.findById(rule.id) != null) return
                appDb.replaceRuleDao.insert(rule)
            }
        }
        appDb.sourceRecycleBinDao.delete(item)
    }

    fun hasConflict(item: SourceRecycleBin): Boolean {
        return when (item.type) {
            TYPE_BOOK_SOURCE -> appDb.bookSourceDao.has(item.key)
            TYPE_RSS_SOURCE -> appDb.rssSourceDao.has(item.key)
            TYPE_REPLACE_RULE -> item.key.toLongOrNull()?.let { appDb.replaceRuleDao.findById(it) != null } == true
            else -> false
        }
    }

    fun cleanupExpired(now: Long = System.currentTimeMillis()) {
        appDb.sourceRecycleBinDao.deleteExpired(now)
    }
}
