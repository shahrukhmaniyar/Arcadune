package com.swordfish.touchinput.skin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.swordfish.touchinput.radial.settings.TouchControllerID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Making buttons bigger (or smaller) in Edit Controls never puts one button on another. The
 * overlap check here is written apart from the app's: round buttons, sticks and d-pads are
 * circles, everything else is a box, and upright captions count as part of their button.
 * Sizes are changed the way the player does it: one press of + at a time.
 */
class SkinSpreadTest {
    /** One surface the editor can draw on: its size in dp, and whether it is a sideways (pinned) one. */
    private data class Surface(
        val name: String,
        val pinned: Boolean,
        val width: Float,
        val height: Float,
    )

    private val sideways =
        listOf(
            800f to 360f,
            960f to 380f,
            640f to 360f,
            915f to 412f,
            840f to 700f,
            600f to 450f,
            1280f to 800f,
            1024f to 768f,
        )
    private val uprightWidths = listOf(360f, 411f, 430f, 600f)

    private fun surfacesFor(
        layout: SkinLayout,
        landscape: Boolean,
    ): List<Surface> =
        if (landscape) {
            sideways.map { (w, h) -> Surface("${w.toInt()}x${h.toInt()}", true, w - 8f, h - 8f) }
        } else {
            uprightWidths.map { w -> Surface("${w.toInt()}w", false, w, layout.height * w / layout.width) }
        }

    private fun everyLayout(): List<Triple<String, SkinLayout, Boolean>> =
        SkinVariant.entries.flatMap { variant ->
            TouchControllerID.entries.mapNotNull { id ->
                ArcaduneSkins.forController(id, variant)?.let { skin -> id to skin }
                    ?.let { (id, skin) ->
                        listOf(
                            Triple("$id $variant portrait", skin.portrait, false),
                            Triple("$id $variant landscape", skin.landscape, true),
                        )
                    }
            }.flatten()
        }

    /** A control as drawn: a circle (centre, radius) or a box. */
    private class Shape(
        val box: Rect,
        val circle: Boolean,
    )

    private fun SkinControl.isRound(): Boolean =
        this is SkinControl.Dpad ||
            this is SkinControl.Stick ||
            (this is SkinControl.Button && shape == ButtonShape.ROUND)

    private fun SkinLayout.shapes(
        surface: Surface,
        customization: SkinCustomization,
        includeHidden: Boolean = false,
    ): Map<Int, Shape> {
        val geometry = SkinGeometry(this, surface.pinned, surface.width, surface.height)
        val rects = geometry.frameRects(customization, rewindAvailable = true, includeHidden = includeHidden)
        return rects.associate { (i, r) ->
            val c = controls[i]
            val captionUnits =
                if (!surface.pinned && c is SkinControl.Button && c.labelBelow && c.icon == null &&
                    style != SkinStyle.OVERLAY
                ) {
                    11f * c.captionScale
                } else {
                    0f
                }
            val square = abs(r.width - r.height) <= 0.12f * maxOf(r.width, r.height)
            val round = c.isRound() && square && captionUnits == 0f
            i to Shape(r.copy(bottom = r.bottom + captionUnits * geometry.scale), round)
        }
    }

    /** True when the two overlap by more than half a dp. */
    private fun Shape.cuts(o: Shape): Boolean {
        if (circle && o.circle) {
            val d = hypot(box.center.x - o.box.center.x, box.center.y - o.box.center.y)
            return d < minOf(box.width, box.height) / 2f + minOf(o.box.width, o.box.height) / 2f - 0.5f
        }
        if (circle != o.circle) {
            val c = if (circle) this else o
            val b = if (circle) o.box else box
            val p = c.box.center
            val nx = p.x.coerceIn(b.left, b.right)
            val ny = p.y.coerceIn(b.top, b.bottom)
            val d = hypot(p.x - nx, p.y - ny)
            if (d > 0.01f) return d < minOf(c.box.width, c.box.height) / 2f - 0.5f
        }
        val w = minOf(box.right, o.box.right) - maxOf(box.left, o.box.left)
        val h = minOf(box.bottom, o.box.bottom) - maxOf(box.top, o.box.top)
        return w > 0.5f && h > 0.5f
    }

