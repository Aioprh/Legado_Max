package io.legado.app.ui.book.read.page.delegate

import android.graphics.Canvas
import android.view.MotionEvent
import android.view.VelocityTracker
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isImage
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.provider.ChapterProvider

class ScrollPageDelegate(readView: ReadView) : PageDelegate(readView) {

    private val velocityDuration = 1000

    private val mVelocity: VelocityTracker = VelocityTracker.obtain()
    private val slopSquare get() = readView.pageSlopSquare2

    var noAnim: Boolean = false
    
    private var lastScrollY = 0
    private var maxScrollY = 0
    private val prefetchThreshold = 0.7f
    
    private var hasTriggeredPrefetch = false

    override fun onAnimStart(animationSpeed: Int) {
        readView.onScrollAnimStart()
        fling(
            0, touchY.toInt(), 0, mVelocity.yVelocity.toInt(),
            0, 0, -10 * viewHeight, 10 * viewHeight
        )
    }

    override fun onAnimStop() {
        readView.onScrollAnimStop()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            readView.setStartPoint(
                event.getX(event.pointerCount - 1),
                event.getY(event.pointerCount - 1),
                false
            )
        } else if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            readView.setStartPoint(event.x, event.y, false)
            return
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                abortAnim()
                mVelocity.clear()
            }

            MotionEvent.ACTION_MOVE -> {
                onScroll(event)
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                onAnimStart(readView.defaultAnimationSpeed)
            }
        }
    }

    override fun onScroll() {
        curPage.scroll((touchY - lastY).toInt())
        checkLazyPrefetch()
    }

    override fun onDraw(canvas: Canvas) {
        // nothing
    }
    
    private fun checkLazyPrefetch() {
        val textChapter = ReadBook.curTextChapter ?: return
        val lazyContent = textChapter.lazyContent ?: return
        
        val scrollY = curPage.scrollY
        val pageHeight = curPage.height
        
        if (pageHeight <= 0) return
        
        val progress = scrollY.toFloat() / pageHeight
        
        if (progress > prefetchThreshold && !hasTriggeredPrefetch) {
            lazyContent.prefetchNextPage()
            hasTriggeredPrefetch = true
        }
        
        if (progress < prefetchThreshold - 0.1f) {
            hasTriggeredPrefetch = false
        }
    }

    private fun onScroll(event: MotionEvent) {
        mVelocity.addMovement(event)
        mVelocity.computeCurrentVelocity(velocityDuration)
        val pointX = event.getX(event.pointerCount - 1)
        val pointY = event.getY(event.pointerCount - 1)
        if (isMoved || readView.isLongScreenShot()) {
            readView.setTouchPoint(pointX, pointY, false)
        }
        if (!isMoved) {
            val deltaX = (pointX - startX).toInt()
            val deltaY = (pointY - startY).toInt()
            val distance = deltaX * deltaX + deltaY * deltaY
            isMoved = distance > slopSquare
            if (isMoved) {
                readView.setStartPoint(event.x, event.y, false)
            }
        }
        if (isMoved) {
            isRunning = true
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            readView.setTouchPoint(scroller.currX.toFloat(), scroller.currY.toFloat(), false)
        } else if (isStarted) {
            onAnimStop()
            stopScroll()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mVelocity.recycle()
    }

    override fun abortAnim() {
        readView.onScrollAnimStop()
        isStarted = false
        isMoved = false
        isRunning = false
        if (!scroller.isFinished) {
            readView.isAbortAnim = true
            scroller.abortAnimation()
        } else {
            readView.isAbortAnim = false
        }
    }

    override fun nextPageByAnim(animationSpeed: Int) {
        if (readView.isAbortAnim) {
            readView.isAbortAnim = false
            return
        }
        if (noAnim) {
            curPage.scroll(calcNextPageOffset())
            return
        }
        readView.setStartPoint(0f, 0f, false)
        startScroll(0, 0, 0, calcNextPageOffset(), animationSpeed)
    }

    override fun prevPageByAnim(animationSpeed: Int) {
        if (readView.isAbortAnim) {
            readView.isAbortAnim = false
            return
        }
        if (noAnim) {
            curPage.scroll(calcPrevPageOffset())
            return
        }
        readView.setStartPoint(0f, 0f, false)
        startScroll(0, 0, 0, calcPrevPageOffset(), animationSpeed)
    }

    private fun calcNextPageOffset(): Int {
        val visibleHeight = ChapterProvider.visibleHeight
        val book = ReadBook.book
        if (book == null || book.isImage) {
            return -visibleHeight
        }
        val visiblePage = readView.getCurVisiblePage()
        val isTextStyle = book.getImageStyle().equals(Book.imgStyleText, true)
        if (!isTextStyle && visiblePage.hasImageOrEmpty()) {
            return -visibleHeight
        }
        val lastLineTop = visiblePage.lines.last().lineTop.toInt()
        val offset = lastLineTop - ChapterProvider.paddingTop
        return -offset
    }

    private fun calcPrevPageOffset(): Int {
        val visibleHeight = ChapterProvider.visibleHeight
        val book = ReadBook.book
        if (book == null || book.isImage) {
            return visibleHeight
        }
        val visiblePage = readView.getCurVisiblePage()
        val isTextStyle = book.getImageStyle().equals(Book.imgStyleText, true)
        if (!isTextStyle && visiblePage.hasImageOrEmpty()) {
            return visibleHeight
        }
        val firstLineBottom = visiblePage.lines.first().lineBottom.toInt()
        val offset = visibleHeight - (firstLineBottom - ChapterProvider.paddingTop)
        return offset
    }
}