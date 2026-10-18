package blocky_game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MomotUiCoordinatorTest {

    @Test
    void testAlgorithmSwitch() {
        MomotUiCoordinator coordinator = new MomotUiCoordinator(null, null);

        coordinator.setMomotAlgorithm("MEMETIC_NSGA_II");
        assertEquals("MEMETIC_NSGA_II", System.getProperty("blocky.algorithm"));

        coordinator.setMomotAlgorithm("NSGA_II");
        assertEquals("NSGA_II", System.getProperty("blocky.algorithm"));

        coordinator.setMomotAlgorithm("unknown");
        assertEquals("NSGA_II", System.getProperty("blocky.algorithm"));
    }

    @Test
    void testStringEscaping() {
        assertEquals("foo\\\"bar", MomotUiCoordinator.escapeJsonString("foo\"bar"));
        assertEquals("line1\\nline2", MomotUiCoordinator.escapeJsonString("line1\nline2"));
        assertEquals("", MomotUiCoordinator.escapeJsonString(null));

        assertEquals("foo\\\"bar", MomotUiCoordinator.escapeForJsStringLiteral("foo\"bar"));
        assertEquals("foo\\'bar", MomotUiCoordinator.escapeForJsStringLiteral("foo'bar"));
        assertEquals("line1\\nline2", MomotUiCoordinator.escapeForJsStringLiteral("line1\nline2"));
        assertEquals("", MomotUiCoordinator.escapeForJsStringLiteral(null));
    }

    @Test
    void testInitialState() {
        MomotUiCoordinator coordinator = new MomotUiCoordinator(null, null);
        assertFalse(coordinator.isMomotRunning());
        assertFalse(coordinator.isPendingShowMomotPanel());

        coordinator.setPendingShowMomotPanel(true);
        assertTrue(coordinator.isPendingShowMomotPanel());
    }
}
