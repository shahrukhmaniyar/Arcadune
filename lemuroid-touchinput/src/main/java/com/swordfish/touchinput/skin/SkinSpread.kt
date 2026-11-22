package com.swordfish.touchinput.skin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.hypot

/** The space left between two buttons that had to be moved apart, in dp. */
private const val SPREAD_GAP_DP = 3f
private const val SPREAD_MAX_ROUNDS = 80

/** Two shapes must overlap by more than this (dp) to count: edge to edge is not an overlap. */
private const val OVERLAP_DP = 0.5f

/** A size change smaller than this is no change. */
private const val SIZE_EPSILON = 0.004f

/** Buttons of one kind closer than this share of the smaller one's size belong together (a diamond, a row). */
private const val GROUP_GAP_SHARE = 0.5f

/** The most a group's spacing opens up to make room for one bigger button. */
private const val MAX_GROUP_STRETCH = 3f

/** When the asked size does not fit: the share of the change tried first, and how many halvings follow. */
private const val SEARCH_FIRST_STEP = 1f / 32f
private const val SEARCH_ROUNDS = 7

/** Room for the name drawn under small upright buttons, in skin units (the same as the editor's hit test). */
private const val CAPTION_UNITS = 11f

/**
 * One control as it is drawn: a circle for round buttons, sticks and d-pads (whose square boxes
 * meet at the corners in a diamond while the buttons themselves do not), a box for the rest.
 */
private class Piece(
    val rect: Rect,
    val round: Boolean,
) {
    val radius: Float get() = minOf(rect.width, rect.height) / 2f

    fun movedTo(center: Offset) = Piece(rect.translate(center - rect.center), round)
}

private fun SkinControl.isRoundShape(): Boolean =
    when (this) {
        is SkinControl.Button -> shape == ButtonShape.ROUND
        is SkinControl.Stick, is SkinControl.Dpad -> true
        is SkinControl.Menu, is SkinControl.Rewind -> false
    }

/** Which controls may form a group: round buttons, the small pills, the shoulder buttons, each with their own kind. */
private fun SkinControl.groupKind(): Int? =
    when (this) {
        is SkinControl.Button ->
            when (shape) {
                ButtonShape.ROUND -> 0
                ButtonShape.PILL -> 1
                ButtonShape.SHOULDER -> 2
            }
        is SkinControl.Menu, is SkinControl.Rewind -> 1
        is SkinControl.Dpad, is SkinControl.Stick -> null
    }

private fun SkinLayout.pieces(
    geometry: SkinGeometry,
    pinToEdges: Boolean,
    customization: SkinCustomization,
    rewindAvailable: Boolean,
    fastForwardSupported: Boolean,
    includeHidden: Boolean = false,
): Map<Int, Piece> =
    geometry.frameRects(customization, rewindAvailable, includeHidden, fastForwardSupported).associate { (i, r) ->
        val control = controls[i]
        val captioned =
            !pinToEdges &&
                control is SkinControl.Button &&
                control.labelBelow &&
                control.icon == null &&
                style != SkinStyle.OVERLAY
        val caption = if (control is SkinControl.Button) CAPTION_UNITS * control.captionScale * geometry.scale else 0f
        val rect = if (captioned) r.copy(bottom = r.bottom + caption) else r
        val square = r.width > 0f && abs(r.width - r.height) <= 0.12f * maxOf(r.width, r.height)
        i to Piece(rect, control.isRoundShape() && square && !captioned)
    }

/**
 * How far [b] has to move away from [a] to leave [gap] dp between them, or null when it already
 * does. A negative [gap] asks whether they overlap by more than that.
 */
