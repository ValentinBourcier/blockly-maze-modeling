package blocky_momot;

import blocky.AtomicStatement;
import blocky.AtomicStatementKind;
import blocky.BlockyFactory;
import blocky.BlockyPackage;
import blocky.Body;
import blocky.Cell;
import blocky.CellType;
import blocky.ConditionKind;
import blocky.Container;
import blocky.Game;
import blocky.GameStatus;
import blocky.IfStmt;
import blocky.Level;
import blocky.Loop;
import blocky.Statement;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ground-truth search of Ground-Truth-Mission.md (plan 2: is a user program Pareto-optimal in Actions and Blocks).
 *
 * Mode "amin": per level, A_min = the fewest moves plus turns from the start (with its start orientation) to the
 * goal, i.e. the fewest Actions any program can have (an if-check only adds actions, a loop adds none). Computed as a
 * shortest path over (cell, heading). The straight-line program along that route is run with BlockySimulator as a
 * check: it must reach the goal with A_min actions and A_min blocks.
 *
 * Mode "check-sim": checks 1, 2 and 4 of the search (Ground-Truth-Mission.md, step 2 of plan 2). Generates every
 * program the game can build up to a block limit, compares the count with the closed form, runs every program with the
 * fast simulator and with BlockySimulator on each level (both must agree on goal reached and Actions), and checks that
 * no goal-reaching program has fewer Actions than A_min.
 *
 * Usage: GroundTruthEnumerator amin &lt;levels dir with N.xmi&gt; &lt;out.csv&gt; &lt;level&gt;...
 *        GroundTruthEnumerator check-sim &lt;levels dir&gt; &lt;max blocks&gt; &lt;level&gt;...
 *
 * Program text: F/L/R = move forward / turn left / turn right, W(..) = repeat until goal,
 * a(then|else) l(..|..) r(..|..) = if path ahead / left / right (as in ReportGoalChecker and LandscapeAnalysis).
 */
public final class GroundTruthEnumerator {

    // ---- maze (directions as Direction values: 0 north, 1 east, 2 south, 3 west; neighbours in the same order) ----
    private final Level level;
    private final int[][] adj;   // per cell: top, right, bottom, left (index or -1)
    private final boolean[] wall;
    private final boolean[] win;
    private final int startCell;
    private final int startDir;
    private final int maxActions;  // the game's tick limit for loops (SimulationEngine.computeLoopLimit)

    private GroundTruthEnumerator(Level level) {
        this.level = level;
        List<Cell> cells = level.getMap().getCells();
        Map<Cell, Integer> index = new IdentityHashMap<>();
        for (int i = 0; i < cells.size(); i++) index.put(cells.get(i), i);
        CellType winType = BlockySimulator.determineWinCellType(level);
        int n = cells.size();
        adj = new int[n][4];
        wall = new boolean[n];
        win = new boolean[n];
        int start = -1;
        for (int i = 0; i < n; i++) {
            Cell c = cells.get(i);
            Cell[] nb = { c.getTop(), c.getRight(), c.getBottom(), c.getLeft() };
            for (int d = 0; d < 4; d++) adj[i][d] = nb[d] == null ? -1 : index.get(nb[d]);
            wall[i] = c.getType() == CellType.WALL;
            win[i] = c.getType() == winType;
            if (start < 0 && c.getType() == CellType.START) start = i;
        }
        // same start as BlockySimulator.executeLikeGame: the first START cell, else the first cell
        startCell = start < 0 ? 0 : start;
        startDir = BlockySimulator.determineStartOrientation(level, cells.get(startCell)).getValue();
        maxActions = SimulationEngine.computeLoopLimit(level.getMap());
    }

    // ---- A_min ----

    /** Shortest path over (cell, heading), a move or a turn costing 1; returns the straight-line program, or null. */
    private String shortestRouteProgram() {
        int states = adj.length * 4;
        int[] prev = new int[states];
        char[] how = new char[states];
        Arrays.fill(prev, -2);
        int s0 = startCell * 4 + startDir;
        prev[s0] = -1;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(s0);
        while (!queue.isEmpty()) {
            int s = queue.removeFirst();
            int cell = s / 4, dir = s % 4;
            if (win[cell] && s != s0) return path(prev, how, s);
            int fwd = adj[cell][dir];
            int[] next = { fwd >= 0 && !wall[fwd] ? fwd * 4 + dir : -1, cell * 4 + (dir + 3) % 4, cell * 4 + (dir + 1) % 4 };
            char[] step = { 'F', 'L', 'R' };
            for (int k = 0; k < 3; k++) {
                int t = next[k];
                if (t < 0 || prev[t] != -2) continue;
                prev[t] = s;
                how[t] = step[k];
                queue.addLast(t);
            }
        }
        return null;
    }

