package io.legado.app.ui.urlRecord

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.data.entities.UrlRecord
import io.legado.app.databinding.ItemUrlRecordBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * URL访问记录列表适配器
 * 
 * 用于在RecyclerView中展示URL记录列表项。
 * 每个列表项显示：
 * - 请求方法（GET/POST等）
 * - 响应状态码（带颜色标识）
 * - 请求耗时
 * - 请求时间
 * - 域名
 * - 完整URL
 * - 来源名称（可选）
 * - 请求体内容（POST请求，可选）
 */
class UrlRecordAdapter : RecyclerView.Adapter<UrlRecordAdapter.ViewHolder>() {

    // 记录数据列表
    private var items: List<UrlRecord> = emptyList()
    
    // 日期格式化器
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * 设置列表数据
     * @param newItems 新的数据列表
     */
    fun setItems(newItems: List<UrlRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUrlRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    /**
     * 列表项ViewHolder
     * 
     * 负责绑定单条URL记录数据到视图。
     */
    inner class ViewHolder(private val binding: ItemUrlRecordBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        /**
         * 绑定URL记录数据到视图
         * @param record 要绑定的URL记录
         */
        fun bind(record: UrlRecord) {
            binding.apply {
                // 设置基本信息
                tvMethod.text = record.method
                tvDomain.text = record.domain
                tvUrl.text = record.url
                tvTime.text = dateFormat.format(Date(record.timestamp))
                tvDuration.text = "${record.duration}ms"
                
                // 根据响应状态设置状态码颜色
                when {
                    record.errorMsg != null -> {
                        // 请求失败，显示红色"错误"
                        tvStatus.text = "错误"
                        tvStatus.setTextColor(0xFFFF5722.toInt())
                    }
                    record.responseCode in 200..299 -> {
                        // 成功响应，显示绿色状态码
                        tvStatus.text = "${record.responseCode}"
                        tvStatus.setTextColor(0xFF4CAF50.toInt())
                    }
                    else -> {
                        // 其他状态，显示橙色状态码
                        tvStatus.text = "${record.responseCode}"
                        tvStatus.setTextColor(0xFFFF9800.toInt())
                    }
                }
                
                // 设置来源名称（可选显示）
                if (record.sourceName != null) {
                    tvSource.text = record.sourceName
                    tvSource.visibility = View.VISIBLE
                } else {
                    tvSource.visibility = View.GONE
                }
                
                // 设置POST请求体内容（可选显示，限制100字符）
                if (record.requestBody != null) {
                    tvRequestBody.text = record.requestBody.take(100)
                    tvRequestBody.visibility = View.VISIBLE
                } else {
                    tvRequestBody.visibility = View.GONE
                }
            }
        }
    }
}
