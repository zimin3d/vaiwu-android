package su.neit.vaiwu.combat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridTest {

    @Test
    fun chebyshevDistanceCountsDiagonalsAsOne() {
        assertEquals(0, distance(Pos(2, 2), Pos(2, 2)))
        assertEquals(1, distance(Pos(2, 2), Pos(3, 3)))
        assertEquals(4, distance(Pos(1, 1), Pos(5, 3)))
    }

    @Test
    fun hasCoverOnlyWhenObstacleIsAdjacentToTargetOnLineOfFire() {
        val grid = TacticalGrid(
            covers = listOf(CoverTile(Pos(4, 2), CoverKind.Boulder)),
        )
        // Shooter at (1,2), cover at (4,2), target at (5,2): cover is adjacent to target.
        assertTrue(hasCover(grid, Pos(1, 2), Pos(5, 2)))
        // Shooter at (3,2) right behind cover (4,2), target far at (7,2): shooter's own cover doesn't shield target.
        assertFalse(hasCover(grid, Pos(3, 2), Pos(7, 2)))
        // Clear line on another row.
        assertFalse(hasCover(grid, Pos(1, 0), Pos(7, 0)))
    }

    @Test
    fun findPathRoutesAroundCoverAndBlocksDiagonalSqueeze() {
        // Two cover tiles forming a diagonal pinch at (2,1) and (1,2):
        // moving directly from (1,1) to (2,2) must NOT squeeze diagonally through them.
        val grid = TacticalGrid(
            width = 5,
            height = 5,
            covers = listOf(
                CoverTile(Pos(2, 1), CoverKind.Pillar),
                CoverTile(Pos(1, 2), CoverKind.Pillar),
            ),
        )
        val path = findPath(grid, Pos(1, 1), Pos(2, 2))
        assertNotNull(path)
        assertTrue("Path must go around diagonal pinch, got $path", path!!.size > 1)
        assertEquals(Pos(2, 2), path.last())
        assertTrue(path.none { grid.isCover(it) })
    }

    @Test
    fun findPathReturnsNullWhenCompletelyWalledOff() {
        val wall = (0 until GRID_HEIGHT).map { y -> CoverTile(Pos(4, y), CoverKind.Boulder) }
        val grid = TacticalGrid(covers = wall)
        assertNull(findPath(grid, Pos(1, 2), Pos(7, 2)))
    }

    @Test
    fun pathCostRespectsCellsPerAp() {
        assertEquals(0, pathCost(emptyList()))
        assertEquals(1, pathCost(listOf(Pos(1, 0))))
        assertEquals(1, pathCost(listOf(Pos(1, 0), Pos(2, 0))))
        assertEquals(2, pathCost(listOf(Pos(1, 0), Pos(2, 0), Pos(3, 0))))
        assertEquals(1, pathCost(listOf(Pos(1, 0), Pos(2, 0), Pos(3, 0)), cellsPerAp = 3))
        assertEquals(3, pathCost(listOf(Pos(1, 0), Pos(2, 0), Pos(3, 0)), cellsPerAp = 1))
    }

    @Test
    fun stepTowardMovesCloserAvoidingOccupiedTiles() {
        val grid = TacticalGrid()
        val occupied = setOf(Pos(2, 2), Pos(3, 2))
        val step = stepToward(grid, Pos(1, 2), Pos(7, 2), CELLS_PER_AP, occupied)
        assertNotNull(step)
        assertFalse(step in occupied)
        assertTrue(distance(step!!, Pos(7, 2)) < distance(Pos(1, 2), Pos(7, 2)))
    }

    @Test
    fun generatedArenasAlwaysStayConnected() {
        val reserved = setOf(Pos(1, 2), Pos(7, 2), Pos(7, 0), Pos(7, 4))
        for (theme in ArenaTheme.entries) {
            for (seed in 1L..25L) {
                val (grid, _) = generateArenaGrid(theme, SeededRng.fromSeed(seed * 97L), reserved)
                assertEquals(GRID_WIDTH, grid.width)
                assertEquals(GRID_HEIGHT, grid.height)
                for (r in reserved) {
                    assertFalse("Spawn $r must not be cover", grid.isCover(r))
                }
                for (target in reserved.filter { it != Pos(1, 2) }) {
                    assertNotNull("Arena $theme seed $seed disconnected", findPath(grid, Pos(1, 2), target))
                }
            }
        }
    }
}
