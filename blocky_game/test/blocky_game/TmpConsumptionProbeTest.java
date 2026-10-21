package blocky_game;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;

class TmpConsumptionProbeTest {
    static String post(HttpClient c, String base, String path, String sid, String body) throws Exception {
        HttpRequest r = HttpRequest.newBuilder().uri(URI.create(base + path))
            .header("Content-Type", "application/json").header("X-Session-ID", sid)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return c.send(r, HttpResponse.BodyHandlers.ofString()).body();
    }
    @Test
    void probe() throws Exception {
        HttpServer s = HttpSearchServer.startServer(0);
        String base = "http://localhost:" + s.getAddress().getPort();
        HttpClient c = HttpClient.newHttpClient();
        String ns = c.send(HttpRequest.newBuilder().uri(URI.create(base + "/api/session/new"))
            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).body();
        String sid = ns.replaceAll(".*\"sessionId\":\"([^\"]+)\".*", "$1");
        String xml = "<xml><block type=\"maze_moveForward\"><next><block type=\"maze_moveForward\"></block></next></block></xml>";
        for (int lvl : new int[]{1, 2, 4}) {
            String grid = "[[0,0,0,0,0],[0,2,1,1,0],[0,0,0,3,0],[0,0,0,0,0],[0,0,0,0,0]]";
            String meta = "{\"level\":" + lvl + ",\"maxBlocks\":10,\"startDirection\":1}";
            String snap = "{\"epoch\":" + (10 + lvl) + ",\"map\":" + grid + ",\"meta\":" + meta
                + ",\"xml\":\"" + xml.replace("\"", "\\\"") + "\",\"allowEmpty\":false,\"level\":" + lvl + "}";
            System.out.println("PROBE snapshot L" + lvl + " -> " + post(c, base, "/api/session/snapshot", sid, snap));
            System.out.println("PROBE run L" + lvl + " -> " + post(c, base, "/api/simulation/run", sid, "{\"level\":" + lvl + "}"));
        }
        s.stop(0);
    }
}