private fun push(
    a: Piece,
    b: Piece,
    gap: Float,
): Offset? {
    if (a.round && b.round) {
        val d = b.rect.center - a.rect.center
        val dist = d.getDistance()
        val need = a.radius + b.radius + gap - dist
        if (need <= 0f) return null
        val dir = if (dist < 0.01f) Offset(1f, 0f) else d / dist
        return dir * need
    }
    if (a.round != b.round) {
        val circle = if (a.round) a else b
        val box = if (a.round) b.rect else a.rect
        val c = circle.rect.center
        val closest = Offset(c.x.coerceIn(box.left, box.right), c.y.coerceIn(box.top, box.bottom))
        val d = c - closest
        val dist = d.getDistance()
        if (dist > 0.01f) {
            val need = circle.radius + gap - dist
            if (need <= 0f) return null
            val away = d / dist * need
            return if (b.round) away else -away
        }
    }
    val r1 = a.rect
    val r2 = b.rect
    val penX = minOf(r1.right, r2.right) - maxOf(r1.left, r2.left) + gap
    val penY = minOf(r1.bottom, r2.bottom) - maxOf(r1.top, r2.top) + gap
    if (penX <= 0f || penY <= 0f) return null
    return if (penX <= penY) {
        Offset(if (r2.center.x >= r1.center.x) penX else -penX, 0f)
    } else {
        Offset(0f, if (r2.center.y >= r1.center.y) penY else -penY)
    }
}

private fun overlapPairs(pieces: Map<Int, Piece>): Set<Pair<Int, Int>> {
    val ids = pieces.keys.sorted()
    return buildSet {
        for (x in ids.indices) {
            for (y in x + 1 until ids.size) {
                if (push(pieces.getValue(ids[x]), pieces.getValue(ids[y]), -OVERLAP_DP) != null) add(ids[x] to ids[y])
            }
        }
    }
}

/** The space between the two shapes, in dp: negative when they overlap. */
private fun gapBetween(
    a: Piece,
    b: Piece,
): Float {
    if (a.round && b.round) return (b.rect.center - a.rect.center).getDistance() - a.radius - b.radius
    if (a.round != b.round) {
        val circle = if (a.round) a else b
        val box = if (a.round) b.rect else a.rect
        val c = circle.rect.center
        val closest = Offset(c.x.coerceIn(box.left, box.right), c.y.coerceIn(box.top, box.bottom))
        val dist = (c - closest).getDistance()
        if (dist > 0.01f) return dist - circle.radius
    }
    val dx = maxOf(b.rect.left - a.rect.right, a.rect.left - b.rect.right)
    val dy = maxOf(b.rect.top - a.rect.bottom, a.rect.top - b.rect.bottom)
    return if (dx > 0f && dy > 0f) hypot(dx, dy) else maxOf(dx, dy)
}

/**
 * The space two buttons keep while sizes change: what they had (a group keeps its shape), at most
 * [SPREAD_GAP_DP] when [atMostSmallGap], and [SPREAD_GAP_DP] when they were overlapping.
 */
private fun keptGap(
    before: Float,
    atMostSmallGap: Boolean,
): Float =
    when {
        before < -OVERLAP_DP -> SPREAD_GAP_DP
        atMostSmallGap -> before.coerceIn(0f, SPREAD_GAP_DP)
        else -> before.coerceAtLeast(0f)
    }

private fun pairOf(
    i: Int,
    j: Int,
) = if (i < j) i to j else j to i

/** Buttons that sit together (a diamond, a pair, a row of pills) as the player has them now. */
private fun SkinLayout.groups(
    pieces: Map<Int, Piece>,
    customization: SkinCustomization,
): List<List<Int>> {
    val ids = pieces.keys.sorted()
    val parent = ids.associateWith { it }.toMutableMap()

    fun root(i: Int): Int {
        var r = i
        while (parent.getValue(r) != r) r = parent.getValue(r)
        return r
    }
    for (x in ids.indices) {
        for (y in x + 1 until ids.size) {
            val i = ids[x]
            val j = ids[y]
            val kind = controls[i].groupKind() ?: continue
            if (controls[j].groupKind() != kind) continue
            val a = pieces.getValue(i)
            val b = pieces.getValue(j)
            // Measured against the buttons' normal size, so a group stays the same group while it is resized.
            val small =
                minOf(
                    minOf(a.rect.width, a.rect.height) / customization.effectiveScale(i),
                    minOf(b.rect.width, b.rect.height) / customization.effectiveScale(j),
                )
            if (push(a, b, small * GROUP_GAP_SHARE) != null) parent[root(i)] = root(j)
        }
    }
    return ids.groupBy { root(it) }.values.toList()
}

