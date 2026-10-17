package com.example.arkanoid

import kotlin.math.*
import kotlin.random.Random

data class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float)
enum class BonusType { EXTRA_BALL, WIDE_PADDLE, BOMB }
data class Bonus(var x: Float, var y: Float, val originX: Float, val originY: Float, val type: BonusType)
data class Explosion(val x: Float, val y: Float, var age: Float = 0f)

/** Pure Kotlin game rules; all updates and input run on the UI thread. */
class GameEngine(private val random: Random = Random.Default) {
    companion object {
        const val WIDTH = 1080f
        const val HEIGHT = 1600f
        const val PADDLE_Y = 1440f
        const val RADIUS = 20f
        const val MAX_SPEED = 1500f
        const val BOMB_RADIUS = 100f
    }
    val bricks = mutableListOf<Brick>()
    val balls = mutableListOf<Ball>()
    val bonuses = mutableListOf<Bonus>()
    val explosions = mutableListOf<Explosion>()
    var score = 0; private set
    var level = 1; private set
    var gameOver = false; private set
    var gameWin = false; private set
    var paused = false
    var paddleX = WIDTH / 2; private set
    var wideSeconds = 0f; private set
    val paddleWidth get() = if (wideSeconds > 0) 300f else 200f
    private var destroyed = 0
    private val recentDestroyed = mutableListOf<Brick>()

    init { resetGame() }

    fun resetGame() {
        score = 0; level = 1; destroyed = 0; recentDestroyed.clear()
        gameOver = false; gameWin = false; paused = false
        startLevel()
    }

    private fun startLevel() {
        paddleX = WIDTH / 2; wideSeconds = 0f
        balls.clear(); bonuses.clear(); explosions.clear(); bricks.clear()
        balls.add(Ball(WIDTH / 2, PADDLE_Y - 100, 600f, -600f))
        val colors = intArrayOf(0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(),
            0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFF00ACC1.toInt())
        repeat(level * 2 + 1) { row ->
            repeat(8) { col -> bricks.add(Brick(col * 135f + 4, 200 + row * 80f + 4, 127f, 72f, colors[row])) }
        }
    }

    fun movePaddle(x: Float) { paddleX = x.coerceIn(paddleWidth / 2, WIDTH - paddleWidth / 2) }

    fun update(seconds: Float) {
        if (paused || gameOver || gameWin || seconds <= 0) return
        // Substeps prevent fast balls passing through thin objects after a slow frame.
        val count = ceil(seconds.coerceAtMost(0.05f) / (1f / 240f)).toInt().coerceAtLeast(1)
        val dt = seconds.coerceAtMost(0.05f) / count
        repeat(count) {
            step(dt)
            if (gameOver || gameWin) return
        }
    }

