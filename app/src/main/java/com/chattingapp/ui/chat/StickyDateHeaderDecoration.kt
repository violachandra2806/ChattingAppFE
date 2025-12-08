package com.chattingapp.ui.chat

import android.graphics.Canvas
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chattingapp.R

class StickyDateHeaderDecoration : RecyclerView.ItemDecoration() {

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

        val topChild = parent.getChildAt(0) ?: return
        val topChildPosition = parent.getChildAdapterPosition(topChild)
        if (topChildPosition == RecyclerView.NO_POSITION) return

        val adapter = parent.adapter ?: return
        val currentHeader = getHeaderViewForItem(topChildPosition, parent) ?: return

        fixLayoutSize(parent, currentHeader)

        val contactPoint = currentHeader.bottom
        val childInContact = getChildInContact(parent, contactPoint) ?: return

        val childAdapterPosition = parent.getChildAdapterPosition(childInContact)
        if (childAdapterPosition == RecyclerView.NO_POSITION) return

        if (isHeader(childAdapterPosition, adapter)) {
            moveHeader(c, currentHeader, childInContact)
            return
        }

        drawHeader(c, currentHeader)
    }

    private fun getHeaderViewForItem(itemPosition: Int, parent: RecyclerView): View? {
        if (parent.adapter == null) return null

        val headerPosition = getHeaderPositionForItem(itemPosition, parent.adapter!!)
        if (headerPosition == RecyclerView.NO_POSITION) return null

        val headerType = parent.adapter!!.getItemViewType(headerPosition)

        // Check if it's a date header type (TYPE_DATE_HEADER = 0)
        if (headerType != 0) return null

        val headerHolder = parent.adapter!!.createViewHolder(parent, headerType)
        parent.adapter!!.onBindViewHolder(headerHolder, headerPosition)
        return headerHolder.itemView
    }

    private fun drawHeader(c: Canvas, header: View) {
        c.save()
        c.translate(0f, 0f)
        header.draw(c)
        c.restore()
    }

    private fun moveHeader(c: Canvas, currentHeader: View, nextHeader: View) {
        c.save()
        c.translate(0f, (nextHeader.top - currentHeader.height).toFloat())
        currentHeader.draw(c)
        c.restore()
    }

    private fun getChildInContact(parent: RecyclerView, contactPoint: Int): View? {
        var childInContact: View? = null
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.bottom > contactPoint) {
                if (child.top <= contactPoint) {
                    childInContact = child
                    break
                }
            }
        }
        return childInContact
    }

    private fun fixLayoutSize(parent: ViewGroup, view: View) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

        val childWidthSpec = ViewGroup.getChildMeasureSpec(
            widthSpec,
            parent.paddingLeft + parent.paddingRight,
            view.layoutParams.width
        )
        val childHeightSpec = ViewGroup.getChildMeasureSpec(
            heightSpec,
            parent.paddingTop + parent.paddingBottom,
            view.layoutParams.height
        )

        view.measure(childWidthSpec, childHeightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun isHeader(position: Int, adapter: RecyclerView.Adapter<*>): Boolean {
        return adapter.getItemViewType(position) == 0 // TYPE_DATE_HEADER
    }

    private fun getHeaderPositionForItem(itemPosition: Int, adapter: RecyclerView.Adapter<*>): Int {
        var headerPosition = RecyclerView.NO_POSITION
        var currentPosition = itemPosition

        while (currentPosition >= 0) {
            if (isHeader(currentPosition, adapter)) {
                headerPosition = currentPosition
                break
            }
            currentPosition--
        }
        return headerPosition
    }
}
