package blocky_game;

import blocky.AtomicStatement;
import blocky.AtomicStatementKind;
import blocky.BlockyPackage;
import blocky.Body;
import blocky.ConditionKind;
import blocky.Container;
import blocky.Game;
import blocky.GameStatus;
import blocky.IfStmt;
import blocky.Level;
import blocky.Loop;
import blocky.Statement;
import blocky_momot.BlockyProgramMetrics;
import blocky_momot.BlockySimulator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Replays the programs of a session report and tells whether each one reaches the goal.
 *
 * Input: the JSON of the admin page ("Download JSON" of one session, or the all-sessions export),
 * a session folder {@code level_sessions/<id>/} with {@code events.jsonl} and {@code models/}, or a folder of these
 * (such as {@code level_sessions/}), which reads every session folder and {@code *.json} file directly inside it.
 * Every event with a Blockly XML program ({@code program_run} and {@code candidate_exploration}) is rebuilt
 * with the game's own code ({@link BlocklyXmlParser}, {@link BlockyAstMapper}) on the clean level
 * {@code N.xmi} and run with {@link BlockySimulator}, the simulator behind the search objectives. Power (kW) is
 * the Actions objective and memory (kB) the Blocks objective, computed as in the game's consumption readout
 * ({@link DirectManipulationHttpHandler}).
 *
 * Usage: ReportGoalChecker &lt;report.json | session folder | folder of reports&gt; [--levels &lt;dir with N.xmi&gt;] [--csv &lt;out.csv&gt;]
 */
public final class ReportGoalChecker {

    private static final class Event {
        String session, timestamp, type, variant, modelName, objectiveLine, xml;
        int level;
        long iterationMs = -1;  // active time before this event (the admin page's "Iteration (active)"), -1 if not recorded
        int reportedRuns = -1;  // the level's programRuns from the report's "levels", -1 if the input has none
        // results
        String status = "", note = "", program = "", matchesLoadedCandidate = "";
        int actions = -1, blocks = -1;
    }

