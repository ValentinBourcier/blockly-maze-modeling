package blocky_game;

import blocky.*;
import blocky_momot.BlockySimulator;
import blocky_momot.SimulationEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization tests locking parity between BlockySimulator and SimulationEngine
 * across all input levels and representative statement sequences.
 */
class PegmanSimulationCharacterizationTest {

    private static ModelStorageService storageService;

    @BeforeAll
    static void setup() {
        storageService = new ModelStorageService();
    }

    private File resolveLevelFile(int id) {
        File file = new File("blocky_game/model/input/" + id + ".xmi").getAbsoluteFile();
        if (!file.exists()) {
            file = new File("model/input/" + id + ".xmi").getAbsoluteFile();
        }
        assertTrue(file.exists(), "Input level file not found for level " + id + ": " + file.getAbsolutePath());
        return file;
    }

    private Level loadLevel(int id) throws IOException {
        File f = resolveLevelFile(id);
        ModelStorageService.LoadResult lr = storageService.loadFromFile(f);
        assertNotNull(lr.level);
        return lr.level;
    }

    private List<String> extractPositions(ExecutionTrace trace) {
        List<String> result = new ArrayList<>();
        if (trace != null) {
            for (GameState s : trace.getStates()) {
                if (s.getPosition() != null) {
                    result.add("(" + s.getPosition().getX() + "," + s.getPosition().getY() + ")");
                } else {
                    result.add("(null)");
                }
            }
        }
        return result;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void testInitialLevelStateCharacterization(int levelId) throws IOException {
        Level level = loadLevel(levelId);

        // Run SimulationEngine
        SimulationEngine.SimulationResult simResult = SimulationEngine.simulate(level);
        GameStatus simStatus = simResult.getFinalStatus();
        int simSteps = simResult.getSteps();
        List<String> positions = extractPositions(simResult.getTrace());

        // Locked canonical initial state baselines
        GameStatus expectedStatus = GameStatus.RUNNING;
        int expectedSteps;
        List<String> expectedPositions;

        switch (levelId) {
            case 1:
                expectedSteps = 1;
                expectedPositions = List.of("(2,4)", "(2,4)");
                break;
            case 2:
                expectedSteps = 1;
                expectedPositions = List.of("(2,4)", "(2,4)");
                break;
            case 3:
                expectedSteps = 1;
                expectedPositions = List.of("(1,4)", "(1,4)");
                break;
            case 4:
                expectedSteps = 1;
                expectedPositions = List.of("(1,6)", "(1,6)");
                break;
            case 5:
                expectedSteps = 1;
                expectedPositions = List.of("(3,6)", "(3,6)");
                break;
            case 6:
                expectedSteps = 1;
                expectedPositions = List.of("(1,6)", "(1,6)");
                break;
            case 7:
                expectedSteps = 1;
                expectedPositions = List.of("(1,2)", "(1,2)");
                break;
            case 8:
                expectedSteps = 1;
                expectedPositions = List.of("(1,6)", "(1,6)");
                break;
            case 9:
                // Level 9 input XMI contains a pre-saved MOVE_FORWARD step
                expectedSteps = 1;
                expectedPositions = List.of("(5,6)", "(6,6)");
                break;
            case 10:
                expectedSteps = 1;
                expectedPositions = List.of("(1,6)", "(1,6)");
                break;
            default:
                throw new IllegalArgumentException("Unknown level: " + levelId);
        }

        assertEquals(expectedStatus, simStatus, "Status mismatch for level " + levelId);
        assertEquals(expectedSteps, simSteps, "Step count mismatch for level " + levelId);
        assertEquals(expectedPositions, positions, "Position sequence mismatch for level " + levelId);

        // Also assert BlockySimulator adapter fitness values match
        assertEquals(simStatus, BlockySimulator.run(level));
        // BlockySimulator counts game actions (moves, turns, path checks): an empty container is none, level 9 has one saved move.
        assertEquals(levelId == 9 ? 1 : 0, BlockySimulator.simulationSteps(level));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void testSingleStepMoveForwardParity(int levelId) throws IOException {
        Level level = loadLevel(levelId);
        Body body = BlockyFactory.eINSTANCE.createBody();
        level.setSolution(body);

        Container c1 = BlockyFactory.eINSTANCE.createContainer();
        AtomicStatement a1 = BlockyFactory.eINSTANCE.createAtomicStatement();
        a1.setKind(AtomicStatementKind.MOVE_FORWARD);
        c1.setStatement(a1);
        body.setFirstContainer(c1);

        SimulationEngine.SimulationResult simResult = SimulationEngine.simulate(level);
        List<String> positions = extractPositions(simResult.getTrace());

        List<String> expectedPositions;
        switch (levelId) {
            case 1:
            case 2:
                expectedPositions = List.of("(2,4)", "(3,4)");
                break;
            case 3:
                expectedPositions = List.of("(1,4)", "(2,4)");
                break;
            case 4:
            case 6:
            case 8:
            case 10:
                expectedPositions = List.of("(1,6)", "(2,6)");
                break;
            case 5:
                expectedPositions = List.of("(3,6)", "(4,6)");
                break;
            case 7:
                expectedPositions = List.of("(1,2)", "(2,2)");
                break;
            case 9:
                expectedPositions = List.of("(5,6)", "(6,6)");
                break;
            default:
                throw new IllegalArgumentException("Unknown level: " + levelId);
        }

        assertEquals(GameStatus.RUNNING, simResult.getFinalStatus(), "Status mismatch for level " + levelId);
        assertEquals(1, simResult.getSteps(), "Step count mismatch for level " + levelId);
        assertEquals(expectedPositions, positions, "Position sequence mismatch for single step level " + levelId);

        // BlockySimulator adapter parity
        assertEquals(GameStatus.RUNNING, BlockySimulator.run(level));
        assertEquals(1, BlockySimulator.simulationSteps(level));
    }

    @Test
    void testLevel1WinningProgramParity() throws IOException {
        Level level = loadLevel(1);
        Body body = BlockyFactory.eINSTANCE.createBody();
        level.setSolution(body);

        // Level 1: START (2,4) facing EAST. (3,4) is EMPTY. (4,4) is GOAL.
        Container c1 = BlockyFactory.eINSTANCE.createContainer();
        AtomicStatement a1 = BlockyFactory.eINSTANCE.createAtomicStatement();
        a1.setKind(AtomicStatementKind.MOVE_FORWARD);
        c1.setStatement(a1);

        Container c2 = BlockyFactory.eINSTANCE.createContainer();
        AtomicStatement a2 = BlockyFactory.eINSTANCE.createAtomicStatement();
        a2.setKind(AtomicStatementKind.MOVE_FORWARD);
        c2.setStatement(a2);

        c1.setNext(c2);
        body.setFirstContainer(c1);

        GameStatus momotStatus = BlockySimulator.run(level);
        int momotSteps = BlockySimulator.simulationSteps(level);
        int momotDist = BlockySimulator.distanceToGoalOrPenalty(level, 100000);

        SimulationEngine.SimulationResult simResult = SimulationEngine.simulate(level);

        assertEquals(GameStatus.WON, momotStatus);
        assertEquals(GameStatus.WON, simResult.getFinalStatus());
        assertEquals(2, momotSteps);
        assertEquals(2, simResult.getSteps());
        assertEquals(0, momotDist);

        List<String> positions = extractPositions(simResult.getTrace());
        assertEquals(List.of("(2,4)", "(3,4)", "(4,4)"), positions);
    }

    @Test
    void testLevel1CrashProgramParity() throws IOException {
        Level level = loadLevel(1);
        Body body = BlockyFactory.eINSTANCE.createBody();
        level.setSolution(body);

        // Turn LEFT then MOVE_FORWARD -> crashes into wall (2,3)
        Container c1 = BlockyFactory.eINSTANCE.createContainer();
        AtomicStatement a1 = BlockyFactory.eINSTANCE.createAtomicStatement();
        a1.setKind(AtomicStatementKind.TURN_LEFT);
        c1.setStatement(a1);

        Container c2 = BlockyFactory.eINSTANCE.createContainer();
        AtomicStatement a2 = BlockyFactory.eINSTANCE.createAtomicStatement();
        a2.setKind(AtomicStatementKind.MOVE_FORWARD);
        c2.setStatement(a2);

        c1.setNext(c2);
        body.setFirstContainer(c1);

        GameStatus momotStatus = BlockySimulator.run(level);
        SimulationEngine.SimulationResult simResult = SimulationEngine.simulate(level);

        assertEquals(GameStatus.CRASHED, momotStatus);
        assertEquals(GameStatus.CRASHED, simResult.getFinalStatus());
        assertEquals(2, simResult.getSteps());

        List<String> positions = extractPositions(simResult.getTrace());
        assertEquals(List.of("(2,4)", "(2,4)", "(2,4)"), positions);
    }

    @Test
    void testConditionalsAndLoopParity() throws IOException {
        Level level = loadLevel(10);
        Body body = BlockyFactory.eINSTANCE.createBody();
        level.setSolution(body);

        // While loop with if-path-ahead move forward else turn left
        Loop loop = BlockyFactory.eINSTANCE.createLoop();
        Container loopContainer = BlockyFactory.eINSTANCE.createContainer();
        loopContainer.setStatement(loop);
        body.setFirstContainer(loopContainer);

        Body loopBody = BlockyFactory.eINSTANCE.createBody();
        loop.setBody(loopBody);

        IfStmt ifStmt = BlockyFactory.eINSTANCE.createIfStmt();
        ifStmt.setCondition(ConditionKind.CHECK_FORWARD);

        Body thenBody = BlockyFactory.eINSTANCE.createBody();
        Container cMove = BlockyFactory.eINSTANCE.createContainer();
        AtomicStatement aMove = BlockyFactory.eINSTANCE.createAtomicStatement();
        aMove.setKind(AtomicStatementKind.MOVE_FORWARD);
        cMove.setStatement(aMove);
        thenBody.setFirstContainer(cMove);
        ifStmt.setThenBody(thenBody);

        Body elseBody = BlockyFactory.eINSTANCE.createBody();
        Container cTurn = BlockyFactory.eINSTANCE.createContainer();
        AtomicStatement aTurn = BlockyFactory.eINSTANCE.createAtomicStatement();
        aTurn.setKind(AtomicStatementKind.TURN_LEFT);
        cTurn.setStatement(aTurn);
        elseBody.setFirstContainer(cTurn);
        ifStmt.setElseBody(elseBody);

        Container ifContainer = BlockyFactory.eINSTANCE.createContainer();
        ifContainer.setStatement(ifStmt);
        loopBody.setFirstContainer(ifContainer);

        GameStatus momotStatus = BlockySimulator.run(level);
        int momotSteps = BlockySimulator.simulationSteps(level);

        SimulationEngine.SimulationResult simResult = SimulationEngine.simulate(level);

        assertEquals(momotStatus, simResult.getFinalStatus());
        // BlockySimulator counts game actions (the loop block and the if blocks of the engine trace differ), so only the status is shared.
        assertTrue(momotSteps > 0);
        assertEquals(GameStatus.CRASHED, simResult.getFinalStatus());
        assertEquals(129, simResult.getSteps());
        assertEquals(130, simResult.getTrace().getStates().size());
        assertEquals("(1,6)", extractPositions(simResult.getTrace()).get(0));
    }

    @Test
    void testLevel2WinningProgramParity() throws IOException {
        Level level = loadLevel(2);
        Body body = BlockyFactory.eINSTANCE.createBody();
        level.setSolution(body);

        // Sequence: MOVE_FORWARD, TURN_LEFT, MOVE_FORWARD, TURN_RIGHT, MOVE_FORWARD
        AtomicStatementKind[] kinds = {
            AtomicStatementKind.MOVE_FORWARD,
            AtomicStatementKind.TURN_LEFT,
            AtomicStatementKind.MOVE_FORWARD,
            AtomicStatementKind.TURN_RIGHT,
            AtomicStatementKind.MOVE_FORWARD
        };
        Container head = null;
        Container cur = null;
        for (AtomicStatementKind k : kinds) {
            Container c = BlockyFactory.eINSTANCE.createContainer();
            AtomicStatement a = BlockyFactory.eINSTANCE.createAtomicStatement();
            a.setKind(k);
            c.setStatement(a);
            if (head == null) {
                head = c;
            } else {
                cur.setNext(c);
            }
            cur = c;
        }
        body.setFirstContainer(head);

        SimulationEngine.SimulationResult simResult = SimulationEngine.simulate(level);
        assertEquals(GameStatus.WON, simResult.getFinalStatus());
        assertEquals(5, simResult.getSteps());
        assertEquals(List.of("(2,4)", "(3,4)", "(3,4)", "(3,3)", "(3,3)", "(4,3)"), extractPositions(simResult.getTrace()));

        assertEquals(GameStatus.WON, BlockySimulator.run(level));
        assertEquals(5, BlockySimulator.simulationSteps(level));
        assertEquals(0, BlockySimulator.distanceToGoalOrPenalty(level, 100000));
    }
}
