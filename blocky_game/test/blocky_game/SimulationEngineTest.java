package blocky_game;

import blocky.AtomicStatement;
import blocky.AtomicStatementKind;
import blocky.BlockyFactory;
import blocky.Body;
import blocky.Cell;
import blocky.CellType;
import blocky.ConditionKind;
import blocky.Container;
import blocky.Direction;
import blocky.GameStatus;
import blocky.GridMap;
import blocky.IfStmt;
import blocky.Level;
import blocky.Loop;
import blocky.SensorDirection;
import blocky.Statement;
import blocky_momot.SimulationEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineTest {

    private Cell createCell(GridMap map, int x, int y, CellType type) {
        Cell c = BlockyFactory.eINSTANCE.createCell();
        c.setX(x);
        c.setY(y);
        c.setType(type);
        if (map != null) {
            map.getCells().add(c);
        }
        return c;
    }

    private void linkHorizontal(Cell left, Cell right) {
        if (left != null) left.setRight(right);
        if (right != null) right.setLeft(left);
    }

    private void linkVertical(Cell top, Cell bottom) {
        if (top != null) top.setBottom(bottom);
        if (bottom != null) bottom.setTop(top);
    }

    private Container append(Body body, Statement stmt) {
        Container container = BlockyFactory.eINSTANCE.createContainer();
        container.setStatement(stmt);
        if (body.getFirstContainer() == null) {
            body.setFirstContainer(container);
        } else {
            Container cur = body.getFirstContainer();
            while (cur.getNext() != null) {
                cur = cur.getNext();
            }
            cur.setNext(container);
        }
        return container;
    }

    private AtomicStatement createAtomic(AtomicStatementKind kind) {
        AtomicStatement stmt = BlockyFactory.eINSTANCE.createAtomicStatement();
        stmt.setKind(kind);
        return stmt;
    }

    @Test
    void testLinearProgramMovementReachGoal() {
        Level level = BlockyFactory.eINSTANCE.createLevel();
        GridMap map = BlockyFactory.eINSTANCE.createGridMap();
        map.setWidth(3);
        map.setHeight(3);
        level.setMap(map);

        // (0,0) START -> (1,0) EMPTY
        //                  |
        //                (1,1) EMPTY
        //                  |
        //                (1,2) GOAL
        Cell c00 = createCell(map, 0, 0, CellType.START);
        Cell c10 = createCell(map, 1, 0, CellType.EMPTY);
        Cell c11 = createCell(map, 1, 1, CellType.EMPTY);
        Cell c12 = createCell(map, 1, 2, CellType.GOAL);

        linkHorizontal(c00, c10);
        linkVertical(c10, c11);
        linkVertical(c11, c12);

        Body body = BlockyFactory.eINSTANCE.createBody();
        level.setSolution(body);
        level.setStartOrientation(Direction.EAST);

        // Sequence: MOVE_FORWARD, TURN_RIGHT, MOVE_FORWARD, MOVE_FORWARD
        append(body, createAtomic(AtomicStatementKind.MOVE_FORWARD));
        append(body, createAtomic(AtomicStatementKind.TURN_RIGHT));
        append(body, createAtomic(AtomicStatementKind.MOVE_FORWARD));
        append(body, createAtomic(AtomicStatementKind.MOVE_FORWARD));

        SimulationEngine.SimulationResult res = SimulationEngine.simulate(level, body);
        assertNotNull(res);
        assertTrue(res.isWon());
        assertEquals(GameStatus.WON, res.getFinalStatus());
        assertEquals(4, res.getSteps());
        assertEquals(5, res.getTrace().getStates().size());

        // Verify state trajectory
        assertEquals(c00, res.getTrace().getStates().get(0).getPosition());
        assertEquals(Direction.EAST, res.getTrace().getStates().get(0).getOrientation());

        assertEquals(c10, res.getTrace().getStates().get(1).getPosition());
        assertEquals(Direction.EAST, res.getTrace().getStates().get(1).getOrientation());

        assertEquals(c10, res.getTrace().getStates().get(2).getPosition());
        assertEquals(Direction.SOUTH, res.getTrace().getStates().get(2).getOrientation());

        assertEquals(c11, res.getTrace().getStates().get(3).getPosition());
        assertEquals(Direction.SOUTH, res.getTrace().getStates().get(3).getOrientation());

        assertEquals(c12, res.getTrace().getStates().get(4).getPosition());
        assertEquals(Direction.SOUTH, res.getTrace().getStates().get(4).getOrientation());
        assertEquals(GameStatus.WON, res.getTrace().getStates().get(4).getStatus());

        // Verify logging
        assertFalse(res.getLogs().isEmpty());
        assertTrue(res.getLogs().get(0).contains("Start: (0,0) dir=EAST"));
        assertTrue(res.getLogs().get(res.getLogs().size() - 1).contains("GOAL"));
    }

    @Test
    void testCollisionDetectionWallAndBoundary() {
        // Wall collision
        GridMap map = BlockyFactory.eINSTANCE.createGridMap();
        Cell start = createCell(map, 0, 0, CellType.START);
        Cell wall = createCell(map, 1, 0, CellType.WALL);
        linkHorizontal(start, wall);

        Body bodyWall = BlockyFactory.eINSTANCE.createBody();
        append(bodyWall, createAtomic(AtomicStatementKind.MOVE_FORWARD));

        SimulationEngine.SimulationResult resWall = SimulationEngine.simulate(map, start, Direction.EAST, bodyWall, CellType.GOAL);
        assertTrue(resWall.isCrashed());
        assertEquals(GameStatus.CRASHED, resWall.getFinalStatus());
        assertEquals(start, resWall.getTrace().getStates().get(1).getPosition());
        assertTrue(resWall.getLogs().get(1).contains("CRASH"));

        // Boundary collision (no neighbor in orientation direction)
        Body bodyBoundary = BlockyFactory.eINSTANCE.createBody();
        append(bodyBoundary, createAtomic(AtomicStatementKind.MOVE_FORWARD));

        SimulationEngine.SimulationResult resBoundary = SimulationEngine.simulate(map, start, Direction.NORTH, bodyBoundary, CellType.GOAL);
        assertTrue(resBoundary.isCrashed());
        assertEquals(GameStatus.CRASHED, resBoundary.getFinalStatus());
        assertEquals(start, resBoundary.getTrace().getStates().get(1).getPosition());
        assertTrue(resBoundary.getLogs().get(1).contains("CRASH"));
    }

    @Test
    void testLoopExecutionMazeForever() {
        GridMap map = BlockyFactory.eINSTANCE.createGridMap();
        map.setWidth(4);
        map.setHeight(1);

        Cell c0 = createCell(map, 0, 0, CellType.START);
        Cell c1 = createCell(map, 1, 0, CellType.EMPTY);
        Cell c2 = createCell(map, 2, 0, CellType.EMPTY);
        Cell c3 = createCell(map, 3, 0, CellType.GOAL);
        linkHorizontal(c0, c1);
        linkHorizontal(c1, c2);
        linkHorizontal(c2, c3);

        Body body = BlockyFactory.eINSTANCE.createBody();
        Loop loop = BlockyFactory.eINSTANCE.createLoop();
        append(body, loop);

        Body loopBody = BlockyFactory.eINSTANCE.createBody();
        loop.setBody(loopBody);
        append(loopBody, createAtomic(AtomicStatementKind.MOVE_FORWARD));

        SimulationEngine.SimulationResult res = SimulationEngine.simulate(map, c0, Direction.EAST, body, CellType.GOAL);
        assertTrue(res.isWon());
        assertEquals(GameStatus.WON, res.getFinalStatus());
        assertEquals(4, res.getSteps());
        assertEquals(c3, res.getTrace().getStates().get(res.getTrace().getStates().size() - 1).getPosition());
    }

    @Test
    void testInfiniteLoopDetection() {
        GridMap map = BlockyFactory.eINSTANCE.createGridMap();
        map.setWidth(2);
        map.setHeight(2);

        Cell c0 = createCell(map, 0, 0, CellType.START);
        Cell c1 = createCell(map, 1, 0, CellType.EMPTY);
        linkHorizontal(c0, c1);

        Body body = BlockyFactory.eINSTANCE.createBody();
        Loop loop = BlockyFactory.eINSTANCE.createLoop();
        append(body, loop);

        Body loopBody = BlockyFactory.eINSTANCE.createBody();
        loop.setBody(loopBody);
        // Repeatedly turn left in place -> will never reach goal
        append(loopBody, createAtomic(AtomicStatementKind.TURN_LEFT));

        SimulationEngine.SimulationResult res = SimulationEngine.simulate(map, c0, Direction.EAST, body, CellType.GOAL);
        assertTrue(res.isCrashed());
        assertEquals(GameStatus.CRASHED, res.getFinalStatus());
        assertTrue(res.getSteps() > SimulationEngine.computeLoopLimit(map));
    }

    @Test
    void testEmptyLoopBodyCrashing() {
        GridMap map = BlockyFactory.eINSTANCE.createGridMap();
        Cell c0 = createCell(map, 0, 0, CellType.START);

        Body body = BlockyFactory.eINSTANCE.createBody();
        Loop loop = BlockyFactory.eINSTANCE.createLoop();
        append(body, loop);
        loop.setBody(BlockyFactory.eINSTANCE.createBody()); // empty body

        SimulationEngine.SimulationResult res = SimulationEngine.simulate(map, c0, Direction.EAST, body, CellType.GOAL);
        assertTrue(res.isCrashed());
        assertEquals(GameStatus.CRASHED, res.getFinalStatus());
    }

    @Test
    void testIfConditionBranches() {
        GridMap map = BlockyFactory.eINSTANCE.createGridMap();
        // Setup:
        //      (1,0) WALL
        // (0,1) EMPTY <-> (1,1) START <-> (2,1) GOAL
        Cell start = createCell(map, 1, 1, CellType.START);
        Cell wallAhead = createCell(map, 1, 0, CellType.WALL);
        Cell emptyLeft = createCell(map, 0, 1, CellType.EMPTY);
        Cell goalRight = createCell(map, 2, 1, CellType.GOAL);

        linkVertical(wallAhead, start); // start facing NORTH -> top is wallAhead
        linkHorizontal(emptyLeft, start); // start facing NORTH -> left is WEST (emptyLeft)
        linkHorizontal(start, goalRight); // start facing NORTH -> right is EAST (goalRight)

        // Test 1: If CHECK_FORWARD (wall ahead) -> then: MOVE, else: TURN_RIGHT
        Body body1 = BlockyFactory.eINSTANCE.createBody();
        IfStmt ifStmt1 = BlockyFactory.eINSTANCE.createIfStmt();
        ifStmt1.setCondition(ConditionKind.CHECK_FORWARD);
        append(body1, ifStmt1);

        Body thenBody1 = BlockyFactory.eINSTANCE.createBody();
        append(thenBody1, createAtomic(AtomicStatementKind.MOVE_FORWARD));
        ifStmt1.setThenBody(thenBody1);

        Body elseBody1 = BlockyFactory.eINSTANCE.createBody();
        append(elseBody1, createAtomic(AtomicStatementKind.TURN_RIGHT));
        ifStmt1.setElseBody(elseBody1);

        // After if-else, move forward to reach goal
        append(body1, createAtomic(AtomicStatementKind.MOVE_FORWARD));

        SimulationEngine.SimulationResult res1 = SimulationEngine.simulate(map, start, Direction.NORTH, body1, CellType.GOAL);
        assertTrue(res1.isWon());
        assertEquals(goalRight, res1.getTrace().getStates().get(res1.getTrace().getStates().size() - 1).getPosition());

        // Test 2: If CHECK_LEFT (empty to the left) -> then: TURN_LEFT + MOVE_FORWARD
        Body body2 = BlockyFactory.eINSTANCE.createBody();
        IfStmt ifStmt2 = BlockyFactory.eINSTANCE.createIfStmt();
        ifStmt2.setCondition(ConditionKind.CHECK_LEFT);
        append(body2, ifStmt2);

        Body thenBody2 = BlockyFactory.eINSTANCE.createBody();
        append(thenBody2, createAtomic(AtomicStatementKind.TURN_LEFT));
        append(thenBody2, createAtomic(AtomicStatementKind.MOVE_FORWARD));
        ifStmt2.setThenBody(thenBody2);

        SimulationEngine.SimulationResult res2 = SimulationEngine.simulate(map, start, Direction.NORTH, body2, CellType.GOAL);
        assertEquals(GameStatus.RUNNING, res2.getFinalStatus());
        assertEquals(emptyLeft, res2.getTrace().getStates().get(res2.getTrace().getStates().size() - 1).getPosition());

        // Test 3: If CHECK_RIGHT (goal to the right) -> then: TURN_RIGHT + MOVE_FORWARD
        Body body3 = BlockyFactory.eINSTANCE.createBody();
        IfStmt ifStmt3 = BlockyFactory.eINSTANCE.createIfStmt();
        ifStmt3.setCondition(ConditionKind.CHECK_RIGHT);
        append(body3, ifStmt3);

        Body thenBody3 = BlockyFactory.eINSTANCE.createBody();
        append(thenBody3, createAtomic(AtomicStatementKind.TURN_RIGHT));
        append(thenBody3, createAtomic(AtomicStatementKind.MOVE_FORWARD));
        ifStmt3.setThenBody(thenBody3);

        SimulationEngine.SimulationResult res3 = SimulationEngine.simulate(map, start, Direction.NORTH, body3, CellType.GOAL);
        assertTrue(res3.isWon());
        assertEquals(goalRight, res3.getTrace().getStates().get(res3.getTrace().getStates().size() - 1).getPosition());
    }

    @Test
    void testDirectManipulationGoalDetection() {
        Level level = BlockyFactory.eINSTANCE.createLevel();
        GridMap map = BlockyFactory.eINSTANCE.createGridMap();
        level.setMap(map);

        // (0,0) START -> (1,0) EMPTY -> (2,0) DMG -> (3,0) GOAL
        Cell c0 = createCell(map, 0, 0, CellType.START);
        Cell c1 = createCell(map, 1, 0, CellType.EMPTY);
        Cell c2 = createCell(map, 2, 0, CellType.DMG);
        Cell c3 = createCell(map, 3, 0, CellType.GOAL);
        linkHorizontal(c0, c1);
        linkHorizontal(c1, c2);
        linkHorizontal(c2, c3);

        Body body = BlockyFactory.eINSTANCE.createBody();
        level.setSolution(body);
        level.setStartOrientation(Direction.EAST);

        // 3 forward moves: if DMG is active, should win at (2,0) after 2 moves and stop
        append(body, createAtomic(AtomicStatementKind.MOVE_FORWARD));
        append(body, createAtomic(AtomicStatementKind.MOVE_FORWARD));
        append(body, createAtomic(AtomicStatementKind.MOVE_FORWARD));

        SimulationEngine.SimulationResult res = SimulationEngine.simulate(level, body);
        assertTrue(res.isWon());
        assertEquals(GameStatus.WON, res.getFinalStatus());
        assertEquals(2, res.getSteps());
        assertEquals(c2, res.getTrace().getStates().get(res.getTrace().getStates().size() - 1).getPosition());
    }

    @Test
    void testRelativeDirectionAndSensors() {
        assertEquals(Direction.WEST, SimulationEngine.getRelativeDir(Direction.NORTH, SensorDirection.LEFT));
        assertEquals(Direction.EAST, SimulationEngine.getRelativeDir(Direction.NORTH, SensorDirection.RIGHT));
        assertEquals(Direction.NORTH, SimulationEngine.getRelativeDir(Direction.NORTH, SensorDirection.AHEAD));

        assertEquals(Direction.NORTH, SimulationEngine.getRelativeDir(Direction.EAST, SensorDirection.LEFT));
        assertEquals(Direction.SOUTH, SimulationEngine.getRelativeDir(Direction.EAST, SensorDirection.RIGHT));

        assertEquals(Direction.EAST, SimulationEngine.getRelativeDir(Direction.SOUTH, SensorDirection.LEFT));
        assertEquals(Direction.WEST, SimulationEngine.getRelativeDir(Direction.SOUTH, SensorDirection.RIGHT));

        assertEquals(Direction.SOUTH, SimulationEngine.getRelativeDir(Direction.WEST, SensorDirection.LEFT));
        assertEquals(Direction.NORTH, SimulationEngine.getRelativeDir(Direction.WEST, SensorDirection.RIGHT));

        assertEquals(SensorDirection.AHEAD, SimulationEngine.conditionKindToSensor(ConditionKind.CHECK_FORWARD));
        assertEquals(SensorDirection.LEFT, SimulationEngine.conditionKindToSensor(ConditionKind.CHECK_LEFT));
        assertEquals(SensorDirection.RIGHT, SimulationEngine.conditionKindToSensor(ConditionKind.CHECK_RIGHT));
        assertEquals(SensorDirection.AHEAD, SimulationEngine.conditionKindToSensor(null));
    }
}
