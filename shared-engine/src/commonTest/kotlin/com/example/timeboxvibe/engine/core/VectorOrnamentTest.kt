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
            if (rowHasIndex(canvas, x, 22, 10)) hits++
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

    @Test
    fun smallHooksStayNearCornersOnSkinnyButton() {
        val canvas = SoftwareEngineCanvas(28, 56)
        canvas.clear(0)
        VectorOrnament.strokeRectFrame(AliasedVectorLayer(canvas), 2f, 2f, 20f, 48f, 7, 1f, VectorFrameKind.SMALL)

        assertTrue(hasIndex(canvas, 2, 2, 7), "top-left stroke")
        assertTrue(hasIndex(canvas, 21, 2, 7), "top-right sits on fill edge")
        var centerHits = 0
        var y = 20
        while (y <= 32) {
            var x = 11
            while (x <= 15) {
                if (canvas.framebuffer.colorIndexAt(x, y) == 7) centerHits++
                x++
            }
            y++
        }
        assertTrue(centerHits == 0, "skinny button center stays empty ($centerHits)")
    }

    @Test
    fun fillThenStrokeLeavesNoBackgroundGutter() {
        val canvas = SoftwareEngineCanvas(100, 48)
        canvas.clear(0)
        canvas.fillRectDither(8f, 8f, 88f, 40f, 3, 3, SoftDitherPattern.SOLID)
        VectorOrnament.strokeRectFrame(AliasedVectorLayer(canvas), 8f, 8f, 80f, 32f, 10, 1f, VectorFrameKind.PANEL)

        assertTrue(canvas.framebuffer.colorIndexAt(8, 20) == 10, "left stroke on fill edge")
        assertTrue(canvas.framebuffer.colorIndexAt(87, 20) == 10, "right stroke on last fill pixel")
        assertTrue(canvas.framebuffer.colorIndexAt(88, 20) == 0, "outside the widget stays background")
        val inside = canvas.framebuffer.colorIndexAt(20, 9)
        assertTrue(inside == 3 || inside == 10, "just inside the top edge is fill or ink, not a gutter")
    }

    @Test
    fun chromeBandIsFrameColorNotPlayfield() {
        val canvas = SoftwareEngineCanvas(120, 56)
        canvas.clear(0)
        val layer = AliasedVectorLayer(canvas)
        VectorOrnament.paintRectFrame(canvas, layer, 8f, 8f, 96f, 40f, 3, 10, VectorFrameKind.PANEL)

        val band = VectorOrnament.chromeBand(VectorFrameKind.PANEL, 40f)
        assertTrue(band >= 2f, "chrome has thickness")
        val midY = 8 + (band / 2f).toInt()
        val chrome = canvas.framebuffer.colorIndexAt(20, midY)
        assertTrue(chrome == 10, "chrome band is the frame color ($chrome)")
        val interior = canvas.framebuffer.colorIndexAt(40, 28)
        assertTrue(interior == 3, "interior is the panel fill ($interior)")
        assertTrue(canvas.framebuffer.colorIndexAt(7, 20) == 0, "outside stays playfield")
        assertTrue(cornerInk(canvas, 8, 8, 10) > 12, "corner scroll sits on the chrome")
    }

    @Test
    fun fieldPatternIsWavesNotDotLattice() {
        val canvas = SoftwareEngineCanvas(96, 64)
        canvas.clear(0)
        VectorOrnament.strokeFieldPattern(AliasedVectorLayer(canvas), 0f, 0f, 96f, 64f, 1)

        val ink = countIndex(canvas, 1)
        assertTrue(ink > 80, "青海波 has a stroke, not a U-dot lattice ($ink)")

        var isolatedOrigins = 0
        var cells = 0
        var gy = 0
        while (gy < 64) {
            var gx = 0
            while (gx < 96) {
                cells++
                if (canvas.framebuffer.colorIndexAt(gx, gy) == 1 && cellInk(canvas, gx, gy, 1) <= 2) {
                    isolatedOrigins++
                }
                gx += 16
            }
            gy += 16
        }
        assertTrue(isolatedOrigins < cells / 2, "field is not HUD dots ($isolatedOrigins/$cells)")
    }

    @Test
    fun fieldPatternSnapsToWorldGrid() {
        val full = SoftwareEngineCanvas(96, 48)
        val inset = SoftwareEngineCanvas(96, 48)
        full.clear(0)
        inset.clear(0)
        VectorOrnament.strokeFieldPattern(AliasedVectorLayer(full), 0f, 0f, 96f, 48f, 4)
        VectorOrnament.strokeFieldPattern(AliasedVectorLayer(inset), 16f, 16f, 80f, 48f, 4)

        var matches = 0
        var samples = 0
        var y = 16
        while (y < 48) {
            var x = 16
            while (x < 80) {
                if (full.framebuffer.colorIndexAt(x, y) == 4) {
                    samples++
                    if (inset.framebuffer.colorIndexAt(x, y) == 4) matches++
                }
                x++
            }
            y++
        }
        assertTrue(samples > 20, "full field has ink in the overlap ($samples)")
        assertTrue(matches == samples, "header cover redraw lands on the same U tiles ($matches/$samples)")
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

    private fun cellInk(canvas: SoftwareEngineCanvas, originX: Int, originY: Int, color: Int): Int {
        var count = 0
        var y = originY
        while (y < originY + 4 && y < canvas.framebuffer.height) {
            var x = originX
            while (x < originX + 4 && x < canvas.framebuffer.width) {
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