    private static String path(int[] prev, char[] how, int s) {
        StringBuilder b = new StringBuilder();
        for (; prev[s] >= 0; s = prev[s]) b.append(how[s]);
        return b.reverse().toString();
    }

    private static void amin(File levelsDir, File out, List<Integer> levels) throws IOException {
        out.getAbsoluteFile().getParentFile().mkdirs();
        try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8)) {
            w.println("level,A_min,moves,turns,route_program,simulator_status,simulator_actions,simulator_blocks,check");
            System.out.printf("%-5s %-5s %-5s %-5s %-9s %-9s %-6s %s%n", "Level", "A_min", "Moves", "Turns", "Simulator",
                    "Act/Blk", "Check", "Route program");
            for (int n : levels) {
                Level level = loadLevel(new File(levelsDir, n + ".xmi"));
                String route = new GroundTruthEnumerator(level).shortestRouteProgram();
                if (route == null) {
                    System.out.printf("%-5d goal not reachable%n", n);
                    w.println(n + ",,,,,,,,goal not reachable");
                    continue;
                }
                int moves = (int) route.chars().filter(c -> c == 'F').count();
                Body body = toBody(route);
                level.setSolution(body);
                GameStatus status = BlockySimulator.run(level);
                int actions = BlockySimulator.simulationSteps(level);
                int blocks = BlockyProgramMetrics.countStatements(body);
                boolean ok = status == GameStatus.WON && actions == route.length() && blocks == route.length();
                System.out.printf("%-5d %-5d %-5d %-5d %-9s %-9s %-6s %s%n", n, route.length(), moves, route.length() - moves,
                        status, actions + "/" + blocks, ok ? "ok" : "FAILED", route);
                w.println(String.join(",", String.valueOf(n), String.valueOf(route.length()), String.valueOf(moves),
                        String.valueOf(route.length() - moves), route, String.valueOf(status), String.valueOf(actions),
                        String.valueOf(blocks), ok ? "ok" : "FAILED"));
            }
        }
        System.out.println("CSV written to " + out.getAbsolutePath());
    }

    // ---- programs ----

    /** One block: F L R, W = repeat until goal, a l r = if path ahead / left / right (b = else part). */
    private static final class Node {
        final char type;
        final Node[] a, b;

        Node(char type, Node[] a, Node[] b) {
            this.type = type;
            this.a = a;
            this.b = b;
        }
    }

    private static final Node[] EMPTY = new Node[0];

    static String text(Node[] seq) {
        StringBuilder sb = new StringBuilder();
        appendText(seq, sb);
        return sb.toString();
    }

    private static void appendText(Node[] seq, StringBuilder sb) {
        for (Node n : seq) {
            sb.append(n.type);
            if (n.type == 'W') {
                sb.append('(');
                appendText(n.a, sb);
                sb.append(')');
            } else if (n.a != null) {
                sb.append('(');
                appendText(n.a, sb);
                sb.append('|');
                appendText(n.b, sb);
                sb.append(')');
            }
        }
    }

    /** A stream of programs, produced one by one: nothing is kept in memory, so the count is not limited by memory. */
    interface Programs {
        void forEach(Consumer<Node[]> out);
    }

    /**
     * Produces the sequences with an exact block count one by one, in a fixed order. Game grammar: a loop
     * (maze_forever) has no next connection, so it is always the last block of its sequence. An if without else and
     * an if with an empty else behave alike and have the same block count, so they are one program here. In reduced
     * mode (the loop bodies of the reduced programs) no if has both parts empty and no sequence has redundant turns
     * (R3, R4). Sequences up to CACHED blocks are kept, because they are produced again and again as parts of longer ones.
     */
    static final class Sequences {
        private static final int CACHED = 4;
        private final boolean reduced;
        private final List<List<Node[]>> cache = new ArrayList<>();

        Sequences(boolean reduced) {
            this.reduced = reduced;
            for (int n = 0; n <= CACHED; n++) {
                List<Node[]> l = new ArrayList<>();
                produce(n, l::add);
                cache.add(l);
            }
        }

        /** Every sequence with exactly n blocks. */
        void forEach(int n, Consumer<Node[]> out) {
            if (n < cache.size()) {
                for (Node[] seq : cache.get(n)) out.accept(seq);
            } else {
                produce(n, out);
            }
        }

        private void produce(int n, Consumer<Node[]> out) {
            if (n == 0) {
                out.accept(EMPTY);
                return;
            }
            forEach(n - 1, body -> out.accept(new Node[] { new Node('W', body, null) }));  // a loop, last
            for (int k = 1; k <= n; k++) {
                int restSize = n - k;
                statements(k, first -> forEach(restSize, rest -> {
                    if (!reduced || !redundantTurns(first, rest)) out.accept(prepend(first, rest));
                }));
            }
        }

        /** Every single non-loop statement with exactly k blocks. */
        private void statements(int k, Consumer<Node> out) {
            if (k == 1) {
                for (char c : new char[] { 'F', 'L', 'R' }) out.accept(new Node(c, null, null));
                if (reduced) return;  // an if with 1 block has both parts empty
            }
            for (char c : new char[] { 'a', 'l', 'r' }) {
                for (int t = 0; t <= k - 1; t++) {
                    int elseSize = k - 1 - t;
                    forEach(t, then -> forEach(elseSize, els -> out.accept(new Node(c, then, els))));
                }
            }
        }
    }

    /** Every program the game can build, up to maxBlocks ("all" mode). */
    static Programs allPrograms(int maxBlocks) {
        return out -> {
            Sequences seqs = new Sequences(false);
            for (int n = 1; n <= maxBlocks; n++) seqs.forEach(n, out);
        };
    }

    /** Number of sequences with exactly n blocks in the game grammar, in closed form (independent of the generator). */
    static long[] closedFormCounts(int maxBlocks) {
        long[] s = new long[maxBlocks + 1];
        s[0] = 1;
        for (int n = 1; n <= maxBlocks; n++) {
            long total = s[n - 1];  // a loop as the last block: 1 + body
            for (int k = 1; k <= n; k++) {
                long statements = k == 1 ? 3 : 0;
                for (int t = 0; t <= k - 1; t++) statements += 3 * s[t] * s[k - 1 - t];
                total += statements * s[n - k];
            }
            s[n] = total;
        }
        return s;
    }

    /**
     * The programs left after the skipping rules ("reduced" mode), up to maxBlocks: a straight-line prefix of moves
     * and turns (R1: no if outside a loop; R2 holds by the game grammar, nothing follows a loop), optionally followed by
     * one loop whose body is any sequence without an empty if and without redundant turns (R3, R4). Redundant turns are
     * LR, RL and LLL; RR is dropped too because LL has the same effect and cost (so RRR is excluded as well). Only the
     * prefixes of one length are kept at a time (at most 3 to the power of the length); programs are streamed.
     */
    static Programs reducedPrograms(int maxBlocks) {
        return out -> {
            Sequences bodies = new Sequences(true);
            List<Node[]> prefixes = new ArrayList<>();
            prefixes.add(EMPTY);
            for (int s = 0; s <= maxBlocks; s++) {
                List<Node[]> longer = new ArrayList<>();
                for (Node[] prefix : prefixes) {
                    if (prefix.length > 0) out.accept(prefix);  // straight line, no loop
                    for (int b = 0; s + 1 + b <= maxBlocks; b++) {
                        bodies.forEach(b, body -> {
                            Node[] p = Arrays.copyOf(prefix, prefix.length + 1);
                            p[prefix.length] = new Node('W', body, null);
                            out.accept(p);
                        });
                    }
                    if (s < maxBlocks) {
                        for (char c : new char[] { 'F', 'L', 'R' }) {
                            Node[] p = Arrays.copyOf(prefix, prefix.length + 1);
                            p[prefix.length] = new Node(c, null, null);
                            if (!redundantTail(p)) longer.add(p);
                        }
                    }
                }
                prefixes = longer;
            }
        };
    }

    /** Whether first followed by rest starts with LR, RL, RR or LLL (rest is already free of them). */
    private static boolean redundantTurns(Node first, Node[] rest) {
        if (rest.length == 0 || (first.type != 'L' && first.type != 'R')) return false;
        char x = first.type, y = rest[0].type;
        if ((x == 'L' && y == 'R') || (x == 'R' && y == 'L') || (x == 'R' && y == 'R')) return true;
        return x == 'L' && y == 'L' && rest.length > 1 && rest[1].type == 'L';
    }

    /** Whether the prefix ends in LR, RL, RR or LLL. */
    private static boolean redundantTail(Node[] p) {
        int n = p.length;
        if (n < 2) return false;
        char x = p[n - 2].type, y = p[n - 1].type;
        if ((x == 'L' && y == 'R') || (x == 'R' && y == 'L') || (x == 'R' && y == 'R')) return true;
        return n >= 3 && x == 'L' && y == 'L' && p[n - 3].type == 'L';
    }

    private static Node[] prepend(Node first, Node[] rest) {
        Node[] seq = new Node[rest.length + 1];
        seq[0] = first;
        System.arraycopy(rest, 0, seq, 1, rest.length);
        return seq;
    }

    static int blocks(Node[] seq) {
        int n = 0;
        for (Node x : seq) {
            n++;
            if (x.a != null) n += blocks(x.a);
            if (x.b != null) n += blocks(x.b);
        }
        return n;
    }

    // ---- fast simulation, same rules as BlockySimulator.executeLikeGame ----

    private int pos, dir, actions;
    private boolean crashed;

    /** Runs the program; afterwards {@link #actions} holds the action count. Returns whether it reaches the goal. */
    boolean simulate(Node[] program) {
        pos = startCell;
        dir = startDir;
        actions = 0;
        crashed = false;
        execSeq(program);
        return !crashed && win[pos];
    }

    private void execSeq(Node[] seq) {
        for (int i = 0; i < seq.length && !crashed; i++) {
            Node n = seq[i];
            switch (n.type) {
            case 'F': {
                actions++;
                int t = adj[pos][dir];
                if (t < 0 || wall[t]) crashed = true;
                else pos = t;
                break;
            }
            case 'L':
                actions++;
                dir = (dir + 3) % 4;
                break;
            case 'R':
                actions++;
                dir = (dir + 1) % 4;
                break;
            case 'W':
                while (!crashed && !win[pos]) {
                    if (actions > maxActions) {
                        crashed = true;  // the game's tick limit
                        break;
                    }
                    int before = actions;
                    execSeq(n.a);
                    if (actions == before && !crashed) crashed = true;  // no progress
                }
                break;
            default: {
                actions++;  // the path check is an action
                int look = n.type == 'a' ? dir : n.type == 'l' ? (dir + 3) % 4 : (dir + 1) % 4;
                int t = adj[pos][look];
                execSeq(t >= 0 && !wall[t] ? n.a : n.b);
            }
            }
        }
    }

    // ---- checks 1 and 2 ----

    /**
     * Check 1: the generator produces exactly the closed-form number of programs. Check 2: on every level, the fast
     * simulator agrees with BlockySimulator on goal reached and Actions for every program.
     */
    private static void checkSim(File levelsDir, int maxBlocks, List<Integer> levels) throws IOException {
        long t0 = System.currentTimeMillis();
        long[] expected = closedFormCounts(maxBlocks);
        long[] generatedBySize = new long[maxBlocks + 1];
        List<GroundTruthEnumerator> mazes = new ArrayList<>();
        for (int n : levels) mazes.add(new GroundTruthEnumerator(loadLevel(new File(levelsDir, n + ".xmi"))));
        long[] goal = new long[levels.size()], mismatches = new long[levels.size()];
        int[] fewest = new int[levels.size()];
        Arrays.fill(fewest, Integer.MAX_VALUE);
        List<List<String>> examples = new ArrayList<>();
        for (int i = 0; i < levels.size(); i++) examples.add(new ArrayList<>());

        Sequences all = new Sequences(false);
        for (int n = 1; n <= maxBlocks; n++) {
            int size = n;
            all.forEach(n, program -> {
                generatedBySize[size]++;
                String text = text(program);
                Body body = toBody(text);
                for (int i = 0; i < mazes.size(); i++) {
                    GroundTruthEnumerator m = mazes.get(i);
                    boolean fastWon = m.simulate(program);
                    m.level.setSolution(body);
                    boolean realWon = BlockySimulator.run(m.level) == GameStatus.WON;
                    int realActions = BlockySimulator.simulationSteps(m.level);
                    if (fastWon) {
                        goal[i]++;
                        fewest[i] = Math.min(fewest[i], m.actions);
                    }
                    if (fastWon != realWon || m.actions != realActions) {
                        mismatches[i]++;
                        if (examples.get(i).size() < 10) {
                            examples.get(i).add(String.format("%s: fast %s/%d, BlockySimulator %s/%d", text,
                                    fastWon ? "goal" : "no goal", m.actions, realWon ? "goal" : "no goal", realActions));
                        }
                    }
                }
            });
        }

        System.out.println("Check 1: programs generated per block count (all programs the game can build)");
        System.out.printf("  %-7s %-12s %-12s %s%n", "Blocks", "Generated", "Closed form", "Result");
        long generated = 0, closedForm = 0;
        boolean countsOk = true;
        for (int n = 1; n <= maxBlocks; n++) {
            boolean ok = generatedBySize[n] == expected[n];
            countsOk &= ok;
            generated += generatedBySize[n];
            closedForm += expected[n];
            System.out.printf("  %-7d %-12d %-12d %s%n", n, generatedBySize[n], expected[n], ok ? "ok" : "DIFFERENT");
        }
        System.out.printf("  %-7s %-12d %-12d %s%n", "total", generated, closedForm, countsOk ? "ok" : "DIFFERENT");

        System.out.println();
        System.out.println("Check 2: fast simulator against BlockySimulator (goal reached and Actions), every program");
        System.out.printf("  %-6s %-10s %-14s %-11s %s%n", "Level", "Programs", "Reach goal", "Differences", "Result");
        for (int i = 0; i < levels.size(); i++) {
            System.out.printf("  %-6d %-10d %-14d %-11d %s%n", levels.get(i), generated, goal[i], mismatches[i],
                    mismatches[i] == 0 ? "ok" : "DIFFERENT");
            for (String e : examples.get(i)) System.out.println("      " + e);
        }

        System.out.println();
        System.out.println("Check 4: no goal-reaching program has fewer Actions than A_min");
        System.out.printf("  %-6s %-6s %-30s %s%n", "Level", "A_min", "Fewest Actions of the programs", "Result");
        for (int i = 0; i < levels.size(); i++) {
            int aMin = mazes.get(i).shortestRouteProgram().length();
            String found = fewest[i] == Integer.MAX_VALUE ? "none reaches the goal" : String.valueOf(fewest[i]);
            boolean ok = fewest[i] >= aMin;
            System.out.printf("  %-6d %-6d %-30s %s%n", levels.get(i), aMin, found,
                    !ok ? "FEWER THAN A_min" : fewest[i] == aMin ? "ok (A_min reached)" : "ok");
        }
        System.out.printf("(%.1fs)%n", sec(t0));
    }

    // ---- check 3 ----

    /** Reached (blocks, actions) pairs of goal-reaching programs, with the first example found for each pair. */
    private Map<Integer, java.util.TreeMap<Integer, String>> table(Programs programs) {
        Map<Integer, java.util.TreeMap<Integer, String>> t = new java.util.TreeMap<>();
        counted = 0;
        programs.forEach(p -> {
            counted++;
            if (!simulate(p)) return;
            t.computeIfAbsent(blocks(p), k -> new java.util.TreeMap<>()).putIfAbsent(actions, text(p));
        });
        return t;
    }

    private long counted;  // programs run by the last table() call

    /** The non-dominated pairs of a table: per block count the fewest actions, if fewer than with any smaller count. */
    private static List<int[]> front(Map<Integer, java.util.TreeMap<Integer, String>> t) {
        List<int[]> f = new ArrayList<>();
        int best = Integer.MAX_VALUE;
        for (Map.Entry<Integer, java.util.TreeMap<Integer, String>> e : t.entrySet()) {
            int a = e.getValue().firstKey();
            if (a < best) {
                f.add(new int[] { e.getKey(), a });
                best = a;
            }
        }
        return f;
    }

    private static int pairs(Map<Integer, java.util.TreeMap<Integer, String>> t) {
        int n = 0;
        for (java.util.TreeMap<Integer, String> m : t.values()) n += m.size();
        return n;
    }

    /** Check 3: the reduced mode gives the same front as all programs. */
    private static void checkRules(File levelsDir, int maxBlocks, List<Integer> levels) throws IOException {
        long t0 = System.currentTimeMillis();
        Programs all = allPrograms(maxBlocks), reduced = reducedPrograms(maxBlocks);
        System.out.println("Check 3: front (non-dominated Blocks/Actions of goal-reaching programs) of all programs vs reduced");
        for (int n : levels) {
            GroundTruthEnumerator m = new GroundTruthEnumerator(loadLevel(new File(levelsDir, n + ".xmi")));
            Map<Integer, java.util.TreeMap<Integer, String>> ta = m.table(all);
            long nAll = m.counted;
            Map<Integer, java.util.TreeMap<Integer, String>> tr = m.table(reduced);
            long nReduced = m.counted;
            List<int[]> fa = front(ta), fr = front(tr);
            boolean same = fa.size() == fr.size();
            for (int i = 0; same && i < fa.size(); i++) same = Arrays.equals(fa.get(i), fr.get(i));
            System.out.printf("  Level %d: %s (programs: all %d, reduced %d; pairs reached: all %d, reduced %d)%n", n,
                    same ? "same front, ok" : "DIFFERENT FRONT", nAll, nReduced, pairs(ta), pairs(tr));
            System.out.printf("    %-7s %-8s %-30s %s%n", "Blocks", "Actions", "Example (all)", "Example (reduced)");
            int rows = Math.max(fa.size(), fr.size());
            for (int i = 0; i < rows; i++) {
                int[] p = i < fa.size() ? fa.get(i) : fr.get(i);
                String ea = i < fa.size() ? ta.get(fa.get(i)[0]).get(fa.get(i)[1]) : "-";
                String er = i < fr.size() ? tr.get(fr.get(i)[0]).get(fr.get(i)[1]) + (Arrays.equals(p, fr.get(i)) ? "" : " " + Arrays.toString(fr.get(i))) : "-";
                System.out.printf("    %-7d %-8d %-30s %s%n", p[0], p[1], ea, er);
            }
        }
        System.out.printf("(%.1fs)%n", sec(t0));
    }

    // ---- search: the table per level ----

    /**
     * Writes outDir/level{N}_b{maxBlocks}.csv per level: every (Blocks, Actions) pair reached by a goal-reaching program
     * of the reduced mode, with one example program, whether the pair is on the front (non-dominated up to maxBlocks),
     * and whether BlockySimulator confirms the example (goal reached, same Actions and Blocks).
     */
    private static void search(File levelsDir, int maxBlocks, File outDir, List<Integer> levels) throws IOException {
        long t0 = System.currentTimeMillis();
        Programs reduced = reducedPrograms(maxBlocks);
        outDir.mkdirs();
        System.out.printf("Search up to %d blocks (reduced programs)%n", maxBlocks);
        System.out.printf("  %-6s %-12s %-14s %-13s %-12s %-8s %s%n", "Level", "Programs", "Pairs reached", "Front pairs",
                "Confirmed", "Time", "File");
        for (int n : levels) {
            GroundTruthEnumerator m = new GroundTruthEnumerator(loadLevel(new File(levelsDir, n + ".xmi")));
            long tLevel = System.currentTimeMillis();
            Map<Integer, java.util.TreeMap<Integer, String>> t = m.table(reduced);
            java.util.Set<String> onFront = new java.util.HashSet<>();
            for (int[] p : front(t)) onFront.add(p[0] + "/" + p[1]);
            File out = new File(outDir, "level" + n + "_b" + maxBlocks + ".csv");
            int rows = 0, confirmed = 0;
            try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8)) {
                w.println("blocks,actions,front,example_program,confirmed_by_BlockySimulator");
                for (Map.Entry<Integer, java.util.TreeMap<Integer, String>> e : t.entrySet()) {
                    for (Map.Entry<Integer, String> pair : e.getValue().entrySet()) {
                        int blocks = e.getKey(), actions = pair.getKey();
                        Body body = toBody(pair.getValue());
                        m.level.setSolution(body);
                        boolean ok = BlockySimulator.run(m.level) == GameStatus.WON
                                && BlockySimulator.simulationSteps(m.level) == actions
                                && BlockyProgramMetrics.countStatements(body) == blocks;
                        rows++;
                        if (ok) confirmed++;
                        w.println(blocks + "," + actions + "," + (onFront.contains(blocks + "/" + actions) ? "yes" : "no") + ","
                                + pair.getValue() + "," + (ok ? "yes" : "NO"));
                    }
                }
            }
            System.out.printf("  %-6d %-12d %-14d %-13d %-12s %-8s %s%n", n, m.counted, rows, onFront.size(),
                    confirmed + "/" + rows, String.format("%.1fs", sec(tLevel)), out.getName());
        }
        System.out.printf("Written to %s (%.1fs)%n", outDir.getAbsolutePath(), sec(t0));
    }

    // ---- check 5 ----

    /**
     * Check 5: every goal-reaching user model of the models CSV (tools/report-goal-check) on the given levels with at
     * most maxBlocks blocks is one of the generated programs, and the fast simulator gives it the same goal and Actions
     * as the replay (ReportGoalChecker with BlockySimulator).
     */
    private static void checkModels(File levelsDir, int maxBlocks, File modelsCsv, List<Integer> levels) throws IOException {
        List<String> lines = java.nio.file.Files.readAllLines(modelsCsv.toPath(), StandardCharsets.UTF_8);
        List<String> header = Arrays.asList(lines.get(0).split(",", -1));
        int iSession = header.indexOf("session"), iIndex = header.indexOf("index"), iLevel = header.indexOf("level"),
                iProgram = header.indexOf("program"), iStatus = header.indexOf("status"),
                iActions = header.indexOf("power_kW_actions"), iBlocks = header.indexOf("memory_kB_blocks");

        Map<Integer, GroundTruthEnumerator> mazes = new java.util.HashMap<>();
        for (int n : levels) mazes.put(n, new GroundTruthEnumerator(loadLevel(new File(levelsDir, n + ".xmi"))));

        // the user programs to look for, then one pass over every program the game can build up to maxBlocks
        java.util.Set<String> wanted = new java.util.HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] f = line.split(",", -1);
            if (mazes.containsKey(Integer.parseInt(f[iLevel])) && "WON".equals(f[iStatus])
                    && Integer.parseInt(f[iBlocks]) <= maxBlocks) wanted.add(f[iProgram]);
        }
        Map<String, Node[]> byText = new java.util.HashMap<>();
        allPrograms(maxBlocks).forEach(p -> {
            String t = text(p);
            if (wanted.contains(t)) byText.put(t, p);
        });

        System.out.println("Check 5: goal-reaching user models up to " + maxBlocks + " blocks: generated, and same values as the replay");
        System.out.printf("  %-30s %-6s %-6s %-26s %-14s %-14s %s%n", "Session", "Index", "Level", "Program", "Replay A/B",
                "Search A/B", "Result");
        int checked = 0, failed = 0, larger = 0;
        for (String line : lines.subList(1, lines.size())) {
            String[] f = line.split(",", -1);
            int level = Integer.parseInt(f[iLevel]);
            if (!mazes.containsKey(level) || !"WON".equals(f[iStatus])) continue;
            int replayActions = Integer.parseInt(f[iActions]), replayBlocks = Integer.parseInt(f[iBlocks]);
            if (replayBlocks > maxBlocks) {
                larger++;
                continue;
            }
            checked++;
            Node[] p = byText.get(f[iProgram]);
            String search = "not generated";
            boolean ok = false;
            if (p != null) {
                GroundTruthEnumerator m = mazes.get(level);
                boolean won = m.simulate(p);
                search = (won ? "" : "no goal ") + m.actions + "/" + blocks(p);
                ok = won && m.actions == replayActions && blocks(p) == replayBlocks;
            }
            if (!ok) failed++;
            System.out.printf("  %-30s %-6s %-6d %-26s %-14s %-14s %s%n", f[iSession], f[iIndex], level, f[iProgram],
                    replayActions + "/" + replayBlocks, search, ok ? "ok" : "DIFFERENT");
        }
        System.out.printf("  %d models checked, %d different; %d goal-reaching models on these levels have more than %d blocks (not checked)%n",
                checked, failed, larger, maxBlocks);
    }

    private static double sec(long t0) {
        return (System.currentTimeMillis() - t0) / 1000.0;
    }

    // ---- program text to EMF ----

    private static final BlockyFactory FACTORY = BlockyFactory.eINSTANCE;

    /** Builds the EMF program of a program text, for BlockySimulator and BlockyProgramMetrics. */
    static Body toBody(String text) {
        int[] pos = { 0 };
        Body body = parseBody(text, pos);
        if (pos[0] != text.length()) throw new IllegalArgumentException("unexpected '" + text.charAt(pos[0]) + "' in " + text);
        return body;
    }

    private static Body parseBody(String s, int[] pos) {
        Body body = FACTORY.createBody();
        Container prev = null;
        while (pos[0] < s.length() && s.charAt(pos[0]) != ')' && s.charAt(pos[0]) != '|') {
            Container c = FACTORY.createContainer();
            c.setStatement(parseStatement(s, pos));
            if (prev == null) body.setFirstContainer(c);
            else prev.setNext(c);
            prev = c;
        }
        return body;
    }

    private static Statement parseStatement(String s, int[] pos) {
        char c = s.charAt(pos[0]++);
        if (c == 'F' || c == 'L' || c == 'R') {
            AtomicStatement a = FACTORY.createAtomicStatement();
            a.setKind(c == 'F' ? AtomicStatementKind.MOVE_FORWARD : c == 'L' ? AtomicStatementKind.TURN_LEFT : AtomicStatementKind.TURN_RIGHT);
            return a;
        }
        expect(s, pos, '(');
        if (c == 'W') {
            Loop l = FACTORY.createLoop();
            l.setBody(parseBody(s, pos));
            expect(s, pos, ')');
            return l;
        }
        if (c != 'a' && c != 'l' && c != 'r') throw new IllegalArgumentException("unknown block '" + c + "' in " + s);
        IfStmt i = FACTORY.createIfStmt();
        i.setCondition(c == 'a' ? ConditionKind.CHECK_FORWARD : c == 'l' ? ConditionKind.CHECK_LEFT : ConditionKind.CHECK_RIGHT);
        i.setThenBody(parseBody(s, pos));
        if (pos[0] < s.length() && s.charAt(pos[0]) == '|') {
            pos[0]++;
            i.setElseBody(parseBody(s, pos));
        }
        expect(s, pos, ')');
        return i;
    }

    private static void expect(String s, int[] pos, char c) {
        if (pos[0] >= s.length() || s.charAt(pos[0]) != c) throw new IllegalArgumentException("expected '" + c + "' in " + s);
        pos[0]++;
    }

    // ---- levels ----

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

    public static void main(String[] args) throws Exception {
        if (args.length >= 4 && "amin".equals(args[0])) {
            List<Integer> levels = new ArrayList<>();
            for (int i = 3; i < args.length; i++) levels.add(Integer.parseInt(args[i]));
            amin(new File(args[1]), new File(args[2]), levels);
            return;
        }
        if (args.length >= 4 && "check-sim".equals(args[0])) {
            List<Integer> levels = new ArrayList<>();
            for (int i = 3; i < args.length; i++) levels.add(Integer.parseInt(args[i]));
            checkSim(new File(args[1]), Integer.parseInt(args[2]), levels);
            return;
        }
        if (args.length >= 5 && "search".equals(args[0])) {
            List<Integer> levels = new ArrayList<>();
            for (int i = 4; i < args.length; i++) levels.add(Integer.parseInt(args[i]));
            search(new File(args[1]), Integer.parseInt(args[2]), new File(args[3]), levels);
            return;
        }
        if (args.length >= 5 && "check-models".equals(args[0])) {
            List<Integer> levels = new ArrayList<>();
            for (int i = 4; i < args.length; i++) levels.add(Integer.parseInt(args[i]));
            checkModels(new File(args[1]), Integer.parseInt(args[2]), new File(args[3]), levels);
            return;
        }
        if (args.length >= 4 && "check-rules".equals(args[0])) {
            List<Integer> levels = new ArrayList<>();
            for (int i = 3; i < args.length; i++) levels.add(Integer.parseInt(args[i]));
            checkRules(new File(args[1]), Integer.parseInt(args[2]), levels);
            return;
        }
        System.err.println("Usage: GroundTruthEnumerator amin <levels dir> <out.csv> <level>...");
        System.err.println("       GroundTruthEnumerator check-sim <levels dir> <max blocks> <level>...");
        System.exit(2);
    }
}
