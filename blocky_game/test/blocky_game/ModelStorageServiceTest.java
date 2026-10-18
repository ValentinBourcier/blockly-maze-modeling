package blocky_game;

import blocky.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ModelStorageServiceTest {

    @TempDir
    File tempDir;

    @Test
    void testCreateDefaultGameAndSave() throws IOException {
        ModelStorageService service = new ModelStorageService();
        File saveFile = new File(tempDir, "test_game.xmi");

        ModelStorageService.LoadResult res = service.createDefaultGame(saveFile);
        assertNotNull(res);
        assertNotNull(res.game);
        assertNotNull(res.level);
        assertNotNull(res.resource);

        // Add dummy map
        GridMap map = BlockyFactory.eINSTANCE.createGridMap();
        map.setWidth(1);
        map.setHeight(1);
        Cell c = BlockyFactory.eINSTANCE.createCell();
        c.setType(CellType.START);
        map.getCells().add(c);
        res.level.setMap(map);

        service.saveToFile(res.resource, res.level, saveFile);
        assertTrue(saveFile.exists());

        // Reload
        ModelStorageService.LoadResult loaded = service.loadFromFile(saveFile);
        assertNotNull(loaded.game);
        assertNotNull(loaded.level);
        assertEquals(1, loaded.level.getMap().getWidth());
    }

    @Test
    void testEnsureNonNullSolution() {
        Level level = BlockyFactory.eINSTANCE.createLevel();
        assertNull(level.getSolution());

        ModelStorageService.ensureLevelHasNonNullSolution(level);
        assertNotNull(level.getSolution());
        assertNotNull(level.getSolution().getFirstContainer());
    }
}
