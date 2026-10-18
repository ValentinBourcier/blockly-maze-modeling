package blocky_game;

import blocky.AtomicStatement;
import blocky.AtomicStatementKind;
import blocky.BlockyFactory;
import blocky.BlockyPackage;
import blocky.Body;
import blocky.Cell;
import blocky.CellType;
import blocky.ConditionKind;
import blocky.Container;
import blocky.Direction;
import blocky.ExecutionTrace;
import blocky.GameState;
import blocky.GameStatus;
import blocky.GridMap;
import blocky.IfStmt;
import blocky.Level;
import blocky.Loop;
import blocky.SensorDirection;
import blocky.Statement;
import blocky_momot.SimulationEngine;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes and renders “immediate feedback” overlays for old vs new paths.
 *
 * Rule: all immediate-feedback computation and UI overlay code lives here.
 */
public final class ImmediateFeedbackService {
    private ImmediateFeedbackService() {}

    public static final int TILE_SIZE_PX = 50;
    public static final int TILE_CENTER_OFFSET_PX = 25;

    public static final String PAST_OVERLAY_ID = "pathPast";
    public static final String NEW_OVERLAY_ID = "pathNew";

    public static final String PAST_COLOR = "#ff4d4d";
    public static final String NEW_COLOR = "#3bd46a";

    public static final class Paths {
        public final int[][] pastPath;
        public final int[][] newPath;

        private Paths(int[][] pastPath, int[][] newPath) {
            this.pastPath = pastPath;
            this.newPath = newPath;
        }
    }

    public static Paths computePaths(Level level) {
        if (level == null || level.getMap() == null) {
            return new Paths(new int[0][0], new int[0][0]);
        }

        GridMap map = level.getMap();
        Cell startCell = findStartCell(map);
        final CellType winCellType = SimUtils.determineWinCellType(level);
        if (startCell == null) {
            int[][] pastPath = extractPathPoints(firstTrace(level));
            return new Paths(pastPath, new int[0][0]);
        }
        Direction startDir = SimUtils.determineStartOrientation(level, startCell);
        level.setStartOrientation(startDir);

        int[][] pastPath = extractPathPoints(firstTrace(level));
        if (pastPath.length == 0 && startCell != null) {
            pastPath = new int[][] { new int[] { startCell.getX(), startCell.getY() } };
        }

        int[][] newPath;
        boolean enforceConstraints = Boolean.parseBoolean(System.getProperty("blocky.sim.enforceConstraints", "false"));
        if (level.getSolution() == null || (enforceConstraints && SimUtils.violatesLevelConstraints(level, level.getSolution()))) {
            newPath = new int[][] { new int[] { startCell.getX(), startCell.getY() } };
        } else {
            ExecutionTrace newTrace = simulateExecutionTraceBody(level.getSolution(), map, startCell, startDir, winCellType);
            newPath = extractPathPoints(newTrace);
            if (newPath.length == 0 && startCell != null) {
                newPath = new int[][] { new int[] { startCell.getX(), startCell.getY() } };
            }
        }

        return new Paths(pastPath, newPath);
    }

