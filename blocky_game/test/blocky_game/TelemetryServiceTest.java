package blocky_game;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryServiceTest {

    @Test
    void testMeanAndMedianCalculation() {
        List<Long> values = Arrays.asList(100L, 200L, 300L, 400L, 500L);
        assertEquals(300L, TelemetryService.meanOf(values));
        assertEquals(300L, TelemetryService.medianOf(values));

        List<Long> evenValues = Arrays.asList(100L, 200L, 300L, 400L);
        assertEquals(250L, TelemetryService.meanOf(evenValues));
        assertEquals(250L, TelemetryService.medianOf(evenValues));

        assertEquals(0L, TelemetryService.meanOf(Collections.emptyList()));
        assertEquals(0L, TelemetryService.medianOf(Collections.emptyList()));
    }

    @Test
    void testJsonParsingHelpers() {
        String json = "{\"name\":\"test_session\",\"level\":3,\"elapsedMs\":15000}";
        assertEquals("test_session", TelemetryService.parseJsonField(json, "name"));
        assertEquals(3, TelemetryService.parseJsonIntField(json, "level", 0));
        assertEquals(15000L, TelemetryService.parseJsonLongField(json, "elapsedMs", 0L));
        assertNull(TelemetryService.parseJsonField(json, "missing"));
    }

    @Test
    void testEscapeJson() {
        assertEquals("hello\\\"world\\n", TelemetryService.escapeJson("hello\"world\n"));
        assertEquals("", TelemetryService.escapeJson(null));
    }
}
