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

import blocky.BlockyPackage;
import blocky.Body;
import blocky.Cell;
import blocky.CellType;
import blocky.Direction;
import blocky.GameState;
import blocky.GameStatus;
import blocky.GridMap;
import blocky.Level;

/**
 * Headless simulator for Blocky maze programs.
 * Adapts {@link SimulationEngine} traces to MOMoT fitness objectives.
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
        return SimulationEngine.violatesLevelConstraints(level, solution);
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
     * Computed live from the execution trace.
     * Returns {@code 0} when the path reaches the goal, and {@code penalty}
     * when the level is invalid or no goal is reachable.
     */
    public static int closestToGoalOrPenalty(Level level, int penalty) {
        return distanceToGoalOrPenalty(level, penalty);
    }

    /**
     * Run the level's solution on the level's map and return the status of the
     * last game state (WON if goal reached, CRASHED on wall or loop limit, RUNNING if incomplete).
     */
    public static GameStatus run(Level level) {
        if (level == null || level.getMap() == null) {
            return GameStatus.CRASHED;
        }
        if (level.getSolution() == null) {
            return GameStatus.RUNNING; // no program: never wins
        }
        if (ENFORCE_CONSTRAINTS && violatesLevelConstraints(level, level.getSolution())) {
            return GameStatus.CRASHED;
        }
        return SimulationEngine.simulate(level).getFinalStatus();
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
        if (level == null || level.getMap() == null || level.getSolution() == null) {
            return 0;
        }
        if (ENFORCE_CONSTRAINTS && violatesLevelConstraints(level, level.getSolution())) {
            return 100000;
        }
        return SimulationEngine.simulate(level).getSteps();
    }

    /**
     * Executes the level's solution and returns the executed step count if the goal is reached;
     * otherwise returns {@code penalty}.
     */
    public static int stepsToGoalOrPenalty(Level level, int penalty) {
        if (level == null || level.getMap() == null || level.getSolution() == null) {
            return penalty;
        }
        if (ENFORCE_CONSTRAINTS && violatesLevelConstraints(level, level.getSolution())) {
            return penalty;
        }
        SimulationEngine.SimulationResult res = SimulationEngine.simulate(level);
        return res.isWon() ? res.getSteps() : penalty;
    }

    /**
     * Executes the level's solution and returns the minimum BFS distance-to-goal encountered along
     * Pegman's path.
     */
    public static int distanceToGoalOrPenalty(Level level) {
        return distanceToGoalOrPenalty(level, 100000);
    }

    /**
     * Same as {@link #distanceToGoalOrPenalty(Level)} but with a custom penalty value.
     */
    public static int distanceToGoalOrPenalty(Level level, int penalty) {
        if (level == null || level.getMap() == null || level.getSolution() == null) {
            return penalty;
        }
        if (ENFORCE_CONSTRAINTS && violatesLevelConstraints(level, level.getSolution())) {
            return penalty;
        }

        GridMap map = level.getMap();
        final CellType winCellType = determineWinCellType(level);
        Map<Cell, Integer> distanceField = computeDistanceField(map, winCellType);
        if (distanceField.isEmpty()) {
            return penalty;
        }

        SimulationEngine.SimulationResult res = SimulationEngine.simulate(level);
        int min = Integer.MAX_VALUE;
        for (GameState s : res.getTrace().getStates()) {
            if (s != null && s.getPosition() != null) {
                Integer d = distanceField.get(s.getPosition());
                if (d != null) {
                    if (d < min) {
                        min = d;
                        if (min == 0) {
                            break;
                        }
                    }
                }
            }
        }
        return (min == Integer.MAX_VALUE) ? penalty : min;
    }

    public static Map<Cell, Integer> computeDistanceField(GridMap map, CellType goalType) {
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
        return SimulationEngine.determineWinCellType(level);
    }

    public static Direction determineStartOrientation(Level level, Cell start) {
        return SimulationEngine.determineStartOrientation(level, start);
    }
}
