package blocky_game;

import blocky.Cell;
import blocky.Direction;
import blocky.Level;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;

/**
 * HTTP handler module for session management and synchronization routes (/api/session/*).
 */
public class SessionHttpHandler {

    public void register(HttpServer server) {
        server.createContext("/api/session/new", new NewSessionHandler());
        server.createContext("/api/session/state", new SessionStateHandler());
        server.createContext("/api/session/sync", new SyncModelHandler());
        server.createContext("/api/session/map", new SyncMapHandler());
        server.createContext("/api/session/meta", new SyncMetaHandler());
        server.createContext("/api/session/snapshot", new SyncSnapshotHandler());
    }

    private static class NewSessionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            SessionContext session = SessionManager.getInstance().getOrCreateSession(null);
            String json = "{\"status\":\"ok\",\"sessionId\":\"" + session.getSessionId() + "\"}";
            SearchHttpHandler.sendJson(exchange, 200, json);
        }
    }

    private static class SessionStateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);
            GameEngine engine = session.getEngine();
            Level level = engine.getCurrentLevel();

            int levelId = (level != null) ? level.getId() : 1;
            String xml = (level != null) ? engine.solutionToBlocklyXml(level) : "";
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

            int[][] pathPts = new int[0][0];
            int startT = 0;
            if (level != null && level.getMap() != null) {
                Direction startDir = SimUtils.determineStartOrientation(level, startCell);
                startT = engine.directionToT(startDir);
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

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"status\":\"ok\",");
            sb.append("\"sessionId\":\"").append(session.getSessionId()).append("\",");
            sb.append("\"levelId\":").append(levelId).append(",");
            sb.append("\"xml\":\"").append(SearchHttpHandler.escapeJson(xml)).append("\",");
            sb.append("\"grid\":").append(gridSb.toString()).append(",");
            sb.append("\"startPos\":{\"x\":").append(startX).append(",\"y\":").append(startY).append("},");
            sb.append("\"startDirection\":").append(startT).append(",");
            sb.append("\"goalPos\":{\"x\":").append(goalX).append(",\"y\":").append(goalY).append("},");
            if (dmgCell != null) {
                sb.append("\"dmgPos\":{\"x\":").append(dmgCell.getX()).append(",\"y\":").append(dmgCell.getY()).append("},");
            }
            sb.append("\"maxBlocks\":").append(level != null ? level.getMaxBlocks() : -1).append(",");
            sb.append("\"momotRunning\":").append(session.isMomotRunning()).append(",");
            sb.append("\"momotStatus\":\"").append(SearchHttpHandler.escapeJson(session.getMomotStatus())).append("\",");
            sb.append("\"newPath\":").append(pathSb.toString()).append(",");
            sb.append("\"pastPath\":[]");
            sb.append("}");

            SearchHttpHandler.sendJson(exchange, 200, sb.toString());
        }
    }

    private static class SyncModelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            String xml = SearchHttpHandler.parseJsonField(body, "xml");
            if (xml == null && !body.startsWith("{")) {
                xml = body;
            }
            long epoch = SearchHttpHandler.parseJsonLongField(body, "epoch", 0);
            boolean allowEmpty = SearchHttpHandler.parseJsonBooleanField(body, "allowEmpty", false);

            boolean applied = session.syncModel(epoch, xml, allowEmpty);
            SearchHttpHandler.sendJson(exchange, 200, "{\"status\":\"ok\",\"applied\":" + applied + "}");
        }
    }

    private static class SyncMapHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            long epoch = SearchHttpHandler.parseJsonLongField(body, "epoch", 0);
            String mapJson = SearchHttpHandler.parseJsonObjectOrString(body, "map");
            if (mapJson == null && body.trim().startsWith("[")) {
                mapJson = body;
            }

            boolean applied = session.syncMap(epoch, mapJson);
            SearchHttpHandler.sendJson(exchange, 200, "{\"status\":\"ok\",\"applied\":" + applied + "}");
        }
    }

    private static class SyncMetaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            long epoch = SearchHttpHandler.parseJsonLongField(body, "epoch", 0);
            String metaJson = SearchHttpHandler.parseJsonObjectOrString(body, "meta");
            if (metaJson == null && body.trim().startsWith("{")) {
                metaJson = body;
            }

            boolean applied = session.syncLevelMeta(epoch, metaJson);
            SearchHttpHandler.sendJson(exchange, 200, "{\"status\":\"ok\",\"applied\":" + applied + "}");
        }
    }

    private static class SyncSnapshotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            long epoch = SearchHttpHandler.parseJsonLongField(body, "epoch", 0);
            String mapJson = SearchHttpHandler.parseJsonObjectOrString(body, "map");
            String metaJson = SearchHttpHandler.parseJsonObjectOrString(body, "meta");
            String xml = SearchHttpHandler.parseJsonField(body, "xml");
            boolean allowEmpty = SearchHttpHandler.parseJsonBooleanField(body, "allowEmpty", false);

            boolean applied = session.applySnapshot(epoch, mapJson, metaJson, xml, allowEmpty);
            SearchHttpHandler.sendJson(exchange, 200, "{\"status\":\"ok\",\"applied\":" + applied + "}");
        }
    }
}
