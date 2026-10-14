package blocky_game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebAssetLoaderTest {

    @BeforeEach
    void setUp() {
        WebAssetLoader.clearCache();
    }

    @Test
    void testLoadSyncScript() {
        String script = WebAssetLoader.loadScript("scripts/syncScript.js");
        assertNotNull(script);
        assertFalse(script.trim().isEmpty());
        assertTrue(script.contains("syncModel"), "syncScript.js should contain syncModel");
        assertTrue(script.contains("runButton"), "syncScript.js should contain runButton");
    }

    @Test
    void testLoadDebugControlsScript() {
        String script = WebAssetLoader.loadScript("scripts/debugControls.js");
        assertNotNull(script);
        assertFalse(script.trim().isEmpty());
        assertTrue(script.contains("__lvlLoadingEnsure"), "debugControls.js should define __lvlLoadingEnsure");
        assertTrue(script.contains("__momotEnsure"), "debugControls.js should define __momotEnsure");
        assertTrue(script.contains("{{DEBUG_OVERLAY_SNIPPET}}"), "debugControls.js template should contain {{DEBUG_OVERLAY_SNIPPET}}");
        assertTrue(script.contains("{{OVERLAY_RENDER_SNIPPET}}"), "debugControls.js template should contain {{OVERLAY_RENDER_SNIPPET}}");
        assertTrue(script.contains("{{DEBUG_TICK_MS}}"), "debugControls.js template should contain {{DEBUG_TICK_MS}}");
    }

    @Test
    void testLoadTemplateWithReplacements() {
        Map<String, String> replacements = Map.of(
            "DEBUG_OVERLAY_SNIPPET", "/* dummy debug overlay */",
            "OVERLAY_RENDER_SNIPPET", "/* dummy overlay render */",
            "DEBUG_TICK_MS", "300"
        );
        String resolved = WebAssetLoader.loadTemplate("scripts/debugControls.js", replacements);
        assertNotNull(resolved);
        assertFalse(resolved.contains("{{DEBUG_OVERLAY_SNIPPET}}"));
        assertFalse(resolved.contains("{{OVERLAY_RENDER_SNIPPET}}"));
        assertFalse(resolved.contains("{{DEBUG_TICK_MS}}"));
        assertTrue(resolved.contains("/* dummy debug overlay */"));
        assertTrue(resolved.contains("/* dummy overlay render */"));
        assertTrue(resolved.contains("300"));
    }

    @Test
    void testLoadApplyLevelScript() {
        String script = WebAssetLoader.loadScript("scripts/applyLevel.js");
        assertNotNull(script);
        assertFalse(script.trim().isEmpty());
        assertTrue(script.contains("ApplyLevel:"));
        assertTrue(script.contains("window.__loadXml"));
    }

    @Test
    void testLoadMomotSolutionScript() {
        String script = WebAssetLoader.loadScript("scripts/loadMomotSolution.js");
        assertNotNull(script);
        assertFalse(script.trim().isEmpty());
        assertTrue(script.contains("MoMoT:"));
        assertTrue(script.contains("window.__momotXml"));
    }

    @Test
    void testLoadAdminDashboardTemplate() {
        Map<String, String> replacements = new java.util.HashMap<>();
        replacements.put("totalSessions", "1");
        replacements.put("uniqueUsers", "1");
        replacements.put("totalPlaytime", "00:10");
        replacements.put("programRuns", "2");
        replacements.put("momotSearches", "0");
        replacements.put("solutionsLoaded", "0");
        replacements.put("directManipulations", "0");
        replacements.put("generatedAt", "now");
        replacements.put("sessions", "<tr><td>test</td></tr>");
        replacements.put("events", "<div>events</div>");
        replacements.put("blockPreviewScript", "/* blockPreview.js */");

        String html = WebAssetLoader.loadTemplate("templates/adminDashboard.html", replacements);
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("<tr><td>test</td></tr>"));
        assertTrue(html.contains("blockPreview.js"));
    }

    @Test
    void testLoadBlockPreviewScript() {
        String script = WebAssetLoader.loadScript("common/blockPreview.js");
        assertNotNull(script);
        assertTrue(script.contains("blockPreview.js"));
        assertTrue(script.contains("BlockPreview"));
    }

    @Test
    void testNonExistentAssetThrows() {
        assertThrows(IllegalStateException.class, () -> {
            WebAssetLoader.loadScript("scripts/non_existent_file.js");
        });
    }
}