    private fun overlapsIn(s: Map<Int, Shape>): Set<Pair<Int, Int>> {
        val ids = s.keys.sorted()
        return buildSet {
            for (x in ids.indices) {
                for (y in x + 1 until ids.size) {
                    if (s.getValue(ids[x]).cuts(s.getValue(ids[y]))) add(ids[x] to ids[y])
                }
            }
        }
    }

    private fun SkinLayout.overlaps(
        surface: Surface,
        customization: SkinCustomization,
    ) = overlapsIn(shapes(surface, customization))

    /** Overlaps the skin is drawn with (kept as they are). */
    private fun SkinLayout.designed(surface: Surface) =
        overlapsIn(shapes(surface, defaultCustomization, includeHidden = true))

    private fun SkinLayout.spread(
        surface: Surface,
        before: SkinCustomization,
        after: SkinCustomization,
    ) = spreadForGrowth(surface.pinned, surface.width, surface.height, before, after, rewindAvailable = true)

    private fun SkinLayout.pressAllUntil(
        surface: Surface,
        from: SkinCustomization,
        target: Float,
    ): SkinCustomization {
        var c = from
        var asked = from.size
        val up = target >= asked
        while (abs(target - asked) > 0.001f) {
            asked = if (up) minOf(asked + 0.05f, target) else maxOf(asked - 0.05f, target)
            c = spread(surface, c, c.copy(size = asked))
        }
        return c
    }

    private fun SkinLayout.name(i: Int): String =
        when (val c = controls[i]) {
            is SkinControl.Button -> c.label
            is SkinControl.Dpad -> "D-pad"
            is SkinControl.Stick -> "stick"
            is SkinControl.Menu -> "menu"
            is SkinControl.Rewind -> "rewind"
        }

    @Test
    fun nothingChangesWhenNoSizeChanged() {
        for ((name, layout, landscape) in everyLayout()) {
            val base = layout.defaultCustomization
            for (surface in surfacesFor(layout, landscape)) {
                val same = base.copy()
                assertSame("$name ${surface.name}: unchanged", same, layout.spread(surface, base, same))

                val moved = base.copy(offsets = base.offsets + (0 to Offset(5f, 5f)))
                assertSame("$name ${surface.name}: moving", moved, layout.spread(surface, base, moved))
            }
        }
    }

    @Test
    fun allButtonsPressedUpNeverOverlap() {
        val found = mutableListOf<String>()
        for ((name, layout, landscape) in everyLayout()) {
            for (surface in surfacesFor(layout, landscape)) {
                val designed = layout.designed(surface)
                for (target in listOf(1.1f, 1.2f, SkinCustomization.MAX_SIZE)) {
                    val result = layout.pressAllUntil(surface, layout.defaultCustomization, target)
                    (layout.overlaps(surface, result) - designed).forEach { (i, j) ->
                        val pct = (target * 100).toInt()
                        found += "$name ${surface.name} $pct%: ${layout.name(i)} on ${layout.name(j)}"
                    }
                }
            }
        }
        assertTrue("${found.size} overlaps:\n" + found.take(40).joinToString("\n"), found.isEmpty())
    }

    @Test
    fun allButtonsInOneJumpNeverOverlap() {
        val found = mutableListOf<String>()
        for ((name, layout, landscape) in everyLayout()) {
            for (surface in surfacesFor(layout, landscape)) {
                val base = layout.defaultCustomization
                val result = layout.spread(surface, base, base.copy(size = SkinCustomization.MAX_SIZE))
                (layout.overlaps(surface, result) - layout.designed(surface)).forEach { (i, j) ->
                    found += "$name ${surface.name}: ${layout.name(i)} on ${layout.name(j)}"
                }
            }
        }
        assertTrue("${found.size} overlaps:\n" + found.take(40).joinToString("\n"), found.isEmpty())
    }

