package blocky_game;

import blocky.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameEngineFeatureTest {

    @Test
    void testDetermineStartOrientationExplicit() {
        Level level = BlockyFactory.eINSTANCE.createLevel();
        level.setStartOrientation(Direction.SOUTH);

        Cell startCell = BlockyFactory.eINSTANCE.createCell();
        startCell.setType(CellType.START);

        Direction orientation = SimUtils.determineStartOrientation(level, startCell);
        assertEquals(Direction.SOUTH, orientation);
    }

    @Test
    void testDetermineStartOrientationInferred() {
        Level level = BlockyFactory.eINSTANCE.createLevel();
        // Unset explicit start orientation

        Cell start = BlockyFactory.eINSTANCE.createCell();
        start.setX(0); start.setY(0);
        start.setType(CellType.START);

        Cell right = BlockyFactory.eINSTANCE.createCell();
        right.setX(1); right.setY(0);
        right.setType(CellType.EMPTY);

        start.setRight(right);
        right.setLeft(start);

        Direction orientation = SimUtils.determineStartOrientation(level, start);
        assertEquals(Direction.EAST, orientation);
    }

    @Test
    void testComputeTraceFromStateAtomicMove() {
        Level level = BlockyFactory.eINSTANCE.createLevel();
        GridMap map = BlockyFactory.eINSTANCE.createGridMap();
        level.setMap(map);

        Cell c0 = BlockyFactory.eINSTANCE.createCell();
        c0.setX(0); c0.setY(0); c0.setType(CellType.START);

        Cell c1 = BlockyFactory.eINSTANCE.createCell();
        c1.setX(1); c1.setY(0); c1.setType(CellType.EMPTY);

        Cell c2 = BlockyFactory.eINSTANCE.createCell();
        c2.setX(2); c2.setY(0); c2.setType(CellType.GOAL);

        c0.setRight(c1); c1.setLeft(c0);
        c1.setRight(c2); c2.setLeft(c1);

        map.getCells().add(c0);
        map.getCells().add(c1);
        map.getCells().add(c2);

        // Build solution body: moveForward, moveForward
        Body solution = BlockyFactory.eINSTANCE.createBody();
        level.setSolution(solution);

        Container cont1 = BlockyFactory.eINSTANCE.createContainer();
        AtomicStatement move1 = BlockyFactory.eINSTANCE.createAtomicStatement();
        move1.setKind(AtomicStatementKind.MOVE_FORWARD);
        cont1.setStatement(move1);
        solution.setFirstContainer(cont1);

        Container cont2 = BlockyFactory.eINSTANCE.createContainer();
        AtomicStatement move2 = BlockyFactory.eINSTANCE.createAtomicStatement();
        move2.setKind(AtomicStatementKind.MOVE_FORWARD);
        cont2.setStatement(move2);
        cont1.setNext(cont2);

        DebuggingService.DebugTraceResult res = DebuggingService.computeTraceFromState(level, 0, 0, Direction.EAST);
        assertNotNull(res);
        assertNotNull(res.trace);
        assertTrue(res.trace.getStates().size() >= 3);

        assertEquals(0, res.trace.getStates().get(0).getPosition().getX());
        assertEquals(1, res.trace.getStates().get(1).getPosition().getX());
        assertEquals(2, res.trace.getStates().get(2).getPosition().getX());
        assertEquals(GameStatus.WON, res.trace.getStates().get(2).getStatus());
    }

    @Test
    void testComputeTraceFromStateWithLoopAndIf() {
        Level level = BlockyFactory.eINSTANCE.createLevel();
        GridMap map = BlockyFactory.eINSTANCE.createGridMap();
        level.setMap(map);

        Cell c0 = BlockyFactory.eINSTANCE.createCell();
        c0.setX(0); c0.setY(0); c0.setType(CellType.START);

        Cell c1 = BlockyFactory.eINSTANCE.createCell();
        c1.setX(1); c1.setY(0); c1.setType(CellType.GOAL);

        c0.setRight(c1); c1.setLeft(c0);
        map.getCells().add(c0);
        map.getCells().add(c1);

        // Solution with Loop and IfStmt
        Body solution = BlockyFactory.eINSTANCE.createBody();
        level.setSolution(solution);

        Container cont = BlockyFactory.eINSTANCE.createContainer();
        Loop loop = BlockyFactory.eINSTANCE.createLoop();
        cont.setStatement(loop);
        solution.setFirstContainer(cont);

        Body loopBody = BlockyFactory.eINSTANCE.createBody();
        loop.setBody(loopBody);

        Container loopCont = BlockyFactory.eINSTANCE.createContainer();
        IfStmt ifStmt = BlockyFactory.eINSTANCE.createIfStmt();
        ifStmt.setCondition(ConditionKind.CHECK_FORWARD);
        loopCont.setStatement(ifStmt);
        loopBody.setFirstContainer(loopCont);

        Body thenBody = BlockyFactory.eINSTANCE.createBody();
        ifStmt.setThenBody(thenBody);

        Container thenCont = BlockyFactory.eINSTANCE.createContainer();
        AtomicStatement move = BlockyFactory.eINSTANCE.createAtomicStatement();
        move.setKind(AtomicStatementKind.MOVE_FORWARD);
        thenCont.setStatement(move);
        thenBody.setFirstContainer(thenCont);

        DebuggingService.DebugTraceResult res = DebuggingService.computeTraceFromState(level, 0, 0, Direction.EAST);
        assertNotNull(res);
        assertNotNull(res.trace);
        assertFalse(res.trace.getStates().isEmpty());
    }

    @Test
    void testLevelLimitsModuleSelection() {
        String prev = System.getProperty("blocky.levelLimits");
        try {
            System.clearProperty("blocky.levelLimits");
            String defaultModule = MomotFirstGoalBenchmarkRunner.selectHenshinModuleForLevel(1);
            assertTrue(defaultModule.contains("statement_insertions_henshin_text"));

            System.setProperty("blocky.levelLimits", "true");
            String l1 = MomotFirstGoalBenchmarkRunner.selectHenshinModuleForLevel(1);
            assertTrue(l1.contains("atomic_only"));

            String l4 = MomotFirstGoalBenchmarkRunner.selectHenshinModuleForLevel(4);
            assertTrue(l4.contains("no_conds"));

            String l6 = MomotFirstGoalBenchmarkRunner.selectHenshinModuleForLevel(6);
            assertTrue(l6.contains("no_else"));

            String l8 = MomotFirstGoalBenchmarkRunner.selectHenshinModuleForLevel(8);
            assertTrue(l8.contains("statement_insertions_henshin_text"));
        } finally {
            if (prev != null) System.setProperty("blocky.levelLimits", prev);
            else System.clearProperty("blocky.levelLimits");
        }
    }

    @Test
    void testSimulatorDistanceTrackingWithLoop() {
        Level level = BlockyFactory.eINSTANCE.createLevel();
        GridMap map = BlockyFactory.eINSTANCE.createGridMap();
        map.setWidth(5);
        map.setHeight(1);
        level.setMap(map);

        Cell c0 = BlockyFactory.eINSTANCE.createCell();
        c0.setX(0); c0.setY(0); c0.setType(CellType.START);

        Cell c1 = BlockyFactory.eINSTANCE.createCell();
        c1.setX(1); c1.setY(0); c1.setType(CellType.EMPTY);

        Cell c2 = BlockyFactory.eINSTANCE.createCell();
        c2.setX(2); c2.setY(0); c2.setType(CellType.GOAL);

        c0.setRight(c1); c1.setLeft(c0);
        c1.setRight(c2); c2.setLeft(c1);
        map.getCells().add(c0);
        map.getCells().add(c1);
        map.getCells().add(c2);

        // Loop that moves forward
        Body solution = BlockyFactory.eINSTANCE.createBody();
        level.setSolution(solution);
        Container cont = BlockyFactory.eINSTANCE.createContainer();
        Loop loop = BlockyFactory.eINSTANCE.createLoop();
        cont.setStatement(loop);
        solution.setFirstContainer(cont);

        Body loopBody = BlockyFactory.eINSTANCE.createBody();
        loop.setBody(loopBody);
        Container loopCont = BlockyFactory.eINSTANCE.createContainer();
        AtomicStatement move = BlockyFactory.eINSTANCE.createAtomicStatement();
        move.setKind(AtomicStatementKind.MOVE_FORWARD);
        loopCont.setStatement(move);
        loopBody.setFirstContainer(loopCont);

        int dist = blocky_momot.BlockySimulator.distanceToGoalOrPenalty(level, 100000);
        assertEquals(0, dist);
    }

    @Test
    void testDirectManipulationPreservesLevelGoal() {
        GameEngine engine = new GameEngine();
        engine.setMapFromJson("[[2,1,0],[0,1,0],[0,1,3]]");
        GridMap map = engine.getCurrentLevel().getMap();

        // Before DM: goal cell is (2, 2)
        assertEquals(2, engine.getGoalCell(map).getX());
        assertEquals(2, engine.getGoalCell(map).getY());
        assertNull(engine.getDmgCell(map));

        // Direct manipulation on (1, 0)
        engine.teleportPegman(1, 0, 1);

        // Goal cell must still be (2, 2) and DMG is (1, 0)
        Cell currentGoal = engine.getGoalCell(map);
        assertNotNull(currentGoal);
        assertEquals(2, currentGoal.getX());
        assertEquals(2, currentGoal.getY());
        assertEquals(CellType.GOAL, currentGoal.getType());

        Cell dmg = engine.getDmgCell(map);
        assertNotNull(dmg);
        assertEquals(1, dmg.getX());
        assertEquals(0, dmg.getY());
        assertEquals(CellType.DMG, dmg.getType());

        // WebView grid must keep GOAL at (2, 2) as 3 and DMG at (1, 0) as walkable path (1)
        int[][] webGrid = engine.buildGridForWebView(map);
        assertEquals(3, webGrid[2][2]);
        assertEquals(1, webGrid[0][1]);
        assertEquals(2, webGrid[0][0]);

        // Clearing DM leaves GOAL intact
        engine.clearDirectManipulationGoal();
        assertNull(engine.getDmgCell(map));
        assertEquals(CellType.GOAL, engine.getGoalCell(map).getType());
    }
}
