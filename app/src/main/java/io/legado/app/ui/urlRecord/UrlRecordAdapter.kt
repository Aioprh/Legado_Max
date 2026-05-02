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

class UrlRecordAdapter : RecyclerView.Adapter<UrlRecordAdapter.ViewHolder>() {

    private var items: List<UrlRecord> = emptyList()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

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

    inner class ViewHolder(private val binding: ItemUrlRecordBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(record: UrlRecord) {
            binding.apply {
                tvMethod.text = record.method
                tvDomain.text = record.domain
                tvUrl.text = record.url
                tvTime.text = dateFormat.format(Date(record.timestamp))
                tvDuration.text = "${record.duration}ms"
                
                when {
                    record.errorMsg != null -> {
                        tvStatus.text = "错误"
                        tvStatus.setTextColor(0xFFFF5722.toInt())
                    }
                    record.responseCode in 200..299 -> {
                        tvStatus.text = "${record.responseCode}"
                        tvStatus.setTextColor(0xFF4CAF50.toInt())
                    }
                    else -> {
                        tvStatus.text = "${record.responseCode}"
                        tvStatus.setTextColor(0xFFFF9800.toInt())
                    }
                }
                
                if (record.sourceName != null) {
                    tvSource.text = record.sourceName
                    tvSource.visibility = View.VISIBLE
                } else {
                    tvSource.visibility = View.GONE
                }
                
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