    @Test
    fun eachButtonPressedUpNeverOverlaps() {
        val found = mutableListOf<String>()
        for ((name, layout, landscape) in everyLayout()) {
            for (surface in surfacesFor(layout, landscape).take(2)) {
                val base = layout.defaultCustomization
                val designed = layout.designed(surface)
                for (index in layout.shapes(surface, base).keys) {
                    var c = base
                    var scale = 1f
                    while (scale < SkinCustomization.MAX_CONTROL_SCALE - 0.001f) {
                        scale = minOf(scale + 0.1f, SkinCustomization.MAX_CONTROL_SCALE)
                        c = layout.spread(surface, c, c.copy(scales = c.scales + (index to scale)))
                    }
                    (layout.overlaps(surface, c) - designed).forEach { (i, j) ->
                        val grown = layout.name(index)
                        found += "$name ${surface.name}: $grown bigger, ${layout.name(i)} on ${layout.name(j)}"
                    }
                }
            }
        }
        assertTrue("${found.size} overlaps:\n" + found.take(40).joinToString("\n"), found.isEmpty())
    }

    @Test
    fun allButtonsPressedDownNeverOverlap() {
        val found = mutableListOf<String>()
        for ((name, layout, landscape) in everyLayout()) {
            for (surface in surfacesFor(layout, landscape)) {
                val big = layout.pressAllUntil(surface, layout.defaultCustomization, SkinCustomization.MAX_SIZE)
                val small = layout.pressAllUntil(surface, big, SkinCustomization.MIN_SIZE)
                (layout.overlaps(surface, small) - layout.designed(surface)).forEach { (i, j) ->
                    found += "$name ${surface.name}: ${layout.name(i)} on ${layout.name(j)}"
                }
            }
        }
        assertTrue("${found.size} overlaps:\n" + found.take(40).joinToString("\n"), found.isEmpty())
    }

    @Test
    fun aDiamondStaysADiamond() {
        // The SNES face buttons resize as one group: the diamond keeps its exact shape (every distance
        // between two of them changes by the same factor) and the buttons keep the space between them.
        for (landscape in listOf(false, true)) {
            val skin = ArcaduneSkins.forController(TouchControllerID.SNES)!!
            val layout = if (landscape) skin.landscape else skin.portrait
            val surface = surfacesFor(layout, landscape).first()
            val face =
                layout.controls.indices.filter {
                    val c = layout.controls[it]
                    c is SkinControl.Button && c.label in setOf("A", "B", "X", "Y")
                }
            assertEquals(4, face.size)
            val base = layout.defaultCustomization
            for (size in listOf(0.8f, 1.2f)) {
                val resized = layout.spread(surface, base, base.copy(size = size))
                assertEquals(size, resized.size, 0.001f)
                val b = layout.shapes(surface, base)
                val g = layout.shapes(surface, resized)
                val factors = mutableListOf<Float>()
                for (x in face.indices) {
                    for (y in x + 1 until face.size) {
                        val p = b.getValue(face[x]).box.center - b.getValue(face[y]).box.center
                        val q = g.getValue(face[x]).box.center - g.getValue(face[y]).box.center
                        factors += q.getDistance() / p.getDistance()
                    }
                }
                assertTrue("landscape=$landscape $size: same shape $factors", factors.max() - factors.min() < 0.01f)

                // Each button keeps the space to its nearest neighbour.
                fun nearestGap(
                    s: Map<Int, Shape>,
                    i: Int,
                ) = face.filter { it != i }.minOf { j ->
                    (s.getValue(i).box.center - s.getValue(j).box.center).getDistance() -
                        (s.getValue(i).box.width + s.getValue(j).box.width) / 2f
                }
                for (i in face) {
                    assertEquals("landscape=$landscape $size: space kept", nearestGap(b, i), nearestGap(g, i), 0.5f)
                }
            }
        }
    }

