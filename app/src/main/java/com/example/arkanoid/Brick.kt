package com.example.arkanoid

/** Geometry uses a fixed 1080 × 1600 logical playfield, independent of screen density. */
data class Brick(val x: Float, val y: Float, val width: Float, val height: Float, val color: Int) {
    val centerX get() = x + width / 2
    val centerY get() = y + height / 2
}
