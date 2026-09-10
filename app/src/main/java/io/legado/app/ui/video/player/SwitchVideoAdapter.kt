package io.legado.app.ui.video.player

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import io.legado.app.R

class SwitchVideoAdapter<T>(
    context: Context,
    private val dataList: List<T>,
    private val titleProvider: (T) -> String = { it.toString() },
    private var selectedPosition: Int = -1
) : ArrayAdapter<T>(context, 0, dataList) {

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.switch_video_dialog_item, parent, false)

        view.findViewById<TextView>(R.id.episodeNumber).text =
            String.format("%02d", position + 1)

        view.findViewById<TextView>(R.id.text1).text = titleProvider(dataList[position])

        val currentMark = view.findViewById<TextView>(R.id.currentMark)
        currentMark.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE

        return view
    }
}
