package blocky_game;

import blocky.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Deep module responsible for step-by-step interactive debugging:
 * - Session state machine (start, pause, step, stop, skip to end, tick)
 * - Trace execution snapshotting and alignment during paused program edits
 * - Direct manipulation (MoMoT result) path comparison and prefix alignment
 * - JSON frame generation for UI animation and block highlighting
 */
public class InteractiveDebugger {

    private ExecutionTrace debugTrace;
    private List<String> debugLogLines;
    private int debugIndex;
    private boolean debugPaused;
    private boolean debugDirtySolution;
    private int debugStartX;
    private int debugStartY;
    private Direction debugStartDir;
    private int debugCurrentX;
    private int debugCurrentY;
    private Direction debugCurrentDir;
    private boolean debugSessionActive;
    private int[][] debugPastPrefixPath = new int[0][0];
    private String debugImmediateFeedbackNote;

    // Direct manipulation comparison state
    private boolean dmCompareArmed;
    private int dmStartX;
    private int dmStartY;
    private Direction dmStartDir;
    private int[][] dmBaselinePath = new int[0][0];
    private int[][] dmSolutionPath = new int[0][0];
    private int dmCommonLen;
    private int dmAlignedX;
    private int dmAlignedY;
    private int dmAlignedStateIndex;
    private boolean dmAlignedValid;

    public boolean isSessionActive() {
        return debugSessionActive;
    }

    public void markDirtySolution() {
        if (debugTrace != null && debugPaused) {
            debugDirtySolution = true;
        }
    }

    public void armDirectManipulationComparison() {
        this.dmCompareArmed = true;
    }

    public boolean hasDirectManipulationComparison() {
        return dmSolutionPath != null && dmSolutionPath.length > 0 && dmBaselinePath != null && dmBaselinePath.length > 0;
    }

    public int[][] getDmBaselinePath() {
        return dmBaselinePath != null ? dmBaselinePath : new int[0][0];
    }

    public int[][] getDmSolutionPath() {
        return dmSolutionPath != null ? dmSolutionPath : new int[0][0];
    }

    public int getDmCommonLen() {
        return dmCommonLen;
    }

    public boolean isDmAlignedValid() {
        return dmAlignedValid;
    }

    public int getDmAlignedX() {
        return dmAlignedX;
    }

    public int getDmAlignedY() {
        return dmAlignedY;
    }

    /**
     * Called when Pegman is teleported on the map (e.g. for Direct Manipulation).
     */
    public void onPegmanTeleported(Level currentLevel, int x, int y, Direction dir, Cell startCell) {
        debugCurrentX = x;
        debugCurrentY = y;
        debugCurrentDir = dir;

        if (debugTrace == null) {
            debugStartX = x;
            debugStartY = y;
            debugStartDir = dir;
        }

        if (startCell != null) {
            dmStartX = startCell.getX();
            dmStartY = startCell.getY();
        } else {
            dmStartX = x;
            dmStartY = y;
        }
        dmStartDir = (currentLevel != null && currentLevel.eIsSet(BlockyPackage.Literals.LEVEL__START_ORIENTATION))
                ? currentLevel.getStartOrientation()
                : dir;
        try {
            DebuggingService.DebugTraceResult base =
                    DebuggingService.computeTraceFromState(currentLevel, dmStartX, dmStartY, dmStartDir);
            dmBaselinePath = compressTracePositions(base.trace, base.trace != null && base.trace.getStates() != null ? base.trace.getStates().size() - 1 : 0);
        } catch (Exception e) {
            dmBaselinePath = new int[0][0];
        }

        dmSolutionPath = new int[0][0];
        dmCommonLen = 0;
        dmAlignedValid = false;

        if (debugSessionActive) {
            debugDirtySolution = true;
        }
    }

