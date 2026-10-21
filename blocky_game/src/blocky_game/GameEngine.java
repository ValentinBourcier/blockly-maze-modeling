package blocky_game;

import blocky.*;
import blocky_momot.SimulationEngine;
import org.eclipse.emf.ecore.resource.Resource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Facade coordinating Blocky Maze game state, AST transformations,
 * grid topology, persistence, and interactive debugging.
 */
public class GameEngine {

    private Game currentGame;
    private Level currentLevel;
    private Resource resource;

    // Immediate feedback overlays (old vs new execution traces).
    private int[][] pastPath = new int[0][0];
    private int[][] newPath = new int[0][0];

    // Subsystem deep modules
    private final BlockyAstMapper astMapper = new BlockyAstMapper();
    private final MazeGridService gridService = new MazeGridService();
    private final ModelStorageService storageService = new ModelStorageService();
    private final InteractiveDebugger debugger = new InteractiveDebugger();

    public BlockyAstMapper getAstMapper() {
        return astMapper;
    }

    public MazeGridService getGridService() {
        return gridService;
    }

    public ModelStorageService getStorageService() {
        return storageService;
    }

    public InteractiveDebugger getDebugger() {
        return debugger;
    }

    public void clearDirectManipulationGoal() {
        if (currentLevel != null) {
            gridService.clearDirectManipulationGoal(currentLevel.getMap());
        }
    }

    /**
     * Teleport pegman to a specific map cell for direct manipulation.
     * Allowed only onto EMPTY, GOAL, or DMG cells.
     */
    public void teleportPegman(int x, int y, int t) {
        if (currentLevel == null || currentLevel.getMap() == null) return;

        Cell target = gridService.applyTeleportTarget(currentLevel.getMap(), x, y);
        if (target == null) return;

        Direction dir = DebuggingService.tToDirection(t);

        debugger.onPegmanTeleported(currentLevel, x, y, dir, gridService.getStartCell(currentLevel.getMap()));

        currentLevel.getTraces().clear();
        ExecutionTrace trace = BlockyFactory.eINSTANCE.createExecutionTrace();
        currentLevel.getTraces().add(trace);
        GameState initialState = BlockyFactory.eINSTANCE.createGameState();
        initialState.setStep(0);
        initialState.setPosition(target);
        initialState.setOrientation(dir);
        initialState.setStatus(GameStatus.RUNNING);
        trace.getStates().add(initialState);

        saveDirectManipulationRequestXmi();
        saveMomotInputRequestXmi();
    }

    public void saveDirectManipulationRequestXmi() {
        storageService.saveDirectManipulationRequestXmi(currentGame);
    }

    public void saveMomotInputRequestXmi() {
        storageService.saveMomotInputRequestXmi(currentGame);
    }

    public void initializeGame() {
        File xmiFile = storageService.resolveInitialModelFile();
        System.out.println("[GameEngine] Initial XMI file path: " + xmiFile.getAbsolutePath());
        if (xmiFile.exists()) {
            try {
                loadFromFile(xmiFile);
                return;
            } catch (Exception e) {
                System.err.println("[GameEngine] Could not load default initial level from " + xmiFile + ": " + e.getMessage());
            }
        }

        ModelStorageService.LoadResult def = storageService.createDefaultGame(xmiFile);
        this.resource = def.resource;
        this.currentGame = def.game;
        this.currentLevel = def.level;
    }

    public void setMapFromJson(String mapJson) {
        if (currentLevel == null) {
            currentLevel = BlockyFactory.eINSTANCE.createLevel();
            if (currentGame == null) {
                currentGame = BlockyFactory.eINSTANCE.createGame();
            }
            currentGame.getLevels().add(currentLevel);
        }

        currentLevel.getTraces().clear();
        currentLevel.eUnset(BlockyPackage.Literals.LEVEL__START_ORIENTATION);

        Cell oldDmg = gridService.getDmgCell(currentLevel.getMap());
        GridMap map = gridService.parseGridMapFromJson(mapJson, oldDmg);
        currentLevel.setMap(map);
    }

    public void cycleCellType(int x, int y) {
        if (currentLevel != null && currentLevel.getMap() != null) {
            gridService.cycleCellType(currentLevel.getMap(), x, y);
        }
    }

    public void rebuildProgram(List<Map<String, Object>> blockData) {
        System.out.println("[GameEngine] Rebuilding model solution...");
        debugger.markDirtySolution();

        if (currentLevel != null) {
            currentLevel.getTraces().clear();
            currentLevel.setSolution(null);
        }

        if (blockData == null || blockData.isEmpty()) {
            astMapper.clearMappings();
            System.out.println("[GameEngine] Program cleared.");
            return;
        }

        Body solution = astMapper.rebuildProgram(blockData);
        if (currentLevel != null) {
            currentLevel.setSolution(solution);
        }
        System.out.println("[GameEngine] Solution rebuilt. Main sequence length: " + blockData.size());
    }

    public void simulateUserProgram() {
        simulateUserProgramWithLogs();
    }

