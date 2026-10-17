package com.example.arkanoid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** UI-thread animation avoids races between touches, restart and rendering. */
class GameView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val engine = GameEngine()
    private val prefs = context.getSharedPreferences("arkanoid", Context.MODE_PRIVATE)
    private var best = prefs.getInt("best_score", 0)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastFrame = 0L
    private var active = false
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    val isPaused get() = engine.paused

    fun resetGame() { engine.resetGame(); lastFrame = 0; invalidate() }
    fun togglePause() { engine.paused = !engine.paused; lastFrame = 0; invalidate() }
    fun resumeRendering() { active = true; lastFrame = 0; invalidate() }
    fun pauseRendering() { active = false; lastFrame = 0; saveRecord() }

    private fun saveRecord() {
        if (engine.score > best) {
            best = engine.score
            prefs.edit().putInt("best_score", best).apply()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        if (active && lastFrame != 0L) engine.update((now - lastFrame) / 1_000_000_000f)
        lastFrame = now
        saveRecord()
        canvas.drawColor(Color.rgb(26, 35, 126))
        scale = min(width / GameEngine.WIDTH, height / GameEngine.HEIGHT)
        if (scale <= 0) return
        offsetX = (width - GameEngine.WIDTH * scale) / 2
        offsetY = (height - GameEngine.HEIGHT * scale) / 2
        canvas.save()
        canvas.translate(offsetX, offsetY); canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        engine.bricks.forEach {
            paint.color = it.color
            canvas.drawRoundRect(it.x, it.y, it.x + it.width, it.y + it.height, 6f, 6f, paint)
        }
        paint.color = Color.WHITE
        canvas.drawRoundRect(engine.paddleX - engine.paddleWidth / 2, GameEngine.PADDLE_Y - 15,
            engine.paddleX + engine.paddleWidth / 2, GameEngine.PADDLE_Y + 15, 15f, 15f, paint)
        engine.balls.forEach { canvas.drawCircle(it.x, it.y, GameEngine.RADIUS, paint) }
        engine.bonuses.forEach {
            paint.color = when (it.type) {
                BonusType.EXTRA_BALL -> Color.rgb(30, 136, 229)
                BonusType.WIDE_PADDLE -> Color.rgb(67, 160, 71)
                BonusType.BOMB -> Color.rgb(229, 57, 53)
            }
            canvas.drawCircle(it.x, it.y, 28f, paint)
            text(canvas, when (it.type) { BonusType.EXTRA_BALL -> "+"; BonusType.WIDE_PADDLE -> "↔"; BonusType.BOMB -> "!" }, it.x, it.y + 13, 36f, Color.WHITE, true)
        }
        engine.explosions.forEach {
            paint.color = Color.RED; paint.style = Paint.Style.STROKE; paint.strokeWidth = 8f
            paint.alpha = ((1 - it.age / 0.6f) * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(it.x, it.y, GameEngine.BOMB_RADIUS * it.age / 0.6f, paint)
        }
        paint.style = Paint.Style.FILL; paint.alpha = 255
        text(canvas, "Счёт: ${engine.score}", 30f, 65f, 42f)
        text(canvas, "Рекорд: $best", 550f, 65f, 42f)
        text(canvas, "Уровень ${engine.level}/3", 30f, 115f, 32f)
        if (engine.wideSeconds > 0) text(canvas, "Ракетка: ${kotlin.math.ceil(engine.wideSeconds).toInt()} с", 550f, 115f, 32f)
        text(canvas, "Ведите пальцем для управления", 540f, 1560f, 32f, Color.LTGRAY, true)
        val message = when {
            engine.gameWin -> "ПОБЕДА!"
            engine.gameOver -> "ИГРА ОКОНЧЕНА"
            engine.paused -> "ПАУЗА"
            else -> null
        }
        if (message != null) {
            paint.color = 0xBB101842.toInt()
            canvas.drawRect(0f, 650f, 1080f, 900f, paint)
            text(canvas, message, 540f, 795f, 68f,
                if (engine.gameWin) Color.GREEN else Color.WHITE, true)
        }
        canvas.restore()
        if (active && !engine.paused && !engine.gameOver && !engine.gameWin) postInvalidateOnAnimation()
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float,
                     color: Int = Color.WHITE, center: Boolean = false) {
        paint.color = color; paint.textSize = size; paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        canvas.drawText(value, x, y, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (!engine.paused && scale > 0) engine.movePaddle((event.x - offsetX) / scale)
                invalidate()
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
