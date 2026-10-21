package blocky_momot;

import blocky.AtomicStatement;
import blocky.AtomicStatementKind;
import blocky.BlockyFactory;
import blocky.BlockyPackage;
import blocky.Body;
import blocky.Cell;
import blocky.CellType;
import blocky.ConditionKind;
import blocky.Container;
import blocky.Direction;
import blocky.ExecutionTrace;
import blocky.GameState;
import blocky.GameStatus;
import blocky.GridMap;
import blocky.IfStmt;
import blocky.Level;
import blocky.Loop;
import blocky.SensorDirection;
import blocky.Statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deep module encapsulating all Pegman maze simulation logic.
 *
 * Provides deterministic simulation of Blocky programs across levels and bodies,
 * step trace generation, loop bounds protection, collision detection, and
 * structured logging for debugger/feedback services and MoMoT fitness evaluation.
 */
public final class SimulationEngine {

    public static final int DEFAULT_LOOP_LIMIT = 500;

    private SimulationEngine() {}

    /**
     * Immutable outcome of a simulation run.
     */
    public static class SimulationResult {
        public final ExecutionTrace trace;
        public final List<String> logs; // non-null, holds step log messages
        public final GameStatus finalStatus;
        public final int steps;

        public SimulationResult(ExecutionTrace trace, List<String> logs, GameStatus finalStatus, int steps) {
            this.trace = trace;
            this.logs = logs != null ? Collections.unmodifiableList(new ArrayList<>(logs)) : Collections.emptyList();
            this.finalStatus = finalStatus;
            this.steps = steps;
        }

        public ExecutionTrace getTrace() {
            return trace;
        }

        public List<String> getLogs() {
            return logs;
        }

        public GameStatus getFinalStatus() {
            return finalStatus;
        }

        public int getSteps() {
            return steps;
        }

        public boolean isWon() {
            return finalStatus == GameStatus.WON;
        }

        public boolean isCrashed() {
            return finalStatus == GameStatus.CRASHED;
        }
    }

    /**
     * Identifies which cell type counts as the "winning" goal for the given level.
     * Prioritizes DMG (Direct Manipulation Goal) if it exists, otherwise GOAL.
     */
    public static CellType determineWinCellType(Level level) {
        if (level == null || level.getMap() == null) return CellType.GOAL;
        for (Cell c : level.getMap().getCells()) {
            if (c != null && c.getType() == CellType.DMG) {
                return CellType.DMG;
            }
        }
        return CellType.GOAL;
    }

    /**
     * Infers a stable starting orientation if one is not explicitly set in the model.
     * Logic: pick the first non-wall neighbor (N, E, S, W order), fallback to NORTH.
     */
    public static Direction determineStartOrientation(Level level, Cell start) {
        if (level != null && level.eIsSet(BlockyPackage.Literals.LEVEL__START_ORIENTATION)) {
            return level.getStartOrientation();
        }
        if (start == null) return Direction.NORTH;
        if (start.getTop() != null && start.getTop().getType() != CellType.WALL) return Direction.NORTH;
        if (start.getRight() != null && start.getRight().getType() != CellType.WALL) return Direction.EAST;
        if (start.getBottom() != null && start.getBottom().getType() != CellType.WALL) return Direction.SOUTH;
        if (start.getLeft() != null && start.getLeft().getType() != CellType.WALL) return Direction.WEST;
        return Direction.NORTH;
    }

    /**
     * Checks if a solution violates the constraints of a level (max blocks, allowed control flow).
     */
    public static boolean violatesLevelConstraints(Level level, Body solution) {
        if (level == null || solution == null) {
            return false;
        }

        int maxBlocks = level.getMaxBlocks();
        int blocks = countStatements(solution);
        if (maxBlocks > 0 && blocks > maxBlocks) {
            return true;
        }

        if (!level.isAllowLoops() && containsLoop(solution)) {
            return true;
        }

        if (!level.isAllowConditionals() && containsIf(solution)) {
            return true;
        }

        return false;
    }

    public static int countStatements(Body body) {
        if (body == null) return 0;
        return countStatements(body.getFirstContainer());
    }

    private static int countStatements(Container c) {
        int count = 0;
        Container cur = c;
        while (cur != null) {
            Statement s = cur.getStatement();
            if (s != null) {
                count++;
                if (s instanceof Loop) {
                    count += countStatements(((Loop) s).getBody());
                } else if (s instanceof IfStmt) {
                    IfStmt i = (IfStmt) s;
                    count += countStatements(i.getThenBody());
                    count += countStatements(i.getElseBody());
                }
            }
            cur = cur.getNext();
        }
        return count;
    }

    private static boolean containsLoop(Body body) {
        if (body == null) return false;
        return containsLoop(body.getFirstContainer());
    }

