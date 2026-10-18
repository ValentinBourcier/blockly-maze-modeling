package blocky_game;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deep module for loading and caching external web scripts and templates.
 * Provides locality for web asset resolution across classpath and filesystem.
 */
public final class WebAssetLoader {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private WebAssetLoader() {}

    /**
     * Loads a script file as UTF-8 string with memory caching.
     */
    public static String loadScript(String path) {
        return CACHE.computeIfAbsent(path, WebAssetLoader::readResourceOrFile);
    }

    /**
     * Loads a template file and replaces {{key}} placeholders.
     */
    public static String loadTemplate(String path, Map<String, String> replacements) {
        String template = loadScript(path);
        if (replacements == null || replacements.isEmpty()) {
            return template;
        }
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }

    /**
     * Clears cached scripts (useful for tests or hot reload).
     */
    public static void clearCache() {
        CACHE.clear();
    }

    private static String readResourceOrFile(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        // 1. Try class loader with leading slash
        String[] resourceCandidates = {
            "/" + normalized,
            "/blocky_game/resources/" + normalized,
            "/resources/" + normalized
        };

        for (String res : resourceCandidates) {
            try (InputStream is = WebAssetLoader.class.getResourceAsStream(res)) {
                if (is != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        return sb.toString();
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Try file system locations relative to process working directory
        String[] fileCandidates = {
            normalized,
            "src/blocky_game/resources/" + normalized,
            "blocky_game/src/blocky_game/resources/" + normalized,
            "resources/" + normalized,
            "blocky_game/resources/" + normalized,
            "src/blocky_game/" + normalized,
            "blocky_game/src/blocky_game/" + normalized,
            "src/blocky_game/blockly-games-web/" + normalized,
            "blocky_game/src/blocky_game/blockly-games-web/" + normalized
        };

        for (String fPath : fileCandidates) {
            File f = new File(fPath);
            if (f.isFile() && f.exists()) {
                try {
                    return Files.readString(f.toPath(), StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            }
        }

        throw new IllegalStateException("Unable to load web asset: " + path);
    }
}