    public static void main(String[] args) throws Exception {
        String input = null, levelsDir = null, csv = null;
        for (int i = 0; i < args.length; i++) {
            if ("--levels".equals(args[i]) && i + 1 < args.length) levelsDir = args[++i];
            else if ("--csv".equals(args[i]) && i + 1 < args.length) csv = args[++i];
            else input = args[i];
        }
        if (input == null) {
            System.err.println("Usage: ReportGoalChecker <report.json | session folder | folder of reports> [--levels <dir>] [--csv <out.csv>]");
            System.exit(2);
        }
        File levels = findLevelsDir(levelsDir);
        List<Event> events = readEvents(new File(input));

        Map<Integer, Level> levelCache = new HashMap<>();
        for (Event ev : events) {
            if (ev.xml == null || ev.xml.trim().isEmpty()) {
                ev.note = "no program recorded";
                continue;
            }
            try {
                Level level = levelCache.get(ev.level);
                if (level == null) {
                    level = loadLevel(new File(levels, ev.level + ".xmi"));
                    levelCache.put(ev.level, level);
                }
                List<Map<String, Object>> blocks = BlocklyXmlParser.parseBlocklyXml(ev.xml);
                List<String> stacks = new ArrayList<>();
                for (Map<String, Object> stack : blocks) stacks.add(programText(new BlockyAstMapper().rebuildProgram(List.of(stack))));
                ev.program = String.join(" + ", stacks);
                Body body = new BlockyAstMapper().rebuildProgram(blocks);
                level.setSolution(body);
                GameStatus status = BlockySimulator.run(level);
                ev.status = String.valueOf(status);
                ev.actions = BlockySimulator.simulationSteps(level);
                ev.blocks = BlockyProgramMetrics.countStatements(body);
                if (blocks.size() > 1) ev.note = blocks.size() + " unconnected block stacks, run one after another";
            } catch (Exception e) {
                ev.status = "ERROR";
                ev.note = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }

        markLoadedCandidates(events);
        print(events);
        if (csv != null) writeCsv(events, new File(csv));
    }

    // ---- program text ----

    /**
     * The program in the notation of LandscapeAnalysis, one character per block: F/L/R = move forward / turn left /
     * turn right, W(..) = repeat until goal, a(then|else) l(..|..) r(..|..) = if path ahead / left / right.
     * A missing action kind is written L and a missing condition a, as BlockySimulator runs them.
     */
    static String programText(Body body) {
        StringBuilder b = new StringBuilder();
        appendBody(body, b);
        return b.toString();
    }

    private static void appendBody(Body body, StringBuilder b) {
        if (body == null) return;
        for (Container c = body.getFirstContainer(); c != null; c = c.getNext()) {
            Statement s = c.getStatement();
            if (s instanceof AtomicStatement) {
                AtomicStatementKind kind = ((AtomicStatement) s).getKind();
                b.append(kind == AtomicStatementKind.MOVE_FORWARD ? 'F' : kind == AtomicStatementKind.TURN_RIGHT ? 'R' : 'L');
            } else if (s instanceof Loop) {
                b.append("W(");
                appendBody(((Loop) s).getBody(), b);
                b.append(')');
            } else if (s instanceof IfStmt) {
                IfStmt i = (IfStmt) s;
                b.append(i.getCondition() == ConditionKind.CHECK_LEFT ? 'l' : i.getCondition() == ConditionKind.CHECK_RIGHT ? 'r' : 'a');
                b.append('(');
                appendBody(i.getThenBody(), b);
                b.append('|');
                appendBody(i.getElseBody(), b);
                b.append(')');
            } else if (s != null) {
                b.append('?');
            }
        }
    }

    /**
     * A program_run whose program text equals that of a candidate loaded earlier in the same session and level gets
     * the CSV index of that candidate (the most recent such load), otherwise stays empty. This shows that the run is a
     * loaded candidate, not that the participant took it from there.
     */
    private static void markLoadedCandidates(List<Event> events) {
        Map<String, Map<String, Integer>> loaded = new HashMap<>();  // level key -> program text -> CSV index of the load
        for (int i = 0; i < events.size(); i++) {
            Event ev = events.get(i);
            if (ev.program.isEmpty()) continue;
            if ("candidate_exploration".equals(ev.type)) {
                loaded.computeIfAbsent(levelKey(ev), k -> new HashMap<>()).put(ev.program, i + 1);
            } else if ("program_run".equals(ev.type)) {
                Integer index = loaded.getOrDefault(levelKey(ev), Map.of()).get(ev.program);
                ev.matchesLoadedCandidate = index == null ? "" : String.valueOf(index);
            }
        }
    }

    // ---- output ----

    private static final class LevelStats {
        int runs;               // the admin page's "Runs"
        long activeAvgMs = -1;  // the admin page's "Active iter. avg", -1 if no run recorded its active time
    }

    private static String levelKey(Event ev) {
        return ev.session + "\u0000" + ev.level;
    }

    /**
     * Per session and level, as the admin page computes them: runs is the report's programRuns (counted from the
     * program_run events when the input has no "levels"), the active average is the rounded mean of iterationMs over the
     * program_run events ({@link TelemetryService#levelIterations}, {@link TelemetryService#meanOf}).
     */
    private static Map<String, LevelStats> levelStats(List<Event> events) {
        Map<String, LevelStats> stats = new HashMap<>();
        Map<String, long[]> counts = new HashMap<>();  // program_run events, reported runs, iteration sum, iteration count
        for (Event ev : events) {
            long[] c = counts.computeIfAbsent(levelKey(ev), k -> new long[] { 0, -1, 0, 0 });
            if (ev.reportedRuns >= 0) c[1] = ev.reportedRuns;
            if (!"program_run".equals(ev.type)) continue;
            c[0]++;
            if (ev.iterationMs >= 0) {
                c[2] += ev.iterationMs;
                c[3]++;
            }
        }
        for (Map.Entry<String, long[]> e : counts.entrySet()) {
            long[] c = e.getValue();
            LevelStats s = new LevelStats();
            s.runs = (int) (c[1] >= 0 ? c[1] : c[0]);
            if (c[3] > 0) s.activeAvgMs = Math.round((double) c[2] / c[3]);
            stats.put(e.getKey(), s);
        }
        return stats;
    }

    private static String seconds(long ms) {
        return ms < 0 ? "" : String.format(Locale.ROOT, "%.1f", ms / 1000.0);
    }

    private static void print(List<Event> events) {
        String session = null;
        for (int i = 0; i < events.size(); i++) {
            Event ev = events.get(i);
            if (!ev.session.equals(session)) {
                session = ev.session;
                System.out.println();
                System.out.println("Session " + session);
                System.out.printf("  %-4s %-5s %-21s %-8s %-30s %-5s %-8s %9s %10s  %s%n",
                        "#", "Level", "Event", "Variant", "Time", "Goal", "Status", "Power(kW)", "Memory(kB)", "Objectives / note");
            }
            String goal = ev.status.isEmpty() ? "-" : "WON".equals(ev.status) ? "yes" : "no";
            String extra = ev.objectiveLine != null && !ev.objectiveLine.isEmpty() ? "[" + ev.objectiveLine + "] " : "";
            System.out.printf("  %-4d %-5d %-21s %-8s %-30s %-5s %-8s %9s %10s  %s%n",
                    i + 1, ev.level, ev.type, ev.variant, ev.timestamp, goal, ev.status,
                    ev.actions >= 0 ? String.valueOf(ev.actions) : "-", ev.blocks >= 0 ? String.valueOf(ev.blocks) : "-",
                    extra + ev.note);
        }

        // per session and level: how many programs were checked and how many reach the goal
        System.out.println();
        System.out.println("Summary (programs that reach the goal / programs checked)");
        System.out.printf("  %-36s %-5s %-5s %-15s %-14s %-24s %s%n", "Session", "Level", "Runs", "Active avg (s)",
                "program_run", "candidate_exploration", "lowest power (kW) among goal-reaching");
        Map<String, LevelStats> stats = levelStats(events);
        Map<String, int[]> sums = new LinkedHashMap<>();
        for (Event ev : events) {
            if (ev.status.isEmpty()) continue;
            int[] s = sums.computeIfAbsent(levelKey(ev), k -> new int[] { 0, 0, 0, 0, Integer.MAX_VALUE });
            boolean won = "WON".equals(ev.status);
            boolean run = "program_run".equals(ev.type);
            s[run ? 0 : 2] += won ? 1 : 0;
            s[run ? 1 : 3] += 1;
            if (won) s[4] = Math.min(s[4], ev.actions);
        }
        for (Map.Entry<String, int[]> e : sums.entrySet()) {
            String[] key = e.getKey().split("\u0000");
            int[] s = e.getValue();
            LevelStats ls = stats.get(e.getKey());
            System.out.printf("  %-36s %-5s %-5d %-15s %-14s %-24s %s%n", key[0], key[1], ls.runs,
                    ls.activeAvgMs < 0 ? "-" : seconds(ls.activeAvgMs), s[0] + " / " + s[1], s[2] + " / " + s[3],
                    s[4] == Integer.MAX_VALUE ? "-" : String.valueOf(s[4]));
        }
    }

    private static void writeCsv(List<Event> events, File out) throws IOException {
        try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8)) {
            w.println("session,index,level,type,variant,timestamp,modelName,objectiveLine,program,matchesLoadedCandidate,"
                    + "reachesGoal,status,power_kW_actions,memory_kB_blocks,level_runs,level_active_avg_s,note");
            Map<String, LevelStats> stats = levelStats(events);
            for (int i = 0; i < events.size(); i++) {
                Event ev = events.get(i);
                LevelStats ls = stats.get(levelKey(ev));
                w.println(String.join(",", csvCell(ev.session), String.valueOf(i + 1), String.valueOf(ev.level), csvCell(ev.type),
                        csvCell(ev.variant), csvCell(ev.timestamp), csvCell(ev.modelName), csvCell(ev.objectiveLine),
                        csvCell(ev.program), ev.matchesLoadedCandidate, ev.status.isEmpty() ? "" : String.valueOf("WON".equals(ev.status)), ev.status,
                        ev.actions >= 0 ? String.valueOf(ev.actions) : "", ev.blocks >= 0 ? String.valueOf(ev.blocks) : "",
                        String.valueOf(ls.runs), seconds(ls.activeAvgMs), csvCell(ev.note)));
            }
        }
        System.out.println();
        System.out.println("CSV written to " + out.getAbsolutePath());
    }

    private static String csvCell(String s) {
        if (s == null) return "";
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    // ---- input ----

    private static List<Event> readEvents(File input) throws IOException {
        if (input.isDirectory() && !isSessionFolder(input)) return readReportFolder(input);
        List<Event> events = input.isDirectory() ? readSessionFolder(input) : readReportJson(input);
        if (events.isEmpty() && input.isFile()) {
            System.err.println("No events found in " + input + ". The JSON needs \"events\" (the admin page's session JSON or "
                    + "the all-sessions export); the timing report from the Finish task dialog has no programs.");
        }
        return events;
    }

    private static boolean isSessionFolder(File dir) {
        return new File(dir, "events.jsonl").isFile();
    }

    /**
     * A folder of reports, such as {@code level_sessions/}: every session folder and every {@code *.json} file directly
     * inside it. JSON files without events (timing reports) are skipped, and a session found in several places is read
     * once, from the first place (session folders first, then JSON files, each in name order).
     */
    private static List<Event> readReportFolder(File folder) throws IOException {
        File[] children = folder.listFiles();
        if (children == null) throw new IOException("cannot list " + folder);
        Arrays.sort(children);
        List<File> sources = new ArrayList<>();
        for (File f : children) if (f.isDirectory() && isSessionFolder(f)) sources.add(f);
        for (File f : children) if (f.isFile() && f.getName().toLowerCase().endsWith(".json")) sources.add(f);
        if (sources.isEmpty()) throw new IOException("no session folders (with events.jsonl) or *.json reports in " + folder);

        List<Event> events = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> noEvents = new ArrayList<>(), duplicates = new ArrayList<>();
        int used = 0;
        for (File f : sources) {
            List<Event> fileEvents;
            try {
                fileEvents = f.isDirectory() ? readSessionFolder(f) : readReportJson(f);
            } catch (Exception e) {
                System.err.println("Skipping " + f.getName() + ": " + e.getMessage());
                continue;
            }
            if (fileEvents.isEmpty()) {
                noEvents.add(f.getName());
                continue;
            }
            Set<String> fileSessions = new HashSet<>();
            for (Event ev : fileEvents) fileSessions.add(ev.session);
            boolean added = false;
            for (Event ev : fileEvents) {
                if (seen.contains(ev.session)) continue;
                events.add(ev);
                added = true;
            }
            for (String s : fileSessions) if (!seen.add(s)) duplicates.add(s + " (again in " + f.getName() + ")");
            if (added) used++;
        }
        System.out.println("Read " + used + " of " + sources.size() + " session folders and JSON files in " + folder);
        if (!noEvents.isEmpty()) System.out.println("  skipped " + noEvents.size() + " without events (timing reports or empty sessions)");
        for (String d : duplicates) System.out.println("  skipped session already read: " + d);
        return events;
    }

    @SuppressWarnings("unchecked")
    private static List<Event> readSessionFolder(File input) throws IOException {
        List<Event> events = new ArrayList<>();
        File jsonl = new File(input, "events.jsonl");
        for (String line : Files.readAllLines(jsonl.toPath(), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) continue;
            Map<String, Object> m = (Map<String, Object>) new Json(line).value();
            Event ev = toEvent(input.getName(), m);
            Object modelFile = m.get("modelFile");
            if ((ev.xml == null || ev.xml.isEmpty()) && modelFile instanceof String && !((String) modelFile).isEmpty()) {
                File f = new File(input, (String) modelFile);
                if (f.isFile()) ev.xml = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            }
            events.add(ev);
        }
        return events;
    }

    @SuppressWarnings("unchecked")
    private static List<Event> readReportJson(File input) throws IOException {
        List<Event> events = new ArrayList<>();
        Object root = new Json(new String(Files.readAllBytes(input.toPath()), StandardCharsets.UTF_8)).value();
        List<Object> sessions = root instanceof List ? (List<Object>) root : List.of(root);
        for (Object s : sessions) {
            if (!(s instanceof Map)) continue;
            Map<String, Object> sm = (Map<String, Object>) s;
            String id = sm.get("sessionId") instanceof String ? (String) sm.get("sessionId") : input.getName();
            Object evs = sm.get("events");
            if (!(evs instanceof List)) continue;
            Map<String, Object> levels = sm.get("levels") instanceof Map ? (Map<String, Object>) sm.get("levels") : Map.of();
            for (Object e : (List<Object>) evs) {
                if (!(e instanceof Map)) continue;
                Event ev = toEvent(id, (Map<String, Object>) e);
                Object rec = levels.get(String.valueOf(ev.level));
                if (rec instanceof Map && ((Map<String, Object>) rec).get("programRuns") instanceof Number) {
                    ev.reportedRuns = ((Number) ((Map<String, Object>) rec).get("programRuns")).intValue();
                }
                events.add(ev);
            }
        }
        return events;
    }

    private static Event toEvent(String session, Map<String, Object> m) {
        Event ev = new Event();
        ev.session = session;
        ev.timestamp = str(m.get("timestamp"));
        ev.type = str(m.get("type"));
        ev.variant = str(m.get("variant"));
        ev.modelName = str(m.get("modelName"));
        ev.objectiveLine = str(m.get("objectiveLine"));
        ev.xml = str(m.get("xml"));
        ev.iterationMs = m.get("iterationMs") instanceof Number ? ((Number) m.get("iterationMs")).longValue() : -1;
        Object level = m.get("level");
        ev.level = level instanceof Number ? ((Number) level).intValue() : Integer.parseInt(String.valueOf(level));
        return ev;
    }

    private static String str(Object o) {
        return o == null ? "" : o instanceof Double && (Double) o == Math.rint((Double) o) ? String.valueOf(((Double) o).longValue()) : String.valueOf(o);
    }

    // ---- levels ----

    private static File findLevelsDir(String given) throws IOException {
        String[] candidates = given != null ? new String[] { given }
                : new String[] { "blocky_momot/model/input", "../blocky_momot/model/input", "model/input" };
        for (String c : candidates) {
            File f = new File(c);
            if (new File(f, "1.xmi").isFile()) return f;
        }
        throw new IOException("level files N.xmi not found; pass --levels <dir>");
    }

    private static Level loadLevel(File file) throws IOException {
        if (!file.isFile()) throw new IOException("level file not found: " + file);
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().putIfAbsent("xmi", new XMIResourceFactoryImpl());
        EPackage pkg = BlockyPackage.eINSTANCE;
        EPackage.Registry.INSTANCE.put(pkg.getNsURI(), pkg);
        ResourceSet rs = new ResourceSetImpl();
        rs.getPackageRegistry().put(pkg.getNsURI(), pkg);
        Resource r = rs.getResource(URI.createFileURI(file.getAbsolutePath()), true);
        return ((Game) r.getContents().get(0)).getLevels().get(0);
    }

    // ---- minimal JSON reader (objects, arrays, strings, numbers, true/false/null) ----

    private static final class Json {
        private final String s;
        private int i;

        Json(String s) { this.s = s; }

        Object value() {
            skip();
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(start, i));
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            skip();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                skip();
                String k = string();
                skip();
                i++; // ':'
                m.put(k, value());
                skip();
                if (s.charAt(i++) == '}') return m; // otherwise ','
            }
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++;
            skip();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(value());
                skip();
                if (s.charAt(i++) == ']') return l; // otherwise ','
            }
        }

        private String string() {
            StringBuilder b = new StringBuilder();
            i++; // opening quote
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            }
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }
    }
}