    private static boolean containsLoop(Container c) {
        Container cur = c;
        while (cur != null) {
            Statement s = cur.getStatement();
            if (s instanceof Loop) return true;
            if (s instanceof IfStmt) {
                IfStmt i = (IfStmt) s;
                if (containsLoop(i.getThenBody()) || containsLoop(i.getElseBody())) return true;
            }
            cur = cur.getNext();
        }
        return false;
    }

    private static boolean containsIf(Body body) {
        if (body == null) return false;
        return containsIf(body.getFirstContainer());
    }

    private static boolean containsIf(Container c) {
        Container cur = c;
        while (cur != null) {
            Statement s = cur.getStatement();
            if (s != null) {
                if (s instanceof IfStmt) return true;
                if (s instanceof Loop) {
                    if (containsIf(((Loop) s).getBody())) return true;
                }
            }
            cur = cur.getNext();
        }
        return false;
    }

    /**
     * Simulates the solution of the given Level.
     */
    public static SimulationResult simulate(Level level) {
        return simulate(level, level != null ? level.getSolution() : null);
    }

    /**
     * Full simulation from a Level and a program Body.
     * Evaluates constraints (if enabled), infers start orientation if needed, and runs simulation.
     */
    public static SimulationResult simulate(Level level, Body body) {
        if (level == null) {
            ExecutionTrace trace = BlockyFactory.eINSTANCE.createExecutionTrace();
            List<String> logs = new ArrayList<>();
            logs.add("Result: CRASH (No current level)");
            return new SimulationResult(trace, logs, GameStatus.CRASHED, 0);
        }

        GridMap map = level.getMap();
        if (map == null) {
            ExecutionTrace trace = BlockyFactory.eINSTANCE.createExecutionTrace();
            List<String> logs = new ArrayList<>();
            logs.add("Result: CRASH (No map in level)");
            return new SimulationResult(trace, logs, GameStatus.CRASHED, 0);
        }

        Cell startNode = null;
        for (Cell c : map.getCells()) {
            if (c != null && c.getType() == CellType.START) {
                startNode = c;
                break;
            }
        }
        if (startNode == null && !map.getCells().isEmpty()) {
            startNode = map.getCells().get(0);
        }
        if (startNode == null) {
            ExecutionTrace trace = BlockyFactory.eINSTANCE.createExecutionTrace();
            List<String> logs = new ArrayList<>();
            logs.add("Result: CRASH (No start cell)");
            return new SimulationResult(trace, logs, GameStatus.CRASHED, 0);
        }

        Direction startDir = determineStartOrientation(level, startNode);
        if (!level.eIsSet(BlockyPackage.Literals.LEVEL__START_ORIENTATION)) {
            level.setStartOrientation(startDir);
        }

        CellType winCellType = determineWinCellType(level);

        boolean enforceConstraints = Boolean.parseBoolean(System.getProperty("blocky.sim.enforceConstraints", "false"));
        if (enforceConstraints && violatesLevelConstraints(level, body)) {
            ExecutionTrace trace = BlockyFactory.eINSTANCE.createExecutionTrace();
            GameState initialState = BlockyFactory.eINSTANCE.createGameState();
            initialState.setStep(0);
            initialState.setPosition(startNode);
            initialState.setOrientation(startDir);
            initialState.setStatus(GameStatus.CRASHED);
            trace.getStates().add(initialState);

            List<String> logs = new ArrayList<>();
            logs.add("Start: " + formatPos(startNode) + " dir=" + startDir);
            logs.add("Result: CRASH (Constraints violated)");
            return new SimulationResult(trace, logs, GameStatus.CRASHED, 0);
        }

        return simulate(map, startNode, startDir, body, winCellType);
    }

    /**
     * Simulation from specific map, start cell, start direction, target win cell type (DMG or GOAL),
     * and optional program Body.
     */
    public static SimulationResult simulate(GridMap map, Cell startCell, Direction startDir, Body body, CellType winCellType) {
        ExecutionTrace trace = BlockyFactory.eINSTANCE.createExecutionTrace();
        List<String> logs = new ArrayList<>();

        if (startDir == null) {
            startDir = Direction.NORTH;
        }
        if (winCellType == null) {
            winCellType = CellType.GOAL;
        }

        GameState initialState = BlockyFactory.eINSTANCE.createGameState();
        initialState.setStep(0);
        initialState.setPosition(startCell);
        initialState.setOrientation(startDir);
        initialState.setStatus(GameStatus.RUNNING);
        trace.getStates().add(initialState);

        logs.add("Start: " + formatPos(startCell) + " dir=" + startDir);

        if (body != null) {
            executeBody(body, initialState, trace, map, logs, winCellType);
        }

        GameState last = trace.getStates().isEmpty() ? null : trace.getStates().get(trace.getStates().size() - 1);
        GameStatus status = last != null ? last.getStatus() : GameStatus.CRASHED;
        int steps = last != null ? last.getStep() : 0;

        return new SimulationResult(trace, logs, status, steps);
    }

