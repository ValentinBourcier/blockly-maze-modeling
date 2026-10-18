package blocky_game;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SessionContextTest {

    @Test
    void testSessionCreationAndDirectory() {
        SessionManager manager = SessionManager.getInstance();
        SessionContext session = manager.getOrCreateSession(null);

        assertNotNull(session);
        assertNotNull(session.getSessionId());
        assertFalse(session.getSessionId().trim().isEmpty());

        File sessionDir = session.getSessionDir();
        assertNotNull(sessionDir);
        assertTrue(sessionDir.exists());
        assertTrue(sessionDir.isDirectory());
        assertTrue(sessionDir.getName().contains(session.getSessionId()));

        assertNotNull(session.getEngine());
    }

    @Test
    void testTouchAndLastAccessedTime() throws InterruptedException {
        SessionContext session = new SessionContext("test-touch-session");
        long t1 = session.getLastAccessedTime();
        Thread.sleep(10);
        session.touch();
        long t2 = session.getLastAccessedTime();

        assertTrue(t2 >= t1);
    }

    @Test
    void testMomotLogAndStatusDefaults() {
        SessionContext session = new SessionContext("test-momot-defaults");
        assertFalse(session.isMomotRunning());
        assertEquals("Idle", session.getMomotStatus());

        List<String> logs = session.getMomotLogs();
        assertNotNull(logs);
        assertTrue(logs.isEmpty());
    }

    @Test
    void testMomotRunServiceInterruptedExecutesOnDoneWithoutInterruptedException() throws Exception {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(capturedErr));

            AtomicBoolean onDoneCalled = new AtomicBoolean(false);
            AtomicBoolean threadInterruptedDuringOnDone = new AtomicBoolean(true);
            CountDownLatch doneLatch = new CountDownLatch(1);

            String testSessionId = "test-interrupted-spec-" + System.nanoTime();
            MomotRunService.RunSpec spec = new MomotRunService.RunSpec(
                "nonexistent.xmi", "target/test-out", 10, 10, 1, 1, false, 1, testSessionId
            );

            MomotRunService.runAsync(spec, null, null, () -> {
                threadInterruptedDuringOnDone.set(Thread.currentThread().isInterrupted());
                onDoneCalled.set(true);
                doneLatch.countDown();
            }, null);

            // Interrupt/cancel the search
            MomotRunService.stopMomotSearch(testSessionId);

            assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "onDone callback must execute when search is interrupted");
            assertTrue(onDoneCalled.get(), "onDone must have been called");
            assertFalse(threadInterruptedDuringOnDone.get(), "Thread interrupted flag must be cleared before onDone is invoked");

            String errText = capturedErr.toString();
            assertFalse(errText.contains("PlatformImpl.waitForStart"),
                "No PlatformImpl.waitForStart exception should be logged to stderr: " + errText);
            assertFalse(errText.contains("java.lang.InterruptedException"),
                "No InterruptedException stack trace should be dumped to stderr: " + errText);
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void testSessionContextStopMomotRunCleansUpWithoutExceptions() {
        SessionContext session = new SessionContext("test-stop-session-" + System.nanoTime());
        session.runMomotWithParams(42, 100, 10000, 5, 10);
        assertTrue(session.isMomotRunning() || "Waiting".equals(session.getMomotStatus()) || "Running".equals(session.getMomotStatus()));

        assertDoesNotThrow(session::stopMomotRun, "stopMomotRun should execute cleanly without throwing exceptions");
        assertFalse(session.isMomotRunning(), "Session must not be running after stop");
        assertEquals("Stopped", session.getMomotStatus(), "Status should be Stopped");
    }
}