/** [groups] joined into rows: groups holding buttons of one kind whose centres sit on one line. */
private fun SkinLayout.rows(
    pieces: Map<Int, Piece>,
    groups: List<List<Int>>,
): List<List<Int>> {
    val parent = groups.indices.toMutableList()

    fun root(g: Int): Int {
        var r = g
        while (parent[r] != r) r = parent[r]
        return r
    }
    val groupIndex = HashMap<Int, Int>()
    groups.forEachIndexed { g, members -> members.forEach { groupIndex[it] = g } }
    val ids = pieces.keys.sorted()
    for (x in ids.indices) {
        for (y in x + 1 until ids.size) {
            val i = ids[x]
            val j = ids[y]
            val kind = controls[i].groupKind() ?: continue
            if (controls[j].groupKind() != kind) continue
            val a = pieces.getValue(i).rect
            val b = pieces.getValue(j).rect
            if (abs(a.center.y - b.center.y) > 0.25f * minOf(a.height, b.height)) continue
            val gi = groupIndex[i] ?: continue
            val gj = groupIndex[j] ?: continue
            parent[root(gi)] = root(gj)
        }
    }
    return groups.indices.groupBy { root(it) }.values.map { linked -> linked.flatMap { groups[it] } }
}

/** The surface a layout is arranged on. */
private class Surface(
    val geometry: SkinGeometry,
    val pinToEdges: Boolean,
    val width: Float,
    val height: Float,
    val unitsAbove: Float,
    val rewindAvailable: Boolean,
    val fastForwardSupported: Boolean,
) {
    private val top: Float get() = -unitsAbove * geometry.scale

    /** How far [r] reaches past the left, top, right and bottom edges (0 inside). */
    fun overflow(r: Rect): FloatArray =
        floatArrayOf(
            maxOf(0f, -r.left),
            maxOf(0f, top - r.top),
            maxOf(0f, r.right - width),
            maxOf(0f, r.bottom - height),
        )

    /** The move (within [low]..[high] per axis) that brings [r] back inside, allowing [allowed] past each edge. */
    fun inside(
        r: Rect,
        allowed: FloatArray,
    ): Pair<ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>> =
        Pair(
            (-allowed[0] - r.left)..(width + allowed[2] - r.right),
            (top - allowed[1] - r.top)..(height + allowed[3] - r.bottom),
        )
}

private val NO_OVERFLOW = FloatArray(4)

/** The layout before a size change, worked out once for every attempt at the new size. */
private class Start(
    val customization: SkinCustomization,
    val pieces: Map<Int, Piece>,
    val overflow: Map<Int, FloatArray>,
)

/** The last [designedOverlaps] answer: the slider asks for the same layout and surface many times a second. */
@Volatile
private var designedCache: Triple<SkinLayout, String, Set<Pair<Int, Int>>>? = null

/** Overlaps the skin is drawn with (none of the shipped skins has one): they are kept as they are. */
private fun SkinLayout.designedOverlaps(surface: Surface): Set<Pair<Int, Int>> {
    val key =
        "${surface.pinToEdges} ${surface.width} ${surface.height} ${surface.rewindAvailable} " +
            surface.fastForwardSupported
    designedCache?.let { (layout, cachedKey, pairs) -> if (layout === this && cachedKey == key) return pairs }
    val pairs =
        overlapPairs(
            pieces(
                surface.geometry,
                surface.pinToEdges,
                defaultCustomization,
                surface.rewindAvailable,
                surface.fastForwardSupported,
                includeHidden = true,
            ),
        )
    designedCache = Triple(this, key, pairs)
    return pairs
}

/**
 * How far past each edge every control may reach once resized to [sized]: as far as it already
 * did in [before], or as far as the layout itself draws it at that size (a tab cut by the top of
 * the panel on purpose); anything else stays whole on the screen.
 */
