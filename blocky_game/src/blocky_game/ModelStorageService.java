package blocky_game;

import blocky.*;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import java.io.File;
import java.io.IOException;

/**
 * Deep module responsible for EMF XMI persistence, loading,
 * and deterministic snapshot exports for MoMoT synthesis.
 */
public class ModelStorageService {

    public static class LoadResult {
        public final Resource resource;
        public final Game game;
        public final Level level;

        public LoadResult(Resource resource, Game game, Level level) {
            this.resource = resource;
            this.game = game;
            this.level = level;
        }
    }

    static {
        BlockyPackage.eINSTANCE.eClass();
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
    }

    /**
     * Resolves the default initial model file path across possible working directories.
     */
    public File resolveInitialModelFile() {
        File xmiFile = new File("blocky_game/model/input/1.xmi").getAbsoluteFile();
        if (!xmiFile.exists()) {
            xmiFile = new File("model/input/1.xmi").getAbsoluteFile();
        }
        if (!xmiFile.exists()) {
            xmiFile = new File("blocky_game/level1_state.xmi").getAbsoluteFile();
        }
        if (!xmiFile.exists()) {
            xmiFile = new File("level1_state.xmi").getAbsoluteFile();
        }
        return xmiFile;
    }

    /**
     * Creates a new in-memory Game and Level within a new EMF Resource at the given file target.
     */
    public LoadResult createDefaultGame(File file) {
        ResourceSet resSet = new ResourceSetImpl();
        Resource res = resSet.createResource(URI.createFileURI(file.getAbsolutePath()));
        Game game = BlockyFactory.eINSTANCE.createGame();
        Level level = BlockyFactory.eINSTANCE.createLevel();
        game.getLevels().add(level);
        res.getContents().add(game);
        return new LoadResult(res, game, level);
    }

    /**
     * Loads a model from an XMI file, returning the loaded Resource, Game, and primary Level.
     * Supports both Game roots and legacy Level roots (wrapping them in Game).
     */
    public LoadResult loadFromFile(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("File does not exist: " + (file == null ? "null" : file.getAbsolutePath()));
        }

        ResourceSet resSet = new ResourceSetImpl();
        URI uri = URI.createFileURI(file.getAbsolutePath());
        Resource newResource = resSet.createResource(uri);
        newResource.load(null);

        if (newResource.getContents().isEmpty()) {
            throw new IllegalArgumentException("XMI file has no root element: " + file.getAbsolutePath());
        }
        Object root = newResource.getContents().get(0);

        Game loadedGame;
        Level loadedLevel;

        if (root instanceof Game) {
            loadedGame = (Game) root;
            if (loadedGame.getLevels().isEmpty()) {
                throw new IllegalArgumentException("Game has no levels.");
            }
            loadedLevel = loadedGame.getLevels().get(0);
        } else if (root instanceof Level) {
            loadedLevel = (Level) root;
            loadedGame = BlockyFactory.eINSTANCE.createGame();
            loadedGame.getLevels().add(loadedLevel);
            newResource.getContents().clear();
            newResource.getContents().add(loadedGame);
        } else {
            throw new IllegalArgumentException("XMI root is neither Game nor Level: "
                    + (root != null ? root.getClass().getName() : "null"));
        }

        if (loadedLevel.getMap() == null) {
            throw new IllegalArgumentException("Level has no map.");
        }

        return new LoadResult(newResource, loadedGame, loadedLevel);
    }

    /**
     * Saves the current Level and Game to an XMI file.
     */
    public void saveToFile(Resource resource, Level level, File file) {
        if (file == null || resource == null) return;
        try {
            ensureLevelHasNonNullSolution(level);
            resource.setURI(URI.createFileURI(file.getAbsolutePath()));
            resource.save(null);
            System.out.println("[ModelStorageService] Model saved to: " + resource.getURI().toFileString());
        } catch (Exception e) {
            System.err.println("[ModelStorageService] SAVE FAILED to " + file.getAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Saves a snapshot representing the current situation after direct manipulation.
     */
    public void saveDirectManipulationRequestXmi(Game currentGame) {
        try {
            File out = new File("blocky_game/direct_manipulation_request.xmi");
            if (!out.getParentFile().exists()) out = new File("direct_manipulation_request.xmi");

            ResourceSet resSet = new ResourceSetImpl();
            Resource outRes = resSet.createResource(URI.createFileURI(out.getAbsolutePath()));

            Game snapshot = currentGame != null ? EcoreUtil.copy(currentGame) : null;
            ensureGameHasNonNullSolutions(snapshot);
            if (snapshot != null) {
                outRes.getContents().add(snapshot);
                outRes.save(null);
                System.out.println("[ModelStorageService] Direct manipulation request saved to: " + outRes.getURI().toFileString());
            }
        } catch (Exception e) {
            System.err.println("[ModelStorageService] Direct manipulation request save failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Saves the DM request model to the default MoMoT input location for automated search runs.
     */
    public void saveMomotInputRequestXmi(Game currentGame) {
        try {
            File out = new File("blocky_momot/model/input/direct_manipulation_request.xmi");
            if (!out.getParentFile().exists()) {
                out = new File("../blocky_momot/model/input/direct_manipulation_request.xmi");
            }
            if (!out.getParentFile().exists()) {
                out = new File("direct_manipulation_request_momot.xmi");
            } else {
                out.getParentFile().mkdirs();
            }

            ResourceSet resSet = new ResourceSetImpl();
            Resource outRes = resSet.createResource(URI.createFileURI(out.getAbsolutePath()));

            Game snapshot = currentGame != null ? EcoreUtil.copy(currentGame) : null;
            ensureGameHasNonNullSolutions(snapshot);
            if (snapshot != null) {
                outRes.getContents().add(snapshot);
                outRes.save(null);
                System.out.println("[ModelStorageService] MoMoT DM request saved to: " + outRes.getURI().toFileString());
            }
        } catch (Exception e) {
            System.err.println("[ModelStorageService] MoMoT DM request save failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ensures the given level has a non-null solution Body and first Container.
     */
    public static void ensureLevelHasNonNullSolution(Level level) {
        if (level == null) return;
        if (level.getSolution() != null) return;
        Body b = BlockyFactory.eINSTANCE.createBody();
        Container c = BlockyFactory.eINSTANCE.createContainer();
        b.setFirstContainer(c);
        level.setSolution(b);
    }

    /**
     * Ensures all levels in the game have non-null solutions.
     */
    public static void ensureGameHasNonNullSolutions(Game game) {
        if (game == null || game.getLevels() == null) return;
        for (Level lvl : game.getLevels()) {
            ensureLevelHasNonNullSolution(lvl);
        }
    }
}
