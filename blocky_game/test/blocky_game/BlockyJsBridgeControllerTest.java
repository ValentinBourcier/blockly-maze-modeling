package blocky_game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockyJsBridgeControllerTest {

    private GameEngine engine;
    private BlockyJsBridgeController controller;
    private BlockyJsBridgeController.JSBridge bridge;

    @BeforeEach
    void setUp() {
        engine = new GameEngine();
        engine.initializeGame();
        controller = new BlockyJsBridgeController(engine, null, null, null);
        bridge = controller.getBridge();
    }

    @Test
    void testBridgeSyncMap() {
        String mapJson = "[[1,1,1],[1,2,3],[1,1,1]]";
        bridge.syncMap(mapJson);

        assertNotNull(engine.getCurrentLevel());
        assertNotNull(engine.getCurrentLevel().getMap());
    }

    @Test
    void testBridgeSyncLevelMeta() {
        String metaJson = "{\"level\":1,\"maxBlocks\":5,\"startDirection\":1}";
        bridge.syncLevelMeta(metaJson);

        assertNotNull(engine.getCurrentLevel());
        assertEquals(5, engine.getCurrentLevel().getMaxBlocks());
    }

    @Test
    void testBridgeSyncModel() {
        String xml = "<xml><block type=\"maze_moveForward\"><next><block type=\"maze_moveForward\"></block></next></block></xml>";
        bridge.syncModel(xml);

        assertNotNull(engine.getCurrentLevel().getSolution());
        assertNotNull(engine.getCurrentLevel().getSolution().getFirstContainer());
        assertNotNull(engine.getCurrentLevel().getSolution().getFirstContainer().getNext());
    }

    @Test
    void testBridgeSuppressSync() {
        String xml = "<xml><block type=\"maze_moveForward\"></block></xml>";
        bridge.syncModel(xml);
        assertNotNull(engine.getCurrentLevel().getSolution().getFirstContainer());

        controller.setSuppressSync(true);
        bridge.syncModel("<xml><block type=\"maze_turn\"><field name=\"DIR\">turnLeft</field></block></xml>");

        // Should retain moveForward because sync was suppressed
        blocky.Statement stmt = engine.getCurrentLevel().getSolution().getFirstContainer().getStatement();
        assertTrue(stmt instanceof blocky.AtomicStatement);
        assertEquals(blocky.AtomicStatementKind.MOVE_FORWARD, ((blocky.AtomicStatement) stmt).getKind());

        controller.setSuppressSync(false);
        assertFalse(controller.isSuppressSync());
    }

    @Test
    void testBridgeDebuggingMethods() {
        String mapJson = "[[2,0,3]]";
        bridge.syncMap(mapJson);
        String xml = "<xml><block type=\"maze_moveForward\"></block></xml>";
        bridge.syncModel(xml);

        String start = bridge.debugStart(0, 0, 1);
        assertNotNull(start);
        assertTrue(start.contains("running") || start.contains("paused"));

        String step = bridge.debugStep();
        assertNotNull(step);

        String stop = bridge.debugStop();
        assertNotNull(stop);
        assertTrue(stop.contains("stopped") || stop.contains("false"));
    }

    @Test
    void testToJsonStringArrayLiteral() {
        String json = BlockyJsBridgeController.toJsonStringArrayLiteral(java.util.List.of("alpha", "beta"));
        assertEquals("[\"alpha\",\"beta\"]", json);

        assertEquals("[]", BlockyJsBridgeController.toJsonStringArrayLiteral(null));
        assertEquals("[]", BlockyJsBridgeController.toJsonStringArrayLiteral(java.util.Collections.emptyList()));
    }
}
