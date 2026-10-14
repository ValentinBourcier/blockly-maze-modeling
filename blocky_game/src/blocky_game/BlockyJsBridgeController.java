package blocky_game;

import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import blocky.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.common.util.URI;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deep module that coordinates two-way communication between the JavaFX application
 * (GameEngine, EMF models) and the embedded Blockly WebView via JavaFX JSBridge.
 */
public class BlockyJsBridgeController {

    private final GameEngine engine;
    private final WebView webView;
    private final BlockySnapshotService snapshotService;
    private final MomotUiCoordinator momotCoordinator;
    private final JSBridge bridge;

    /** Suppress sync from JS to Java while injecting loaded state into the WebView. */
    private volatile boolean suppressSync;
    /** True while loading/injecting a model, until JS confirms injection complete. */
    private volatile boolean awaitingInjectComplete;
    /** When true, after the next page load we apply currentLevel to the WebView. */
    private volatile boolean pendingApplyLevel;

    /** Monotonically increasing generation id for each successful page load. */
    private final AtomicInteger pageGen = new AtomicInteger(0);
    /** Current generation id for the currently loaded page. */
    private volatile int currentPageGen;

    public BlockyJsBridgeController(GameEngine engine, WebView webView,
                                    BlockySnapshotService snapshotService,
                                    MomotUiCoordinator momotCoordinator) {
        this.engine = engine;
        this.webView = webView;
        this.snapshotService = snapshotService;
        this.momotCoordinator = momotCoordinator;
        this.bridge = new JSBridge();
        if (momotCoordinator != null) {
            momotCoordinator.setBridgeController(this);
        }
    }

    public JSBridge getBridge() {
        return bridge;
    }

    public boolean isSuppressSync() {
        return suppressSync;
    }

    public void setSuppressSync(boolean suppressSync) {
        this.suppressSync = suppressSync;
    }

    public boolean isAwaitingInjectComplete() {
        return awaitingInjectComplete;
    }

    public void setAwaitingInjectComplete(boolean awaiting) {
        this.awaitingInjectComplete = awaiting;
    }

    public boolean isPendingApplyLevel() {
        return pendingApplyLevel;
    }

    public void setPendingApplyLevel(boolean pending) {
        this.pendingApplyLevel = pending;
    }

    public int getCurrentPageGen() {
        return currentPageGen;
    }

