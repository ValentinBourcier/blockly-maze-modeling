package blocky_game;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deep module responsible for logging, aggregating, and exporting participant session telemetry,
 * level timer tracking, and execution events.
 */
public class TelemetryService {

    public enum ActivityType {
        PROGRAM_RUN,
        MOMOT_SEARCH,
        SOLUTION_LOAD,
        DIRECT_MANIPULATION
    }

    public static class LevelRecord {
        public long elapsedMs;
        public String variant;
        public String updatedAt;
        public int programRuns;
        public int momotSearches;
        public int solutionsLoaded;
        public int directManipulations;

        public LevelRecord(long elapsedMs, String variant, String updatedAt) {
            this(elapsedMs, variant, updatedAt, 0, 0, 0, 0);
        }

        public LevelRecord(long elapsedMs, String variant, String updatedAt, int programRuns, int momotSearches, int solutionsLoaded, int directManipulations) {
            this.elapsedMs = elapsedMs;
            this.variant = variant != null ? variant : "momot";
            this.updatedAt = updatedAt != null ? updatedAt : java.time.Instant.now().toString();
            this.programRuns = Math.max(0, programRuns);
            this.momotSearches = Math.max(0, momotSearches);
            this.solutionsLoaded = Math.max(0, solutionsLoaded);
            this.directManipulations = Math.max(0, directManipulations);
        }
    }

    public static class SessionEvent {
        public String timestamp;
        public String type;
        public String variant;
        public int level;
        public String modelName;
        public String modelFile;
        public String modelPath;
        public String objectiveLine;
        public String xml = "";
        public long levelElapsedMs = -1;
        public long iterationMs = -1;
        public String levelArrivedAt;
        public long wallIterationMs = -1;
        public int generation = -1;
        public String searchId;
    }

    public static class AdminSessionSummary {
        public String sessionId;
        public String userId;
        public String startedAt;
        public long totalElapsedMs;
        public int completedLevels;
        public int totalProgramRuns;
        public int totalMomotSearches;
        public int totalSolutionsLoaded;
        public int totalDirectManipulations;
        public Map<Integer, LevelRecord> levels = new TreeMap<>();
        public List<SessionEvent> events = new ArrayList<>();
    }

    private static final Object SESSION_FILE_LOCK = new Object();