    private fun step(dt: Float) {
        wideSeconds = (wideSeconds - dt).coerceAtLeast(0f)
        movePaddle(paddleX)
        explosions.forEach { it.age += dt }
        explosions.removeAll { it.age > 0.6f }
        balls.toList().forEach { ball ->
            val oldX = ball.x; val oldY = ball.y
            ball.x += ball.vx * dt; ball.y += ball.vy * dt
            if (ball.x < RADIUS) { ball.x = RADIUS; ball.vx = abs(ball.vx) }
            if (ball.x > WIDTH - RADIUS) { ball.x = WIDTH - RADIUS; ball.vx = -abs(ball.vx) }
            if (ball.y < 130 + RADIUS) { ball.y = 130 + RADIUS; ball.vy = abs(ball.vy) }
            if (ball.vy > 0 && oldY + RADIUS <= PADDLE_Y - 15 && ball.y + RADIUS >= PADDLE_Y - 15 &&
                abs(ball.x - paddleX) <= paddleWidth / 2 + RADIUS) {
                ball.y = PADDLE_Y - 15 - RADIUS
                val speed = (hypot(ball.vx, ball.vy) * 1.02f).coerceAtMost(MAX_SPEED)
                val angle = ((ball.x - paddleX) / (paddleWidth / 2)).coerceIn(-1f, 1f) * 1.05f
                ball.vx = sin(angle) * speed; ball.vy = -cos(angle) * speed
            }
            val brick = bricks.firstOrNull { intersects(ball.x, ball.y, RADIUS, it.x, it.y, it.width, it.height) }
            if (brick != null) {
                if (oldX + RADIUS <= brick.x || oldX - RADIUS >= brick.x + brick.width) {
                    ball.x = if (ball.vx > 0) brick.x - RADIUS else brick.x + brick.width + RADIUS
                    ball.vx = -ball.vx
                } else {
                    ball.y = if (oldY < brick.centerY) brick.y - RADIUS else brick.y + brick.height + RADIUS
                    ball.vy = -ball.vy
                }
                destroyBrick(brick)
                val speed = hypot(ball.vx, ball.vy)
                val factor = min(1.05f, MAX_SPEED / speed)
                ball.vx *= factor; ball.vy *= factor
            }
        }
        balls.removeAll { it.y - RADIUS > HEIGHT }
        val caught = mutableListOf<Bonus>()
        bonuses.forEach {
            it.y += 300f * dt
            if (intersects(it.x, it.y, 26f, paddleX - paddleWidth / 2, PADDLE_Y - 15, paddleWidth, 30f)) caught.add(it)
        }
        bonuses.removeAll(caught.toSet())
        bonuses.removeAll { it.y > HEIGHT + 30 }
        caught.forEach { applyBonus(it) }
        if (bricks.isEmpty()) {
            if (level == 3) gameWin = true else { level++; startLevel() }
        } else if (balls.isEmpty()) gameOver = true
    }

    private fun destroyBrick(brick: Brick) {
        if (!bricks.remove(brick)) return
        score++; destroyed++; recentDestroyed.add(brick)
        if (destroyed % 5 == 0) {
            // Use a random source among the five just-destroyed bricks, including blast hits.
            val type = if ((destroyed / 5) % 3 == 1) BonusType.EXTRA_BALL
                else if ((destroyed / 5) % 3 == 2) BonusType.WIDE_PADDLE else BonusType.BOMB
            val origin = recentDestroyed.random(random)
            recentDestroyed.clear()
            bonuses.add(Bonus(origin.centerX, origin.centerY, origin.centerX, origin.centerY, type))
        }
    }

    private fun applyBonus(bonus: Bonus) {
        when (bonus.type) {
            BonusType.EXTRA_BALL -> {
                val source = balls.randomOrNull(random)
                val extra = if (source != null) {
                    val vx = if (abs(source.vx) < 60f) 300f else -source.vx
                    Ball(source.x, source.y, vx, -abs(source.vy).coerceAtLeast(300f))
                } else Ball(paddleX, PADDLE_Y - 60, 600f, -600f)
                val factor = min(1f, MAX_SPEED / hypot(extra.vx, extra.vy))
                extra.vx *= factor; extra.vy *= factor
                balls.add(extra)
            }
            BonusType.WIDE_PADDLE -> { wideSeconds = 10f; movePaddle(paddleX) }
            BonusType.BOMB -> {
                // Circle/rectangle distance also catches bricks intersecting the blast edge.
                bricks.filter {
                    val dx = bonus.originX - bonus.originX.coerceIn(it.x, it.x + it.width)
                    val dy = bonus.originY - bonus.originY.coerceIn(it.y, it.y + it.height)
                    dx * dx + dy * dy <= BOMB_RADIUS * BOMB_RADIUS
                }.forEach { destroyBrick(it) }
                explosions.add(Explosion(bonus.originX, bonus.originY))
            }
        }
    }

    private fun intersects(cx: Float, cy: Float, r: Float, x: Float, y: Float, w: Float, h: Float): Boolean {
        val dx = cx - cx.coerceIn(x, x + w); val dy = cy - cy.coerceIn(y, y + h)
        return dx * dx + dy * dy <= r * r
    }
}