    /**
     * Executes a Body within an ongoing trace.
     */
    public static GameState executeBody(Body body, GameState state, ExecutionTrace trace, GridMap map, List<String> logs, CellType winCellType) {
        if (body == null) {
            return state;
        }
        return executeContainerChain(body.getFirstContainer(), state, trace, map, logs, winCellType);
    }

    public static GameState executeBody(Body body, GameState state, ExecutionTrace trace, CellType winCellType) {
        return executeBody(body, state, trace, null, null, winCellType);
    }

    /**
     * Iterates over a chain of containers, updating game states and logging steps.
     */
    public static GameState executeContainerChain(Container first, GameState state, ExecutionTrace trace, GridMap map, List<String> logs, CellType winCellType) {
        Container current = first;
        GameState last = state;
        while (current != null && last.getStatus() == GameStatus.RUNNING && (last.getPosition() == null || last.getPosition().getType() != winCellType)) {
            Statement stmt = current.getStatement();
            last = executeSingle(stmt, last, trace, map, logs, winCellType);
            current = current.getNext();
        }
        return last;
    }

    public static GameState executeContainerChain(Container first, GameState state, ExecutionTrace trace, CellType winCellType) {
        return executeContainerChain(first, state, trace, null, null, winCellType);
    }

    /**
     * Executes a single statement, creating the next GameState in the trace and recording logs.
     */
    public static GameState executeSingle(Statement stmt, GameState prev, ExecutionTrace trace, GridMap map, List<String> logs, CellType winCellType) {
        GameState next = BlockyFactory.eINSTANCE.createGameState();
        next.setStep(prev.getStep() + 1);
        next.setOrientation(prev.getOrientation());
        next.setPosition(prev.getPosition());
        next.setStatus(GameStatus.RUNNING);
        next.setExecutingStatement(stmt);
        next.setPrevious(prev);
        trace.getStates().add(next);

        if (stmt == null) {
            if (logs != null) {
                logs.add("Step " + next.getStep() + ": (empty)");
            }
            return next;
        }

        if (stmt instanceof AtomicStatement) {
            AtomicStatement a = (AtomicStatement) stmt;
            AtomicStatementKind kind = a.getKind();
            if (kind == null) kind = AtomicStatementKind.TURN_LEFT;
            switch (kind) {
            case MOVE_FORWARD: {
                Cell target = getAdjacent(next.getPosition(), next.getOrientation());
                if (target == null || target.getType() == CellType.WALL) {
                    next.setStatus(GameStatus.CRASHED);
                    if (logs != null) {
                        logs.add("Step " + next.getStep() + ": MoveForward -> CRASH at (" + safeX(next.getPosition()) + "," + safeY(next.getPosition()) + ")");
                    }
                } else {
                    next.setPosition(target);
                    if (target.getType() == winCellType) {
                        next.setStatus(GameStatus.WON);
                        if (logs != null) {
                            logs.add("Step " + next.getStep() + ": MoveForward -> (" + target.getX() + "," + target.getY() + ") GOAL");
                        }
                    } else {
                        if (logs != null) {
                            logs.add("Step " + next.getStep() + ": MoveForward -> (" + target.getX() + "," + target.getY() + ")");
                        }
                    }
                }
                break;
            }
            case TURN_LEFT: {
                Direction before = next.getOrientation();
                next.setOrientation(getRelativeDir(next.getOrientation(), SensorDirection.LEFT));
                if (logs != null) {
                    logs.add("Step " + next.getStep() + ": TurnLeft -> " + before + "→" + next.getOrientation());
                }
                break;
            }
            case TURN_RIGHT: {
                Direction before = next.getOrientation();
                next.setOrientation(getRelativeDir(next.getOrientation(), SensorDirection.RIGHT));
                if (logs != null) {
                    logs.add("Step " + next.getStep() + ": TurnRight -> " + before + "→" + next.getOrientation());
                }
                break;
            }
            default:
                if (logs != null) {
                    logs.add("Step " + next.getStep() + ": " + stmt.getClass().getSimpleName().replace("Impl", ""));
                }
                break;
            }
        } else if (stmt instanceof Loop) {
            if (logs != null) {
                logs.add("Step " + next.getStep() + ": Loop");
            }
            Loop r = (Loop) stmt;
            GameState loop = next;
            int maxSteps = computeLoopLimit(map);
            while (loop.getStatus() == GameStatus.RUNNING && (loop.getPosition() == null || loop.getPosition().getType() != winCellType)) {
                if (loop.getStep() > maxSteps) {
                    loop.setStatus(GameStatus.CRASHED);
                    if (logs != null) {
                        logs.add("Result: INFINITE_LOOP (bound " + maxSteps + " exceeded)");
                    }
                    break;
                }
                int previousStep = loop.getStep();
                Body b = r.getBody();
                loop = executeContainerChain(b != null ? b.getFirstContainer() : null, loop, trace, map, logs, winCellType);
                if (loop.getStep() == previousStep) {
                    loop.setStatus(GameStatus.CRASHED);
                    if (logs != null) {
                        logs.add("Result: CRASH (empty loop body / no progress)");
                    }
                    break;
                }
            }
            return loop;
        } else if (stmt instanceof IfStmt) {
            IfStmt i = (IfStmt) stmt;
            boolean cond = checkCondition(next, i.getCondition());
            if (logs != null) {
                String branch = cond ? "then" : (i.getElseBody() != null ? "else" : "skip");
                logs.add("Step " + next.getStep() + ": If " + conditionKindToSensor(i.getCondition()) + " -> " + cond + " (" + branch + ")");
            }
            if (cond) {
                Body b = i.getThenBody();
                return executeContainerChain(b != null ? b.getFirstContainer() : null, next, trace, map, logs, winCellType);
            }
            if (i.getElseBody() != null) {
                Body b = i.getElseBody();
                return executeContainerChain(b != null ? b.getFirstContainer() : null, next, trace, map, logs, winCellType);
            }
        } else {
            if (logs != null) {
                logs.add("Step " + next.getStep() + ": " + stmt.getClass().getSimpleName().replace("Impl", ""));
            }
        }

        return next;
    }