private fun SkinLayout.edgeAllowance(
    surface: Surface,
    beforeOverflow: Map<Int, FloatArray>,
    sized: SkinCustomization,
): Map<Int, FloatArray> {
    val drawn =
        pieces(
            surface.geometry,
            surface.pinToEdges,
            defaultCustomization.copy(
                size = sized.size,
                scales = sized.scales,
                hidden = sized.hidden,
                shown = sized.shown,
            ),
            surface.rewindAvailable,
            surface.fastForwardSupported,
        )
    return beforeOverflow.mapValues { (i, a) ->
        val b = drawn[i]?.let { surface.overflow(it.rect) } ?: NO_OVERFLOW
        FloatArray(4) { maxOf(a[it], b[it]) }
    }
}

/**
 * Resizes and makes room in one go. Buttons that sit together resize as one group: the group's
 * spacing scales around one point just enough that each button keeps the space to its nearest neighbour, so a
 * diamond stays a diamond. One button made bigger opens its group around itself. Groups that then meet move
 * apart as a whole. Every button stays whole on the screen. Only [SkinCustomization.offsets] are written.
 */
private fun SkinLayout.arrange(
    surface: Surface,
    start: Start,
    after: SkinCustomization,
    designed: Set<Pair<Int, Int>>,
): SkinCustomization {
    val geometry = surface.geometry
    val before = start.customization
    val prev = start.pieces
    val next = pieces(geometry, surface.pinToEdges, after, surface.rewindAvailable, surface.fastForwardSupported)
    if (next.isEmpty()) return after
    val centers = next.mapValues { it.value.rect.center }.toMutableMap()

    fun at(i: Int) = next.getValue(i).movedTo(centers.getValue(i))

    // Buttons stay whole on the screen (a button the layout already had past an edge may stay as far past it).
    val allowed = edgeAllowance(surface, start.overflow, after)

    fun keepOnSurface(members: List<Int>) {
        var lowX = Float.NEGATIVE_INFINITY
        var highX = Float.POSITIVE_INFINITY
        var lowY = Float.NEGATIVE_INFINITY
        var highY = Float.POSITIVE_INFINITY
        for (i in members) {
            val (x, y) = surface.inside(at(i).rect, allowed[i] ?: NO_OVERFLOW)
            lowX = maxOf(lowX, x.start)
            highX = minOf(highX, x.endInclusive)
            lowY = maxOf(lowY, y.start)
            highY = minOf(highY, y.endInclusive)
        }
        val dx = if (lowX <= highX) 0f.coerceIn(lowX, highX) else (lowX + highX) / 2f
        val dy = if (lowY <= highY) 0f.coerceIn(lowY, highY) else (lowY + highY) / 2f
        if (dx != 0f || dy != 0f) members.forEach { centers[it] = centers.getValue(it) + Offset(dx, dy) }
    }

    val gapsBefore = HashMap<Pair<Int, Int>, Float>()

    fun gapBefore(
        i: Int,
        j: Int,
    ): Float {
        val a = prev[i] ?: return SPREAD_GAP_DP
        val b = prev[j] ?: return SPREAD_GAP_DP
        return gapsBefore.getOrPut(pairOf(i, j)) { gapBetween(a, b) }
    }

    val groups = groups(prev.filterKeys { it in next }, before)
    for (group in groups) {
        if (group.size < 2) {
            keepOnSurface(group)
            continue
        }
        val ratios = group.map { after.effectiveScale(it) / before.effectiveScale(it) }
        val uniform = ratios.max() - ratios.min() < 0.01f
        val grownHere = group.filter { after.effectiveScale(it) > before.effectiveScale(it) + SIZE_EPSILON }
        val start = group.associateWith { centers.getValue(it) }
        val anchor =
            if (!uniform && grownHere.size == 1) {
                start.getValue(grownHere.single())
            } else {
                Offset(start.values.map { it.x }.average().toFloat(), start.values.map { it.y }.average().toFloat())
            }

        // The whole group scales its spacing around one point, so it keeps its shape: just enough for
        // each button to keep the space to its nearest neighbour (all buttons resized), or a small gap
        // (one button bigger). Pairs further apart need no more than that, so the same pair decides
        // whether the group gets bigger or smaller, and smaller then bigger again comes back.
        val nearest =
            group.associateWith { i ->
                group.filter { it != i && pairOf(i, it) !in designed }.minOfOrNull { gapBefore(i, it) } ?: 0f
            }

        fun clear(stretch: Float): Boolean {
            val moved =
                group.associateWith { next.getValue(it).movedTo(anchor + (start.getValue(it) - anchor) * stretch) }
            for (x in group.indices) {
                for (y in x + 1 until group.size) {
                    val i = group[x]
                    val j = group[y]
                    if (pairOf(i, j) in designed) continue
                    val had = minOf(gapBefore(i, j), nearest.getValue(i), nearest.getValue(j))
                    val keep = keptGap(had, atMostSmallGap = !uniform)
                    if (gapBetween(moved.getValue(i), moved.getValue(j)) < keep - 0.01f) return false
                }
            }
            return true
        }
        var low = minOf(1f, ratios.min())
        var high = maxOf(1f, ratios.max()) * MAX_GROUP_STRETCH
        val stretch =
            when {
                clear(low) -> low
                !clear(high) -> high
                else -> {
                    repeat(24) {
                        val middle = (low + high) / 2f
                        if (clear(middle)) high = middle else low = middle
                    }
                    high
                }
            }
        if (abs(stretch - 1f) > 0.0005f) {
            group.forEach { centers[it] = anchor + (start.getValue(it) - anchor) * stretch }
        }
        keepOnSurface(group)
    }

    // Groups (and single buttons) that now meet move apart as a whole. Buttons of one kind on one
    // line (L and R, the row of small buttons) move together too, so a row stays straight.
    val units = rows(prev.filterKeys { it in next }, groups)
    val groupOf = HashMap<Int, List<Int>>()
    for (unit in units) unit.forEach { groupOf[it] = unit }
    next.keys.forEach { if (it !in groupOf) groupOf[it] = listOf(it) }
    val ids = next.keys.sorted()

    fun shift(
        unit: List<Int>,
        by: Offset,
    ): Offset {
        val before0 = centers.getValue(unit.first())
        unit.forEach { centers[it] = centers.getValue(it) + by }
        keepOnSurface(unit)
        return centers.getValue(unit.first()) - before0
    }

    // Each round looks only at the buttons whose group moved in the round before (all of them at first).
    var moving: Set<List<Int>>? = null
    for (round in 0 until SPREAD_MAX_ROUNDS) {
        val movedNow = HashSet<List<Int>>()
        for (x in ids.indices) {
            for (y in x + 1 until ids.size) {
                val i = ids[x]
                val j = ids[y]
                val gi = groupOf.getValue(i)
                val gj = groupOf.getValue(j)
                if (gi === gj || pairOf(i, j) in designed) continue
                if (moving != null && gi !in moving && gj !in moving) continue
                val p = push(at(i), at(j), keptGap(gapBefore(i, j), atMostSmallGap = true) - 0.05f) ?: continue
                // Half each way; what one side cannot take (it is against an edge) the other side takes.
                val movedI = shift(gi, -p / 2f)
                val movedJ = shift(gj, p + movedI)
                if ((p + movedI - movedJ).getDistance() > 0.05f) shift(gi, movedJ - p - movedI)
                movedNow += gi
                movedNow += gj
            }
        }
        if (movedNow.isEmpty()) break
        moving = movedNow
    }

    val nextOffsets = after.offsets.toMutableMap()
    for ((index, piece) in next) {
        val d = centers.getValue(index) - piece.rect.center
        if (abs(d.x) < 0.05f && abs(d.y) < 0.05f) continue
        val current = after.offsets[index] ?: Offset.Zero
        val candidate = Offset(current.x + d.x / geometry.scale, current.y + d.y / geometry.scale)
        // Written as it is: the buttons were already kept whole on the screen above (the editor's drag limit would
        // pull a tab the layout draws past the top of the panel back in).
        nextOffsets[index] = candidate
    }
    return after.copy(offsets = nextOffsets)
}