    @Test
    fun rowsStayStraight() {
        // L and R, and the row of small buttons, stay level with each other when buttons get bigger (within
        // 5 dp: a button grouped with one above it, the N64 START under Z, moves a little with its group).
        fun SkinControl.kind() =
            when (this) {
                is SkinControl.Button -> shape
                is SkinControl.Menu, is SkinControl.Rewind -> ButtonShape.PILL
                else -> null
            }
        val found = mutableListOf<String>()
        for ((name, layout, landscape) in everyLayout()) {
            for (surface in surfacesFor(layout, landscape).take(3)) {
                val base = layout.defaultCustomization
                val before = layout.shapes(surface, base)
                val after = layout.shapes(surface, layout.pressAllUntil(surface, base, SkinCustomization.MAX_SIZE))
                val ids = before.keys.sorted()
                for (x in ids.indices) {
                    for (y in x + 1 until ids.size) {
                        val i = ids[x]
                        val j = ids[y]
                        val kind = layout.controls[i].kind() ?: continue
                        if (layout.controls[j].kind() != kind) continue
                        val a = before.getValue(i).box
                        val b = before.getValue(j).box
                        if (abs(a.center.y - b.center.y) > 0.5f) continue
                        val dy = abs(after.getValue(i).box.center.y - after.getValue(j).box.center.y)
                        val pair = "${layout.name(i)} and ${layout.name(j)}"
                        if (dy > 5f) found += "$name ${surface.name}: $pair $dy dp apart"
                    }
                }
            }
        }
        assertTrue("${found.size}:\n" + found.take(30).joinToString("\n"), found.isEmpty())
    }

    @Test
    fun smallerThenBackComesBackToTheSameLayout() {
        val found = mutableListOf<String>()
        for ((name, layout, landscape) in everyLayout()) {
            val surface = surfacesFor(layout, landscape).first()
            val base = layout.defaultCustomization
            val back = layout.pressAllUntil(surface, layout.pressAllUntil(surface, base, 0.8f), base.size)
            val a = layout.shapes(surface, base)
            val b = layout.shapes(surface, back)
            for ((i, s) in a) {
                val d = (s.box.center - b.getValue(i).box.center).getDistance()
                // Within 1.5 dp: two separate buttons that were closer than the 3 dp gap may come back that 3 dp apart.
                if (d > 1.5f) found += "$name: ${layout.name(i)} ends ${"%.1f".format(d)} dp away"
            }
        }
        assertTrue(found.take(30).joinToString("\n"), found.isEmpty())
    }

    @Test
    fun layoutsMadeBigBeforeThisAreCleanedUp() {
        // 1.1.2 and earlier let buttons grow into each other. Size changes never make that worse, and
        // pressing All buttons down to 110% clears it everywhere.
        val left = mutableListOf<String>()
        var before = 0
        for ((name, layout, landscape) in everyLayout()) {
            for (surface in surfacesFor(layout, landscape)) {
                val designed = layout.designed(surface)
                val old = layout.defaultCustomization.copy(size = SkinCustomization.MAX_SIZE)
                val overlapsBefore = layout.overlaps(surface, old) - designed
                val first = layout.spread(surface, old, old.copy(size = SkinCustomization.MAX_SIZE - 0.05f))
                val overlapsFirst = layout.overlaps(surface, first) - designed
                assertTrue("$name ${surface.name}: never worse", overlapsBefore.containsAll(overlapsFirst))
                val result = layout.pressAllUntil(surface, first, 1.1f)
                val overlapsAfter = layout.overlaps(surface, result) - designed
                before += overlapsBefore.size
                overlapsAfter.forEach {
                        (i, j) ->
                    left += "$name ${surface.name}: ${layout.name(i)} on ${layout.name(j)}"
                }
            }
        }
        assertTrue(before > 0)
        assertTrue("old overlaps: $before, left: ${left.size}\n" + left.take(30).joinToString("\n"), left.isEmpty())
    }

