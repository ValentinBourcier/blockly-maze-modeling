package blocky_game;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class AdminReportServiceTest {

    @Test
    void testPasswordMatching() {
        String prev = System.getProperty("blocky.adminPassword");
        try {
            System.setProperty("blocky.adminPassword", "secret-key");
            AdminReportService service = new AdminReportService();

            assertTrue(service.adminPasswordMatches("secret-key"));
            assertFalse(service.adminPasswordMatches("wrong-key"));
            assertFalse(service.adminPasswordMatches(null));
            assertFalse(service.adminPasswordMatches(""));
        } finally {
            if (prev != null) System.setProperty("blocky.adminPassword", prev);
            else System.clearProperty("blocky.adminPassword");
        }
    }

    @Test
    void testGenerateAdminHtmlReport() {
        AdminReportService service = new AdminReportService();
        String html = service.generateAdminHtmlReport(Collections.emptyList());

        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("Blockly Maze - Sessions & Timing Report"));
        assertTrue(html.contains("No sessions recorded yet."));
        assertTrue(html.contains("blockPreview.js"));
    }

    @Test
    void testFormatTime() {
        assertEquals("00:05", AdminReportService.formatTimeSecOrMs(5000));
        assertEquals("01:05", AdminReportService.formatTimeSecOrMs(65000));
        assertEquals("01:00:00", AdminReportService.formatTimeSecOrMs(3600000));
        assertEquals("-", AdminReportService.formatTimeSecOrMs(0));
    }
}
