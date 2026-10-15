package blocky_game;

import blocky.Cell;
import blocky.Direction;
import blocky.Game;
import blocky.Level;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * HTTP handler module for MoMoT search, execution, and solution loading routes (/api/momot/*).
 */
public class MomotSearchHttpHandler {

    private final TelemetryService telemetry;

    public MomotSearchHttpHandler(TelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    public void register(HttpServer server) {
        server.createContext("/api/momot/run", new MomotRunHandler(telemetry));
        server.createContext("/api/momot/stop", new MomotStopHandler());
        server.createContext("/api/momot/status", new MomotStatusHandler());
        server.createContext("/api/momot/solutions", new MomotSolutionsHandler());
        server.createContext("/api/momot/solutionPath", new MomotSolutionPathHandler());
        server.createContext("/api/momot/load", new MomotLoadHandler(telemetry));
    }

    private static class MomotRunHandler implements HttpHandler {
        private final TelemetryService telemetry;
        MomotRunHandler(TelemetryService telemetry) { this.telemetry = telemetry; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            int seed = SearchHttpHandler.parseJsonIntField(body, "seed", 0);
            int pop = SearchHttpHandler.parseJsonIntField(body, "populationSize", 20);
            int eval = SearchHttpHandler.parseJsonIntField(body, "maxEvaluations", 1000);
            int runs = SearchHttpHandler.parseJsonIntField(body, "nrRuns", 1);
            int solLen = SearchHttpHandler.parseJsonIntField(body, "solutionLength", 10);

            long epoch = SearchHttpHandler.parseJsonLongField(body, "epoch", 0);
            String mapJson = SearchHttpHandler.parseJsonObjectOrString(body, "map");
            String metaJson = SearchHttpHandler.parseJsonObjectOrString(body, "meta");
            String xml = SearchHttpHandler.parseJsonField(body, "xml");

            boolean wasRunning = session.isMomotRunning();
            session.runMomotWithParams(epoch, mapJson, metaJson, xml, seed, pop, eval, runs, solLen);
            if (!wasRunning && session.isMomotRunning() && telemetry != null) {
                String timerSid = SearchHttpHandler.extractTimerSessionId(exchange, body);
                int levelId = SearchHttpHandler.extractLevelId(exchange, body, session);
                telemetry.recordActivity(timerSid, levelId, TelemetryService.ActivityType.MOMOT_SEARCH);
            }

            String json = "{\"status\":\"ok\",\"running\":" + session.isMomotRunning() + ",\"outputDir\":\"" + SearchHttpHandler.escapeJson(session.getMomotCurrentOutputDir()) + "\"}";
            SearchHttpHandler.sendJson(exchange, 200, json);
        }
    }

    private static class MomotStopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            session.stopMomotRun();
            SearchHttpHandler.sendJson(exchange, 200, "{\"status\":\"ok\",\"running\":false}");
        }
    }

    private static class MomotStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"running\":").append(session.isMomotRunning()).append(",");
            sb.append("\"status\":\"").append(SearchHttpHandler.escapeJson(session.getMomotStatus())).append("\",");
            sb.append("\"outputDir\":\"").append(SearchHttpHandler.escapeJson(session.getMomotCurrentOutputDir())).append("\",");
            sb.append("\"progress\":{");
            sb.append("\"run\":").append(session.getProgressRun()).append(",");
            sb.append("\"totalRuns\":").append(session.getProgressTotalRuns()).append(",");
            sb.append("\"gen\":").append(session.getProgressGen()).append(",");
            sb.append("\"totalGens\":").append(session.getProgressTotalGens()).append(",");
            sb.append("\"pct\":").append(String.format(java.util.Locale.US, "%.1f", session.getProgressPct()));
            sb.append("},");
            sb.append("\"logs\":[");
            List<String> logs = session.getMomotLogs();
            for (int i = 0; i < logs.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(SearchHttpHandler.escapeJson(logs.get(i))).append("\"");
            }
            sb.append("]}");

            SearchHttpHandler.sendJson(exchange, 200, sb.toString());
        }
    }

    private static class MomotSolutionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);
            List<MomotResultsService.SolutionEntry> sols = session.listMomotSolutions();

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < sols.size(); i++) {
                MomotResultsService.SolutionEntry e = sols.get(i);
                if (i > 0) sb.append(",");
                sb.append("{");
                sb.append("\"outputDir\":\"").append(SearchHttpHandler.escapeJson(e.outputDir)).append("\",");
                sb.append("\"modelPath\":\"").append(SearchHttpHandler.escapeJson(e.modelPath)).append("\",");
                sb.append("\"objectiveLine\":\"").append(SearchHttpHandler.escapeJson(e.objectiveLine)).append("\",");
                sb.append("\"summary\":\"").append(SearchHttpHandler.escapeJson(e.summary)).append("\"");
                sb.append("}");
            }
            sb.append("]");

            SearchHttpHandler.sendJson(exchange, 200, sb.toString());
        }
    }

    private static class MomotSolutionPathHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            String modelPath = SearchHttpHandler.parseJsonField(body, "modelPath");
            if (modelPath == null || modelPath.trim().isEmpty()) {
                SearchHttpHandler.sendJson(exchange, 200, "{\"status\":\"ok\",\"path\":[]}");
                return;
            }

            try {
                File file = new File(modelPath.trim());
                if (!file.exists()) {
                    SearchHttpHandler.sendJson(exchange, 200, "{\"status\":\"ok\",\"path\":[]}");
                    return;
                }

                ResourceSet resSet = new ResourceSetImpl();
                URI uri = URI.createFileURI(file.getAbsolutePath());
                Resource res = resSet.createResource(uri);
                res.load(null);

                if (res.getContents().isEmpty()) {
                    SearchHttpHandler.sendJson(exchange, 200, "{\"status\":\"ok\",\"path\":[]}");
                    return;
                }

                Object root = res.getContents().get(0);
                Level level = null;
                if (root instanceof Game) {
                    Game game = (Game) root;
                    if (!game.getLevels().isEmpty()) level = game.getLevels().get(0);
                } else if (root instanceof Level) {
                    level = (Level) root;
                }

                if (level == null || level.getMap() == null) {
                    SearchHttpHandler.sendJson(exchange, 200, "{\"status\":\"ok\",\"path\":[]}");
                    return;
                }

                Cell startCell = session.getEngine().getStartCell(level.getMap());
                Direction startDir = SimUtils.determineStartOrientation(level, startCell);

                DebuggingService.DebugTraceResult result = DebuggingService.computeTraceFromState(
                    level,
                    startCell != null ? startCell.getX() : 0,
                    startCell != null ? startCell.getY() : 0,
                    startDir
                );

                int[][] pts = result.statePositions;
                StringBuilder sb = new StringBuilder("{\"status\":\"ok\",\"path\":[");
                for (int i = 0; i < pts.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append("[").append(pts[i][0]).append(",").append(pts[i][1]).append("]");
                }
                sb.append("]}");

                SearchHttpHandler.sendJson(exchange, 200, sb.toString());
            } catch (Exception e) {
                System.err.println("[MomotSolutionPathHandler] Error: " + e.getMessage());
                SearchHttpHandler.sendJson(exchange, 200, "{\"status\":\"ok\",\"path\":[]}");
            }
        }
    }

    private static class MomotLoadHandler implements HttpHandler {
        private final TelemetryService telemetry;
        MomotLoadHandler(TelemetryService telemetry) { this.telemetry = telemetry; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            String modelPath = SearchHttpHandler.parseJsonField(body, "modelPath");
            boolean ok = session.loadMomotSolution(modelPath);

            GameEngine engine = session.getEngine();
            Level level = engine.getCurrentLevel();

            int levelId = (level != null) ? level.getId() : 1;
            String xml = (ok && level != null) ? engine.solutionToBlocklyXml(level) : "";

            if (ok && telemetry != null) {
                String timerSid = SearchHttpHandler.extractTimerSessionId(exchange, body);
                if (timerSid == null || timerSid.trim().isEmpty()) {
                    timerSid = SearchHttpHandler.extractSessionId(exchange, body);
                }
                int timerLevelId = SearchHttpHandler.extractLevelId(exchange, body, session);
                telemetry.recordActivity(timerSid, timerLevelId, TelemetryService.ActivityType.SOLUTION_LOAD);

                if (timerSid != null) {
                    String modelName = modelPath != null ? new File(modelPath).getName() : "solution.xmi";
                    String objectiveLine = "";
                    Integer generation = null;
                    String searchId = null;
                    for (MomotResultsService.SolutionEntry e : session.listMomotSolutions()) {
                        if (modelPath != null && (modelPath.equals(e.modelPath) || (e.modelPath != null && modelName.equals(new File(e.modelPath).getName())))) {
                            if (e.objectiveLine != null) {
                                objectiveLine = e.objectiveLine;
                            }
                            generation = e.generationToForm;
                            if (e.outputDir != null) {
                                File outDir = new File(e.outputDir);
                                searchId = "output".equals(outDir.getName()) && outDir.getParentFile() != null
                                        ? outDir.getParentFile().getName() : outDir.getName();
                            }
                            break;
                        }
                    }
                    if (objectiveLine.isEmpty()) {
                        String fromBody = SearchHttpHandler.parseJsonField(body, "objectiveLine");
                        if (fromBody != null) objectiveLine = fromBody;
                    }
                    String timestamp = SearchHttpHandler.parseJsonField(body, "timestamp");
                    if (timestamp == null || timestamp.trim().isEmpty()) {
                        timestamp = java.time.Instant.now().toString();
                    }
                    String variant = SearchHttpHandler.parseJsonField(body, "variant");
                    if (variant == null || variant.trim().isEmpty()) {
                        variant = "momot";
                    }
                    telemetry.appendSessionEvent(timerSid, "candidate_exploration", variant, timerLevelId, modelName, xml, modelPath, objectiveLine, timestamp,
                            -1L, null, generation, searchId);
                }
            }
            int[][] grid = (level != null && level.getMap() != null) ? engine.buildGridForWebView(level.getMap()) : new int[0][0];

            Cell startCell = (level != null && level.getMap() != null) ? engine.getStartCell(level.getMap()) : null;
            Cell goalCell = (level != null && level.getMap() != null) ? engine.getGoalCell(level.getMap()) : null;
            Cell dmgCell = (level != null && level.getMap() != null) ? engine.getDmgCell(level.getMap()) : null;

            int startX = startCell != null ? startCell.getX() : 0;
            int startY = startCell != null ? startCell.getY() : 0;
            int goalX = goalCell != null ? goalCell.getX() : 0;
            int goalY = goalCell != null ? goalCell.getY() : 0;

            StringBuilder gridSb = new StringBuilder("[");
            for (int r = 0; r < grid.length; r++) {
                if (r > 0) gridSb.append(",");
                gridSb.append("[");
                for (int c = 0; c < grid[r].length; c++) {
                    if (c > 0) gridSb.append(",");
                    gridSb.append(grid[r][c]);
                }
                gridSb.append("]");
            }
            gridSb.append("]");

            int startT = 0;
            if (level != null && level.getMap() != null) {
                Direction startDir = SimUtils.determineStartOrientation(level, startCell);
                startT = engine.directionToT(startDir);
            }

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"status\":\"").append(ok ? "ok" : "error").append("\",");
            sb.append("\"levelId\":").append(levelId).append(",");
            sb.append("\"xml\":\"").append(SearchHttpHandler.escapeJson(xml)).append("\",");
            sb.append("\"grid\":").append(gridSb.toString()).append(",");
            sb.append("\"startPos\":{\"x\":").append(startX).append(",\"y\":").append(startY).append("},");
            sb.append("\"goalPos\":{\"x\":").append(goalX).append(",\"y\":").append(goalY).append("},");
            if (dmgCell != null) {
                sb.append("\"dmgPos\":{\"x\":").append(dmgCell.getX()).append(",\"y\":").append(dmgCell.getY()).append("},");
            }
            sb.append("\"startDirection\":").append(startT);
            sb.append("}");

            SearchHttpHandler.sendJson(exchange, ok ? 200 : 400, sb.toString());
        }
    }
}
