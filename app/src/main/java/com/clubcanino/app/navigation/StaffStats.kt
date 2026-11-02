package com.clubcanino.app.navigation

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.clubcanino.app.R

data class StaffStat(
    @StringRes val labelRes: Int,
    val value: String,
    @DrawableRes val iconRes: Int
)

object StaffStats {

    fun show(container: LinearLayout, stats: List<StaffStat>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        val gap = container.resources.getDimensionPixelSize(R.dimen.spacing_sm)
        stats.chunked(2).forEach { rowStats ->
            val row = LinearLayout(container.context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            rowStats.forEachIndexed { index, stat ->
                val card = inflater.inflate(R.layout.item_summary_card, row, false)
                card.findViewById<TextView>(R.id.textStatLabel).setText(stat.labelRes)
                card.findViewById<TextView>(R.id.textStatValue).text = stat.value
                card.findViewById<ImageView>(R.id.imageStat).setImageResource(stat.iconRes)
                card.layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    if (index == 0 && rowStats.size > 1) marginEnd = gap
                }
                row.addView(card)
            }
            if (rowStats.size == 1) {
                row.addView(View(container.context), LinearLayout.LayoutParams(0, 0, 1f))
            }
            container.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = gap }
            )
        }
    }
}