    /**
     * Called after loading a model file to check and compute DM diff overlays if armed.
     */
    public void onModelLoaded(Level loadedLevel) {
        if (dmCompareArmed && dmBaselinePath != null && dmBaselinePath.length > 0) {
            try {
                DebuggingService.DebugTraceResult sol =
                        DebuggingService.computeTraceFromState(loadedLevel, dmStartX, dmStartY, dmStartDir);
                dmSolutionPath = compressTracePositions(sol.trace, sol.trace != null && sol.trace.getStates() != null ? sol.trace.getStates().size() - 1 : 0);
                dmCommonLen = longestCommonPrefixLenByXY(dmBaselinePath, dmSolutionPath);
                dmAlignedValid = false;
                if (dmCommonLen > 0 && dmBaselinePath.length >= dmCommonLen) {
                    for (int i = dmCommonLen - 1; i >= 0; i--) {
                        int[] pt = dmBaselinePath[i];
                        if (pt != null && pt.length >= 2) {
                            int tx = pt[0];
                            int ty = pt[1];
                            int idx = findLastRunningStateIndexAtOrBefore(sol.trace, tx, ty);
                            if (idx >= 0) {
                                dmAlignedX = tx;
                                dmAlignedY = ty;
                                dmAlignedStateIndex = idx;
                                dmAlignedValid = true;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                dmSolutionPath = new int[0][0];
                dmCommonLen = 0;
                dmAlignedValid = false;
            } finally {
                dmCompareArmed = false;
            }
        } else {
            dmCompareArmed = false;
        }
    }

    /**
     * Starts a debug session from the specified WebView pegman state.
     */
    public String debugStart(Level currentLevel, BlockyAstMapper astMapper, int startX, int startY, int startT) {
        if (currentLevel == null) {
            return "{\"index\":0,\"total\":0}";
        }
        debugStartX = startX;
        debugStartY = startY;
        debugStartDir = DebuggingService.tToDirection(startT);
        debugCurrentX = startX;
        debugCurrentY = startY;
        debugCurrentDir = debugStartDir;
        debugPaused = true;
        debugDirtySolution = false;
        debugSessionActive = true;

        int desiredIndex = -1;
        int traceStartX = startX;
        int traceStartY = startY;
        Direction traceStartDir = debugStartDir;
        try {
            if (hasDirectManipulationComparison() && dmAlignedValid && startX == dmAlignedX && startY == dmAlignedY) {
                traceStartX = dmStartX;
                traceStartY = dmStartY;
                traceStartDir = dmStartDir != null ? dmStartDir : debugStartDir;
                desiredIndex = dmAlignedStateIndex;
            }
        } catch (Exception ignored) {
        }

        DebuggingService.DebugTraceResult result =
                DebuggingService.computeTraceFromState(currentLevel, traceStartX, traceStartY, traceStartDir);
        debugTrace = result.trace;
        debugLogLines = result.logLines;

        try {
            if (debugTrace != null && debugTrace.getStates() != null) {
                if (traceStartX != startX || traceStartY != startY) {
                    if (desiredIndex < 0) {
                        desiredIndex = findLastRunningStateIndexAtOrBefore(debugTrace, startX, startY);
                    }
                    if (desiredIndex < 0) {
                        DebuggingService.DebugTraceResult fallback =
                                DebuggingService.computeTraceFromState(currentLevel, startX, startY, debugStartDir);
                        debugTrace = fallback.trace;
                        debugLogLines = fallback.logLines;
                        desiredIndex = 0;
                        debugImmediateFeedbackNote = "Debugger: alignment cell (" + startX + "," + startY
                                + ") not found on recomputed trace; starting from that cell.";
                    }
                }
            }
        } catch (Exception ignored) {
            desiredIndex = 0;
        }

        debugIndex = Math.max(0, Math.min(desiredIndex, (debugTrace != null && debugTrace.getStates() != null) ? debugTrace.getStates().size() - 1 : 0));
        syncCurrentStateSnapshotFromTraceIndex();
        debugPastPrefixPath = new int[0][0];
        debugImmediateFeedbackNote = null;

        return debugFrameJson(currentLevel, astMapper);
    }

    public String debugTogglePause(Level currentLevel, BlockyAstMapper astMapper) {
        if (debugTrace == null) {
            return debugFrameJson(currentLevel, astMapper);
        }
        debugPaused = !debugPaused;
        if (!debugPaused && debugDirtySolution) {
            alignDebugTraceAfterPausedEdits(currentLevel, false);
            debugDirtySolution = false;
        }
        return debugFrameJson(currentLevel, astMapper);
    }

    public String debugStepOnce(Level currentLevel, BlockyAstMapper astMapper) {
        if (currentLevel == null) return "{\"index\":0,\"total\":0}";
        if (debugTrace == null) {
            return debugFrameJson(currentLevel, astMapper);
        }

        if (debugDirtySolution) {
            alignDebugTraceAfterPausedEdits(currentLevel, true);
            debugDirtySolution = false;
            debugPaused = true;
            syncCurrentStateSnapshotFromTraceIndex();
            return debugFrameJson(currentLevel, astMapper);
        }

        int lastIndex = debugTrace.getStates().size() - 1;
        if (debugIndex < lastIndex) {
            debugIndex++;
        }
        debugPaused = true;
        syncCurrentStateSnapshotFromTraceIndex();
        return debugFrameJson(currentLevel, astMapper);
    }

    public String debugStop(Level currentLevel, BlockyAstMapper astMapper) {
        if (currentLevel == null) return "{\"index\":0,\"total\":0}";
        if (debugTrace == null) return debugFrameJson(currentLevel, astMapper);

        debugPaused = true;
        debugCurrentX = debugStartX;
        debugCurrentY = debugStartY;
        debugCurrentDir = debugStartDir;
        debugIndex = 0;

        if (debugDirtySolution) {
            recomputeDebugTraceFromCurrentState(currentLevel);
            debugDirtySolution = false;
        }
        debugSessionActive = false;

        return debugFrameJson(currentLevel, astMapper);
    }

    public String debugSkipToEnd(Level currentLevel, BlockyAstMapper astMapper) {
        if (debugTrace == null || debugTrace.getStates() == null || debugTrace.getStates().isEmpty()) {
            return debugFrameJson(currentLevel, astMapper);
        }
        debugIndex = debugTrace.getStates().size() - 1;
        debugPaused = true;
        syncCurrentStateSnapshotFromTraceIndex();
        return debugFrameJson(currentLevel, astMapper);
    }

    public String debugTick(Level currentLevel, BlockyAstMapper astMapper) {
        if (debugTrace == null) return debugFrameJson(currentLevel, astMapper);
        if (debugPaused) return debugFrameJson(currentLevel, astMapper);

        int lastIndex = debugTrace.getStates().size() - 1;
        if (debugIndex < lastIndex) {
            debugIndex++;
            syncCurrentStateSnapshotFromTraceIndex();
        } else {
            debugPaused = true;
        }

        if (debugDirtySolution) {
            alignDebugTraceAfterPausedEdits(currentLevel, false);
            debugDirtySolution = false;
            debugPaused = true;
        }

        return debugFrameJson(currentLevel, astMapper);
    }

    private void alignDebugTraceAfterPausedEdits(Level currentLevel, boolean advanceOneStepAfterAlign) {
        if (currentLevel == null) return;
        if (debugTrace == null || debugTrace.getStates() == null || debugTrace.getStates().isEmpty()) return;

        int[][] pastPrefix = compressTracePositions(debugTrace, debugIndex);
        debugPastPrefixPath = pastPrefix != null ? pastPrefix : new int[0][0];

        DebuggingService.DebugTraceResult result =
                DebuggingService.computeTraceFromState(currentLevel, debugStartX, debugStartY, debugStartDir);
        ExecutionTrace newTrace = result.trace;
        if (newTrace == null || newTrace.getStates() == null || newTrace.getStates().isEmpty()) {
            return;
        }

        int[][] newFull = compressTracePositions(newTrace, newTrace.getStates().size() - 1);
        int commonLen = longestCommonPrefixLenByXY(debugPastPrefixPath, newFull);
        if (commonLen <= 0 && newFull != null && newFull.length > 0) {
            commonLen = 1;
        }

        int mappedIndex = -1;
        int commonX = debugStartX;
        int commonY = debugStartY;
        if (commonLen > 0 && newFull != null && newFull.length >= commonLen) {
            for (int i = commonLen - 1; i >= 0; i--) {
                int[] pt = newFull[i];
                if (pt != null && pt.length >= 2) {
                    int idx = findLastRunningStateIndexAtOrBefore(newTrace, pt[0], pt[1]);
                    if (idx >= 0) {
                        commonX = pt[0];
                        commonY = pt[1];
                        mappedIndex = idx;
                        break;
                    }
                }
            }
        }

        if (mappedIndex < 0) {
            mappedIndex = 0;
            commonX = debugStartX;
            commonY = debugStartY;
        }

        debugTrace = newTrace;
        debugLogLines = result.logLines;
        debugIndex = Math.max(0, Math.min(mappedIndex, newTrace.getStates().size() - 1));
        syncCurrentStateSnapshotFromTraceIndex();

        debugImmediateFeedbackNote = "Immediate Feedback: program changed -> aligned to last common cell ("
                + commonX + "," + commonY + "), commonPrefixLen=" + commonLen
                + (advanceOneStepAfterAlign ? " (then stepped once)" : "");

        if (advanceOneStepAfterAlign) {
            int lastIndex = debugTrace.getStates().size() - 1;
            if (debugIndex < lastIndex) {
                debugIndex++;
                syncCurrentStateSnapshotFromTraceIndex();
            }
        }
    }

    private void recomputeDebugTraceFromCurrentState(Level currentLevel) {
        if (currentLevel == null) return;
        Direction dir = debugCurrentDir != null ? debugCurrentDir : Direction.EAST;
        DebuggingService.DebugTraceResult result =
                DebuggingService.computeTraceFromState(currentLevel, debugCurrentX, debugCurrentY, dir);
        debugTrace = result.trace;
        debugLogLines = result.logLines;
        debugIndex = 0;
    }

    private void syncCurrentStateSnapshotFromTraceIndex() {
        if (debugTrace == null || debugTrace.getStates() == null || debugTrace.getStates().isEmpty()) return;
        int safeIndex = Math.max(0, Math.min(debugIndex, debugTrace.getStates().size() - 1));
        GameState s = debugTrace.getStates().get(safeIndex);
        Cell pos = s != null ? s.getPosition() : null;
        if (pos != null) {
            debugCurrentX = pos.getX();
            debugCurrentY = pos.getY();
        }
        debugCurrentDir = s != null ? s.getOrientation() : debugCurrentDir;
    }

    public String debugFrameJson(Level currentLevel, BlockyAstMapper astMapper) {
        if (currentLevel == null || debugTrace == null || debugTrace.getStates() == null) {
            int q = debugCurrentX;
            int r = debugCurrentY;
            int t = DebuggingService.directionToT(debugCurrentDir);
            try {
                if (currentLevel != null && currentLevel.getMap() != null) {
                    Cell start = null;
                    for (Cell c : currentLevel.getMap().getCells()) {
                        if (c.getType() == CellType.START) {
                            start = c;
                            break;
                        }
                    }
                    if (start != null) {
                        q = start.getX();
                        r = start.getY();
                    }
                    Direction dir = (currentLevel.eIsSet(BlockyPackage.Literals.LEVEL__START_ORIENTATION))
                            ? currentLevel.getStartOrientation()
                            : null;
                    if (dir != null) {
                        t = DebuggingService.directionToT(dir);
                    }
                }
            } catch (Exception ignored) {
            }
            return "{\"index\":0"
                    + ",\"total\":0"
                    + ",\"q\":" + q
                    + ",\"s\":" + r
                    + ",\"t\":" + t
                    + ",\"prefix\":[]"
                    + ",\"pastPrefix\":[]"
                    + ",\"newPreview\":[]"
                    + ",\"common\":0"
                    + ",\"paused\":true"
                    + ",\"dirty\":false"
                    + ",\"blockId\":\"\""
                    + ",\"result\":\"RUNNING\""
                    + ",\"logLine\":\"\""
                    + ",\"note\":\"\""
                    + "}";
        }
        List<GameState> states = debugTrace.getStates();
        int total = states.size();
        int safeIndex = Math.max(0, Math.min(debugIndex, total - 1));
        GameState s = states.get(safeIndex);
        Cell pos = s != null ? s.getPosition() : null;
        Direction dir = s != null ? s.getOrientation() : debugCurrentDir;
        int q = pos != null ? pos.getX() : debugCurrentX;
        int r = pos != null ? pos.getY() : debugCurrentY;
        int t = DebuggingService.directionToT(dir);

        int[][] prefixPathArr = compressTracePositions(debugTrace, safeIndex);
        int prefixLen = prefixPathArr.length;
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < prefixLen; i++) {
            if (i > 0) sb.append(",");
            sb.append("[").append(prefixPathArr[i][0]).append(",").append(prefixPathArr[i][1]).append("]");
        }
        sb.append("]");

        int[][] pastPrefixPath = (debugPastPrefixPath != null) ? debugPastPrefixPath : new int[0][0];
        int[][] newPreviewPath = compressTracePositions(debugTrace, debugTrace.getStates().size() - 1);
        int commonLen = prefixLen;

        String result = "RUNNING";
        if (safeIndex >= total - 1) {
            GameStatus st = s != null ? s.getStatus() : null;
            if (st == GameStatus.WON) {
                result = "GOAL";
            } else if (st == GameStatus.CRASHED) {
                int loopBound = 0;
                if (currentLevel.getMap() != null) {
                    loopBound = currentLevel.getMap().getWidth() * currentLevel.getMap().getHeight() * 2;
                }
                result = (s != null && loopBound > 0 && s.getStep() >= loopBound) ? "INFINITE_LOOP" : "CRASH";
            }
        }

        String logLine = null;
        if (debugLogLines != null && safeIndex >= 0 && safeIndex < debugLogLines.size()) {
            logLine = debugLogLines.get(safeIndex);
        }
        if (logLine == null) {
            logLine = "Step " + (s != null ? s.getStep() : safeIndex) + ": (no log)";
        }
        logLine = escapeJsonString(logLine);

        String logPrefix = "[]";
        String logFromAlign = "[]";
        try {
            if (debugLogLines != null && !debugLogLines.isEmpty()) {
                int upto = Math.max(0, Math.min(safeIndex, debugLogLines.size() - 1));
                StringBuilder lp = new StringBuilder();
                lp.append("[");
                for (int i = 0; i <= upto; i++) {
                    if (i > 0) lp.append(",");
                    lp.append("\"").append(escapeJsonString(debugLogLines.get(i))).append("\"");
                }
                lp.append("]");
                logPrefix = lp.toString();

                if (dmAlignedValid && dmAlignedStateIndex >= 0 && dmAlignedStateIndex <= upto) {
                    StringBuilder la = new StringBuilder();
                    la.append("[");
                    for (int i = dmAlignedStateIndex; i <= upto; i++) {
                        if (i > dmAlignedStateIndex) la.append(",");
                        la.append("\"").append(escapeJsonString(debugLogLines.get(i))).append("\"");
                    }
                    la.append("]");
                    logFromAlign = la.toString();
                }
            }
        } catch (Exception ignored) {
            logPrefix = "[]";
            logFromAlign = "[]";
        }

        String blockId = "";
        try {
            Statement exec = null;
            if (states != null && safeIndex < total - 1) {
                GameState nextState = states.get(Math.min(safeIndex + 1, total - 1));
                exec = nextState != null ? nextState.getExecutingStatement() : null;
            } else {
                exec = (s != null) ? s.getExecutingStatement() : null;
            }
            String mapped = (astMapper != null && exec != null) ? astMapper.getBlocklyId(exec) : null;
            blockId = mapped != null ? mapped : "";
        } catch (Exception ignored) {
            blockId = "";
        }
        blockId = escapeJsonString(blockId);

        String note = debugImmediateFeedbackNote != null ? debugImmediateFeedbackNote : "";
        if (note != null && !note.isEmpty()) {
            debugImmediateFeedbackNote = null;
        }
        note = escapeJsonString(note);

        return "{\"index\":" + safeIndex
                + ",\"total\":" + total
                + ",\"q\":" + q
                + ",\"s\":" + r
                + ",\"t\":" + t
                + ",\"prefix\":" + sb.toString()
                + ",\"pastPrefix\":" + ImmediateFeedbackService.toJsonArray(pastPrefixPath)
                + ",\"newPreview\":" + ImmediateFeedbackService.toJsonArray(newPreviewPath)
                + ",\"common\":" + commonLen
                + ",\"paused\":" + debugPaused
                + ",\"dirty\":" + debugDirtySolution
                + ",\"blockId\":\"" + blockId + "\""
                + ",\"result\":\"" + result + "\""
                + ",\"logLine\":\"" + logLine + "\""
                + ",\"logPrefix\":" + logPrefix
                + ",\"logFromAlign\":" + logFromAlign
                + ",\"note\":\"" + note + "\""
                + "}";
    }

    private static int longestCommonPrefixLenByXY(int[][] a, int[][] b) {
        if (a == null || b == null) return 0;
        int n = Math.min(a.length, b.length);
        int i = 0;
        for (; i < n; i++) {
            int[] pa = a[i];
            int[] pb = b[i];
            if (pa == null || pb == null || pa.length < 2 || pb.length < 2) break;
            if (pa[0] != pb[0] || pa[1] != pb[1]) break;
        }
        return i;
    }

    private static int findLastRunningStateIndexAtOrBefore(ExecutionTrace trace, int x, int y) {
        if (trace == null || trace.getStates() == null) return -1;
        int best = -1;
        List<GameState> states = trace.getStates();
        for (int i = 0; i < states.size(); i++) {
            GameState s = states.get(i);
            if (s == null) continue;
            Cell pos = s.getPosition();
            if (pos == null) continue;
            if (pos.getX() == x && pos.getY() == y && s.getStatus() == GameStatus.RUNNING) {
                best = i;
            }
        }
        return best;
    }

    private static int[][] compressTracePositions(ExecutionTrace trace, int uptoIndexInclusive) {
        if (trace == null || trace.getStates() == null || trace.getStates().isEmpty()) return new int[0][0];
        List<GameState> states = trace.getStates();
        int safeUpto = Math.max(0, Math.min(uptoIndexInclusive, states.size() - 1));
        List<int[]> pts = new ArrayList<>();
        Integer lastX = null;
        Integer lastY = null;
        for (int i = 0; i <= safeUpto; i++) {
            GameState s = states.get(i);
            Cell pos = s != null ? s.getPosition() : null;
            if (pos == null) continue;
            int x = pos.getX();
            int y = pos.getY();
            if (lastX != null && lastX == x && lastY == y) continue;
            pts.add(new int[] { x, y });
            lastX = x;
            lastY = y;
        }
        int[][] arr = new int[pts.size()][2];
        for (int i = 0; i < pts.size(); i++) {
            int[] p = pts.get(i);
            arr[i][0] = p[0];
            arr[i][1] = p[1];
        }
        return arr;
    }

    private static String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