    public static GameState executeSingle(Statement stmt, GameState prev, ExecutionTrace trace, CellType winCellType) {
        return executeSingle(stmt, prev, trace, null, null, winCellType);
    }

    /**
     * Determines loop limit based on grid map dimensions or defaults to 500.
     */
    public static int computeLoopLimit(GridMap map) {
        if (map != null && map.getWidth() > 0 && map.getHeight() > 0) {
            return map.getWidth() * map.getHeight() * 2;
        }
        return DEFAULT_LOOP_LIMIT;
    }

    /**
     * Checks whether the cell in the relative sensor direction from state's position and orientation is walkable.
     */
    public static boolean checkSensor(GameState state, SensorDirection sensor) {
        if (state == null || state.getPosition() == null) return false;
        Direction actual = getRelativeDir(state.getOrientation(), sensor);
        Cell target = getAdjacent(state.getPosition(), actual);
        return target != null && target.getType() != CellType.WALL;
    }

    /**
     * Evaluates condition kind (forward, left, right) relative to state.
     */
    public static boolean checkCondition(GameState state, ConditionKind ck) {
        return checkSensor(state, conditionKindToSensor(ck));
    }

    /**
     * Returns adjacent cell in the absolute compass direction.
     */
    public static Cell getAdjacent(Cell c, Direction d) {
        if (c == null || d == null) return null;
        switch (d) {
            case NORTH:
                return c.getTop();
            case SOUTH:
                return c.getBottom();
            case EAST:
                return c.getRight();
            case WEST:
                return c.getLeft();
        }
        return null;
    }

    /**
     * Converts a relative sensor direction to absolute direction given current orientation.
     */
    public static Direction getRelativeDir(Direction curr, SensorDirection sensor) {
        if (curr == null) return Direction.NORTH;
        if (sensor == null || sensor == SensorDirection.AHEAD)
            return curr;
        if (sensor == SensorDirection.LEFT) {
            switch (curr) {
                case NORTH:
                    return Direction.WEST;
                case WEST:
                    return Direction.SOUTH;
                case SOUTH:
                    return Direction.EAST;
                case EAST:
                    return Direction.NORTH;
            }
        } else {
            switch (curr) {
                case NORTH:
                    return Direction.EAST;
                case EAST:
                    return Direction.SOUTH;
                case SOUTH:
                    return Direction.WEST;
                case WEST:
                    return Direction.NORTH;
            }
        }
        return curr;
    }

    /**
     * Maps condition kind to sensor direction (defaults to AHEAD for null or CHECK_FORWARD).
     */
    public static SensorDirection conditionKindToSensor(ConditionKind ck) {
        if (ck == null) {
            ck = ConditionKind.CHECK_FORWARD;
        }
        if (ck == ConditionKind.CHECK_LEFT) {
            return SensorDirection.LEFT;
        }
        if (ck == ConditionKind.CHECK_RIGHT) {
            return SensorDirection.RIGHT;
        }
        return SensorDirection.AHEAD;
    }

    public static int safeX(Cell c) {
        return c != null ? c.getX() : 0;
    }

    public static int safeY(Cell c) {
        return c != null ? c.getY() : 0;
    }

    public static String formatPos(Cell c) {
        if (c == null) return "null";
        return "(" + c.getX() + "," + c.getY() + ")";
    }
}
