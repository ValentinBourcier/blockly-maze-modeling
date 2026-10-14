package blocky_game;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Deep module responsible for admin authentication, session querying,
 * HTML dashboard rendering, and export serialization.
 */
public class AdminReportService {

    private final Set<String> activeAdminTokens = Collections.synchronizedSet(new HashSet<>());

    public String getAdminPassword() {
        String prop = System.getProperty("blocky.adminPassword");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("BLOCKY_ADMIN_PASSWORD");
        return env == null ? "" : env;
    }

    public boolean adminPasswordMatches(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String expected = getAdminPassword();
        return !expected.isEmpty() && expected.equals(candidate);
    }

    public String createAdminSessionToken() {
        String token = UUID.randomUUID().toString();
        activeAdminTokens.add(token);
        return token;
    }

    public boolean isAuthorizedAdmin(HttpExchange exchange, String requestBody) {
        if (exchange != null && exchange.getRequestHeaders() != null) {
            List<String> authHeaders = exchange.getRequestHeaders().get("Authorization");
            if (authHeaders != null) {
                for (String h : authHeaders) {
                    if (h.startsWith("Bearer ")) {
                        String token = h.substring("Bearer ".length()).trim();
                        if (activeAdminTokens.contains(token) || adminPasswordMatches(token)) return true;
                    }
                }
            }
            List<String> tokenHeaders = exchange.getRequestHeaders().get("X-Admin-Token");
            if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
                String token = tokenHeaders.get(0).trim();
                if (activeAdminTokens.contains(token) || adminPasswordMatches(token)) return true;
            }
            List<String> passHeaders = exchange.getRequestHeaders().get("X-Admin-Password");
            if (passHeaders != null && !passHeaders.isEmpty()) {
                String pass = passHeaders.get(0).trim();
                if (adminPasswordMatches(pass)) return true;
            }
        }
        if (exchange != null) {
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String p : query.split("&")) {
                    if (p.startsWith("token=")) {
                        String token = p.substring("token=".length()).trim();
                        if (activeAdminTokens.contains(token) || adminPasswordMatches(token)) return true;
                    }
                    if (p.startsWith("password=")) {
                        String pass = p.substring("password=".length()).trim();
                        if (adminPasswordMatches(pass)) return true;
                    }
                }
            }
        }
        if (requestBody != null) {
            String token = TelemetryService.parseJsonField(requestBody, "token");
            if (token != null && (activeAdminTokens.contains(token) || adminPasswordMatches(token))) return true;
            String pass = TelemetryService.parseJsonField(requestBody, "password");
            if (adminPasswordMatches(pass)) return true;
        }
        return false;
    }

    public String generateAdminHtmlReport(List<TelemetryService.AdminSessionSummary> sessions) {
        long grandTotalMs = 0;
        int grandTotalRuns = 0;
        int grandTotalSearches = 0;
        int grandTotalLoaded = 0;
        int grandTotalDms = 0;
        Set<String> uniqueUsers = new HashSet<>();
        for (TelemetryService.AdminSessionSummary s : sessions) {
            grandTotalMs += s.totalElapsedMs;
            grandTotalRuns += s.totalProgramRuns;
            grandTotalSearches += s.totalMomotSearches;
            grandTotalLoaded += s.totalSolutionsLoaded;
            grandTotalDms += s.totalDirectManipulations;
            uniqueUsers.add(s.userId);
        }

        Map<String, String> replacements = new HashMap<>();
        replacements.put("totalSessions", String.valueOf(sessions.size()));
        replacements.put("uniqueUsers", String.valueOf(uniqueUsers.size()));
        replacements.put("totalPlaytime", formatTimeSecOrMs(grandTotalMs));
        replacements.put("programRuns", String.valueOf(grandTotalRuns));
        replacements.put("momotSearches", String.valueOf(grandTotalSearches));
        replacements.put("solutionsLoaded", String.valueOf(grandTotalLoaded));
        replacements.put("directManipulations", String.valueOf(grandTotalDms));
        replacements.put("generatedAt", java.time.Instant.now().toString());
        replacements.put("sessions", buildSessionsTableRows(sessions));
        replacements.put("events", buildEventsSection(sessions));
        String blockPreviewScript = "";
        try {
            blockPreviewScript = WebAssetLoader.loadScript("common/blockPreview.js");
        } catch (Exception e) {
            blockPreviewScript = "/* blockPreview.js could not be loaded: " + e.getMessage() + " */";
        }
        replacements.put("blockPreviewScript", blockPreviewScript);

        return WebAssetLoader.loadTemplate("templates/adminDashboard.html", replacements);
    }

    private static String buildSessionsTableRows(List<TelemetryService.AdminSessionSummary> sessions) {
        if (sessions.isEmpty()) {
            return "<tr><td colspan=\"18\" style=\"text-align:center; padding: 20px;\">No sessions recorded yet.</td></tr>\n";
        }
        StringBuilder sb = new StringBuilder();
        for (TelemetryService.AdminSessionSummary s : sessions) {
            sb.append("<tr>\n");
            sb.append("  <td><span class=\"badge\">").append(escapeHtml(s.sessionId)).append("</span></td>\n");
            sb.append("  <td><strong>").append(escapeHtml(s.userId)).append("</strong></td>\n");
            sb.append("  <td>").append(escapeHtml(s.startedAt != null ? s.startedAt : "-")).append("</td>\n");
            sb.append("  <td><strong>").append(s.totalProgramRuns).append("</strong></td>\n");
            sb.append("  <td><strong>").append(s.totalMomotSearches).append("</strong></td>\n");
            sb.append("  <td><strong>").append(s.totalSolutionsLoaded).append("</strong></td>\n");
            sb.append("  <td><strong>").append(s.totalDirectManipulations).append("</strong></td>\n");
            for (int l = 1; l <= 10; l++) {
                TelemetryService.LevelRecord rec = s.levels.get(l);
                if (rec != null && (rec.elapsedMs > 0 || rec.programRuns > 0 || rec.momotSearches > 0 || rec.solutionsLoaded > 0 || rec.directManipulations > 0)) {
                    sb.append("  <td>");
                    if (rec.elapsedMs > 0) {
                        sb.append(formatTimeSecOrMs(rec.elapsedMs)).append("<br><small style=\"color:#8c959f;\">").append(escapeHtml(rec.variant)).append("</small>");
                    } else {
                        sb.append("-");
                    }
                    if (rec.programRuns > 0 || rec.momotSearches > 0 || rec.solutionsLoaded > 0 || rec.directManipulations > 0) {
                        sb.append("<br><small style=\"color:#57606a;\">R:").append(rec.programRuns)
                          .append(" S:").append(rec.momotSearches)
                          .append(" L:").append(rec.solutionsLoaded)
                          .append(" D:").append(rec.directManipulations)
                          .append("</small>");
                    }
                    List<Integer> loadedGens = TelemetryService.loadedGenerations(s.events, l);
                    if (!loadedGens.isEmpty()) {
                        sb.append("<br><small style=\"color:#57606a;\">loaded gen ");
                        for (int gi = 0; gi < loadedGens.size(); gi++) {
                            if (gi > 0) sb.append(", ");
                            sb.append(loadedGens.get(gi));
                        }
                        sb.append("</small>");
                    }
                    List<Long> its = TelemetryService.levelIterations(s.events, l, false);
                    if (!its.isEmpty()) {
                        sb.append("<br><small style=\"color:#57606a;\">active avg ").append(formatTimeSecOrMs(TelemetryService.meanOf(its)))
                          .append(" med ").append(formatTimeSecOrMs(TelemetryService.medianOf(its)))
                          .append(" (n=").append(its.size()).append(")</small>");
                    }
                    List<Long> wall = TelemetryService.levelIterations(s.events, l, true);
                    if (!wall.isEmpty()) {
                        sb.append("<br><small style=\"color:#57606a;\">wall avg ").append(formatTimeSecOrMs(TelemetryService.meanOf(wall)))
                          .append(" med ").append(formatTimeSecOrMs(TelemetryService.medianOf(wall)))
                          .append(" (n=").append(wall.size()).append(")</small>");
                    }
                    sb.append("</td>\n");
                } else {
                    sb.append("  <td style=\"color:#8c959f;\">-</td>\n");
                }
            }
            sb.append("  <td class=\"total-cell\">").append(formatTimeSecOrMs(s.totalElapsedMs)).append("</td>\n");
            sb.append("</tr>\n");
        }
        return sb.toString();
    }

    private static String buildEventsSection(List<TelemetryService.AdminSessionSummary> sessions) {
        StringBuilder sb = new StringBuilder();
        boolean anyEvents = false;
        for (TelemetryService.AdminSessionSummary s : sessions) {
            if (s.events == null || s.events.isEmpty()) continue;
            anyEvents = true;
            sb.append("<div class=\"session-events-section\">\n");
            sb.append("  <h3 style=\"margin: 0 0 10px 0; font-size: 15px;\">Session: <span class=\"badge\">")
              .append(escapeHtml(s.sessionId)).append("</span> &bull; User: <strong>")
              .append(escapeHtml(s.userId)).append("</strong> (").append(s.events.size()).append(" events)</h3>\n");

            for (TelemetryService.SessionEvent ev : s.events) {
                sb.append("  <div class=\"event-card\">\n");
                sb.append("    <div class=\"event-meta\">\n");
                String badgeClass = "program_run".equals(ev.type) ? "badge-run" : "badge-cand";
                sb.append("      <span class=\"").append(badgeClass).append("\">").append(escapeHtml(ev.type)).append("</span>\n");
                sb.append("      <span><strong>Level:</strong> ").append(ev.level).append("</span>\n");
                sb.append("      <span><strong>Variant:</strong> ").append(escapeHtml(ev.variant != null ? ev.variant : "-")).append("</span>\n");
                sb.append("      <span><strong>Time:</strong> ").append(escapeHtml(ev.timestamp != null ? ev.timestamp : "-")).append("</span>\n");
                if (ev.iterationMs >= 0) {
                    sb.append("      <span><strong>Iteration (active):</strong> ").append(String.format("%.1f", ev.iterationMs / 1000.0)).append(" s</span>\n");
                }
                if (ev.wallIterationMs >= 0) {
                    sb.append("      <span><strong>Iteration (wall-clock):</strong> ").append(String.format("%.1f", ev.wallIterationMs / 1000.0)).append(" s</span>\n");
                }
                if (ev.generation >= 0) {
                    sb.append("      <span><strong>Generation:</strong> ").append(ev.generation).append("</span>\n");
                }
                if (ev.modelName != null && !ev.modelName.isEmpty()) {
                    sb.append("      <span><strong>Model:</strong> ").append(escapeHtml(ev.modelName)).append("</span>\n");
                }
                if (ev.objectiveLine != null && !ev.objectiveLine.isEmpty()) {
                    sb.append("      <span><strong>Objective:</strong> ").append(escapeHtml(ev.objectiveLine)).append("</span>\n");
                }
                sb.append("    </div>\n");

                if (ev.xml != null && !ev.xml.trim().isEmpty()) {
                    sb.append("    <div class=\"block-preview\" data-xml=\"").append(escapeHtml(ev.xml)).append("\"></div>\n");
                    sb.append("    <details class=\"xml-toggle\"><summary>Raw Workspace XML</summary><pre>").append(escapeHtml(ev.xml)).append("</pre></details>\n");
                } else {
                    sb.append("    <div style=\"font-size:12px; color:#8c959f; font-style:italic;\">No workspace XML snapshot recorded for this event.</div>\n");
                }
                sb.append("  </div>\n");
            }
            sb.append("</div>\n");
        }
        if (!anyEvents) {
            return "<p style=\"color:#57606a; font-size:13px;\">No session events recorded yet.</p>\n";
        }
        return sb.toString();
    }

    public static String formatTimeSecOrMs(long ms) {
        if (ms <= 0) return "-";
        long totalSec = ms / 1000;
        long mins = totalSec / 60;
        long secs = totalSec % 60;
        long hrs = mins / 60;
        mins = mins % 60;
        if (hrs > 0) {
            return String.format("%02d:%02d:%02d", hrs, mins, secs);
        }
        return String.format("%02d:%02d", mins, secs);
    }

    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    public HttpHandler createLoginHandler() {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    SearchHttpHandler.sendCorsAndNoContent(exchange);
                    return;
                }
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    SearchHttpHandler.sendJson(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
                    return;
                }
                try {
                    String body = SearchHttpHandler.readRequestBody(exchange);
                    String pass = TelemetryService.parseJsonField(body, "password");
                    if (pass == null) pass = "";

                    if (adminPasswordMatches(pass)) {
                        String token = createAdminSessionToken();
                        SearchHttpHandler.sendJson(exchange, 200, "{\"status\":\"ok\",\"token\":\"" + token + "\"}");
                    } else {
                        SearchHttpHandler.sendJson(exchange, 401, "{\"status\":\"error\",\"message\":\"Invalid admin password\"}");
                    }
                } catch (Exception e) {
                    SearchHttpHandler.sendJson(exchange, 500, "{\"status\":\"error\",\"message\":\"" + TelemetryService.escapeJson(e.getMessage()) + "\"}");
                }
            }
        };
    }

    public HttpHandler createSessionsHandler(TelemetryService telemetry) {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    SearchHttpHandler.sendCorsAndNoContent(exchange);
                    return;
                }
                try {
                    if (!isAuthorizedAdmin(exchange, null)) {
                        SearchHttpHandler.sendJson(exchange, 401, "{\"status\":\"error\",\"message\":\"Unauthorized\"}");
                        return;
                    }

                    List<TelemetryService.AdminSessionSummary> all = telemetry.loadAllSessionSummaries();

                    String accept = exchange.getRequestHeaders() != null ? exchange.getRequestHeaders().getFirst("Accept") : null;
                    String query = exchange.getRequestURI().getQuery();
                    boolean formatHtml = (query != null && query.contains("format=html")) || (accept != null && accept.contains("text/html"));

                    if (formatHtml) {
                        String html = generateAdminHtmlReport(all);
                        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                        return;
                    }

                    StringBuilder sb = new StringBuilder("{\"status\":\"ok\",\"sessions\":[");
                    for (int i = 0; i < all.size(); i++) {
                        if (i > 0) sb.append(",");
                        TelemetryService.AdminSessionSummary s = all.get(i);
                        sb.append("{")
                          .append("\"sessionId\":\"").append(TelemetryService.escapeJson(s.sessionId)).append("\",")
                          .append("\"userId\":\"").append(TelemetryService.escapeJson(s.userId)).append("\",")
                          .append("\"startedAt\":\"").append(TelemetryService.escapeJson(s.startedAt)).append("\",")
                          .append("\"totalElapsedMs\":").append(s.totalElapsedMs).append(",")
                          .append("\"completedLevels\":").append(s.completedLevels).append(",")
                          .append("\"totalProgramRuns\":").append(s.totalProgramRuns).append(",")
                          .append("\"totalMomotSearches\":").append(s.totalMomotSearches).append(",")
                          .append("\"totalSolutionsLoaded\":").append(s.totalSolutionsLoaded).append(",")
                          .append("\"totalDirectManipulations\":").append(s.totalDirectManipulations).append(",")
                          .append("\"levels\":{");

                        boolean firstLvl = true;
                        for (Map.Entry<Integer, TelemetryService.LevelRecord> entry : s.levels.entrySet()) {
                            if (!firstLvl) sb.append(",");
                            firstLvl = false;
                            sb.append("\"").append(entry.getKey()).append("\":{")
                              .append("\"elapsedMs\":").append(entry.getValue().elapsedMs).append(",")
                              .append("\"variant\":\"").append(TelemetryService.escapeJson(entry.getValue().variant)).append("\",")
                              .append("\"updatedAt\":\"").append(TelemetryService.escapeJson(entry.getValue().updatedAt)).append("\",")
                              .append("\"programRuns\":").append(entry.getValue().programRuns).append(",")
                              .append("\"momotSearches\":").append(entry.getValue().momotSearches).append(",")
                              .append("\"solutionsLoaded\":").append(entry.getValue().solutionsLoaded).append(",")
                              .append("\"directManipulations\":").append(entry.getValue().directManipulations).append(",")
                              .append(TelemetryService.iterationStatsJson(s.events, entry.getKey()))
                              .append("}");
                        }
                        sb.append("},\"events\":[");
                        for (int evIdx = 0; evIdx < s.events.size(); evIdx++) {
                            if (evIdx > 0) sb.append(",");
                            TelemetryService.SessionEvent ev = s.events.get(evIdx);
                            sb.append("{")
                              .append("\"timestamp\":\"").append(TelemetryService.escapeJson(ev.timestamp != null ? ev.timestamp : "")).append("\",")
                              .append("\"type\":\"").append(TelemetryService.escapeJson(ev.type != null ? ev.type : "")).append("\",")
                              .append("\"variant\":\"").append(TelemetryService.escapeJson(ev.variant != null ? ev.variant : "")).append("\",")
                              .append("\"level\":").append(ev.level).append(",")
                              .append("\"modelName\":\"").append(TelemetryService.escapeJson(ev.modelName != null ? ev.modelName : "")).append("\",")
                              .append("\"modelFile\":\"").append(TelemetryService.escapeJson(ev.modelFile != null ? ev.modelFile : "")).append("\",")
                              .append("\"modelPath\":\"").append(TelemetryService.escapeJson(ev.modelPath != null ? ev.modelPath : "")).append("\",")
                              .append("\"objectiveLine\":\"").append(TelemetryService.escapeJson(ev.objectiveLine != null ? ev.objectiveLine : "")).append("\",")
                              .append("\"levelElapsedMs\":").append(TelemetryService.numOrNull(ev.levelElapsedMs)).append(",")
                              .append("\"iterationMs\":").append(TelemetryService.numOrNull(ev.iterationMs)).append(",")
                              .append("\"wallIterationMs\":").append(TelemetryService.numOrNull(ev.wallIterationMs)).append(",")
                              .append("\"generation\":").append(TelemetryService.numOrNull(ev.generation)).append(",")
                              .append("\"searchId\":\"").append(TelemetryService.escapeJson(ev.searchId != null ? ev.searchId : "")).append("\",")
                              .append("\"levelArrivedAt\":\"").append(TelemetryService.escapeJson(ev.levelArrivedAt != null ? ev.levelArrivedAt : "")).append("\",")
                              .append("\"xml\":").append(ev.xml != null ? "\"" + TelemetryService.escapeJson(ev.xml) + "\"" : "\"\"")
                              .append("}");
                        }
                        sb.append("]}");
                    }
                    sb.append("]}");
                    SearchHttpHandler.sendJson(exchange, 200, sb.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                    SearchHttpHandler.sendJson(exchange, 500, "{\"status\":\"error\",\"message\":\"" + TelemetryService.escapeJson(e.getMessage()) + "\"}");
                }
            }
        };
    }

    public HttpHandler createExportHandler(TelemetryService telemetry) {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    SearchHttpHandler.sendCorsAndNoContent(exchange);
                    return;
                }
                try {
                    if (!isAuthorizedAdmin(exchange, null)) {
                        SearchHttpHandler.sendJson(exchange, 401, "{\"status\":\"error\",\"message\":\"Unauthorized\"}");
                        return;
                    }

                    String format = "json";
                    String query = exchange.getRequestURI().getQuery();
                    if (query != null) {
                        for (String p : query.split("&")) {
                            if (p.startsWith("format=")) {
                                format = p.substring("format=".length()).trim().toLowerCase();
                            }
                        }
                    }

                    List<TelemetryService.AdminSessionSummary> all = telemetry.loadAllSessionSummaries();

                    if ("html".equalsIgnoreCase(format)) {
                        String html = generateAdminHtmlReport(all);
                        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                        exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"all_sessions_report.html\"");
                        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                    } else {
                        StringBuilder sb = new StringBuilder("[\n");
                        for (int i = 0; i < all.size(); i++) {
                            if (i > 0) sb.append(",\n");
                            TelemetryService.AdminSessionSummary s = all.get(i);
                            sb.append("  {\n")
                              .append("    \"sessionId\": \"").append(TelemetryService.escapeJson(s.sessionId)).append("\",\n")
                              .append("    \"userId\": \"").append(TelemetryService.escapeJson(s.userId)).append("\",\n")
                              .append("    \"startedAt\": \"").append(TelemetryService.escapeJson(s.startedAt)).append("\",\n")
                              .append("    \"totalElapsedMs\": ").append(s.totalElapsedMs).append(",\n")
                              .append("    \"completedLevels\": ").append(s.completedLevels).append(",\n")
                              .append("    \"totalProgramRuns\": ").append(s.totalProgramRuns).append(",\n")
                              .append("    \"totalMomotSearches\": ").append(s.totalMomotSearches).append(",\n")
                              .append("    \"totalSolutionsLoaded\": ").append(s.totalSolutionsLoaded).append(",\n")
                              .append("    \"totalDirectManipulations\": ").append(s.totalDirectManipulations).append(",\n")
                              .append("    \"levels\": {");
                            boolean firstLvl = true;
                            for (Map.Entry<Integer, TelemetryService.LevelRecord> entry : s.levels.entrySet()) {
                                if (!firstLvl) sb.append(",");
                                firstLvl = false;
                                sb.append("\n      \"").append(entry.getKey()).append("\": {\n")
                                  .append("        \"elapsedMs\": ").append(entry.getValue().elapsedMs).append(",\n")
                                  .append("        \"variant\": \"").append(TelemetryService.escapeJson(entry.getValue().variant)).append("\",\n")
                                  .append("        \"updatedAt\": \"").append(TelemetryService.escapeJson(entry.getValue().updatedAt)).append("\",\n")
                                  .append("        \"programRuns\": ").append(entry.getValue().programRuns).append(",\n")
                                  .append("        \"momotSearches\": ").append(entry.getValue().momotSearches).append(",\n")
                                  .append("        \"solutionsLoaded\": ").append(entry.getValue().solutionsLoaded).append(",\n")
                                  .append("        \"directManipulations\": ").append(entry.getValue().directManipulations).append(",\n")
                                  .append("        ").append(TelemetryService.iterationStatsJson(s.events, entry.getKey())).append("\n")
                                  .append("      }");
                            }
                            if (!firstLvl) sb.append("\n    ");
                            sb.append("},\n    \"events\": [");
                            for (int evIdx = 0; evIdx < s.events.size(); evIdx++) {
                                if (evIdx > 0) sb.append(",");
                                TelemetryService.SessionEvent ev = s.events.get(evIdx);
                                sb.append("\n      {\n")
                                  .append("        \"timestamp\": \"").append(TelemetryService.escapeJson(ev.timestamp != null ? ev.timestamp : "")).append("\",\n")
                                  .append("        \"type\": \"").append(TelemetryService.escapeJson(ev.type != null ? ev.type : "")).append("\",\n")
                                  .append("        \"variant\": \"").append(TelemetryService.escapeJson(ev.variant != null ? ev.variant : "")).append("\",\n")
                                  .append("        \"level\": ").append(ev.level).append(",\n")
                                  .append("        \"modelName\": \"").append(TelemetryService.escapeJson(ev.modelName != null ? ev.modelName : "")).append("\",\n")
                                  .append("        \"modelFile\": \"").append(TelemetryService.escapeJson(ev.modelFile != null ? ev.modelFile : "")).append("\",\n")
                                  .append("        \"modelPath\": \"").append(TelemetryService.escapeJson(ev.modelPath != null ? ev.modelPath : "")).append("\",\n")
                                  .append("        \"objectiveLine\": \"").append(TelemetryService.escapeJson(ev.objectiveLine != null ? ev.objectiveLine : "")).append("\",\n")
                                  .append("        \"levelElapsedMs\": ").append(TelemetryService.numOrNull(ev.levelElapsedMs)).append(",\n")
                                  .append("        \"iterationMs\": ").append(TelemetryService.numOrNull(ev.iterationMs)).append(",\n")
                                  .append("        \"wallIterationMs\": ").append(TelemetryService.numOrNull(ev.wallIterationMs)).append(",\n")
                                  .append("        \"generation\": ").append(TelemetryService.numOrNull(ev.generation)).append(",\n")
                                  .append("        \"searchId\": ").append(ev.searchId != null ? "\"" + TelemetryService.escapeJson(ev.searchId) + "\"" : "null").append(",\n")
                                  .append("        \"levelArrivedAt\": ").append(ev.levelArrivedAt != null ? "\"" + TelemetryService.escapeJson(ev.levelArrivedAt) + "\"" : "null").append(",\n")
                                  .append("        \"xml\": ").append(ev.xml != null ? "\"" + TelemetryService.escapeJson(ev.xml) + "\"" : "\"\"").append("\n")
                                  .append("      }");
                            }
                            sb.append("\n    ]\n  }");
                        }
                        sb.append("\n]");
                        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                        exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"all_sessions_export.json\"");
                        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    SearchHttpHandler.sendJson(exchange, 500, "{\"status\":\"error\",\"message\":\"" + TelemetryService.escapeJson(e.getMessage()) + "\"}");
                }
            }
        };
    }
}
