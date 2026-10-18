package blocky_game;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP handler module for tracking and persisting user level timing metrics (/api/level-time).
 */
public class LevelTimeHttpHandler implements HttpHandler {

    private final TelemetryService telemetry;

    public LevelTimeHttpHandler(TelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    public void register(HttpServer server) {
        server.createContext("/api/level-time", this);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            SearchHttpHandler.sendCorsAndNoContent(exchange);
            return;
        }

        try {
            String body = SearchHttpHandler.readRequestBody(exchange);
            String sessionId = SearchHttpHandler.parseJsonField(body, "sessionId");
            if (sessionId == null || sessionId.trim().isEmpty()) {
                if (exchange.getRequestHeaders() != null) {
                    List<String> hdr = exchange.getRequestHeaders().get("X-Timer-Session-ID");
                    if (hdr != null && !hdr.isEmpty()) sessionId = hdr.get(0);
                    if (sessionId == null || sessionId.trim().isEmpty()) {
                        List<String> hdr2 = exchange.getRequestHeaders().get("X-Session-ID");
                        if (hdr2 != null && !hdr2.isEmpty()) sessionId = hdr2.get(0);
                    }
                }
                if (sessionId == null || sessionId.trim().isEmpty()) {
                    String query = exchange.getRequestURI().getQuery();
                    if (query != null && query.contains("sessionId=")) {
                        for (String param : query.split("&")) {
                            if (param.startsWith("sessionId=")) {
                                sessionId = param.substring("sessionId=".length());
                                break;
                            }
                        }
                    }
                }
            }

            if (sessionId == null || !sessionId.matches("^[a-zA-Z0-9_-]{1,128}$")) {
                SearchHttpHandler.sendJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Invalid or missing sessionId\"}");
                return;
            }

            String userId = SearchHttpHandler.parseJsonField(body, "userId");
            String variant = SearchHttpHandler.parseJsonField(body, "variant");
            int level = SearchHttpHandler.parseJsonIntField(body, "level", 0);
            long elapsedMs = SearchHttpHandler.parseJsonLongField(body, "elapsedMs", -1L);
            String timestamp = SearchHttpHandler.parseJsonField(body, "timestamp");
            if (timestamp == null || timestamp.trim().isEmpty()) {
                timestamp = java.time.Instant.now().toString();
            }

            File dir = TelemetryService.resolveLevelSessionsDir();
            File sessionFile = new File(dir, sessionId + ".json");

            Map<Integer, TelemetryService.LevelRecord> levels = new TreeMap<>();
            String startedAt = timestamp;
            String storedUserId = userId != null && !userId.trim().isEmpty() ? userId : "anonymous";

            synchronized (SearchHttpHandler.SESSION_FILE_LOCK) {
                if (sessionFile.exists()) {
                    String existingContent = new String(Files.readAllBytes(sessionFile.toPath()), StandardCharsets.UTF_8);
                    String sUid = SearchHttpHandler.parseJsonField(existingContent, "userId");
                    if (sUid != null && !sUid.trim().isEmpty()) {
                        storedUserId = sUid;
                    }
                    String sStart = SearchHttpHandler.parseJsonField(existingContent, "startedAt");
                    if (sStart != null && !sStart.trim().isEmpty()) {
                        startedAt = sStart;
                    }

                    Pattern pLvl = Pattern.compile("\"(\\d+)\"\\s*:\\s*\\{([^}]+)\\}");
                    Matcher mLvl = pLvl.matcher(existingContent);
                    while (mLvl.find()) {
                        try {
                            int l = Integer.parseInt(mLvl.group(1));
                            String inner = mLvl.group(2);
                            long eMs = SearchHttpHandler.parseJsonLongField(inner, "elapsedMs", 0L);
                            String v = SearchHttpHandler.parseJsonField(inner, "variant");
                            String u = SearchHttpHandler.parseJsonField(inner, "updatedAt");
                            int runs = SearchHttpHandler.parseJsonIntField(inner, "programRuns", 0);
                            int searches = SearchHttpHandler.parseJsonIntField(inner, "momotSearches", 0);
                            int loaded = SearchHttpHandler.parseJsonIntField(inner, "solutionsLoaded", 0);
                            int dms = SearchHttpHandler.parseJsonIntField(inner, "directManipulations", 0);
                            levels.put(l, new TelemetryService.LevelRecord(eMs, v, u, runs, searches, loaded, dms));
                        } catch (Exception ignored) {}
                    }
                }

                if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && level >= 1 && level <= 10 && elapsedMs >= 0) {
                    TelemetryService.LevelRecord existing = levels.get(level);
                    if (existing == null) {
                        levels.put(level, new TelemetryService.LevelRecord(elapsedMs, variant, timestamp, 0, 0, 0, 0));
                    } else {
                        long maxElapsed = Math.max(existing.elapsedMs, elapsedMs);
                        String useVariant = variant != null ? variant : existing.variant;
                        String useUpdatedAt = (elapsedMs >= existing.elapsedMs) ? timestamp : existing.updatedAt;
                        levels.put(level, new TelemetryService.LevelRecord(maxElapsed, useVariant, useUpdatedAt, existing.programRuns, existing.momotSearches, existing.solutionsLoaded, existing.directManipulations));
                    }
                }

                StringBuilder sb = new StringBuilder("{\n");
                sb.append("  \"sessionId\": \"").append(SearchHttpHandler.escapeJson(sessionId)).append("\",\n");
                sb.append("  \"userId\": \"").append(SearchHttpHandler.escapeJson(storedUserId)).append("\",\n");
                sb.append("  \"startedAt\": \"").append(SearchHttpHandler.escapeJson(startedAt)).append("\",\n");
                sb.append("  \"levels\": {");

                boolean first = true;
                for (Map.Entry<Integer, TelemetryService.LevelRecord> entry : levels.entrySet()) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("\n    \"").append(entry.getKey()).append("\": {\n");
                    sb.append("      \"elapsedMs\": ").append(entry.getValue().elapsedMs).append(",\n");
                    sb.append("      \"variant\": \"").append(SearchHttpHandler.escapeJson(entry.getValue().variant)).append("\",\n");
                    sb.append("      \"updatedAt\": \"").append(SearchHttpHandler.escapeJson(entry.getValue().updatedAt)).append("\",\n");
                    sb.append("      \"programRuns\": ").append(entry.getValue().programRuns).append(",\n");
                    sb.append("      \"momotSearches\": ").append(entry.getValue().momotSearches).append(",\n");
                    sb.append("      \"solutionsLoaded\": ").append(entry.getValue().solutionsLoaded).append(",\n");
                    sb.append("      \"directManipulations\": ").append(entry.getValue().directManipulations).append("\n");
                    sb.append("    }");
                }
                if (!first) sb.append("\n  ");
                sb.append("}\n}");

