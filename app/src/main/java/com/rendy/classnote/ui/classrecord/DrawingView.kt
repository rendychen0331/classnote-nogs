package com.rendy.classnote.ui.classrecord

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Stroke(val path: Path, val paint: Paint)

    private val strokes = mutableListOf<Stroke>()
    private val redoStack = mutableListOf<Stroke>()
    private var currentPath = Path()
    private var currentPaint = buildPaint(Color.parseColor("#212121"), 5f, false)
    private var loadedBitmap: Bitmap? = null

    var strokeColor: Int = Color.parseColor("#212121")
        set(v) { field = v; refreshPaint() }

    var strokeWidth: Float = 5f
        set(v) { field = v; refreshPaint() }

    var isEraser: Boolean = false
        set(v) { field = v; refreshPaint() }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun refreshPaint() {
        currentPaint = buildPaint(strokeColor, if (isEraser) 28f else strokeWidth, isEraser)
    }

    private fun buildPaint(color: Int, width: Float, eraser: Boolean) = Paint().apply {
        this.color = color
        this.strokeWidth = width
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        if (eraser) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        loadedBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        strokes.forEach { canvas.drawPath(it.path, it.paint) }
        canvas.drawPath(currentPath, currentPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path().also { it.moveTo(event.x, event.y) }
                redoStack.clear()
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                strokes.add(Stroke(currentPath, Paint(currentPaint)))
                currentPath = Path()
                invalidate()
            }
        }
        return true
    }

    fun undo() {
        if (strokes.isNotEmpty()) { redoStack.add(strokes.removeLast()); invalidate() }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) { strokes.add(redoStack.removeLast()); invalidate() }
    }

    fun clearAll() {
        strokes.clear(); redoStack.clear(); loadedBitmap = null; invalidate()
    }

    fun isEmpty() = strokes.isEmpty() && loadedBitmap == null

    fun loadBitmap(bitmap: Bitmap) {
        loadedBitmap = bitmap; strokes.clear(); redoStack.clear(); invalidate()
    }

    fun toBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp))
        return bmp
    }
}