    public static String toJsonArray(int[][] path) {
        if (path == null || path.length == 0) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < path.length; i++) {
            int[] pt = path[i];
            if (i > 0) sb.append(",");
            if (pt == null || pt.length < 2) {
                sb.append("[0,0]");
            } else {
                sb.append("[").append(pt[0]).append(",").append(pt[1]).append("]");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public static String buildWindowInjectPathsScript(int[][] pastPath, int[][] newPath) {
        return "window.__injectPastPath = " + toJsonArray(pastPath) + ";"
                + "window.__injectNewPath = " + toJsonArray(newPath) + ";";
    }

    /**
     * JavaScript snippet that draws both overlays as SVG polylines + circles.
     * Must be inserted into the WebView initialization script after `Wd()` and `$d(false)`.
     */
    public static String buildOverlayRenderJs() {
        return ""
                + "  try { "
                + "    var ns = 'http://www.w3.org/2000/svg'; "
                + "    function drawOverlay(id, path, color, offset, dash) { "
                + "      var svg = document.getElementById('svgMaze'); "
                + "      var old = document.getElementById(id); "
                + "      if (!path || !path.length || !svg) { "
                + "        if (old && old.parentNode) old.parentNode.removeChild(old); "
                + "        return; "
                + "      } "
                + "      var off = (typeof offset === 'number') ? offset : 25; "
                + "      var pts = []; "
                + "      for (var i=0; i<path.length; i++) { "
                + "        pts.push((50*path[i][0]+off) + ',' + (50*path[i][1]+off)); "
                + "      } "
                + "      var ptsString = (path.length >= 2) ? pts.join(' ') : ''; "
                + "      if (old) { "
                + "        var existingKey = old.getAttribute('data-path-points'); "
                + "        var existingPoly = old.querySelector('polyline'); "
                + "        var polyMatches = (path.length >= 2) ? (existingPoly && existingPoly.getAttribute('points') === ptsString) : !existingPoly; "
                + "        if (existingKey === ptsString && polyMatches) return; "
                + "        if (old.parentNode) old.parentNode.removeChild(old); "
                + "      } "
                + "      var g = document.createElementNS(ns, 'g'); "
                + "      g.setAttribute('id', id); "
                + "      g.setAttribute('data-immediate-feedback', 'true'); "
                + "      g.setAttribute('data-path-points', ptsString); "
                + "      if (path.length >= 2) { "
                + "        var poly = document.createElementNS(ns, 'polyline'); "
                + "        poly.setAttribute('points', ptsString); "
                + "        poly.setAttribute('fill', 'none'); "
                + "        poly.setAttribute('stroke', color); "
                + "        poly.setAttribute('stroke-width', '7'); "
                + "        poly.setAttribute('stroke-linecap', 'round'); "
                + "        poly.setAttribute('stroke-linejoin', 'round'); "
                + "        poly.setAttribute('stroke-opacity', '0.9'); "
                + "        if (dash) poly.setAttribute('stroke-dasharray', dash); "
                + "        g.appendChild(poly); "
                + "      } "
                + "      for (var j=0; j<path.length; j++) { "
                + "        var cx = (50*path[j][0]+off); "
                + "        var cy = (50*path[j][1]+off); "
                + "        var c = document.createElementNS(ns, 'circle'); "
                + "        c.setAttribute('cx', cx); "
                + "        c.setAttribute('cy', cy); "
                + "        c.setAttribute('r', '4'); "
                + "        c.setAttribute('fill', color); "
                + "        c.setAttribute('fill-opacity', '0.75'); "
                + "        g.appendChild(c); "
                + "      } "
                + "      var peg = document.getElementById('pegman'); "
                + "      if (peg && peg.parentNode) svg.insertBefore(g, peg); else svg.appendChild(g); "
                + "    } "
                + "    function commonPrefixLen(a, b) { "
                + "      try { "
                + "        if (!a || !b) return 0; "
                + "        var n = Math.min(a.length || 0, b.length || 0); "
                + "        var i = 0; "
                + "        for (; i < n; i++) { "
                + "          var pa = a[i], pb = b[i]; "
                + "          if (!pa || !pb || pa.length < 2 || pb.length < 2) break; "
                + "          if (pa[0] !== pb[0] || pa[1] !== pb[1]) break; "
                + "        } "
                + "        return i; "
                + "      } catch(e) { return 0; } "
                + "    } "
                + "    window.__ifRender = function() { "
                + "      try { "
                + "        var rawStep = (typeof window.__ifCurrentStep === 'number') ? window.__ifCurrentStep : 0; "
                + "        var step = Math.max(1, rawStep); "
                + "        if (window.__injectDmEnabled && window.__injectDmSolutionPath) { "
                + "          var sol  = window.__injectDmSolutionPath || []; "
                + "          var solRem  = (sol.length >= step) ? sol.slice(step - 1) : []; "
                + "          drawOverlay('dmSol', solRem, '#a020f0', 30, '12 6 2 6'); "
                + "          var ob = document.getElementById('dmBase'); if (ob && ob.parentNode) ob.parentNode.removeChild(ob); "
                + "          var oc = document.getElementById('dmCommon'); if (oc && oc.parentNode) oc.parentNode.removeChild(oc); "
                + "        } else { "
                + "          var next = window.__injectNewPath || []; "
                + "          var predictedFull = (next.length >= step) ? next.slice(step - 1) : []; "
                + "          drawOverlay('ifNew', predictedFull, '#ff8c1a', 25, '10 6'); "
                + "          var op = document.getElementById('ifPast'); if (op && op.parentNode) op.parentNode.removeChild(op); "
                + "          var oc2 = document.getElementById('ifCommon'); if (oc2 && oc2.parentNode) oc2.parentNode.removeChild(oc2); "
                + "        } "
                + "      } catch(e) { console.log('__ifRender error: ' + e); } "
                + "    }; "
                + "    window.__ifRender(); "
                + "  } catch(e) { if (window.javaBridge) window.javaBridge.logJS('ImmediateFeedback definition error: ' + e); }";
    }

    private static ExecutionTrace firstTrace(Level level) {
        if (level.getTraces() == null || level.getTraces().isEmpty()) return null;
        return level.getTraces().get(0);
    }

    private static Cell findStartCell(GridMap map) {
        if (map == null || map.getCells() == null || map.getCells().isEmpty()) return null;
        for (Cell c : map.getCells()) {
            if (c.getType() == CellType.START) return c;
        }
        // Fallback to first cell to keep UI stable.
        return map.getCells().get(0);
    }

    private static int[][] extractPathPoints(ExecutionTrace trace) {
        if (trace == null || trace.getStates() == null || trace.getStates().isEmpty()) return new int[0][0];

        List<int[]> points = new ArrayList<>();
        Integer lastX = null;
        Integer lastY = null;

        for (GameState s : trace.getStates()) {
            if (s == null) continue;
            Cell pos = s.getPosition();
            if (pos == null) continue;
            int x = pos.getX();
            int y = pos.getY();
            if (lastX != null && lastX == x && lastY == y) continue; // compress duplicates (turn steps)
            points.add(new int[] { x, y });
            lastX = x;
            lastY = y;
        }

        if (points.isEmpty()) return new int[0][0];

        int[][] arr = new int[points.size()][2];
        for (int i = 0; i < points.size(); i++) {
            int[] pt = points.get(i);
            arr[i][0] = pt[0];
            arr[i][1] = pt[1];
        }
        return arr;
    }

    private static ExecutionTrace simulateExecutionTraceBody(Body solution, GridMap map, Cell startCell, Direction startDir, CellType winCellType) {
        return SimulationEngine.simulate(map, startCell, startDir, solution, winCellType).getTrace();
    }
}