                Files.write(sessionFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));

                StringBuilder resp = new StringBuilder("{\"status\":\"ok\",");
                resp.append("\"sessionId\":\"").append(SearchHttpHandler.escapeJson(sessionId)).append("\",");
                resp.append("\"userId\":\"").append(SearchHttpHandler.escapeJson(storedUserId)).append("\",");
                resp.append("\"levels\":{");
                first = true;
                for (Map.Entry<Integer, TelemetryService.LevelRecord> entry : levels.entrySet()) {
                    if (!first) resp.append(",");
                    first = false;
                    resp.append("\"").append(entry.getKey()).append("\":{");
                    resp.append("\"elapsedMs\":").append(entry.getValue().elapsedMs).append(",");
                    resp.append("\"variant\":\"").append(SearchHttpHandler.escapeJson(entry.getValue().variant)).append("\",");
                    resp.append("\"updatedAt\":\"").append(SearchHttpHandler.escapeJson(entry.getValue().updatedAt)).append("\",");
                    resp.append("\"programRuns\":").append(entry.getValue().programRuns).append(",");
                    resp.append("\"momotSearches\":").append(entry.getValue().momotSearches).append(",");
                    resp.append("\"solutionsLoaded\":").append(entry.getValue().solutionsLoaded).append(",");
                    resp.append("\"directManipulations\":").append(entry.getValue().directManipulations);
                    resp.append("}");
                }
                resp.append("}}");

                SearchHttpHandler.sendJson(exchange, 200, resp.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            SearchHttpHandler.sendJson(exchange, 500, "{\"status\":\"error\",\"message\":\"" + SearchHttpHandler.escapeJson(e.getMessage()) + "\"}");
        }
    }
}