    public static File resolveLevelSessionsDir() {
        File dir = new File("blocky_game/level_sessions");
        if (!dir.exists()) {
            File alt = new File("level_sessions");
            if (alt.exists()) {
                dir = alt;
            } else if (new File("blocky_game").isDirectory()) {
                dir = new File("blocky_game/level_sessions");
            } else {
                dir = new File("level_sessions");
            }
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsoluteFile();
    }

    public void recordActivity(String timerSessionId, int levelId, ActivityType activityType) {
        if (timerSessionId == null || !timerSessionId.matches("^[a-zA-Z0-9_-]{1,128}$")) {
            return;
        }
        if (levelId < 1 || levelId > 10) {
            levelId = 1;
        }
        File dir = resolveLevelSessionsDir();
        File sessionFile = new File(dir, timerSessionId + ".json");

        synchronized (SESSION_FILE_LOCK) {
            if (!sessionFile.exists()) {
                try {
                    String initial = "{\n" +
                            "  \"sessionId\": \"" + escapeJson(timerSessionId) + "\",\n" +
                            "  \"userId\": \"anonymous\",\n" +
                            "  \"startedAt\": \"" + java.time.Instant.now().toString() + "\",\n" +
                            "  \"levels\": {}\n" +
                            "}";
                    Files.writeString(sessionFile.toPath(), initial, StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            }
            if (!sessionFile.exists()) {
                return;
            }
            try {
                String content = new String(Files.readAllBytes(sessionFile.toPath()), StandardCharsets.UTF_8);
                String storedUserId = parseJsonField(content, "userId");
                if (storedUserId == null || storedUserId.trim().isEmpty()) {
                    storedUserId = "anonymous";
                }
                String startedAt = parseJsonField(content, "startedAt");
                if (startedAt == null || startedAt.trim().isEmpty()) {
                    startedAt = java.time.Instant.now().toString();
                }

                Map<Integer, LevelRecord> levels = new TreeMap<>();
                Pattern pLvl = Pattern.compile("\"(\\d+)\"\\s*:\\s*\\{([^}]+)\\}");
                Matcher mLvl = pLvl.matcher(content);
                while (mLvl.find()) {
                    try {
                        int l = Integer.parseInt(mLvl.group(1));
                        String inner = mLvl.group(2);
                        long eMs = parseJsonLongField(inner, "elapsedMs", 0L);
                        String v = parseJsonField(inner, "variant");
                        String u = parseJsonField(inner, "updatedAt");
                        int runs = parseJsonIntField(inner, "programRuns", 0);
                        int searches = parseJsonIntField(inner, "momotSearches", 0);
                        int loaded = parseJsonIntField(inner, "solutionsLoaded", 0);
                        int dms = parseJsonIntField(inner, "directManipulations", 0);
                        levels.put(l, new LevelRecord(eMs, v, u, runs, searches, loaded, dms));
                    } catch (Exception ignored) {}
                }

                LevelRecord rec = levels.get(levelId);
                if (rec == null) {
                    rec = new LevelRecord(0L, "momot", java.time.Instant.now().toString(), 0, 0, 0, 0);
                }

                int runs = rec.programRuns;
                int searches = rec.momotSearches;
                int loaded = rec.solutionsLoaded;
                int dms = rec.directManipulations;

                if (activityType == ActivityType.PROGRAM_RUN) runs++;
                else if (activityType == ActivityType.MOMOT_SEARCH) searches++;
                else if (activityType == ActivityType.SOLUTION_LOAD) loaded++;
                else if (activityType == ActivityType.DIRECT_MANIPULATION) dms++;

                levels.put(levelId, new LevelRecord(rec.elapsedMs, rec.variant, rec.updatedAt, runs, searches, loaded, dms));

                StringBuilder sb = new StringBuilder("{\n");
                sb.append("  \"sessionId\": \"").append(escapeJson(timerSessionId)).append("\",\n");
                sb.append("  \"userId\": \"").append(escapeJson(storedUserId)).append("\",\n");
                sb.append("  \"startedAt\": \"").append(escapeJson(startedAt)).append("\",\n");
                sb.append("  \"levels\": {");

                boolean first = true;
                for (Map.Entry<Integer, LevelRecord> entry : levels.entrySet()) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("\n    \"").append(entry.getKey()).append("\": {\n");
                    sb.append("      \"elapsedMs\": ").append(entry.getValue().elapsedMs).append(",\n");
                    sb.append("      \"variant\": \"").append(escapeJson(entry.getValue().variant)).append("\",\n");
                    sb.append("      \"updatedAt\": \"").append(escapeJson(entry.getValue().updatedAt)).append("\",\n");
                    sb.append("      \"programRuns\": ").append(entry.getValue().programRuns).append(",\n");
                    sb.append("      \"momotSearches\": ").append(entry.getValue().momotSearches).append(",\n");
                    sb.append("      \"solutionsLoaded\": ").append(entry.getValue().solutionsLoaded).append(",\n");
                    sb.append("      \"directManipulations\": ").append(entry.getValue().directManipulations).append("\n");
                    sb.append("    }");
                }
                if (!first) sb.append("\n  ");
                sb.append("}\n}");

                Files.write(sessionFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                System.err.println("[TelemetryService] Failed to record activity: " + e.getMessage());
            }
        }
    }

    public void appendSessionEvent(String timerSessionId, String type, String variant, int levelId,
                                   String modelName, String xml, String modelPath, String objectiveLine,
                                   String timestamp) {
        appendSessionEvent(timerSessionId, type, variant, levelId, modelName, xml, modelPath, objectiveLine, timestamp, -1L, null, null, null);
    }

    public void appendSessionEvent(String timerSessionId, String type, String variant, int levelId,
                                   String modelName, String xml, String modelPath, String objectiveLine,
                                   String timestamp, long levelElapsedMs, String levelArrivedAt) {
        appendSessionEvent(timerSessionId, type, variant, levelId, modelName, xml, modelPath, objectiveLine, timestamp,
                levelElapsedMs, levelArrivedAt, null, null);
    }

    public void appendSessionEvent(String timerSessionId, String type, String variant, int levelId,
                                   String modelName, String xml, String modelPath, String objectiveLine,
                                   String timestamp, long levelElapsedMs, String levelArrivedAt,
                                   Integer generation, String searchId) {
        if (timerSessionId == null || !timerSessionId.matches("^[a-zA-Z0-9_-]{1,128}$")) {
            return;
        }
        if (levelId < 1 || levelId > 10) {
            levelId = 1;
        }
        if (variant == null || variant.trim().isEmpty()) {
            variant = "momot";
        }
        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = java.time.Instant.now().toString();
        }
        File baseDir = resolveLevelSessionsDir();
        File sessionDir = new File(baseDir, timerSessionId);
        File modelsDir = new File(sessionDir, "models");

        synchronized (SESSION_FILE_LOCK) {
            try {
                if (!modelsDir.exists()) {
                    modelsDir.mkdirs();
                }
                File eventsFile = new File(sessionDir, "events.jsonl");
                int eventIndex = 1;
                boolean isRun = "program_run".equals(type);
                boolean hadPreviousRun = false;
                long previousElapsed = -1L;
                String previousTimestamp = null;
                if (eventsFile.exists()) {
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(eventsFile), StandardCharsets.UTF_8))) {
                        String existing;
                        while ((existing = r.readLine()) != null) {
                            eventIndex++;
                            if (isRun && "program_run".equals(parseJsonField(existing, "type"))
                                    && parseJsonIntField(existing, "level", 0) == levelId) {
                                hadPreviousRun = true;
                                previousElapsed = parseJsonLongField(existing, "levelElapsedMs", -1L);
                                previousTimestamp = parseJsonField(existing, "timestamp");
                            }
                        }
                    }
                }
                long iterationMs = -1L;
                if (isRun && levelElapsedMs >= 0) {
                    if (!hadPreviousRun) {
                        iterationMs = levelElapsedMs;
                    } else if (previousElapsed >= 0) {
                        iterationMs = levelElapsedMs >= previousElapsed ? levelElapsedMs - previousElapsed : levelElapsedMs;
                    }
                }
                long wallIterationMs = -1L;
                if (isRun) {
                    String reference = hadPreviousRun ? previousTimestamp : levelArrivedAt;
                    try {
                        if (reference != null && !reference.trim().isEmpty()) {
                            long nowMs = java.time.Instant.parse(timestamp).toEpochMilli();
                            long refMs = java.time.Instant.parse(reference.trim()).toEpochMilli();
                            if (nowMs >= refMs) wallIterationMs = nowMs - refMs;
                        }
                    } catch (Exception ignored) {}
                }

                String savedModelFile = "";
                if (xml != null && !xml.trim().isEmpty()) {
                    String safeName = (modelName != null && !modelName.trim().isEmpty())
                            ? modelName.replaceAll("[^a-zA-Z0-9_.-]", "_")
                            : "model_" + System.currentTimeMillis();
                    if (!safeName.endsWith(".xml")) {
                        safeName += ".xml";
                    }
                    String fileName = String.format("%03d_%s", eventIndex, safeName);
                    File xmlOut = new File(modelsDir, fileName);
                    Files.writeString(xmlOut.toPath(), xml, StandardCharsets.UTF_8);
                    savedModelFile = "models/" + fileName;
                }

                StringBuilder line = new StringBuilder();
                line.append("{")
                        .append("\"timestamp\":\"").append(escapeJson(timestamp)).append("\",")
                        .append("\"type\":\"").append(escapeJson(type)).append("\",")
                        .append("\"variant\":\"").append(escapeJson(variant)).append("\",")
                        .append("\"level\":").append(levelId).append(",")
                        .append("\"modelName\":\"").append(escapeJson(modelName != null ? modelName : "")).append("\",")
                        .append("\"modelFile\":\"").append(escapeJson(savedModelFile)).append("\",")
                        .append("\"modelPath\":\"").append(escapeJson(modelPath != null ? modelPath : "")).append("\",")
                        .append("\"objectiveLine\":\"").append(escapeJson(objectiveLine != null ? objectiveLine : "")).append("\",")
                        .append("\"levelElapsedMs\":").append(numOrNull(levelElapsedMs)).append(",")
                        .append("\"iterationMs\":").append(numOrNull(iterationMs)).append(",")
                        .append("\"wallIterationMs\":").append(numOrNull(wallIterationMs)).append(",")
                        .append("\"generation\":").append(generation != null && generation >= 0 ? String.valueOf(generation) : "null").append(",")
                        .append("\"searchId\":").append(searchId != null && !searchId.trim().isEmpty() ? "\"" + escapeJson(searchId) + "\"" : "null").append(",")
                        .append("\"levelArrivedAt\":").append(levelArrivedAt != null && !levelArrivedAt.trim().isEmpty() ? "\"" + escapeJson(levelArrivedAt) + "\"" : "null")
                        .append("}\n");

                Files.write(eventsFile.toPath(), line.toString().getBytes(StandardCharsets.UTF_8),
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {
                System.err.println("[TelemetryService] Failed to append session event: " + e.getMessage());
            }
        }
    }

    public List<SessionEvent> loadSessionEvents(File dir, String sessionId) {
        List<SessionEvent> events = new ArrayList<>();
        if (dir == null || sessionId == null) return events;
        File sessionDir = new File(dir, sessionId);
        File eventsFile = new File(sessionDir, "events.jsonl");
        if (!eventsFile.exists() || !eventsFile.isFile()) {
            return events;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(eventsFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                SessionEvent evt = new SessionEvent();
                evt.timestamp = parseJsonField(line, "timestamp");
                evt.type = parseJsonField(line, "type");
                evt.variant = parseJsonField(line, "variant");
                evt.level = parseJsonIntField(line, "level", 1);
                evt.modelName = parseJsonField(line, "modelName");
                evt.modelFile = parseJsonField(line, "modelFile");
                evt.modelPath = parseJsonField(line, "modelPath");
                evt.objectiveLine = parseJsonField(line, "objectiveLine");
                evt.levelElapsedMs = parseJsonLongField(line, "levelElapsedMs", -1L);
                evt.iterationMs = parseJsonLongField(line, "iterationMs", -1L);
                evt.levelArrivedAt = parseJsonField(line, "levelArrivedAt");
                evt.wallIterationMs = parseJsonLongField(line, "wallIterationMs", -1L);
                evt.generation = parseJsonIntField(line, "generation", -1);
                evt.searchId = parseJsonField(line, "searchId");

                if (evt.modelFile != null && !evt.modelFile.trim().isEmpty()) {
                    File xmlFile = new File(sessionDir, evt.modelFile.trim());
                    if (xmlFile.exists() && xmlFile.isFile()) {
                        try {
                            evt.xml = Files.readString(xmlFile.toPath(), StandardCharsets.UTF_8);
                        } catch (Exception ignored) {}
                    }
                }
                if (evt.xml == null) evt.xml = "";
                events.add(evt);
            }
        } catch (Exception e) {
            System.err.println("[TelemetryService] Failed to load session events for " + sessionId + ": " + e.getMessage());
        }
        return events;
    }

    public List<AdminSessionSummary> loadAllSessionSummaries() {
        List<AdminSessionSummary> list = new ArrayList<>();
        File dir = resolveLevelSessionsDir();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try {
                    String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                    AdminSessionSummary sum = new AdminSessionSummary();
                    sum.sessionId = parseJsonField(content, "sessionId");
                    if (sum.sessionId == null || sum.sessionId.trim().isEmpty()) {
                        sum.sessionId = f.getName().replace(".json", "");
                    }
                    sum.userId = parseJsonField(content, "userId");
                    if (sum.userId == null || sum.userId.trim().isEmpty()) {
                        sum.userId = "anonymous";
                    }
                    sum.startedAt = parseJsonField(content, "startedAt");
                    if (sum.startedAt == null || sum.startedAt.trim().isEmpty()) {
                        sum.startedAt = java.time.Instant.ofEpochMilli(f.lastModified()).toString();
                    }

                    Pattern pLvl = Pattern.compile("\"(\\d+)\"\\s*:\\s*\\{([^}]+)\\}");
                    Matcher mLvl = pLvl.matcher(content);
                    long totalMs = 0;
                    int count = 0;
                    int totalRuns = 0;
                    int totalSearches = 0;
                    int totalLoaded = 0;
                    int totalDms = 0;
                    while (mLvl.find()) {
                        try {
                            int l = Integer.parseInt(mLvl.group(1));
                            String inner = mLvl.group(2);
                            long eMs = parseJsonLongField(inner, "elapsedMs", 0L);
                            String v = parseJsonField(inner, "variant");
                            String u = parseJsonField(inner, "updatedAt");
                            int runs = parseJsonIntField(inner, "programRuns", 0);
                            int searches = parseJsonIntField(inner, "momotSearches", 0);
                            int loaded = parseJsonIntField(inner, "solutionsLoaded", 0);
                            int dms = parseJsonIntField(inner, "directManipulations", 0);
                            sum.levels.put(l, new LevelRecord(eMs, v, u, runs, searches, loaded, dms));
                            totalMs += eMs;
                            if (eMs > 0) count++;
                            totalRuns += runs;
                            totalSearches += searches;
                            totalLoaded += loaded;
                            totalDms += dms;
                        } catch (Exception ignored) {}
                    }
                    sum.totalElapsedMs = totalMs;
                    sum.completedLevels = count;
                    sum.totalProgramRuns = totalRuns;
                    sum.totalMomotSearches = totalSearches;
                    sum.totalSolutionsLoaded = totalLoaded;
                    sum.totalDirectManipulations = totalDms;
                    sum.events = loadSessionEvents(dir, sum.sessionId);
                    list.add(sum);
                } catch (Exception ignored) {}
            }
        }
        list.sort((a, b) -> (b.startedAt != null ? b.startedAt : "").compareTo(a.startedAt != null ? a.startedAt : ""));
        return list;
    }

    public static List<Integer> loadedGenerations(List<SessionEvent> events, int level) {
        List<Integer> out = new ArrayList<>();
        if (events == null) return out;
        for (SessionEvent ev : events) {
            if ("candidate_exploration".equals(ev.type) && ev.level == level && ev.generation >= 0) {
                out.add(ev.generation);
            }
        }
        return out;
    }

    public static String loadedGenerationsJson(List<SessionEvent> events, int level) {
        List<Integer> gens = loadedGenerations(events, level);
        StringBuilder sb = new StringBuilder("\"loadedGenerations\":[");
        for (int i = 0; i < gens.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(gens.get(i));
        }
        return sb.append("]").toString();
    }

    public static List<Long> levelIterations(List<SessionEvent> events, int level, boolean wall) {
        List<Long> out = new ArrayList<>();
        if (events == null) return out;
        for (SessionEvent ev : events) {
            long v = wall ? ev.wallIterationMs : ev.iterationMs;
            if ("program_run".equals(ev.type) && ev.level == level && v >= 0) {
                out.add(v);
            }
        }
        return out;
    }

    public static long meanOf(List<Long> values) {
        if (values == null || values.isEmpty()) return 0L;
        long sum = 0;
        for (long v : values) sum += v;
        return Math.round((double) sum / values.size());
    }

    public static long medianOf(List<Long> values) {
        if (values == null || values.isEmpty()) return 0L;
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 1) return sorted.get(n / 2);
        return Math.round((sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0);
    }

    public static String statsMembers(String prefix, List<Long> its) {
        StringBuilder sb = new StringBuilder("\"").append(prefix).append("sMs\":[");
        for (int i = 0; i < its.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(its.get(i));
        }
        sb.append("],\"").append(prefix).append("AvgMs\":").append(its.isEmpty() ? "null" : String.valueOf(meanOf(its)));
        sb.append(",\"").append(prefix).append("MedianMs\":").append(its.isEmpty() ? "null" : String.valueOf(medianOf(its)));
        return sb.toString();
    }

    public static String iterationStatsJson(List<SessionEvent> events, int level) {
        return statsMembers("iteration", levelIterations(events, level, false)) + ","
                + statsMembers("wallIteration", levelIterations(events, level, true)) + ","
                + loadedGenerationsJson(events, level);
    }

    public static String numOrNull(long v) {
        return v >= 0 ? String.valueOf(v) : "null";
    }

    public static String escapeJson(String s) {
        return SearchHttpHandler.escapeJson(s);
    }

    public static String parseJsonField(String json, String field) {
        return SearchHttpHandler.parseJsonField(json, field);
    }

    public static int parseJsonIntField(String json, String field, int defaultVal) {
        return SearchHttpHandler.parseJsonIntField(json, field, defaultVal);
    }

    public static long parseJsonLongField(String json, String field, long defaultVal) {
        return SearchHttpHandler.parseJsonLongField(json, field, defaultVal);
    }
}