/**
 * Edit Controls' size controls ("All buttons" and one button's own size) without overlaps.
 *
 * Buttons that sit together are a group and resize as one, keeping their shape; one button made
 * bigger opens its group around itself; groups that meet move apart; everything stays on screen.
 * When there is no room left, the size stops at the biggest one that fits. Overlaps the skin is
 * drawn with are kept; any other overlap (a layout made bigger before this existed) is cleared
 * where there is room. Nothing changes when no size changed. Dragging is not affected: a button
 * dragged away from its group is no longer part of it.
 */
fun SkinLayout.spreadForGrowth(
    pinToEdges: Boolean,
    surfaceWidthDp: Float,
    surfaceHeightDp: Float,
    before: SkinCustomization,
    after: SkinCustomization,
    rewindAvailable: Boolean = true,
    fastForwardSupported: Boolean = true,
): SkinCustomization {
    val resized = controls.indices.any { abs(after.effectiveScale(it) - before.effectiveScale(it)) > SIZE_EPSILON }
    if (!resized || surfaceWidthDp <= 0f || surfaceHeightDp <= 0f) return after
    val geometry = SkinGeometry(this, pinToEdges, surfaceWidthDp, surfaceHeightDp)
    if (geometry.scale <= 0f) return after
    val surface =
        Surface(
            geometry,
            pinToEdges,
            surfaceWidthDp,
            surfaceHeightDp,
            // Only the room the layout itself has above the panel: the editor lets a button be dragged over the
            // game, but making room never pushes one there.
            unitsAbove(),
            rewindAvailable,
            fastForwardSupported,
        )
    val designed = designedOverlaps(surface)
    val beforePieces = pieces(geometry, pinToEdges, before, rewindAvailable, fastForwardSupported)
    val start = Start(before, beforePieces, beforePieces.mapValues { surface.overflow(it.value.rect) })
    val already = overlapPairs(beforePieces) - designed

    fun attempt(t: Float): SkinCustomization = arrange(surface, start, before.towards(after, t), designed)

    // Nothing new overlaps, and no button reaches further past an edge of the screen than it did
    // (or than the layout itself puts it, at that size).
    fun clean(result: SkinCustomization): Boolean {
        val allowed = edgeAllowance(surface, start.overflow, result)
        val placed = pieces(geometry, pinToEdges, result, rewindAvailable, fastForwardSupported)
        val onScreen =
            placed.all { (i, piece) ->
                val over = surface.overflow(piece.rect)
                val limit = allowed[i] ?: NO_OVERFLOW
                (0 until 4).all { over[it] <= limit[it] + 0.05f }
            }
        return onScreen && already.containsAll(overlapPairs(placed) - designed)
    }

    // The size [t] of the way to the asked one, arranged; or, if that cannot be done cleanly, the
    // buttons simply resized where they are (always fine when they get smaller); or nothing.
    fun tryAt(t: Float): SkinCustomization? {
        val arranged = attempt(t)
        if (clean(arranged)) return arranged
        val plain = before.towards(after, t)
        return plain.takeIf { clean(it) }
    }

    tryAt(1f)?.let { return it }

    // Not everything fits at the requested size: find the biggest size that does. When not even a
    // sliver more fits (the slider pulled on past the limit), that is known after one more try.
    var best = tryAt(0f) ?: return before
    var low = SEARCH_FIRST_STEP
    var high = 1f
    best = tryAt(low) ?: return best
    repeat(SEARCH_ROUNDS) {
        val middle = (low + high) / 2f
        val candidate = tryAt(middle)
        if (candidate != null) {
            low = middle
            best = candidate
        } else {
            high = middle
        }
    }
    return best
}

/** This customization moved the fraction [t] of the way to [other]: the sizes, nothing else. */
private fun SkinCustomization.towards(
    other: SkinCustomization,
    t: Float,
): SkinCustomization {
    if (t >= 1f) return other

    fun mix(
        from: Float,
        to: Float,
    ) = from + (to - from) * t
    val keys = scales.keys + other.scales.keys
    return other.copy(
        size = mix(size, other.size),
        scales = keys.associateWith { mix(controlScale(it), other.controlScale(it)) },
    )
}
