package com.example.timeboxvibe.engine.core

import kotlin.test.Test
import kotlin.test.assertTrue

class VectorOrnamentTest {
    @Test
    fun deCasteljauQuadraticBowsAwayFromChord() {
        val canvas = SoftwareEngineCanvas(72, 40)
        canvas.clear(0)
        val layer = AliasedVectorLayer(canvas)
        layer.drawQuadraticBezierDeCasteljau(2f, 30f, 34f, 2f, 66f, 30f, 7, 1f)

        assertTrue(hasIndex(canvas, 34, 16, 7), "midpoint of the bow should be inked")
        assertTrue(!hasIndex(canvas, 34, 30, 7), "chord midpoint should stay empty")
    }

    @Test
    fun panelPutsInkInCornersAndDoesNotTileEdgeJewels() {
        val canvas = SoftwareEngineCanvas(176, 72)
        canvas.clear(0)
        val layer = AliasedVectorLayer(canvas)
        VectorOrnament.strokeRectFrame(layer, 8f, 8f, 160f, 56f, 10, 1f, VectorFrameKind.PANEL)

        assertTrue(cornerInk(canvas, 8, 8, 10) > 8, "top-left scroll")
        assertTrue(cornerInk(canvas, 152, 8, 10) > 8, "top-right scroll")

        var hits = 0
        var samples = 0
        var x = 32
        while (x <= 144) {
            if (rowHasIndex(canvas, x, 14, 10)) hits++
            samples++
            x += 16
        }
        assertTrue(hits < samples / 2, "panel edges are sparse arches, not a jewel chain ($hits/$samples)")
    }

    @Test
    fun smallFrameUsesFewerPixelsThanPanel() {
        val panelCanvas = SoftwareEngineCanvas(120, 48)
        val smallCanvas = SoftwareEngineCanvas(120, 48)
        panelCanvas.clear(0)
        smallCanvas.clear(0)
        VectorOrnament.strokeRectFrame(AliasedVectorLayer(panelCanvas), 4f, 4f, 112f, 40f, 5, 1f, VectorFrameKind.PANEL)
        VectorOrnament.strokeRectFrame(AliasedVectorLayer(smallCanvas), 4f, 4f, 112f, 40f, 5, 1f, VectorFrameKind.SMALL)

        val panelInk = countIndex(panelCanvas, 5)
        val smallInk = countIndex(smallCanvas, 5)
        assertTrue(smallInk > 80, "small frame still strokes a rectangle")
        assertTrue(panelInk > smallInk, "panel has scrolls and arches ($panelInk vs $smallInk)")
    }

    @Test
    fun medallionHasFourPetalDirections() {
        val canvas = SoftwareEngineCanvas(48, 48)
        canvas.clear(0)
        VectorOrnament.strokeMedallion(AliasedVectorLayer(canvas), 24f, 24f, 16f, 12, 1f)

        assertTrue(hasIndex(canvas, 24, 10, 12), "up petal")
        assertTrue(hasIndex(canvas, 38, 24, 12), "right petal")
        assertTrue(hasIndex(canvas, 24, 38, 12), "down petal")
        assertTrue(hasIndex(canvas, 10, 24, 12), "left petal")
    }

    private fun hasIndex(canvas: SoftwareEngineCanvas, x: Int, y: Int, color: Int): Boolean {
        var dy = -1
        while (dy <= 1) {
            var dx = -1
            while (dx <= 1) {
                if (canvas.framebuffer.colorIndexAt(x + dx, y + dy) == color) return true
                dx++
            }
            dy++
        }
        return false
    }

    private fun rowHasIndex(canvas: SoftwareEngineCanvas, x: Int, y: Int, color: Int): Boolean {
        var dx = -2
        while (dx <= 2) {
            if (canvas.framebuffer.colorIndexAt(x + dx, y) == color) return true
            if (canvas.framebuffer.colorIndexAt(x + dx, y + 1) == color) return true
            dx++
        }
        return false
    }

    private fun cornerInk(canvas: SoftwareEngineCanvas, left: Int, top: Int, color: Int): Int {
        var count = 0
        var y = top
        while (y < top + 16) {
            var x = left
            while (x < left + 16) {
                if (canvas.framebuffer.colorIndexAt(x, y) == color) count++
                x++
            }
            y++
        }
        return count
    }

    private fun countIndex(canvas: SoftwareEngineCanvas, color: Int): Int {
        var count = 0
        var y = 0
        while (y < canvas.framebuffer.height) {
            var x = 0
            while (x < canvas.framebuffer.width) {
                if (canvas.framebuffer.colorIndexAt(x, y) == color) count++
                x++
            }
            y++
        }
        return count
    }
}
