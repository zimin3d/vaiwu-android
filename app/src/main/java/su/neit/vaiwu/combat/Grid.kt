package su.neit.vaiwu.combat

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** Number of columns on the tactical arena grid (x in `0 until GRID_WIDTH`). */
const val GRID_WIDTH = 9

/** Number of rows on the tactical arena grid (y in `0 until GRID_HEIGHT`). */
const val GRID_HEIGHT = 5

/** Default movement speed: grid cells traversed per 1 AP spent. */
const val CELLS_PER_AP = 2

/** Fallback connectivity anchor when [generateArenaGrid] receives an empty reserved set. */
private val DEFAULT_ARENA_ANCHOR = Pos(1, 2)

/** Discrete cell coordinate on [TacticalGrid]. */
data class Pos(val x: Int, val y: Int)

enum class ArenaTheme {
    Meadow,
    Forest,
    Dungeon,
    BossHall,
}

enum class CoverKind {
    Boulder,
    TreeStump,
    Pillar,
    Crystal,
}

data class CoverTile(
    val pos: Pos,
    val kind: CoverKind,
)

data class TacticalGrid(
    val width: Int = GRID_WIDTH,
    val height: Int = GRID_HEIGHT,
    val covers: List<CoverTile> = emptyList(),
    val theme: ArenaTheme = ArenaTheme.Meadow,
) {
    private val coverPositions: Set<Pos> = covers.map { it.pos }.toSet()

    fun inBounds(p: Pos): Boolean = p.x in 0 until width && p.y in 0 until height

    fun isCover(p: Pos): Boolean = p in coverPositions

    fun coverAt(p: Pos): CoverTile? = covers.firstOrNull { it.pos == p }
}

/** Chebyshev distance: diagonal step costs the same as an orthogonal step. */
fun distance(a: Pos, b: Pos): Int = max(abs(a.x - b.x), abs(a.y - b.y))

/** Cells strictly between [a] and [b] along a Bresenham line. */
fun cellsBetween(a: Pos, b: Pos): List<Pos> {
    val cells = mutableListOf<Pos>()
    var x = a.x
    var y = a.y
    val dx = abs(b.x - a.x)
    val dy = -abs(b.y - a.y)
    val sx = if (a.x < b.x) 1 else -1
    val sy = if (a.y < b.y) 1 else -1
    var err = dx + dy
    while (x != b.x || y != b.y) {
        val e2 = 2 * err
        if (e2 >= dy) {
            err += dy
            x += sx
        }
        if (e2 <= dx) {
            err += dx
            y += sy
        }
        if (x != b.x || y != b.y) {
            cells += Pos(x, y)
        }
    }
    return cells
}

/**
 * Target [to] has cover from [from] if there is a cover tile on the line of fire
 * adjacent to [to]. Cover adjacent to the shooter does not penalise the shooter.
 */
fun hasCover(grid: TacticalGrid, from: Pos, to: Pos): Boolean =
    cellsBetween(from, to).any { c -> grid.isCover(c) && distance(c, to) <= 1 }

/** Minimum AP needed to walk from [from] to [to] using unobstructed Chebyshev distance. */
fun moveCost(from: Pos, to: Pos, cellsPerAp: Int = CELLS_PER_AP): Int {
    val step = cellsPerAp.coerceAtLeast(1)
    return ceil(distance(from, to).toDouble() / step).toInt()
}

private val DIRECTIONS = listOf(
    Pos(1, 0), Pos(-1, 0), Pos(0, 1), Pos(0, -1),
    Pos(1, 1), Pos(1, -1), Pos(-1, 1), Pos(-1, -1),
)

/**
 * Shortest 8-direction path (BFS) from [from] to [to] avoiding cover and [blocked].
 * Prevents diagonal squeezing between two blocked/cover tiles.
 * Returns path cells excluding [from], or null if unreachable within [limit] steps.
 */
fun findPath(
    grid: TacticalGrid,
    from: Pos,
    to: Pos,
    blocked: Set<Pos> = emptySet(),
    limit: Int = Int.MAX_VALUE,
): List<Pos>? {
    if (from == to) return emptyList()
    fun free(p: Pos): Boolean = grid.inBounds(p) && !grid.isCover(p) && p !in blocked
    if (!free(to)) return null

    val prev = HashMap<Pos, Pos?>()
    prev[from] = null
    var frontier = listOf(from)
    var steps = 0
    while (frontier.isNotEmpty() && steps < limit) {
        val next = mutableListOf<Pos>()
        for (cur in frontier) {
            for (d in DIRECTIONS) {
                val p = Pos(cur.x + d.x, cur.y + d.y)
                if (p in prev || !free(p)) continue
                if (d.x != 0 && d.y != 0) {
                    val sideA = Pos(cur.x + d.x, cur.y)
                    val sideB = Pos(cur.x, cur.y + d.y)
                    if (!free(sideA) && !free(sideB)) continue
                }
                prev[p] = cur
                if (p == to) {
                    val path = ArrayDeque<Pos>()
                    var q: Pos? = p
                    while (q != null && q != from) {
                        path.addFirst(q)
                        q = prev[q]
                    }
                    return path.toList()
                }
                next += p
            }
        }
        frontier = next
        steps++
    }
    return null
}

