package blocky_game;

import blocky.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InteractiveDebuggerTest {

    @Test
    void testDebugLifecycle() {
        InteractiveDebugger debugger = new InteractiveDebugger();
        BlockyAstMapper mapper = new BlockyAstMapper();

        Level level = BlockyFactory.eINSTANCE.createLevel();
        GridMap map = BlockyFactory.eINSTANCE.createGridMap();
        map.setWidth(3); map.setHeight(1);
        level.setMap(map);

        Cell c0 = BlockyFactory.eINSTANCE.createCell(); c0.setX(0); c0.setY(0); c0.setType(CellType.START);
        Cell c1 = BlockyFactory.eINSTANCE.createCell(); c1.setX(1); c1.setY(0); c1.setType(CellType.EMPTY);
        Cell c2 = BlockyFactory.eINSTANCE.createCell(); c2.setX(2); c2.setY(0); c2.setType(CellType.GOAL);
        c0.setRight(c1); c1.setLeft(c0);
        c1.setRight(c2); c2.setLeft(c1);
        map.getCells().add(c0); map.getCells().add(c1); map.getCells().add(c2);

        // Solution: moveForward, moveForward
        Body solution = BlockyFactory.eINSTANCE.createBody();
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

        level.setSolution(solution);

        // Start debug session at (0, 0, EAST=1)
        String frameStart = debugger.debugStart(level, mapper, 0, 0, 1);
        assertTrue(debugger.isSessionActive());
        assertTrue(frameStart.contains("\"index\":0"));
        assertTrue(frameStart.contains("\"paused\":true"));

        // Step once
        String frameStep1 = debugger.debugStepOnce(level, mapper);
        assertTrue(frameStep1.contains("\"index\":1"));
        assertTrue(frameStep1.contains("\"q\":1"));

        // Step once to goal
        String frameStep2 = debugger.debugStepOnce(level, mapper);
        assertTrue(frameStep2.contains("\"index\":2"));
        assertTrue(frameStep2.contains("\"q\":2"));
        assertTrue(frameStep2.contains("\"result\":\"GOAL\""));

        // Stop
        String frameStop = debugger.debugStop(level, mapper);
        assertFalse(debugger.isSessionActive());
        assertTrue(frameStop.contains("\"index\":0"));
    }
}
