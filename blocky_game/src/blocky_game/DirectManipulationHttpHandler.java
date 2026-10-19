package blocky_game;

import blocky.Cell;
import blocky.Direction;
import blocky.Level;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.util.List;

/**
 * HTTP handler module for direct manipulation and program simulation routes (/api/dm/*, /api/simulation/run).
 */
public class DirectManipulationHttpHandler {

    private final TelemetryService telemetry;

    public DirectManipulationHttpHandler(TelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    public void register(HttpServer server) {
        server.createContext("/api/dm/request", new DirectManipulationHandler(telemetry));
        server.createContext("/api/dm/clear", new DirectManipulationClearHandler());
        server.createContext("/api/simulation/run", new SimulationRunHandler(telemetry));
    }

    private static class DirectManipulationHandler implements HttpHandler {
        private final TelemetryService telemetry;
        DirectManipulationHandler(TelemetryService telemetry) { this.telemetry = telemetry; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            int q = SearchHttpHandler.parseJsonIntField(body, "q", 0);
            int s = SearchHttpHandler.parseJsonIntField(body, "s", 0);
            int t = SearchHttpHandler.parseJsonIntField(body, "t", 0);

            session.getEngine().teleportPegman(q, s, t);

            if (telemetry != null) {
                String timerSid = SearchHttpHandler.extractTimerSessionId(exchange, body);
                int levelId = SearchHttpHandler.extractLevelId(exchange, body, session);
                telemetry.recordActivity(timerSid, levelId, TelemetryService.ActivityType.DIRECT_MANIPULATION);
            }

            SearchHttpHandler.sendJson(exchange, 200, "{\"status\":\"ok\"}");
        }
    }

    private static class DirectManipulationClearHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);
            session.getEngine().clearDirectManipulationGoal();
            SearchHttpHandler.sendJson(exchange, 200, "{\"status\":\"ok\"}");
        }
    }

    private static class SimulationRunHandler implements HttpHandler {
        private final TelemetryService telemetry;
        SimulationRunHandler(TelemetryService telemetry) { this.telemetry = telemetry; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    SearchHttpHandler.sendCorsAndNoContent(exchange);
                    return;
                }
                String body = SearchHttpHandler.readRequestBody(exchange);
                SessionContext session = SearchHttpHandler.getSession(exchange, body);

                String timerSid = SearchHttpHandler.extractTimerSessionId(exchange, body);
                int levelId = SearchHttpHandler.extractLevelId(exchange, body, session);
                boolean recordExecution = SearchHttpHandler.parseJsonBooleanField(body, "recordExecution", false);
                long epoch = SearchHttpHandler.parseJsonLongField(body, "epoch", 0L);
                String xml = SearchHttpHandler.parseJsonField(body, "xml");
                String metaJson = SearchHttpHandler.parseJsonObjectOrString(body, "meta");

                Level currentLevel = session.getEngine().getCurrentLevel();
                if (metaJson != null && !metaJson.trim().isEmpty()) {
                    session.syncLevelMeta(epoch, metaJson);
                }

                if (xml != null) {
                    if (xml.indexOf("<block") >= 0) {
                        session.syncModel(epoch, xml, false);
                    } else if (xml.trim().isEmpty() || xml.contains("<xml")) {
                        session.syncModel(epoch, xml, true);
                    }
                }

                if (timerSid != null && recordExecution && telemetry != null) {
                    telemetry.recordActivity(timerSid, levelId, TelemetryService.ActivityType.PROGRAM_RUN);
                    String variant = SearchHttpHandler.parseJsonField(body, "variant");
                    if (variant == null || variant.trim().isEmpty()) {
                        variant = "momot";
                    }
                    String timestamp = SearchHttpHandler.parseJsonField(body, "timestamp");
                    if (timestamp == null || timestamp.trim().isEmpty()) {
                        timestamp = java.time.Instant.now().toString();
                    }
                    if (xml == null || xml.trim().isEmpty()) {
                        Level curLvl = session.getEngine().getCurrentLevel();
                        if (curLvl != null) {
                            xml = session.getEngine().solutionToBlocklyXml(curLvl);
                        }
                    }
                    long levelElapsedMs = SearchHttpHandler.parseJsonLongField(body, "levelElapsedMs", -1L);
                    String levelArrivedAt = SearchHttpHandler.parseJsonField(body, "levelArrivedAt");
                    telemetry.appendSessionEvent(timerSid, "program_run", variant, levelId, "workspace", xml, null, null, timestamp, levelElapsedMs, levelArrivedAt);
                }

                List<String> logs = session.getEngine().simulateUserProgramWithLogs();
                Level level = session.getEngine().getCurrentLevel();
                int[][] pathPts = new int[0][0];
                if (level != null && level.getMap() != null) {
                    Cell startCell = session.getEngine().getStartCell(level.getMap());
                    int startX = startCell != null ? startCell.getX() : 0;
                    int startY = startCell != null ? startCell.getY() : 0;
                    Direction startDir = SimUtils.determineStartOrientation(level, startCell);
                    DebuggingService.DebugTraceResult traceRes = DebuggingService.computeTraceFromState(level, startX, startY, startDir);
                    if (traceRes != null && traceRes.statePositions != null) {
                        pathPts = traceRes.statePositions;
                    }
                }
                StringBuilder pathSb = new StringBuilder("[");
                for (int i = 0; i < pathPts.length; i++) {
                    if (i > 0) pathSb.append(",");
                    pathSb.append("[").append(pathPts[i][0]).append(",").append(pathPts[i][1]).append("]");
                }
                pathSb.append("]");

                StringBuilder sb = new StringBuilder("{\"status\":\"ok\",");
                sb.append("\"levelId\":").append(level != null ? level.getId() : 1).append(",");
                sb.append("\"logs\":[");
                for (int i = 0; i < logs.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(SearchHttpHandler.escapeJson(logs.get(i))).append("\"");
                }
                // Live consumption readout: same functions as the MoMoT objectives Blocks (memory) and
                // Actions (power), but without the goal gate.
                int memory = -1;
                int power = -1;
                if (level != null) {
                    memory = blocky_momot.BlockyProgramMetrics.countStatements(level.getSolution());
                    power = blocky_momot.BlockySimulator.simulationSteps(level);
                }
                sb.append("],\"memory\":").append(memory).append(",\"power\":").append(power);
                boolean goalReached = logs != null && logs.contains("Result: GOAL");
                if (level == null || level.getId() != levelId) {
                    goalReached = false;
                }
                sb.append(",\"goalReached\":").append(goalReached);
                sb.append(",\"newPath\":").append(pathSb.toString()).append("}");
                SearchHttpHandler.sendJson(exchange, 200, sb.toString());
            } catch (Exception e) {
                e.printStackTrace();
                SearchHttpHandler.sendJson(exchange, 500, "{\"status\":\"error\",\"message\":\"" + SearchHttpHandler.escapeJson(e.getMessage()) + "\"}");
            }
        }
    }
}
