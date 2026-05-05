package io.legado.app.model.upload

import io.legado.app.data.appDb
import io.legado.app.data.entities.DirectLinkUploadRule
import io.legado.app.data.entities.UploadHistory
import io.legado.app.help.DirectLinkUpload
import kotlinx.coroutines.flow.Flow

class DirectLinkUploadRepository {

    private val ruleDao = appDb.directLinkUploadRuleDao
    private val historyDao = appDb.uploadHistoryDao

    fun getRules(): Flow<List<DirectLinkUploadRule>> = ruleDao.flowAll()

    suspend fun getRuleById(id: Long): DirectLinkUploadRule? = ruleDao.getById(id)

    suspend fun getDefaultRule(): DirectLinkUploadRule? {
        val default = ruleDao.getDefault()
        if (default != null) return default
        val all = ruleDao.getAll()
        return all.firstOrNull()
    }

    suspend fun addRule(rule: DirectLinkUploadRule) {
        ruleDao.insert(rule)
    }

    suspend fun updateRule(rule: DirectLinkUploadRule) {
        ruleDao.update(rule)
    }

    suspend fun deleteRule(rule: DirectLinkUploadRule) {
        ruleDao.delete(rule)
    }

    suspend fun setDefaultRule(ruleId: Long) {
        ruleDao.setDefault(ruleId)
    }

    suspend fun incrementUploadCount(ruleId: Long) {
        ruleDao.incrementUploadCount(ruleId)
    }

    fun getHistories(): Flow<List<UploadHistory>> = historyDao.flowAll()

    fun getHistoriesByRule(ruleId: Long): Flow<List<UploadHistory>> = 
        historyDao.flowByRuleId(ruleId)

    fun searchHistories(keyword: String): Flow<List<UploadHistory>> = 
        historyDao.flowSearch(keyword)

    suspend fun addHistory(history: UploadHistory) {
        historyDao.insert(history)
    }

    suspend fun deleteHistory(history: UploadHistory) {
        historyDao.delete(history)
    }

    suspend fun clearAllHistories(): Int = historyDao.deleteAll()

    suspend fun deleteOldHistories(days: Int): Int {
        val timestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        return historyDao.deleteOldRecords(timestamp)
    }

    suspend fun getUploadStats(): UploadStats {
        val totalCount = historyDao.getCount()
        val successCount = historyDao.getSuccessCount()
        val totalSize = historyDao.getTotalUploadSize() ?: 0L
        return UploadStats(
            totalCount = totalCount,
            successCount = successCount,
            failedCount = totalCount - successCount,
            totalSize = totalSize
        )
    }

    suspend fun migrateFromOldConfig() {
        val oldRule = DirectLinkUpload.getConfig()
        if (oldRule != null && ruleDao.getCount() == 0) {
            val newRule = DirectLinkUploadRule(
                uploadUrl = oldRule.uploadUrl,
                downloadUrlRule = oldRule.downloadUrlRule,
                summary = oldRule.summary,
                compress = oldRule.compress,
                isDefault = true,
                sortOrder = 0
            )
            ruleDao.insert(newRule)
            DirectLinkUpload.delConfig()
        }
    }

    suspend fun importDefaultRules() {
        if (ruleDao.getCount() > 0) return
        
        val defaultRules = DirectLinkUpload.defaultRules.mapIndexed { index, rule ->
            DirectLinkUploadRule(
                uploadUrl = rule.uploadUrl,
                downloadUrlRule = rule.downloadUrlRule,
                summary = rule.summary,
                compress = rule.compress,
                sortOrder = index,
                isDefault = index == 0
            )
        }
        ruleDao.insert(*defaultRules.toTypedArray())
    }
}

data class UploadStats(
    val totalCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val totalSize: Long
)