    /**
     * Attaches the JSBridge to window.javaBridge and sets up synchronization scripts.
     */
    public void setupBridge(WebEngine webEngine) {
        JSObject window = (JSObject) webEngine.executeScript("window");
        window.setMember("javaBridge", bridge);

        if (Boolean.getBoolean("blocky.levelLimits")) {
            try {
                webEngine.executeScript("window.__blockyLevelLimits = true;");
            } catch (Exception ignored) {}
        }

        try {
            currentPageGen = pageGen.incrementAndGet();
            webEngine.executeScript("window.__javaPageGen = " + currentPageGen + ";");
            injectSyncScript(webEngine);
        } catch (Exception e) {
            System.err.println("[BlockyJsBridgeController] injectSyncScript failed: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            injectDebugControls(webEngine);
        } catch (Exception e) {
            System.err.println("[BlockyJsBridgeController] injectDebugControls failed: " + e.getMessage());
            e.printStackTrace();
        }

        if (pendingApplyLevel) {
            try {
                applyLevelToWebView(engine.getCurrentLevel(), webEngine);
            } catch (Exception e) {
                System.err.println("[BlockyJsBridgeController] applyLevelToWebView failed: " + e.getMessage());
                e.printStackTrace();
            } finally {
                pendingApplyLevel = false;
            }
        }
    }

    public void injectSyncScript(WebEngine webEngine) {
        String script = WebAssetLoader.loadScript("scripts/syncScript.js");
        webEngine.executeScript(script);
    }

    public void injectDebugControls(WebEngine webEngine) {
        try {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("DEBUG_OVERLAY_SNIPPET", "");
            replacements.put("OVERLAY_RENDER_SNIPPET", ImmediateFeedbackService.buildOverlayRenderJs());
            replacements.put("DEBUG_TICK_MS", String.valueOf(DebuggingService.DEBUG_TICK_MS));

            String script = WebAssetLoader.loadTemplate("scripts/debugControls.js", replacements);
            webEngine.executeScript(script);
        } catch (Exception e) {
            System.err.println("[BlockyJsBridgeController] injectDebugControls executeScript failed: " + e.getMessage());
        }
    }

    /**
     * Injects the loaded level state into the WebView: map grid, nd/od, metadata,
     * Blockly workspace XML, and resets pegman.
     */
    public void applyLevelToWebView(Level level, WebEngine webEngine) {
        if (level == null || level.getMap() == null || webEngine == null) return;
        System.out.println("[BlockyJsBridgeController] applyLevelToWebView: Level ID=" + level.getId());

        suppressSync = true;
        awaitingInjectComplete = true;

        try {
            int[][] grid = engine.buildGridForWebView(level.getMap());
            Cell startCell = engine.getStartCell(level.getMap());
            Cell dmgCell = engine.getDmgCell(level.getMap());
            Cell goalCell = engine.getGoalCell(level.getMap());
            int levelId = Math.max(1, Math.min(10, level.getId()));
            int maxBlocks = level.getMaxBlocks() <= 0 ? -1 : level.getMaxBlocks();

            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int row = 0; row < grid.length; row++) {
                sb.append("[");
                for (int col = 0; col < grid[row].length; col++) {
                    if (col > 0) sb.append(",");
                    sb.append(grid[row][col]);
                }
                sb.append("]");
                if (row < grid.length - 1) sb.append(",");
            }
            sb.append("]");
            String gridJson = sb.toString();

            webEngine.executeScript("window.__injectGridJson = " + gridJson + ";");
            if (grid.length > 0 && grid[0].length > 0) {
                int rd = grid[0].length;
                int qd = grid.length;
                webEngine.executeScript("window.__injectQd = " + qd + "; window.__injectRd = " + rd + ";");
                webEngine.executeScript("window.__injectSd = " + (50 * rd) + "; window.__injectTd = " + (50 * qd) + ";");
            }
            if (startCell != null) {
                webEngine.executeScript("window.__injectNd = {x: " + startCell.getX() + ", y: " + startCell.getY() + "};");
                webEngine.executeScript("window.__injectQ = " + startCell.getX() + "; window.__injectS = " + startCell.getY() + ";");
            }
            if (goalCell != null) {
                webEngine.executeScript("window.__injectOd = {x: " + goalCell.getX() + ", y: " + goalCell.getY() + "};");
            }
            if (dmgCell != null) {
                webEngine.executeScript("window.__injectDmg = {x: " + dmgCell.getX() + ", y: " + dmgCell.getY() + "};");
            } else {
                webEngine.executeScript("window.__injectDmg = null;");
            }
            webEngine.executeScript("window.__injectK = " + levelId + ";");
            webEngine.executeScript("window.__injectOdVal = " + (maxBlocks < 0 ? "Infinity" : String.valueOf(maxBlocks)) + ";");
            webEngine.executeScript("window.__modelStartT = " + engine.directionToT(level.getStartOrientation()) + ";");

            webEngine.executeScript(ImmediateFeedbackService.buildWindowInjectPathsScript(engine.getPastPath(), engine.getNewPath()));

            if (engine.hasDirectManipulationComparison()) {
                webEngine.executeScript("window.__injectDmEnabled = true;");
                webEngine.executeScript("window.__injectDmBaselinePath = " + ImmediateFeedbackService.toJsonArray(engine.getDmBaselinePath()) + ";");
                webEngine.executeScript("window.__injectDmSolutionPath = " + ImmediateFeedbackService.toJsonArray(engine.getDmSolutionPath()) + ";");
                webEngine.executeScript("window.__injectDmCommonLen = " + engine.getDmCommonLen() + ";");
            } else {
                webEngine.executeScript("window.__injectDmEnabled = false;");
                webEngine.executeScript("window.__injectDmBaselinePath = [];");
                webEngine.executeScript("window.__injectDmSolutionPath = [];");
                webEngine.executeScript("window.__injectDmCommonLen = 0;");
            }

            String xml = engine.solutionToBlocklyXml(level);
            String escapedForJson = MomotUiCoordinator.escapeForJsStringLiteral(xml);
            webEngine.executeScript("window.__loadXml = \"" + escapedForJson + "\";");

            String applyScript = WebAssetLoader.loadScript("scripts/applyLevel.js");
            webEngine.executeScript(applyScript);

            try {
                webEngine.executeScript("(function(){ try { " + ImmediateFeedbackService.buildOverlayRenderJs() + " } catch(e) {} })();");
            } catch (Exception ignored) {}

        } catch (Exception e) {
            System.err.println("[BlockyJsBridgeController] applyLevelToWebView failed: " + e.getMessage());
            e.printStackTrace();
            awaitingInjectComplete = false;
            suppressSync = false;
        }
    }

