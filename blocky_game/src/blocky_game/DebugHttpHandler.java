package blocky_game;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;

/**
 * HTTP handler module for interactive debugging routes (/api/debug/*).
 */
public class DebugHttpHandler {

    public void register(HttpServer server) {
        server.createContext("/api/debug/start", new DebugStartHandler());
        server.createContext("/api/debug/step", new DebugStepHandler());
        server.createContext("/api/debug/pause", new DebugPauseHandler());
        server.createContext("/api/debug/stop", new DebugStopHandler());
        server.createContext("/api/debug/skip", new DebugSkipHandler());
        server.createContext("/api/debug/tick", new DebugTickHandler());
    }

    private static class DebugStartHandler implements HttpHandler {
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

            String resJson = session.getEngine().debugStart(q, s, t);
            SearchHttpHandler.sendJson(exchange, 200, resJson != null ? resJson : "{}");
        }
    }

    private static class DebugStepHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            String resJson = session.getEngine().debugStepOnce();
            SearchHttpHandler.sendJson(exchange, 200, resJson != null ? resJson : "{}");
        }
    }

    private static class DebugPauseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            String resJson = session.getEngine().debugTogglePause();
            SearchHttpHandler.sendJson(exchange, 200, resJson != null ? resJson : "{}");
        }
    }

    private static class DebugStopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            String resJson = session.getEngine().debugStop();
            SearchHttpHandler.sendJson(exchange, 200, resJson != null ? resJson : "{}");
        }
    }

    private static class DebugSkipHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            String resJson = session.getEngine().debugSkipToEnd();
            SearchHttpHandler.sendJson(exchange, 200, resJson != null ? resJson : "{}");
        }
    }

    private static class DebugTickHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                SearchHttpHandler.sendCorsAndNoContent(exchange);
                return;
            }
            String body = SearchHttpHandler.readRequestBody(exchange);
            SessionContext session = SearchHttpHandler.getSession(exchange, body);

            String resJson = session.getEngine().debugTick();
            SearchHttpHandler.sendJson(exchange, 200, resJson != null ? resJson : "{}");
        }
    }
}