    public List<String> simulateUserProgramWithLogs() {
        System.out.println("\n[GameEngine] Starting simulation...");
        List<String> logs = new ArrayList<>();
        logs.add("Simulation start");

        if (currentLevel == null) {
            logs.add("Result: CRASH (No current level)");
            return logs;
        }

        boolean enforceConstraints = Boolean.parseBoolean(System.getProperty("blocky.sim.enforceConstraints", "false"));
        if (enforceConstraints && SimUtils.violatesLevelConstraints(currentLevel, currentLevel.getSolution())) {
            System.out.println("[GameEngine] CRASH: Level constraints violated!");
            logs.add("Result: CRASH (Constraints violated)");
            return logs;
        }

        SimulationEngine.SimulationResult simResult = SimulationEngine.simulate(currentLevel, currentLevel.getSolution());
        currentLevel.getTraces().clear();
        if (simResult.getTrace() != null) {
            currentLevel.getTraces().add(simResult.getTrace());
        }

        logs.addAll(simResult.getLogs());
        System.out.println("[GameEngine] Simulation finished.\n");

        if (simResult.getFinalStatus() == GameStatus.WON) {
            logs.add("Result: GOAL");
        } else if (simResult.getFinalStatus() == GameStatus.CRASHED) {
            logs.add("Result: CRASH");
        } else {
            logs.add("Result: " + simResult.getFinalStatus());
        }
        return logs;
    }

    public static SensorDirection conditionKindToSensor(ConditionKind ck) {
        return SimulationEngine.conditionKindToSensor(ck);
    }

    public static Cell getAdjacent(Cell c, Direction d) {
        return SimulationEngine.getAdjacent(c, d);
    }

    public static Direction getRelativeDir(Direction curr, SensorDirection sensor) {
        return SimulationEngine.getRelativeDir(curr, sensor);
    }

    public void syncLevelMeta(String metaJson) {
        if (currentLevel == null || metaJson == null) return;
        System.out.println("[GameEngine] Syncing level metadata: " + metaJson);
        gridService.applyLevelMetadata(currentLevel, metaJson, debugger.isSessionActive());
    }

    public void saveToFile(File file) {
        storageService.saveToFile(resource, currentLevel, file);
    }

    public Level getCurrentLevel() {
        return currentLevel;
    }

    public Game getCurrentGame() {
        return currentGame;
    }

    public Resource getResource() {
        return resource;
    }

    public int[][] getPastPath() {
        return pastPath != null ? pastPath : new int[0][0];
    }

    public int[][] getNewPath() {
        return newPath != null ? newPath : new int[0][0];
    }

    public void loadFromFile(File file) throws IOException {
        ModelStorageService.LoadResult res = storageService.loadFromFile(file);
        this.resource = res.resource;
        this.currentGame = res.game;
        this.currentLevel = res.level;

        if (this.currentLevel != null
                && !this.currentLevel.eIsSet(BlockyPackage.Literals.LEVEL__START_ORIENTATION)
                && this.currentLevel.getMap() != null) {
            Cell startCell = gridService.getStartCell(this.currentLevel.getMap());
            Direction derived = SimUtils.determineStartOrientation(this.currentLevel, startCell);
            this.currentLevel.setStartOrientation(derived);
        }

        try {
            ImmediateFeedbackService.Paths paths = ImmediateFeedbackService.computePaths(currentLevel);
            this.pastPath = paths.pastPath;
            this.newPath = paths.newPath;
        } catch (Exception e) {
            System.err.println("[GameEngine] ImmediateFeedback compute failed: " + e.getMessage());
            this.pastPath = new int[0][0];
            this.newPath = new int[0][0];
        }

        debugger.onModelLoaded(currentLevel);
        System.out.println("[GameEngine] Loaded level id=" + currentLevel.getId() + " from " + file.getAbsolutePath());
    }

    public void armDirectManipulationComparison() {
        debugger.armDirectManipulationComparison();
    }

    public boolean hasDirectManipulationComparison() {
        return debugger.hasDirectManipulationComparison();
    }

    public int[][] getDmBaselinePath() {
        return debugger.getDmBaselinePath();
    }

    public int[][] getDmSolutionPath() {
        return debugger.getDmSolutionPath();
    }

    public int getDmCommonLen() {
        return debugger.getDmCommonLen();
    }

    public boolean isDmAlignedValid() {
        return debugger.isDmAlignedValid();
    }

    public int getDmAlignedX() {
        return debugger.getDmAlignedX();
    }

    public int getDmAlignedY() {
        return debugger.getDmAlignedY();
    }

    public String debugStart(int startX, int startY, int startT) {
        return debugger.debugStart(currentLevel, astMapper, startX, startY, startT);
    }

    public String debugTogglePause() {
        return debugger.debugTogglePause(currentLevel, astMapper);
    }

    public String debugStepOnce() {
        return debugger.debugStepOnce(currentLevel, astMapper);
    }

    public String debugStop() {
        return debugger.debugStop(currentLevel, astMapper);
    }

    public String debugSkipToEnd() {
        return debugger.debugSkipToEnd(currentLevel, astMapper);
    }

    public String debugTick() {
        return debugger.debugTick(currentLevel, astMapper);
    }

    public String statementChainToXml(Container first) {
        return astMapper.statementChainToXml(first);
    }

    public String solutionToBlocklyXml(Level level) {
        return astMapper.solutionToBlocklyXml(level);
    }

    public int[][] buildGridForWebView(GridMap map) {
        return gridService.buildGridForWebView(map);
    }

    public Cell getStartCell(GridMap map) {
        return gridService.getStartCell(map);
    }

    public Cell getGoalCell(GridMap map) {
        return gridService.getGoalCell(map);
    }

    public Cell getDmgCell(GridMap map) {
        return gridService.getDmgCell(map);
    }

    public int directionToT(Direction d) {
        return MazeGridService.directionToT(d);
    }
}
