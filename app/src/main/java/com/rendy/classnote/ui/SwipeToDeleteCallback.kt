package com.rendy.classnote.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class SwipeActionsCallback(
    context: Context,
    private val onDelete: (position: Int) -> Unit,
    private val onEdit: ((position: Int) -> Unit)? = null,
    private val isSwipeable: ((viewType: Int) -> Boolean)? = null
) : ItemTouchHelper.SimpleCallback(
    0,
    ItemTouchHelper.LEFT or (if (true) ItemTouchHelper.RIGHT else 0)
) {

    private var lastDx = 0f
    private val background = ColorDrawable()
    private val deleteIcon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_delete)
    private val editIcon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_edit)
    private val iconMargin = (24 * context.resources.displayMetrics.density).toInt()

    private val deleteRgb = intArrayOf(244, 67, 54)   // #F44336
    private val editRgb   = intArrayOf(25, 118, 210)  // #1976D2

    // threshold: 左滑刪除 0.5，右滑編輯 0.3
    private fun swipeAppearance(ratio: Float, rgb: IntArray, threshold: Float): Pair<Int, Int> {
        val alpha = if (ratio >= threshold) 255 else (60 + (ratio / threshold * 120)).toInt()
        val bgColor = Color.argb(alpha, rgb[0], rgb[1], rgb[2])
        return bgColor to alpha
    }

    private fun drawSwipeIcon(c: Canvas, icon: android.graphics.drawable.Drawable, itemView: android.view.View, iconAlpha: Int, fromRight: Boolean) {
        val iconSize = icon.intrinsicHeight
        val iconTop = itemView.top + (itemView.height - iconSize) / 2
        if (fromRight) {
            val iconLeft = itemView.right - iconMargin - iconSize
            icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        } else {
            val iconRight = itemView.left + iconMargin + iconSize
            icon.setBounds(itemView.left + iconMargin, iconTop, iconRight, iconTop + iconSize)
        }
        icon.setTint(Color.argb(iconAlpha, 255, 255, 255))
        icon.draw(c)
    }

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        if (isSwipeable?.invoke(viewHolder.itemViewType) == false) return 0
        val dirs = ItemTouchHelper.LEFT or (if (onEdit != null) ItemTouchHelper.RIGHT else 0)
        return dirs
    }

    // 右滑（編輯）30%，左滑（刪除）保持 50%
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) =
        if (lastDx > 0) 0.3f else 0.5f

    override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        if (direction == ItemTouchHelper.LEFT) onDelete(viewHolder.adapterPosition)
        else onEdit?.invoke(viewHolder.adapterPosition)
    }

    override fun onChildDraw(
        c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        lastDx = dX

        when {
            dX < 0 -> {
                val ratio = (-dX / itemView.width).coerceIn(0f, 1f)
                val (bgColor, iconAlpha) = swipeAppearance(ratio, deleteRgb, threshold = 0.5f)
                background.color = bgColor
                background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                background.draw(c)
                deleteIcon?.let { drawSwipeIcon(c, it, itemView, iconAlpha, fromRight = true) }
            }
            dX > 0 -> {
                val ratio = (dX / itemView.width).coerceIn(0f, 1f)
                val (bgColor, iconAlpha) = swipeAppearance(ratio, editRgb, threshold = 0.3f)
                background.color = bgColor
                background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                background.draw(c)
                editIcon?.let { drawSwipeIcon(c, it, itemView, iconAlpha, fromRight = false) }
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