    public static String toJsonStringArrayLiteral(List<String> lines) {
        if (lines == null || lines.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(MomotUiCoordinator.escapeJsonString(lines.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Inner class exposed as window.javaBridge.
     */
    public class JSBridge {

        public void logJS(String msg) {
            System.out.println("[WebView JS] " + msg);
        }

        public void receivePngDataUrl(String dataUrl) {
            if (snapshotService != null) {
                snapshotService.receivePngDataUrl(dataUrl);
            }
        }

        public String getSolutionPath(String xmiPath) {
            if (xmiPath == null || xmiPath.isEmpty()) return "[]";
            try {
                File file = new File(xmiPath.trim());
                if (!file.exists()) return "[]";

                ResourceSet resSet = new ResourceSetImpl();
                URI uri = URI.createFileURI(file.getAbsolutePath());
                Resource res = resSet.createResource(uri);
                res.load(null);

                if (res.getContents().isEmpty()) return "[]";
                Object root = res.getContents().get(0);
                Level level = null;
                if (root instanceof Game) {
                    Game game = (Game) root;
                    if (!game.getLevels().isEmpty()) level = game.getLevels().get(0);
                } else if (root instanceof Level) {
                    level = (Level) root;
                }

                if (level == null || level.getMap() == null) return "[]";

                Cell startCell = engine.getStartCell(level.getMap());
                Direction startDir = SimUtils.determineStartOrientation(level, startCell);

                DebuggingService.DebugTraceResult result = DebuggingService.computeTraceFromState(level,
                    startCell != null ? startCell.getX() : 0,
                    startCell != null ? startCell.getY() : 0,
                    startDir);

                int[][] pts = result.statePositions;
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < pts.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append("[").append(pts[i][0]).append(",").append(pts[i][1]).append("]");
                }
                sb.append("]");
                return sb.toString();
            } catch (Exception e) {
                System.err.println("[JSBridge] getSolutionPath failed: " + e.getMessage());
                return "[]";
            }
        }

        public void injectComplete() {
            awaitingInjectComplete = false;
            suppressSync = false;
            System.out.println("[JSBridge] Injection complete; sync re-enabled.");
            try {
                Platform.runLater(() -> {
                    try {
                        webView.getEngine().executeScript("try { if (window.__lvlLoadingHide) window.__lvlLoadingHide(); } catch(e) {}");
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
            if (momotCoordinator != null) {
                momotCoordinator.onInjectComplete();
            }
        }

        public void runSimulation() {
            System.out.println("[JSBridge] 'Run' detected. Starting Java simulation...");
            try {
                webView.getEngine().executeScript("try { if (window.__execLogClear) window.__execLogClear(); } catch(e) {}");
            } catch (Exception e) {
                System.err.println("[JSBridge] Failed to clear exec log: " + e.getMessage());
            }
            List<String> logs = engine.simulateUserProgramWithLogs();
            try {
                webView.getEngine().executeScript("try { if (window.__execLogAppend) window.__execLogAppend(" + toJsonStringArrayLiteral(logs) + "); } catch(e) {}");
            } catch (Exception e) {
                System.err.println("[JSBridge] Failed to append exec log: " + e.getMessage());
            }
        }

        public void runSimulationWithGen(int gen) {
            if (gen != currentPageGen || awaitingInjectComplete) {
                System.out.println("[JSBridge] runSimulationWithGen ignored. gen=" + gen + " current=" + currentPageGen + " injecting=" + awaitingInjectComplete);
                return;
            }
            runSimulation();
        }

        public void teleportPegman(int q, int s, int t) {
            try {
                System.out.println("[JSBridge] teleportPegman q=" + q + " s=" + s + " t=" + t);
                engine.teleportPegman(q, s, t);
                if (momotCoordinator != null) {
                    try {
                        momotCoordinator.showMomotPanelOnly();
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception e) {
                System.err.println("[JSBridge] teleportPegman failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        public void clearDirectManipulation() {
            try {
                engine.clearDirectManipulationGoal();
            } catch (Exception e) {
                System.err.println("[JSBridge] clearDirectManipulation failed: " + e.getMessage());
            }
        }

        public String listMomotSolutions() {
            if (momotCoordinator != null) {
                return momotCoordinator.listMomotSolutions();
            }
            return "{\"solutions\":[]}";
        }

        public void loadMomotSolution(String xmiPath) {
            if (momotCoordinator != null) {
                momotCoordinator.loadMomotSolution(xmiPath);
            }
        }

        public void setMomotAlgorithm(String algorithm) {
            if (momotCoordinator != null) {
                momotCoordinator.setMomotAlgorithm(algorithm);
            }
        }

        public void runMomotWithParams(int seed, int populationSize, int maxEvaluations, int nrRuns, int solutionLength) {
            if (momotCoordinator != null) {
                momotCoordinator.runMomotWithParams(seed, populationSize, maxEvaluations, nrRuns, solutionLength);
            }
        }

        public void stopMomotRun() {
            if (momotCoordinator != null) {
                momotCoordinator.stopMomotRun();
            }
        }

        public boolean isMomotRunning() {
            return momotCoordinator != null && momotCoordinator.isMomotRunning();
        }

        public boolean isLevelLimits() {
            return Boolean.getBoolean("blocky.levelLimits");
        }

        public String debugStart(int q, int s, int t) {
            System.out.println("[JSBridge] debugStart q=" + q + " s=" + s + " t=" + t);
            return engine.debugStart(q, s, t);
        }

        public String debugStartWithGen(int q, int s, int t, int gen) {
            if (gen != currentPageGen || awaitingInjectComplete) {
                System.out.println("[JSBridge] debugStartWithGen ignored. gen=" + gen + " current=" + currentPageGen + " injecting=" + awaitingInjectComplete);
                return engine.debugStop();
            }
            return debugStart(q, s, t);
        }

        public String debugTogglePause() {
            return engine.debugTogglePause();
        }

        public String debugTogglePauseWithGen(int gen) {
            if (gen != currentPageGen || awaitingInjectComplete) return engine.debugStop();
            return debugTogglePause();
        }

        public String debugStop() {
            return engine.debugStop();
        }

        public String debugStopWithGen(int gen) {
            if (gen != currentPageGen) return engine.debugStop();
            return debugStop();
        }

        public String debugStep() {
            return engine.debugStepOnce();
        }

        public String debugStepWithGen(int gen) {
            if (gen != currentPageGen || awaitingInjectComplete) return engine.debugStop();
            return debugStep();
        }

        public String debugSkipToEnd() {
            return engine.debugSkipToEnd();
        }

        public String debugSkipToEndWithGen(int gen) {
            if (gen != currentPageGen || awaitingInjectComplete) return engine.debugStop();
            return debugSkipToEnd();
        }

        public String debugTick() {
            return engine.debugTick();
        }

        public String debugTickWithGen(int gen) {
            if (gen != currentPageGen || awaitingInjectComplete) return engine.debugStop();
            return debugTick();
        }

        public void syncMap(String mapJson) {
            if (suppressSync) return;
            System.out.println("[JSBridge] Received map JSON from JS: " + mapJson);
            engine.setMapFromJson(mapJson);
        }

        public void syncLevelMeta(String metaJson) {
            if (suppressSync) return;
            System.out.println("[JSBridge] Received level metadata: " + metaJson);

            int oldLevelId = -1;
            try {
                if (engine.getCurrentLevel() != null) {
                    oldLevelId = engine.getCurrentLevel().getId();
                }
            } catch (Exception ignored) {
            }

            engine.syncLevelMeta(metaJson);

            int newLevelId = -1;
            try {
                if (engine.getCurrentLevel() != null) {
                    newLevelId = engine.getCurrentLevel().getId();
                }
            } catch (Exception ignored) {
            }

            if (oldLevelId != newLevelId && momotCoordinator != null) {
                momotCoordinator.onLevelChanged();
            }
        }

        public void syncModel(String xml) {
            if (suppressSync) {
                System.out.println("[JSBridge] syncModel suppressed (loading/injecting in progress).");
                return;
            }
            if (xml == null) return;
            if (xml.indexOf("<block") < 0) {
                System.out.println("[JSBridge] Ignoring empty workspace XML snapshot.");
                return;
            }
            try {
                System.out.println("[JSBridge] Syncing workspace XML (len=" + xml.length() + ")");
                List<Map<String, Object>> data = BlockyAstMapper.parseBlocklyXml(xml);
                engine.rebuildProgram(data);
                System.out.println("[JSBridge] Sync complete. Top-level blocks: " + (data != null ? data.size() : 0));

                try {
                    webView.getEngine().executeScript("if (typeof window.__dbgSyncRefresh === 'function') window.__dbgSyncRefresh();");
                } catch (Exception ignored) {}
            } catch (Exception e) {
                System.err.println("[JSBridge] Sync Error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        public void syncModelForce(String xml) {
            try {
                if (xml == null || xml.indexOf("<block") < 0) {
                    System.out.println("[JSBridge] Force-sync ignored empty workspace XML snapshot.");
                    return;
                }
                System.out.println("[JSBridge] Force-syncing workspace XML:\n" + xml);
                List<Map<String, Object>> data = BlockyAstMapper.parseBlocklyXml(xml);
                engine.rebuildProgram(data);
                System.out.println("[JSBridge] Force-sync complete. Top-level blocks: " + (data != null ? data.size() : 0));
            } catch (Exception e) {
                System.err.println("[JSBridge] Force-sync Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
