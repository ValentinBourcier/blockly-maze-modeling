package blocky_momot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.io.File;
import java.io.IOException;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

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

/**
 * Headless simulator for Blocky maze programs. Executes a Level's solution
 * on its map and returns the final game status (WON, CRASHED, or RUNNING).
 * Used by MOMoT fitness to evaluate whether a transformed model reaches the goal.
 */
public final class BlockySimulator {

    private BlockySimulator() {}

    /**
     * By default, MoMoT evaluation is permissive and executes whatever program exists in the model,
     * even if it violates UI constraints like maxBlocks/allowed control-flow.
     *
     * Enable strict constraint enforcement via:
     *   -Dblocky.sim.enforceConstraints=true
     */
    private static final boolean ENFORCE_CONSTRAINTS =
            Boolean.parseBoolean(System.getProperty("blocky.sim.enforceConstraints", "false"));

    /**
     * The Blocky UI enforces constraints like max blocks / allowed control-flow.
     * For MOMoT fitness we must apply the same rules; otherwise we may mark an
     * illegal program as "WON" even though it can't be executed in-game.
     */
    public static boolean violatesLevelConstraints(Level level, Body solution) {
        if (level == null || solution == null) {
            return false;
        }

        int maxBlocks = level.getMaxBlocks();
        int blocks = countStatements(solution);
        if (maxBlocks > 0 && blocks > maxBlocks) {
            System.out.println("Diag: Violation - too many blocks: " + blocks + " > " + maxBlocks);
            return true;
        }

        if (!level.isAllowLoops() && containsLoop(solution)) {
            System.out.println("Diag: Violation - loops not allowed");
            return true;
        }

        if (!level.isAllowConditionals() && containsIf(solution)) {
            System.out.println("Diag: Violation - conditionals not allowed");
            return true;
        }

        return false;
    }

    private static int countStatements(Body body) {
        if (body == null) {
            return 0;
        }
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
        if (body == null) {
            return false;
        }
        return containsLoop(body.getFirstContainer());
    }

    private static boolean containsLoop(Container c) {
        Container cur = c;
        while (cur != null) {
            Statement s = cur.getStatement();
            if (s instanceof Loop) {
                return true;
            }
            if (s instanceof IfStmt) {
                IfStmt i = (IfStmt) s;
                if (containsLoop(i.getThenBody()) || containsLoop(i.getElseBody())) {
                    return true;
                }
            }
            cur = cur.getNext();
        }
        return false;
    }

    private static boolean containsIf(Body body) {
        if (body == null) {
            return false;
        }
        return containsIf(body.getFirstContainer());
    }

    private static boolean containsIf(Container c) {
        Container cur = c;
        while (cur != null) {
            Statement s = cur.getStatement();
            if (s != null) {
                if (s instanceof IfStmt) {
                    return true;
                }
                if (s instanceof Loop) {
                    if (containsIf(((Loop) s).getBody())) {
                        return true;
                    }
                }
            }
            cur = cur.getNext();
        }
        return false;
    }

