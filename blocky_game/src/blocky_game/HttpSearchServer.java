package blocky_game;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server delivering REST APIs for multi-user web clients and
 * serving static web frontend assets from blockly-games-web.
 */
public class HttpSearchServer {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        String portProp = System.getProperty("http.port");
        if (portProp == null) {
            portProp = System.getenv("PORT");
        }
        if (portProp != null && !portProp.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portProp.trim());
            } catch (NumberFormatException ignored) {}
        }
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        startServer(port);
    }

    public static HttpServer startServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.setExecutor(Executors.newFixedThreadPool(16));

        TelemetryService telemetryService = new TelemetryService();
        AdminReportService adminService = new AdminReportService();

        SearchHttpHandler.registerRoutes(server, telemetryService, adminService);

        server.start();
        System.out.println("[HttpSearchServer] Server started and listening on http://0.0.0.0:" + server.getAddress().getPort() + "/");
        return server;
    }
}