/** AP cost for a path of cells. */
fun pathCost(path: List<Pos>, cellsPerAp: Int = CELLS_PER_AP): Int {
    if (path.isEmpty()) return 0
    val step = cellsPerAp.coerceAtLeast(1)
    return ceil(path.size.toDouble() / step).toInt()
}

/**
 * Closest reachable cell toward [to] within [maxCells] BFS steps, strictly reducing
 * Chebyshev distance to [to].
 */
fun stepToward(
    grid: TacticalGrid,
    from: Pos,
    to: Pos,
    maxCells: Int,
    occupied: Set<Pos>,
): Pos? {
    var best: Pos? = null
    var bestDist = distance(from, to)
    var bestStepLen = Int.MAX_VALUE
    for (dx in -maxCells..maxCells) {
        for (dy in -maxCells..maxCells) {
            if (dx == 0 && dy == 0) continue
            val p = Pos(from.x + dx, from.y + dy)
            val path = findPath(grid, from, p, occupied, maxCells) ?: continue
            val d = distance(p, to)
            if (d < bestDist || (d == bestDist && path.size < bestStepLen && best != null)) {
                best = p
                bestDist = d
                bestStepLen = path.size
            }
        }
    }
    return best
}

/**
 * Generates a curated, guaranteed-connected 9x5 cover layout for an encounter.
 */
fun generateArenaGrid(
    theme: ArenaTheme,
    rng: SeededRng,
    reserved: Set<Pos>,
): Pair<TacticalGrid, SeededRng> {
    val templates = when (theme) {
        ArenaTheme.Meadow -> listOf(
            listOf(Pos(3, 1), Pos(5, 3), Pos(4, 2)),
            listOf(Pos(2, 1), Pos(4, 3), Pos(6, 1), Pos(5, 2)),
            listOf(Pos(3, 0), Pos(3, 3), Pos(5, 1), Pos(5, 4)),
        )
        ArenaTheme.Forest -> listOf(
            listOf(Pos(2, 1), Pos(2, 3), Pos(4, 2), Pos(6, 1), Pos(6, 3)),
            listOf(Pos(3, 1), Pos(3, 3), Pos(5, 2), Pos(6, 0), Pos(6, 4)),
            listOf(Pos(2, 2), Pos(4, 1), Pos(4, 3), Pos(6, 2)),
        )
        ArenaTheme.Dungeon -> listOf(
            listOf(Pos(2, 1), Pos(2, 3), Pos(6, 1), Pos(6, 3)),
            listOf(Pos(3, 1), Pos(3, 3), Pos(5, 1), Pos(5, 3)),
            listOf(Pos(2, 2), Pos(4, 1), Pos(4, 3), Pos(6, 2), Pos(4, 0)),
        )
        ArenaTheme.BossHall -> listOf(
            listOf(Pos(2, 1), Pos(2, 3), Pos(5, 1), Pos(5, 3)),
            listOf(Pos(3, 0), Pos(3, 4), Pos(5, 1), Pos(5, 3)),
            listOf(Pos(2, 1), Pos(4, 2), Pos(2, 3), Pos(6, 1), Pos(6, 3)),
        )
    }
    val (index, nextRng) = rng.nextInt(0, templates.lastIndex)
    val palette = when (theme) {
        ArenaTheme.Meadow -> listOf(CoverKind.Boulder, CoverKind.TreeStump)
        ArenaTheme.Forest -> listOf(CoverKind.TreeStump, CoverKind.Boulder)
        ArenaTheme.Dungeon -> listOf(CoverKind.Pillar, CoverKind.Boulder)
        ArenaTheme.BossHall -> listOf(CoverKind.Pillar, CoverKind.Crystal)
    }
    val accepted = mutableListOf<CoverTile>()
    val anchor = reserved.firstOrNull() ?: DEFAULT_ARENA_ANCHOR
    val targets = reserved.filter { it != anchor }

    for ((i, pos) in templates[index].withIndex()) {
        if (pos in reserved) continue
        val candidate = TacticalGrid(
            width = GRID_WIDTH,
            height = GRID_HEIGHT,
            covers = accepted + CoverTile(pos, palette[i % palette.size]),
            theme = theme,
        )
        val connected = targets.all { t -> findPath(candidate, anchor, t) != null }
        if (connected) {
            accepted += CoverTile(pos, palette[i % palette.size])
        }
    }
    return TacticalGrid(
        width = GRID_WIDTH,
        height = GRID_HEIGHT,
        covers = accepted,
        theme = theme,
    ) to nextRng
}