    /**
     * Load the game model from the given path, annotate all cells in all levels with
     * their distance to the goal, and save the model back to the same path.
     * This is intended to be called during MOMoT initialization so that distance-to-goal
     * values are pre-computed and stored in the model.
     *
     * @param gameXmiPath path to the game XMI file
     */
    public static void initialize(String gameXmiPath) {
        // Ensure XMI is supported.
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().putIfAbsent("xmi", new XMIResourceFactoryImpl());

        // Register Blocky metamodel.
        BlockyPackage pkg = BlockyPackage.eINSTANCE;
        EPackage.Registry.INSTANCE.put(pkg.getNsURI(), pkg);
        EPackage.Registry.INSTANCE.put(pkg.getName(), pkg);

        ResourceSet rs = new ResourceSetImpl();
        rs.getPackageRegistry().put(pkg.getNsURI(), pkg);
        rs.getPackageRegistry().put(pkg.getName(), pkg);

        URI uri;
        File f = new File(gameXmiPath);
        if (f.isAbsolute()) {
            uri = URI.createFileURI(f.getAbsolutePath());
        } else {
            uri = URI.createFileURI(new File(System.getProperty("user.dir"), gameXmiPath).getAbsolutePath());
        }

        Resource r = rs.getResource(uri, true);
        try {
            r.load(null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load game XMI for annotation: " + uri, e);
        }

        if (r.getContents().isEmpty() || !(r.getContents().get(0) instanceof blocky.Game)) {
            return;
        }

        blocky.Game game = (blocky.Game) r.getContents().get(0);
        for (Level level : game.getLevels()) {
            annotateCells(level);
        }

        try {
            r.save(null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save annotated game XMI: " + uri, e);
        }
    }

    /**
     * Computes the BFS distance-to-goal for every cell in the level's map and
     * sets the 'distanceToGoal' attribute on each Cell object.
     *
     * @param level the level to annotate
     */
    public static void annotateCells(Level level) {
        if (level == null || level.getMap() == null) {
            return;
        }
        GridMap map = level.getMap();
        CellType goalType = determineWinCellType(level);
        Map<Cell, Integer> dist = computeDistanceField(map, goalType);

        for (Cell c : map.getCells()) {
            if (c == null) continue;
            Integer d = dist.get(c);
            c.setDistanceToGoal(d != null ? d : -1);
        }
    }

    /**
     * Minimum BFS distance-to-goal encountered along the solution path.
     * Computed live, so cells do not need a pre-set {@code distanceToGoal}.
     * Returns {@code 0} when the path reaches the goal, and {@code penalty}
     * when the level is invalid or no goal is reachable.
     *
     * @param level the level to simulate
     * @param penalty value to return if no goal is reachable or level is invalid
     * @return minimum distance to goal encountered during simulation
     */
    public static int closestToGoalOrPenalty(Level level, int penalty) {
        return distanceToGoalOrPenalty(level, penalty);
    }

    /**
     * Run the level's solution on the level's map and return the status of the
     * last game state (WON if goal reached, CRASHED on wall or loop limit, RUNNING if incomplete).
     *
     * @param level the level with map and solution (may be null solution)
     * @return final GameStatus after execution
     */
    public static GameStatus run(Level level) {
        if (level == null || level.getMap() == null) {
            return GameStatus.CRASHED;
        }
        Body solution = level.getSolution();
        if (solution == null) {
            return GameStatus.RUNNING; // no program: never wins
        }
        if (ENFORCE_CONSTRAINTS && violatesLevelConstraints(level, solution)) {
            return GameStatus.CRASHED;
        }

        GridMap map = level.getMap();
        Cell startCell = null;
        for (Cell c : map.getCells()) {
            if (c.getType() == CellType.START) {
                startCell = c;
                break;
            }
        }
        if (startCell == null) {
            startCell = map.getCells().isEmpty() ? null : map.getCells().get(0);
        }
        if (startCell == null) {
            return GameStatus.CRASHED;
        }

        GameRun g = executeLikeGame(level, startCell, null);
        if (g.crashed) {
            return GameStatus.CRASHED;
        }
        return g.position.getType() == determineWinCellType(level) ? GameStatus.WON : GameStatus.RUNNING;
    }

    /** Outcome of one run with the game's semantics (see {@link #executeLikeGame}). */
    private static final class GameRun {
        Cell position;
        Direction orientation;
        int actions;
        boolean crashed;
        int minDistance = Integer.MAX_VALUE;
    }

    /**
     * Executes the program exactly like the game's Blockly interpreter does:
     * <ul>
     * <li>the whole program runs; reaching the goal does not stop it, and the goal is judged by the final
     * position (statements after the arrival are executed and counted, and may leave the goal again);</li>
     * <li>an action is a move (including a failing one), a turn, or a path check of an if block;
     * loop blocks themselves are not actions;</li>
     * <li>a loop repeats while Pegman is not on the goal (maze_forever); a loop that makes no progress or runs
     * longer than the step budget counts as a crash (the game's tick limit).</li>
     * </ul>
     *
     * @param distanceField if not null, the minimum distance over all visited cells is tracked
     */
    private static GameRun executeLikeGame(Level level, Cell startCell, Map<Cell, Integer> distanceField) {
        GameRun g = new GameRun();
        g.position = startCell;
        g.orientation = determineStartOrientation(level, startCell);
        trackDistance(g, distanceField);
        GridMap map = level.getMap();
        int maxActions = map.getWidth() * map.getHeight() * 2;
        executeBodyLikeGame(level.getSolution(), g, determineWinCellType(level), maxActions, distanceField);
        return g;
    }

    private static void trackDistance(GameRun g, Map<Cell, Integer> distanceField) {
        if (distanceField == null) return;
        Integer d = distanceField.get(g.position);
        if (d != null) g.minDistance = Math.min(g.minDistance, d);
    }

    private static void executeBodyLikeGame(Body body, GameRun g, CellType win, int maxActions,
            Map<Cell, Integer> distanceField) {
        if (body == null) return;
        for (Container c = body.getFirstContainer(); c != null && !g.crashed; c = c.getNext()) {
            Statement stmt = c.getStatement();
            if (stmt instanceof AtomicStatement) {
                AtomicKindExec.apply((AtomicStatement) stmt, g);
                trackDistance(g, distanceField);
            } else if (stmt instanceof Loop) {
                Body loopBody = ((Loop) stmt).getBody();
                while (!g.crashed && g.position.getType() != win) {
                    if (g.actions > maxActions) {
                        g.crashed = true;
                        break;
                    }
                    int before = g.actions;
                    executeBodyLikeGame(loopBody, g, win, maxActions, distanceField);
                    if (g.actions == before && !g.crashed) {
                        g.crashed = true; // no progress: the game would run into its tick limit
                    }
                }
            } else if (stmt instanceof IfStmt) {
                IfStmt i = (IfStmt) stmt;
                g.actions++; // the path check is logged as an action by the game
                SensorDirection sensor = conditionKindToSensor(i.getCondition());
                Cell target = getAdjacent(g.position, getRelativeDir(g.orientation, sensor));
                boolean cond = target != null && target.getType() != CellType.WALL;
                executeBodyLikeGame(cond ? i.getThenBody() : i.getElseBody(), g, win, maxActions, distanceField);
            }
        }
    }

    /** One move or turn, with the same defaults as before (missing kind behaves as TURN_LEFT). */
    private static final class AtomicKindExec {
        static void apply(AtomicStatement a, GameRun g) {
            AtomicStatementKind kind = a.getKind() != null ? a.getKind() : AtomicStatementKind.TURN_LEFT;
            g.actions++;
            switch (kind) {
            case MOVE_FORWARD: {
                Cell target = getAdjacent(g.position, g.orientation);
                if (target == null || target.getType() == CellType.WALL) {
                    g.crashed = true;
                } else {
                    g.position = target;
                }
                break;
            }
            case TURN_RIGHT:
                g.orientation = getRelativeDir(g.orientation, SensorDirection.RIGHT);
                break;
            case TURN_LEFT:
            default:
                g.orientation = getRelativeDir(g.orientation, SensorDirection.LEFT);
                break;
            }
        }
    }

    /**
     * Executes the level's solution and returns the executed step count if the goal is reached;
     * otherwise returns a large penalty value. Intended for MOMoT fitness evaluation.
     */
    public static int stepsToGoalOrPenalty(Level level) {
        return stepsToGoalOrPenalty(level, 100000);
    }

    /**
     * Executes the level's solution and returns the total number of executed steps/actions
     * during the simulation (whether won, crashed, or terminated).
     */
    public static int simulationSteps(Level level) {
        if (level == null || level.getMap() == null) {
            return 0;
        }
        Body solution = level.getSolution();
        if (solution == null) {
            return 0;
        }
        if (ENFORCE_CONSTRAINTS && violatesLevelConstraints(level, solution)) {
            return 100000;
        }

        GridMap map = level.getMap();
        Cell startCell = null;
        for (Cell c : map.getCells()) {
            if (c.getType() == CellType.START) {
                startCell = c;
                break;
            }
        }
        if (startCell == null) {
            startCell = map.getCells().isEmpty() ? null : map.getCells().get(0);
        }
        if (startCell == null) {
            return 0;
        }

        return executeLikeGame(level, startCell, null).actions;
    }

    /**
     * Executes the level's solution and returns the executed step count if the goal is reached;
     * otherwise returns {@code penalty}.
     */
    public static int stepsToGoalOrPenalty(Level level, int penalty) {
        if (level == null || level.getMap() == null) {
            return penalty;
        }
        Body solution = level.getSolution();
        if (solution == null) {
            return penalty;
        }
        if (ENFORCE_CONSTRAINTS && violatesLevelConstraints(level, solution)) {
            return penalty;
        }

        GridMap map = level.getMap();
        Cell startCell = null;
        for (Cell c : map.getCells()) {
            if (c.getType() == CellType.START) {
                startCell = c;
                break;
            }
        }
        if (startCell == null) {
            startCell = map.getCells().isEmpty() ? null : map.getCells().get(0);
        }
        if (startCell == null) {
            return penalty;
        }

        GameRun g = executeLikeGame(level, startCell, null);
        boolean won = !g.crashed && g.position.getType() == determineWinCellType(level);
        return won ? g.actions : penalty;
    }

    /**
     * Executes the level's solution and returns the minimum BFS distance-to-goal encountered along
     * Pegman's path.
     *
     * We first compute a distance field by doing a multi-source BFS from all goal cells:
     * - If the map contains any {@code DMG} cell(s), DMG is treated as the goal.
     * - Otherwise {@code GOAL} is treated as the goal.
     *
     * While executing the program, we track the minimum distance value of any visited cell.
     * If Pegman reaches the goal, this value becomes 0.
     *
     * If the level is invalid, no goal cells exist, or the start cell is not connected to any goal,
     * returns {@code penalty}.
     */
    public static int distanceToGoalOrPenalty(Level level) {
        return distanceToGoalOrPenalty(level, 100000);
    }

    /**
     * Same as {@link #distanceToGoalOrPenalty(Level)} but with a custom penalty value.
     */
    public static int distanceToGoalOrPenalty(Level level, int penalty) {
        if (level == null || level.getMap() == null) {
            return penalty;
        }
        Body solution = level.getSolution();
        if (solution == null) {
            return penalty;
        }
        if (ENFORCE_CONSTRAINTS && violatesLevelConstraints(level, solution)) {
            return penalty;
        }

        GridMap map = level.getMap();
        Cell startCell = null;
        for (Cell c : map.getCells()) {
            if (c.getType() == CellType.START) {
                startCell = c;
                break;
            }
        }
        if (startCell == null) {
            startCell = map.getCells().isEmpty() ? null : map.getCells().get(0);
        }
        if (startCell == null) {
            return penalty;
        }

        final CellType winCellType = determineWinCellType(level);
        Map<Cell, Integer> distanceField = computeDistanceField(map, winCellType);
        if (distanceField.isEmpty()) {
            return penalty;
        }

        GameRun g = executeLikeGame(level, startCell, distanceField);
        if (g.minDistance == Integer.MAX_VALUE) {
            return penalty;
        }
        // The game judges the goal by the final position: a run that passed over the goal but ended elsewhere
        // has not reached it, so it is not reported as distance 0.
        boolean onGoal = !g.crashed && g.position.getType() == winCellType;
        return (g.minDistance == 0 && !onGoal) ? 1 : g.minDistance;
    }

    private static Map<Cell, Integer> computeDistanceField(GridMap map, CellType goalType) {
        Map<Cell, Integer> dist = new IdentityHashMap<>();
        if (map == null || goalType == null) {
            return dist;
        }

        Deque<Cell> q = new ArrayDeque<>();
        for (Cell c : map.getCells()) {
            if (c != null && c.getType() == goalType) {
                dist.put(c, 0);
                q.addLast(c);
            }
        }
        if (q.isEmpty()) {
            return dist;
        }

        while (!q.isEmpty()) {
            Cell cur = q.removeFirst();
            int d = dist.get(cur);
            Cell[] neigh = new Cell[] { cur.getTop(), cur.getRight(), cur.getBottom(), cur.getLeft() };
            for (Cell n : neigh) {
                if (n == null) continue;
                if (n.getType() == CellType.WALL) continue;
                if (dist.containsKey(n)) continue;
                dist.put(n, d + 1);
                q.addLast(n);
            }
        }
        return dist;
    }

    public static CellType determineWinCellType(Level level) {
        if (level == null || level.getMap() == null) return CellType.GOAL;
        for (Cell c : level.getMap().getCells()) {
            if (c != null && c.getType() == CellType.DMG) {
                return CellType.DMG;
            }
        }
        return CellType.GOAL;
    }

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

    private static SensorDirection conditionKindToSensor(ConditionKind ck) {
        // Align with generated EMF default: missing condition behaves as CHECK_FORWARD.
        if (ck == null) ck = ConditionKind.CHECK_FORWARD;
        if (ck == ConditionKind.CHECK_LEFT) return SensorDirection.LEFT;
        if (ck == ConditionKind.CHECK_RIGHT) return SensorDirection.RIGHT;
        return SensorDirection.AHEAD;
    }

    private static Cell getAdjacent(Cell c, Direction d) {
        switch (d) {
            case NORTH: return c.getTop();
            case SOUTH: return c.getBottom();
            case EAST:  return c.getRight();
            case WEST:  return c.getLeft();
        }
        return null;
    }

    private static Direction getRelativeDir(Direction curr, SensorDirection sensor) {
        // Align with generated EMF default: missing orientation behaves as NORTH.
        if (curr == null) curr = Direction.NORTH;
        if (sensor == SensorDirection.AHEAD) return curr;
        if (sensor == SensorDirection.LEFT) {
            switch (curr) {
                case NORTH: return Direction.WEST;
                case WEST:  return Direction.SOUTH;
                case SOUTH: return Direction.EAST;
                case EAST:  return Direction.NORTH;
            }
        } else {
            switch (curr) {
                case NORTH: return Direction.EAST;
                case EAST:  return Direction.SOUTH;
                case SOUTH: return Direction.WEST;
                case WEST:  return Direction.NORTH;
            }
        }
        return curr;
    }
}
