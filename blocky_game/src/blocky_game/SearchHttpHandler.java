package blocky_game;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deep module hosting HTTP endpoint registration, request dispatching, CORS handling,
 * and JSON utility helpers for the Blocky Maze REST backend.
 */
public class SearchHttpHandler {

    public static final Object SESSION_FILE_LOCK = new Object();

    public static void registerRoutes(HttpServer server, TelemetryService telemetry, AdminReportService admin) {
        new SessionHttpHandler().register(server);
        new MomotSearchHttpHandler(telemetry).register(server);
        new DebugHttpHandler().register(server);
        new DirectManipulationHttpHandler(telemetry).register(server);
        new LevelTimeHttpHandler(telemetry).register(server);
        new StaticFileHandler().register(server);

        server.createContext("/api/admin/login", admin.createLoginHandler());
        server.createContext("/api/admin/sessions", admin.createSessionsHandler(telemetry));
        server.createContext("/api/admin/export", admin.createExportHandler(telemetry));
    }

    public static void sendCorsAndNoContent(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, X-Session-ID, Authorization, X-Timer-Session-ID, X-Level-ID");
        exchange.sendResponseHeaders(204, -1);
    }

    public static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, X-Session-ID, Authorization, X-Timer-Session-ID, X-Level-ID");

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static String readRequestBody(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) || "HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            return "";
        }
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    public static SessionContext getSession(HttpExchange exchange, String requestBody) {
        String sessionId = null;
        if (exchange.getRequestHeaders() != null) {
            for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
                if ("x-session-id".equalsIgnoreCase(entry.getKey())) {
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        sessionId = entry.getValue().get(0);
                        break;
                    }
                }
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
        if ((sessionId == null || sessionId.trim().isEmpty()) && requestBody != null) {
            sessionId = parseJsonField(requestBody, "sessionId");
        }
        SessionContext session = SessionManager.getInstance().getOrCreateSession(sessionId);
        if (exchange.getResponseHeaders() != null) {
            exchange.getResponseHeaders().set("X-Session-ID", session.getSessionId());
        }
        return session;
    }

    public static String parseJsonField(String json, String field) {
        if (json == null || json.trim().isEmpty()) return null;
        Pattern pStr = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher mStr = pStr.matcher(json);
        if (mStr.find()) {
            String raw = mStr.group(1);
            return raw.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
        }

        Pattern pNum = Pattern.compile("\"" + field + "\"\\s*:\\s*([0-9\\.]+)");
        Matcher mNum = pNum.matcher(json);
        if (mNum.find()) return mNum.group(1);

        return null;
    }

    public static int parseJsonIntField(String json, String field, int defaultVal) {
        String val = parseJsonField(json, field);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    public static long parseJsonLongField(String json, String field, long defaultVal) {
        String val = parseJsonField(json, field);
        if (val != null) {
            try {
                return Long.parseLong(val);
            } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    public static boolean parseJsonBooleanField(String json, String field, boolean defaultVal) {
        if (json == null || json.trim().isEmpty()) return defaultVal;
        Pattern pBool = Pattern.compile("\"" + field + "\"\\s*:\\s*\"?(true|false)\"?", Pattern.CASE_INSENSITIVE);
        Matcher m = pBool.matcher(json);
        if (m.find()) {
            return Boolean.parseBoolean(m.group(1));
        }
        return defaultVal;
    }

    public static String parseJsonObjectOrString(String json, String field) {
        if (json == null || json.trim().isEmpty()) return null;
        String val = parseJsonField(json, field);
        if (val != null) return val;
        Pattern pRaw = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\[.*?\\]|\\{.*?\\})", Pattern.DOTALL);
        Matcher m = pRaw.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static String extractSessionId(HttpExchange exchange, String body) {
        SessionContext session = getSession(exchange, body);
        return session != null ? session.getSessionId() : null;
    }

    public static String extractTimerSessionId(HttpExchange exchange, String body) {
        if (exchange != null && exchange.getRequestHeaders() != null) {
            for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
                if ("x-timer-session-id".equalsIgnoreCase(entry.getKey())) {
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        String val = entry.getValue().get(0);
                        if (val != null && !val.trim().isEmpty()) {
                            return val.trim();
                        }
                    }
                }
            }
        }
        if (body != null && !body.isEmpty()) {
            String fromBody = parseJsonField(body, "timerSessionId");
            if (fromBody != null && !fromBody.trim().isEmpty()) {
                return fromBody.trim();
            }
        }
        return null;
    }

    public static int extractLevelId(HttpExchange exchange, String body, SessionContext session) {
        if (exchange != null && exchange.getRequestHeaders() != null) {
            for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
                if ("x-level-id".equalsIgnoreCase(entry.getKey())) {
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        try {
                            int lvl = Integer.parseInt(entry.getValue().get(0).trim());
                            if (lvl >= 1 && lvl <= 10) return lvl;
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        if (body != null && !body.isEmpty()) {
            int lvl = parseJsonIntField(body, "level", 0);
            if (lvl >= 1 && lvl <= 10) return lvl;
            lvl = parseJsonIntField(body, "levelId", 0);
            if (lvl >= 1 && lvl <= 10) return lvl;
        }
        if (session != null && session.getEngine() != null && session.getEngine().getCurrentLevel() != null) {
            int lvl = session.getEngine().getCurrentLevel().getId();
            if (lvl >= 1 && lvl <= 10) return lvl;
        }
        return 1;
    }
}
