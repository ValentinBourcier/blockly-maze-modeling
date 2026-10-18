package blocky_game;

import javafx.application.Platform;
import javafx.scene.web.WebView;
import blocky.Level;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deep module that coordinates MoMoT search synthesis runs and UI solution
 * visualization for the desktop BlockyUI WebView.
 */
public class MomotUiCoordinator {

    private final GameEngine engine;
    private final WebView webView;
    private volatile BlockyJsBridgeController bridgeController;

    private volatile String momotCurrentOutputDir;
    private volatile int momotRunLevelId = -1;
    private final AtomicLong momotRunGeneration = new AtomicLong();
    private final AtomicLong loadRequestGeneration = new AtomicLong();
    private volatile Thread momotRunThread;
    private volatile boolean pendingShowMomotPanel;

    public MomotUiCoordinator(GameEngine engine, WebView webView) {
        this.engine = engine;
        this.webView = webView;
    }

    public int getMomotRunLevelId() {
        return momotRunLevelId;
    }

    public long getLoadRequestGeneration() {
        return loadRequestGeneration.get();
    }

    public void setBridgeController(BlockyJsBridgeController bridgeController) {
        this.bridgeController = bridgeController;
    }

    public boolean isPendingShowMomotPanel() {
        return pendingShowMomotPanel;
    }

    public void setPendingShowMomotPanel(boolean pending) {
        this.pendingShowMomotPanel = pending;
    }

    public void onInjectComplete() {
        if (pendingShowMomotPanel) {
            pendingShowMomotPanel = false;
            try {
                webView.getEngine().executeScript("try { if (window.__momotShowAndRefresh) window.__momotShowAndRefresh(); } catch(e) {}");
            } catch (Exception ignored) {
            }
        }
    }

