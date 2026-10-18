package blocky_game;

import blocky.Body;
import blocky.Cell;
import blocky.CellType;
import blocky.Direction;
import blocky.Level;
import blocky_momot.SimulationEngine;

/**
 * Shared simulation utilities delegating to {@link SimulationEngine} in blocky_momot
 * to ensure consistency across UI, Debugger, and Feedback services while preserving
 * backwards compatibility.
 */
public final class SimUtils {
    private SimUtils() {}

    /**
     * Identifies which cell type counts as the "winning" goal for the given level.
     * Prioritizes DMG (Direct Manipulation Goal) if it exists, otherwise GOAL.
     */
    public static CellType determineWinCellType(Level level) {
        return SimulationEngine.determineWinCellType(level);
    }

    /**
     * Infers a stable starting orientation if one is not explicitly set in the model.
     * Logic: pick the first non-wall neighbor (N, E, S, W order), fallback to NORTH.
     */
    public static Direction determineStartOrientation(Level level, Cell start) {
        return SimulationEngine.determineStartOrientation(level, start);
    }

    /**
     * Checks if a solution violates the constraints of a level (max blocks, allowed control flow).
     */
    public static boolean violatesLevelConstraints(Level level, Body solution) {
        return SimulationEngine.violatesLevelConstraints(level, solution);
    }

    public static int countStatements(Body body) {
        return SimulationEngine.countStatements(body);
    }
}