    @Test
    fun buttonsStayWholeOnTheScreen() {
        // No button is pushed or grown past an edge (further than the layout itself has it there).
        val found = mutableListOf<String>()
        for ((name, layout, landscape) in everyLayout()) {
            val base = layout.defaultCustomization
            for (surface in surfacesFor(layout, landscape)) {
                val geometry = SkinGeometry(layout, surface.pinned, surface.width, surface.height)
                val top = -layout.unitsAbove() * geometry.scale

                fun over(r: Rect) =
                    listOf(-r.left, top - r.top, r.right - surface.width, r.bottom - surface.height).map {
                        maxOf(0f, it)
                    }
                val start = layout.shapes(surface, base).mapValues { over(it.value.box) }
                val big = layout.pressAllUntil(surface, base, SkinCustomization.MAX_SIZE)
                val results =
                    listOf(big) +
                        layout.shapes(surface, base).keys.map { index ->
                            var c = base
                            repeat(6) {
                                val bigger = c.scales + (index to c.controlScale(index) + 0.1f)
                                c = layout.spread(surface, c, c.copy(scales = bigger))
                            }
                            c
                        }
                for (result in results) {
                    // As far as the layout itself draws a button past an edge at that size, and no further.
                    val drawn =
                        layout.shapes(surface, base.copy(size = result.size, scales = result.scales))
                            .mapValues { over(it.value.box) }
                    for ((i, s) in layout.shapes(surface, result)) {
                        val o = over(s.box)
                        val a = start[i] ?: listOf(0f, 0f, 0f, 0f)
                        val b = drawn[i] ?: listOf(0f, 0f, 0f, 0f)
                        val limit = a.zip(b) { x, y -> maxOf(x, y) }
                        if ((0 until 4).any { o[it] > limit[it] + 0.6f }) {
                            found += "$name ${surface.name}: ${layout.name(i)} goes past the edge by $o"
                        }
                    }
                }
            }
        }
        assertTrue("${found.size}:\n" + found.distinct().take(20).joinToString("\n"), found.isEmpty())
    }

    @Test
    fun onlyOffsetsChange() {
        for ((name, layout, landscape) in everyLayout()) {
            val base = layout.defaultCustomization
            val surface = surfacesFor(layout, landscape).first()
            val grown = base.copy(size = 1.3f)
            val result = layout.spread(surface, base, grown)
            assertTrue("$name: size between the old and the asked size", result.size in base.size..grown.size)
            assertEquals("$name: scales kept", grown.scales, result.scales)
            assertEquals("$name: hidden kept", grown.hidden, result.hidden)
            assertEquals("$name: shown kept", grown.shown, result.shown)
            assertEquals("$name: opacity kept", grown.opacity, result.opacity)
            assertEquals("$name: screens kept", grown.screenScales, result.screenScales)
        }
    }

    @Test
    fun everyLayoutGrowsFarEnough() {
        // Every sideways layout gets the largest size on every phone, and every upright one at least 105%.
        // Where a layout stops below the largest size (a row of buttons as wide as the screen cannot get
        // wider), it is printed.
        val held = mutableListOf<String>()
        val limits = sortedMapOf<String, Int>()
        for ((name, layout, landscape) in everyLayout()) {
            for (surface in surfacesFor(layout, landscape)) {
                val small = layout.pressAllUntil(surface, layout.defaultCustomization, 1.05f)
                val reached = (small.size * 100).toInt()
                if (small.size < 1.05f - 0.011f) held += "$name ${surface.name}: 105% held at $reached%"
                val big = layout.pressAllUntil(surface, layout.defaultCustomization, SkinCustomization.MAX_SIZE)
                val got = (big.size * 100).toInt()
                if (got < 134) limits[name] = minOf(limits[name] ?: 999, got)
                if (landscape && got < 134) held += "$name ${surface.name}: sideways held at $got%"
            }
        }
        println(
            "Largest size where a layout stops below 135%:\n" +
                limits.entries.joinToString("\n") {
                    "${it.key}: ${it.value}%"
                },
        )
        assertTrue("Held back:\n" + held.take(30).joinToString("\n"), held.isEmpty())
    }
}
