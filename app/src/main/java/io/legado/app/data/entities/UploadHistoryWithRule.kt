package io.legado.app.data.entities

/**
 * 上传历史记录与规则名称的关联数据类
 * 
 * 用于在显示历史记录时获取最新的规则名称
 * 如果规则被删除，则使用历史记录中保存的规则名称
 * 
 * @property history 上传历史记录
 * @property ruleSummary 规则名称（从规则表实时查询，如果规则被删除则为null）
 */
data class UploadHistoryWithRule(
    val history: UploadHistory,
    val ruleSummary: String?
) {
    /**
     * 获取显示的规则名称
     * 优先使用规则表中的最新名称，如果规则被删除则使用历史记录中保存的名称
     */
    fun getDisplayRuleSummary(): String {
        return ruleSummary ?: history.ruleSummary
    }
}