    public void onLevelChanged() {
        synchronized (this) {
            momotRunGeneration.incrementAndGet();
            momotCurrentOutputDir = null;
            momotRunLevelId = -1;
            Thread t = momotRunThread;
            momotRunThread = null;
            if (t != null && t.isAlive()) {
                MomotRunService.stopRun(t);
            }
        }
        MomotRunService.stopMomotSearch("desktop");
        try {
            Platform.runLater(() -> {
                try {
                    webView.getEngine().executeScript(
                        "try { if (window.__momotClearSolutions) window.__momotClearSolutions(); " +
                        "if (window.__momotFirstGoalReset) window.__momotFirstGoalReset(); } catch(e) {}"
                    );
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    public void showMomotPanelOnly() {
        try {
            momotCurrentOutputDir = null;
            pendingShowMomotPanel = true;
            webView.getEngine().executeScript(
                "try { " +
                "  if (window.__momotShowAndRefresh) window.__momotShowAndRefresh();" +
                "  if (window.__momotSetStatus) window.__momotSetStatus('MoMoT panel ready. Place the pegman to start search.');" +
                "} catch(e) {}"
            );
        } catch (Exception ignored) {
        }
    }

    public void setMomotAlgorithm(String algorithm) {
        String value = "MEMETIC_NSGA_II".equals(algorithm) ? "MEMETIC_NSGA_II" : "NSGA_II";
        System.out.println("[MomotUiCoordinator] setMomotAlgorithm " + value);
        System.setProperty("blocky.algorithm", value);
    }

    public void stopMomotRun() {
        System.out.println("[MomotUiCoordinator] stopMomotRun");
        synchronized (this) {
            momotRunGeneration.incrementAndGet();
            Thread t = momotRunThread;
            momotRunThread = null;
            if (t != null && t.isAlive()) {
                MomotRunService.stopRun(t);
            }
        }
        MomotRunService.stopMomotSearch("desktop");
    }

    public boolean isMomotRunning() {
        synchronized (this) {
            Thread t = momotRunThread;
            return t != null && t.isAlive();
        }
    }

    public void runMomotWithParams(int seed, int populationSize, int maxEvaluations, int nrRuns, int solutionLength) {
        System.out.println("[MomotUiCoordinator] runMomotWithParams seed=" + seed + " pop=" + populationSize
                + " eval=" + maxEvaluations + " runs=" + nrRuns + " solLen=" + solutionLength);
        Platform.runLater(() -> startMomotWithParams(seed, populationSize, maxEvaluations, nrRuns, solutionLength));
    }

    public void startMomotWithParams(int seed, int populationSize, int maxEvaluations, int nrRuns, int solutionLength) {
        final long runGen;
        final int levelId;
        synchronized (this) {
            runGen = momotRunGeneration.incrementAndGet();
            Level startLevel = engine.getCurrentLevel();
            levelId = startLevel != null ? startLevel.getId() : -1;
            momotRunLevelId = levelId;
        }
        try {
            pendingShowMomotPanel = true;
            webView.getEngine().executeScript(
                "try { " +
                "  if (window.__momotFirstGoalReset) window.__momotFirstGoalReset(); " +
                "  if (window.__momotShowAndRefresh) window.__momotShowAndRefresh();" +
                "  if (window.__momotLogClear) window.__momotLogClear();" +
                "  if (window.__momotProgressStart) window.__momotProgressStart(" + nrRuns + ", "
                        + Math.max(1, maxEvaluations / Math.max(1, populationSize)) + ");" +
                "  if (window.__momotSetStatus) window.__momotSetStatus('Running MoMoT...');" +
                "} catch(e) {}"
            );
        } catch (Exception ignored) {
        }

        System.setProperty("blocky.seed", String.valueOf(seed));
        System.setProperty("blocky.populationSize", String.valueOf(populationSize));
        System.setProperty("blocky.maxEvaluations", String.valueOf(maxEvaluations));
        System.setProperty("blocky.nrRuns", String.valueOf(nrRuns));
        System.setProperty("blocky.solutionLength", String.valueOf(solutionLength));

        Level lvl = engine.getCurrentLevel();
        int currentLevelId = lvl != null ? lvl.getId() : 1;
        String henshin = (Boolean.getBoolean("blocky.levelLimits") || Boolean.getBoolean("blocky.rules.levelSpecific"))
                ? MomotFirstGoalBenchmarkRunner.selectHenshinModuleForLevel(currentLevelId)
                : MomotFirstGoalBenchmarkRunner.withWrapMoves("statement_insertions_henshin_text_edit_anywhere.henshin");
        if (lvl != null) {
            boolean ifElse = lvl.isAllowIfElse();
            System.setProperty("blocky.allowIfElse", String.valueOf(ifElse));
        } else {
            System.setProperty("blocky.allowIfElse", "true");
        }
        System.setProperty("blocky.henshin", henshin.contains("/") ? henshin : "../blocky_model/transformations/" + henshin);

        MomotRunService.RunSpec baseSpec = MomotRunService.defaultDirectManipulationSpec();
        MomotRunService.RunSpec spec = new MomotRunService.RunSpec(
                baseSpec.inputXmi,
                baseSpec.outputBase,
                populationSize,
                maxEvaluations,
                nrRuns,
                solutionLength,
                false,
                seed,
                "desktop"
        );
        synchronized (this) {
            if (runGen != momotRunGeneration.get()) {
                return;
            }
            momotCurrentOutputDir = spec.outputBase;
        }

        final AtomicBoolean uiRefreshPending = new AtomicBoolean(false);
        final AtomicLong lastRefreshMs = new AtomicLong(0);
        final AtomicInteger currentRun = new AtomicInteger(1);
        final AtomicInteger lastNfe = new AtomicInteger(-1);
        final int evalsPerRun = Math.max(1, maxEvaluations);
        final int generationsPerRun = Math.max(1, maxEvaluations / Math.max(1, populationSize));

        Object liveSubscriber = new Object() {
            public void onProgressUpdated(int currentSeed, int totalSeeds, int nfe, Object paretoFront) {
                if (runGen != momotRunGeneration.get()) return;
                if (currentSeed > 0) {
                    int r = Math.min(nrRuns, currentSeed);
                    if (r > currentRun.get()) {
                        currentRun.set(r);
                    }
                }
                updateProgress(nfe);
            }

            public void onParetoFrontUpdated(int nfe, Object paretoFront) {
                if (runGen != momotRunGeneration.get()) return;
                updateProgress(nfe);
            }

            private void updateProgress(int nfe) {
                int n = Math.max(0, nfe);
                int prev = lastNfe.getAndSet(n);
                if (prev >= 0 && n < prev && currentRun.get() < nrRuns) {
                    currentRun.incrementAndGet();
                }
                final int run = currentRun.get();
                final int gen = Math.min(generationsPerRun, (n + Math.max(1, populationSize) - 1) / Math.max(1, populationSize));
                final double pct = Math.min(100.0, 100.0 * ((run - 1) * (double) evalsPerRun + Math.min(n, evalsPerRun))
                        / ((double) Math.max(1, nrRuns) * evalsPerRun));
                long now = System.currentTimeMillis();
                if ((now - lastRefreshMs.get() >= 250 || lastRefreshMs.get() == 0) && uiRefreshPending.compareAndSet(false, true)) {
                    lastRefreshMs.set(now);
                    Platform.runLater(() -> {
                        uiRefreshPending.set(false);
                        if (runGen != momotRunGeneration.get()) return;
                        try {
                            webView.getEngine().executeScript(
                                "try { if (window.__momotSetProgress) window.__momotSetProgress(" + run + ", " + nrRuns + ", "
                                    + gen + ", " + generationsPerRun + ", " + String.format(java.util.Locale.ROOT, "%.1f", pct) + "); "
                                    + "if (window.__momotShowAndRefresh) window.__momotShowAndRefresh(); } catch(e) {}"
                            );
                        } catch (Exception ignored) {
                        }
                    });
                }
            }
        };

        Thread started = MomotRunService.runAsync(spec, (txt) -> {
            if (txt == null || runGen != momotRunGeneration.get()) return;
            final String safe = txt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
            Platform.runLater(() -> {
                if (runGen != momotRunGeneration.get()) return;
                try {
                    webView.getEngine().executeScript("try { if (window.__momotLogAppend) window.__momotLogAppend(\"" + safe + "\\n\"); } catch(e) {}");
                } catch (Exception ignored) {
                }
            });
        }, () -> {
            if (runGen != momotRunGeneration.get()) return;
            Platform.runLater(() -> {
                if (runGen != momotRunGeneration.get()) return;
                try {
                    webView.getEngine().executeScript(
                        "try { " +
                        "  if (window.__momotSetProgress) window.__momotSetProgress(" + nrRuns + ", " + nrRuns + ", "
                            + generationsPerRun + ", " + generationsPerRun + ", 100.0);" +
                        "  if (window.__momotProgressDone) window.__momotProgressDone();" +
                        "  if (window.__momotShowAndRefresh) window.__momotShowAndRefresh();" +
                        "} catch(e) {}"
                    );
                } catch (Exception ignored3) {
                }
            });
        }, (finalOutDir) -> {
            synchronized (MomotUiCoordinator.this) {
                if (runGen != momotRunGeneration.get()) return;
                Level current = engine.getCurrentLevel();
                int currentId = current != null ? current.getId() : -1;
                if (currentId != levelId) return;
                if (finalOutDir != null && !finalOutDir.trim().isEmpty()) {
                    momotCurrentOutputDir = finalOutDir.trim();
                }
            }
        }, liveSubscriber);

        synchronized (this) {
            if (runGen == momotRunGeneration.get()) {
                momotRunThread = started;
            }
        }
    }

    public String listMomotSolutions() {
        try {
            List<MomotResultsService.SolutionEntry> sols;
            MomotResultsService.SearchMetrics metrics = null;
            String filterDir;
            synchronized (this) {
                filterDir = momotCurrentOutputDir;
            }
            if (filterDir != null && !filterDir.trim().isEmpty()) {
                File outDir = new File(filterDir.trim());
                if (outDir.exists() && outDir.isDirectory()) {
                    sols = MomotResultsService.loadFromOutputDir(outDir);
                    metrics = MomotResultsService.loadSearchMetrics(outDir);
                } else {
                    sols = Collections.emptyList();
                }
            } else {
                sols = Collections.emptyList();
            }
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"firstGoalTimeMs\":").append(metrics != null && metrics.timeToFirstGoalMs != null ? metrics.timeToFirstGoalMs : -1).append(",");
            String firstGoalFormatted = "-";
            if (metrics != null && metrics.timeToFirstGoalMs != null && metrics.timeToFirstGoalMs >= 0) {
                double sec = metrics.timeToFirstGoalMs / 1000.0;
                if (sec < 0.01 && metrics.timeToFirstGoalMs > 0) {
                    firstGoalFormatted = "0.01s";
                } else {
                    firstGoalFormatted = String.format(java.util.Locale.US, "%.2fs", sec);
                }
            }
            sb.append("\"firstGoalFormatted\":\"").append(escapeJsonString(firstGoalFormatted)).append("\",");
            sb.append("\"firstGoalGen\":").append(metrics != null && metrics.generationOfFirstGoal != null ? metrics.generationOfFirstGoal : -1).append(",");
            sb.append("\"solutions\":[");
            for (int i = 0; i < sols.size(); i++) {
                MomotResultsService.SolutionEntry e = sols.get(i);
                if (i > 0) sb.append(",");
                sb.append("{");
                sb.append("\"outputDir\":\"").append(escapeJsonString(e.outputDir)).append("\",");
                sb.append("\"modelPath\":\"").append(escapeJsonString(e.modelPath)).append("\",");
                sb.append("\"objectiveLine\":\"").append(escapeJsonString(e.objectiveLine)).append("\",");
                sb.append("\"summary\":\"").append(escapeJsonString(e.summary)).append("\",");
                sb.append("\"timeToFormMs\":").append(e.timeToFormMs != null ? e.timeToFormMs : -1).append(",");
                String formattedTime = "-";
                if (e.timeToFormMs != null && e.timeToFormMs >= 0) {
                    double sec = e.timeToFormMs / 1000.0;
                    if (sec < 0.01 && e.timeToFormMs > 0) {
                        formattedTime = "0.01s";
                    } else {
                        formattedTime = String.format(java.util.Locale.US, "%.2fs", sec);
                    }
                }
                sb.append("\"timeFormatted\":\"").append(escapeJsonString(formattedTime)).append("\",");
                sb.append("\"generationToForm\":").append(e.generationToForm != null ? e.generationToForm : -1);
                sb.append("}");
            }
            sb.append("]}");
            return sb.toString();
        } catch (Exception ex) {
            return "{\"solutions\":[]}";
        }
    }

    public void loadMomotSolution(String xmiPath) {
        if (xmiPath == null || xmiPath.trim().isEmpty()) return;
        System.out.println("[MomotUiCoordinator] loadMomotSolution path=" + xmiPath);
        final long reqId = loadRequestGeneration.incrementAndGet();
        pendingShowMomotPanel = true;
        Platform.runLater(() -> loadMomotSolutionInPlace(xmiPath.trim(), reqId));
    }

    public void loadMomotSolutionInPlace(String xmiPath) {
        loadMomotSolutionInPlace(xmiPath, loadRequestGeneration.get());
    }

    public void loadMomotSolutionInPlace(String xmiPath, long reqId) {
        if (reqId != loadRequestGeneration.get()) {
            System.out.println("[MomotUiCoordinator] Superseded load request: " + reqId);
            return;
        }
        File xmiFile = new File(xmiPath);
        if (!xmiFile.exists()) {
            try {
                String msg = escapeForJsStringLiteral("Solution model file no longer exists: " + xmiFile.getName());
                webView.getEngine().executeScript(
                    "try { if (window.__momotSetStatus) window.__momotSetStatus(\"" + msg + "\"); } catch(e) {}"
                );
            } catch (Exception ignored) {}
            return;
        }

        try { engine.armDirectManipulationComparison(); } catch (Exception ignored) {}

        try {
            engine.loadFromFile(xmiFile);
            if (momotRunLevelId >= 0 && engine.getCurrentLevel() != null) {
                engine.getCurrentLevel().setId(momotRunLevelId);
            }
        } catch (IOException e) {
            try {
                String msg = escapeForJsStringLiteral("Failed to load solution model: " + e.getMessage());
                webView.getEngine().executeScript(
                    "try { if (window.__momotSetStatus) window.__momotSetStatus(\"" + msg + "\"); } catch(e) {}"
                );
            } catch (Exception ignored) {}
            return;
        } catch (IllegalArgumentException e) {
            try {
                String msg = escapeForJsStringLiteral("Invalid solution model: " + e.getMessage());
                webView.getEngine().executeScript(
                    "try { if (window.__momotSetStatus) window.__momotSetStatus(\"" + msg + "\"); } catch(e) {}"
                );
            } catch (Exception ignored) {}
            return;
        }

        if (reqId != loadRequestGeneration.get()) {
            return;
        }

        Level level = engine.getCurrentLevel();
        if (level == null) return;

        String xml = engine.solutionToBlocklyXml(level);
        try {
            final String preview = xml == null ? "null" : (xml.length() > 400 ? xml.substring(0, 400) + "…" : xml);
            System.out.println("[MomotUiCoordinator] MoMoT inject XML preview: " + preview);
        } catch (Exception ignored) {
        }
        String escapedForJson = escapeForJsStringLiteral(xml);
        String escapedModelName = escapeForJsStringLiteral(xmiFile.getName());

        String injectPastNew = ImmediateFeedbackService.buildWindowInjectPathsScript(engine.getPastPath(), engine.getNewPath());
        String injectDm;
        if (engine.hasDirectManipulationComparison()) {
            injectDm = ""
                + "window.__injectDmEnabled = true;"
                + "window.__injectDmBaselinePath = " + ImmediateFeedbackService.toJsonArray(engine.getDmBaselinePath()) + ";"
                + "window.__injectDmSolutionPath = " + ImmediateFeedbackService.toJsonArray(engine.getDmSolutionPath()) + ";"
                + "window.__injectDmCommonLen = " + engine.getDmCommonLen() + ";";
            if (engine.isDmAlignedValid()) {
                injectDm += "window.__injectQ = " + engine.getDmAlignedX() + ";"
                          + "window.__injectS = " + engine.getDmAlignedY() + ";";
            } else {
                injectDm += "window.__injectQ = undefined; window.__injectS = undefined;";
            }
        } else {
            injectDm = ""
                + "window.__injectDmEnabled = false;"
                + "window.__injectDmBaselinePath = [];"
                + "window.__injectDmSolutionPath = [];"
                + "window.__injectDmCommonLen = 0;"
                + "window.__injectQ = undefined; window.__injectS = undefined;";
        }

        if (bridgeController != null) {
            bridgeController.setSuppressSync(true);
            bridgeController.setAwaitingInjectComplete(true);
        }

        try {
            System.out.println("[MomotUiCoordinator] loadMomotSolutionInPlace: preparing injection...");

            webView.getEngine().executeScript(
                "window.__momotXml = \"" + escapedForJson + "\"; " +
                "window.__momotLastLoadedModelName = \"" + escapedModelName + "\"; " +
                "window.__momotLoadId = " + reqId + ";"
            );
            webView.getEngine().executeScript("try { " + injectPastNew + " } catch(e) {}");
            webView.getEngine().executeScript("try { " + injectDm + " } catch(e) {}");

            System.out.println("[MomotUiCoordinator] loadMomotSolutionInPlace: sending execution script...");

            String loadScript = WebAssetLoader.loadScript("scripts/loadMomotSolution.js");
            Object result = webView.getEngine().executeScript(loadScript);
            System.out.println("[MomotUiCoordinator] loadMomotSolutionInPlace: executeScript returned: " + result);

            try {
                webView.getEngine().executeScript("(function(){ try { " + ImmediateFeedbackService.buildOverlayRenderJs() + " } catch(e) {} })();");
            } catch (Exception ignored) {}

        } catch (Exception e) {
            System.err.println("[MomotUiCoordinator] loadMomotSolutionInPlace failed: " + e.getMessage());
            e.printStackTrace();
            if (bridgeController != null) {
                bridgeController.setAwaitingInjectComplete(false);
                bridgeController.setSuppressSync(false);
            }
        }
    }

    public static String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static String escapeForJsStringLiteral(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("'", "\\'");
    }
}
