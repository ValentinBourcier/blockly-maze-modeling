package blocky_game;

import blocky.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MazeGridServiceTest {

    @Test
    void testParseGridMapFromJson() {
        MazeGridService service = new MazeGridService();
        String json = "[[2,1,0],[0,1,3]]";
        GridMap map = service.parseGridMapFromJson(json, null);

        assertNotNull(map);
        assertEquals(3, map.getWidth());
        assertEquals(2, map.getHeight());
        assertEquals(6, map.getCells().size());

        Cell start = service.getStartCell(map);
        assertNotNull(start);
        assertEquals(0, start.getX());
        assertEquals(0, start.getY());

        Cell goal = service.getGoalCell(map);
        assertNotNull(goal);
        assertEquals(2, goal.getX());
        assertEquals(1, goal.getY());

        assertNull(service.getDmgCell(map));
    }

    @Test
    void testCycleCellType() {
        MazeGridService service = new MazeGridService();
        String json = "[[1,1],[1,1]]";
        GridMap map = service.parseGridMapFromJson(json, null);

        Cell c00 = service.findCellByXY(map, 0, 0);
        assertEquals(CellType.EMPTY, c00.getType());

        // EMPTY -> WALL
        service.cycleCellType(map, 0, 0);
        assertEquals(CellType.WALL, c00.getType());

        // WALL -> START
        service.cycleCellType(map, 0, 0);
        assertEquals(CellType.START, c00.getType());

        // START -> GOAL
        service.cycleCellType(map, 0, 0);
        assertEquals(CellType.GOAL, c00.getType());

        // GOAL -> EMPTY
        service.cycleCellType(map, 0, 0);
        assertEquals(CellType.EMPTY, c00.getType());
    }

    @Test
    void testBuildGridForWebView() {
        MazeGridService service = new MazeGridService();
        String json = "[[2,1],[0,3]]";
        GridMap map = service.parseGridMapFromJson(json, null);

        int[][] webGrid = service.buildGridForWebView(map);
        assertEquals(2, webGrid.length); // height
        assertEquals(2, webGrid[0].length); // width

        assertEquals(2, webGrid[0][0]); // START
        assertEquals(1, webGrid[0][1]); // EMPTY
        assertEquals(0, webGrid[1][0]); // WALL
        assertEquals(3, webGrid[1][1]); // GOAL
    }

    @Test
    void testDirectionToT() {
        assertEquals(0, MazeGridService.directionToT(Direction.NORTH));
        assertEquals(1, MazeGridService.directionToT(Direction.EAST));
        assertEquals(2, MazeGridService.directionToT(Direction.SOUTH));
        assertEquals(3, MazeGridService.directionToT(Direction.WEST));
        assertEquals(1, MazeGridService.directionToT(null));
    }

    @Test
    void testApplyTeleportTargetRules() {
        MazeGridService service = new MazeGridService();
        String json = "[[2,1,0],[0,1,3]]";
        GridMap map = service.parseGridMapFromJson(json, null);

        // Reject out-of-bounds
        assertNull(service.applyTeleportTarget(map, 10, 10));

        // Reject WALL at (2,0)
        assertNull(service.applyTeleportTarget(map, 2, 0));

        // Accept EMPTY at (1,0) -> marked as DMG
        Cell dmgCell = service.applyTeleportTarget(map, 1, 0);
        assertNotNull(dmgCell);
        assertEquals(CellType.DMG, dmgCell.getType());
        assertEquals(dmgCell, service.getDmgCell(map));

        // Accept GOAL at (2,1) -> stays GOAL, clears previous DMG
        Cell goalCell = service.applyTeleportTarget(map, 2, 1);
        assertNotNull(goalCell);
        assertEquals(CellType.GOAL, goalCell.getType());
        assertEquals(CellType.EMPTY, dmgCell.getType());
    }

    @Test
    void testApplyLevelMetadata() {
        MazeGridService service = new MazeGridService();
        Level level = BlockyFactory.eINSTANCE.createLevel();
        String json = "{\"level\":3,\"maxBlocks\":8,\"startDirection\":2,\"allowLoops\":true,\"allowConditionals\":false,\"allowIfElse\":false}";

        Direction dir = service.applyLevelMetadata(level, json, false);
        assertEquals(3, level.getId());
        assertEquals("Maze Level 3", level.getTitle());
        assertEquals(8, level.getMaxBlocks());
        assertTrue(level.isAllowLoops());
        assertFalse(level.isAllowConditionals());
        assertEquals(Direction.SOUTH, dir);
        assertEquals(Direction.SOUTH, level.getStartOrientation());
    }
}
