package blocky_game;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * HTTP handler module for serving static assets from blockly-games-web (/).
 */
public class StaticFileHandler implements HttpHandler {
    private final File baseWebDir;

    public StaticFileHandler() {
        File webDir = new File("blocky_game/src/blocky_game/blockly-games-web");
        if (!webDir.exists()) {
            webDir = new File("src/blocky_game/blockly-games-web");
        }
        if (!webDir.exists()) {
            webDir = new File("blockly-games-web");
        }
        File resolved = webDir.getAbsoluteFile();
        try {
            resolved = webDir.getCanonicalFile();
        } catch (IOException ignored) {}
        this.baseWebDir = resolved;
        System.out.println("[HttpSearchServer] Serving static web assets from: " + baseWebDir.getAbsolutePath());
    }

    public void register(HttpServer server) {
        server.createContext("/", this);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            SearchHttpHandler.sendCorsAndNoContent(exchange);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.trim().isEmpty()) {
            path = "/maze.html";
        }

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        File requestedFile = new File(baseWebDir, path).getCanonicalFile();
        if (!requestedFile.exists() && !path.contains(".")) {
            File htmlFallback = new File(baseWebDir, path + ".html").getCanonicalFile();
            if (htmlFallback.exists() && htmlFallback.isFile()) {
                requestedFile = htmlFallback;
            }
        }
        String reqPath = requestedFile.getPath();
        String basePath = baseWebDir.getPath();
        boolean isUnderBase = reqPath.equalsIgnoreCase(basePath) || reqPath.toLowerCase().startsWith(basePath.toLowerCase() + File.separator) || reqPath.toLowerCase().startsWith(basePath.toLowerCase());
        if (!isUnderBase || !requestedFile.exists() || requestedFile.isDirectory()) {
            String response = "404 Not Found";
            exchange.sendResponseHeaders(404, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
            return;
        }

        String contentType = getContentType(requestedFile.getName());
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        byte[] fileBytes;
        if (Boolean.getBoolean("blocky.levelLimits") && requestedFile.getName().equalsIgnoreCase("maze.html")) {
            String html = Files.readString(requestedFile.toPath(), StandardCharsets.UTF_8);
            html = html.replace("<head>", "<head>\n  <script>window.__blockyLevelLimits = true;</script>");
            fileBytes = html.getBytes(StandardCharsets.UTF_8);
        } else {
            fileBytes = Files.readAllBytes(requestedFile.toPath());
        }
        exchange.sendResponseHeaders(200, fileBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(fileBytes);
        }
    }

    private String getContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }
}
