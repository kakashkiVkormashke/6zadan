package com.example.arkanoid

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private fun engine() = GameEngine(Random(42))
    @Test fun levelsAndWin() {
        val game = engine()
        assertEquals(24, game.bricks.size)
        game.bricks.clear(); game.update(0.004f)
        assertEquals(2, game.level); assertEquals(40, game.bricks.size)
        game.bricks.clear(); game.update(0.004f)
        assertEquals(3, game.level); assertEquals(56, game.bricks.size)
        game.bricks.clear(); game.update(0.004f)
        assertTrue(game.gameWin)
    }
    @Test fun pauseFreezesPhysicsAndBonusTimer() {
        val game = engine()
        game.bonuses.add(Bonus(game.paddleX, GameEngine.PADDLE_Y, 0f, 0f, BonusType.WIDE_PADDLE))
        game.update(0.004f)
        assertEquals(300f, game.paddleWidth, 0.001f)
        val y = game.balls.first().y
        game.paused = true
        repeat(3000) { game.update(0.004f) }
        assertEquals(y, game.balls.first().y, 0.001f)
        assertEquals(10f, game.wideSeconds, 0.001f)
    }
    @Test fun wideBonusExpiresAfterTenPlayingSeconds() {
        val game = engine()
        game.balls.clear(); game.balls.add(Ball(500f, 1000f, 0f, 0f))
        game.bonuses.add(Bonus(game.paddleX, GameEngine.PADDLE_Y, 0f, 0f, BonusType.WIDE_PADDLE))
        game.update(0.004f)
        repeat(2501) { game.update(0.004f) }
        assertEquals(200f, game.paddleWidth, 0.001f)
    }
    @Test fun extraBallAndLossOnlyAfterLastBall() {
        val game = engine()
        game.bonuses.add(Bonus(game.paddleX, GameEngine.PADDLE_Y, 0f, 0f, BonusType.EXTRA_BALL))
        game.update(0.004f); assertEquals(2, game.balls.size)
        game.balls.first().y = 1700f
        game.update(0.004f); assertFalse(game.gameOver)
        game.balls.first().y = 1700f
        game.update(0.004f); assertTrue(game.gameOver)
        game.resetGame(); assertEquals(1, game.balls.size); assertEquals(0, game.score)
        assertFalse(game.gameOver)
    }
    @Test fun bombUsesOriginalBrickPositionAndAwardsPoints() {
        val game = engine()
        val origin = game.bricks[10]
        val count = game.bricks.size
        game.bonuses.add(Bonus(game.paddleX, GameEngine.PADDLE_Y, origin.centerX, origin.centerY, BonusType.BOMB))
        game.update(0.004f)
        assertTrue(game.bricks.size < count)
        assertEquals(count - game.bricks.size, game.score)
        assertEquals(origin.centerX, game.explosions.single().x, 0.001f)
        assertTrue(game.bricks.any { it.x > 900f })
    }
    @Test fun collisionScoresAndFifthBrickDropsExtraBall() {
        val game = engine()
        repeat(5) {
            val brick = game.bricks.last()
            game.balls.clear()
            game.balls.add(Ball(brick.centerX, brick.y + brick.height + 21, 0f, -600f))
            game.update(0.004f)
        }
        assertEquals(5, game.score)
        assertEquals(BonusType.EXTRA_BALL, game.bonuses.single().type)
    }
    @Test fun wallAndPaddleBounceAndClamp() {
        val game = engine()
        game.movePaddle(-1000f); assertEquals(100f, game.paddleX, 0.001f)
        game.movePaddle(10000f); assertEquals(980f, game.paddleX, 0.001f)
        game.movePaddle(540f)
        game.balls.clear(); val ball = Ball(540f, 1404f, 0f, 600f); game.balls.add(ball)
        game.update(0.004f); assertTrue(ball.vy < 0)
        ball.x = 20f; ball.y = 1000f; ball.vx = -600f
        game.update(0.004f); assertTrue(ball.vx > 0)
    }
}
