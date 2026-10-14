package blocky_game;

import blocky.*;
import blocky_momot.SimulationEngine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deep module responsible for GridMap operations:
 * - Parsing 2D JSON grid representations into EMF GridMap / Cell instances
 * - Cell adjacency and coordinate lookups
 * - Start / Goal / DMG cell queries and mutation
 * - 2D grid export for WebView rendering
 */
public class MazeGridService {

    /**
     * Parses a JSON grid representation (e.g. "[[0,1,2...],[...]]") into an EMF GridMap.
     * Links cell neighbors (top, bottom, left, right).
     *
     * @param mapJson raw JSON 2D array string
     * @param oldDmg  optional previously-set DMG cell to preserve if within bounds and empty
     * @return populated GridMap instance
     */
    public GridMap parseGridMapFromJson(String mapJson, Cell oldDmg) {
        if (mapJson == null || mapJson.trim().isEmpty()) {
            return BlockyFactory.eINSTANCE.createGridMap();
        }

        String cleaned = mapJson.replaceAll("\\s+", "");
        if (cleaned.startsWith("[[")) {
            cleaned = cleaned.substring(2, cleaned.length() - 2);
        }

        String[] rowStrs = cleaned.split("\\],\\[");
        int height = rowStrs.length;
        int width = rowStrs[0].split(",").length;

        GridMap map = BlockyFactory.eINSTANCE.createGridMap();
        map.setWidth(width);
        map.setHeight(height);

        Cell[][] grid = new Cell[width][height];

        for (int y = 0; y < height; y++) {
            String[] colStrs = rowStrs[y].split(",");
            for (int x = 0; x < width; x++) {
                int val = Integer.parseInt(colStrs[x].trim());
                Cell cell = BlockyFactory.eINSTANCE.createCell();
                cell.setX(x);
                cell.setY(y);

                switch (val) {
                    case 0:
                        cell.setType(CellType.WALL);
                        break;
                    case 1:
                        cell.setType(CellType.EMPTY);
                        break;
                    case 2:
                        cell.setType(CellType.START);
                        break;
                    case 3:
                        cell.setType(CellType.GOAL);
                        break;
                    default:
                        cell.setType(CellType.EMPTY);
                        break;
                }

                grid[x][y] = cell;
                map.getCells().add(cell);
            }
        }

        // Restore DMG marker on an EMPTY cell if oldDmg was within bounds
        if (oldDmg != null) {
            Cell newDmgCell = findCellByXY(map, oldDmg.getX(), oldDmg.getY());
            if (newDmgCell != null && newDmgCell.getType() == CellType.EMPTY) {
                newDmgCell.setType(CellType.DMG);
            }
        }

        // Link cell neighbors
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Cell cell = grid[x][y];
                if (y > 0)
                    cell.setTop(grid[x][y - 1]);
                if (y < height - 1)
                    cell.setBottom(grid[x][y + 1]);
                if (x > 0)
                    cell.setLeft(grid[x - 1][y]);
                if (x < width - 1)
                    cell.setRight(grid[x + 1][y]);
            }
        }

        return map;
    }

    /**
     * Finds a cell by (x, y) coordinates within the GridMap.
     */
    public Cell findCellByXY(GridMap map, int x, int y) {
        if (map == null || map.getCells() == null) return null;
        for (Cell c : map.getCells()) {
            if (c != null && c.getX() == x && c.getY() == y) return c;
        }
        return null;
    }

    /**
     * Clears any Direct Manipulation Goal (DMG) markers on the map, restoring them to EMPTY.
     */
    public void clearDirectManipulationGoal(GridMap map) {
        if (map == null || map.getCells() == null) return;
        for (Cell c : map.getCells()) {
            if (c != null && c.getType() == CellType.DMG) {
                c.setType(CellType.EMPTY);
            }
        }
    }

    /**
     * Validates whether Pegman can be placed at (x, y) and applies Direct Manipulation Goal (DMG)
     * marking if placed on an EMPTY cell.
     * Allowed only onto EMPTY, GOAL, or DMG cells. Rejects WALL or missing cells.
     *
     * @param map grid map
     * @param x target x
     * @param y target y
     * @return the target Cell if teleport was accepted, or null if rejected
     */
    public Cell applyTeleportTarget(GridMap map, int x, int y) {
        if (map == null) return null;
        Cell target = findCellByXY(map, x, y);
        if (target == null) {
            System.err.println("[GameEngine] teleportPegman rejected: no cell at x=" + x + " y=" + y);
            return null;
        }
        if (!(target.getType() == CellType.EMPTY || target.getType() == CellType.GOAL || target.getType() == CellType.DMG)) {
            System.err.println("[GameEngine] teleportPegman rejected: target type=" + target.getType() + " at x=" + x + " y=" + y);
            return null;
        }

        clearDirectManipulationGoal(map);
        if (target.getType() == CellType.EMPTY) {
            target.setType(CellType.DMG);
        }
        return target;
    }

    /**
     * Parses level metadata JSON and updates level attributes:
     * id, title, maxBlocks, allowLoops, allowConditionals, allowIfElse, and startOrientation.
     *
     * @param level level to update
     * @param metaJson metadata JSON string
     * @param isDebugActive whether an active debug session is currently running
     * @return the resolved starting direction
     */
    public Direction applyLevelMetadata(Level level, String metaJson, boolean isDebugActive) {
        if (level == null || metaJson == null) return null;
        try {
            int maxBlocks        = extractJsonInt(metaJson, "maxBlocks",        -1);
            int startDirCode     = extractJsonInt(metaJson, "startDirection",    1);
            int levelNum         = extractJsonInt(metaJson, "level",             1);
            boolean allowLoops   = extractJsonBool(metaJson, "allowLoops",   false);
            boolean allowConds   = extractJsonBool(metaJson, "allowConditionals", false);
            boolean allowIfElse  = extractJsonBool(metaJson, "allowIfElse", false);

            level.setId(levelNum);
            level.setTitle("Maze Level " + levelNum);
            level.setMaxBlocks(maxBlocks < 0 ? 0 : maxBlocks);
            level.setAllowLoops(allowLoops);
            level.setAllowConditionals(allowConds);
            level.setAllowIfElse(allowIfElse);

            Direction dir = null;
            if (level.eIsSet(BlockyPackage.Literals.LEVEL__START_ORIENTATION)) {
                dir = level.getStartOrientation();
            } else if (level.getMap() != null) {
                Cell startCell = getStartCell(level.getMap());
                dir = SimulationEngine.determineStartOrientation(level, startCell);
            }
            if (dir == null) {
                switch (startDirCode) {
                    case 0:  dir = Direction.NORTH; break;
                    case 1:  dir = Direction.EAST;  break;
                    case 2:  dir = Direction.SOUTH; break;
                    case 3:  dir = Direction.WEST;  break;
                    default: dir = Direction.EAST;
                }
            }
            if (!isDebugActive) {
                level.setStartOrientation(dir);
            } else {
                System.out.println("[GameEngine] Ignoring startDirection update during active debug session.");
            }

            System.out.println("[GameEngine] Level=" + levelNum
                    + ", maxBlocks=" + (maxBlocks < 0 ? "unlimited" : maxBlocks)
                    + ", startDir=" + dir
                    + ", allowLoops=" + allowLoops
                    + ", allowConditionals=" + allowConds);
            return dir;
        } catch (Exception e) {
            System.err.println("[GameEngine] Failed to parse level metadata: " + e.getMessage());
            return null;
        }
    }

    private int extractJsonInt(String json, String key, int defaultVal) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?[0-9]+)");
        Matcher m = p.matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : defaultVal;
    }

    private boolean extractJsonBool(String json, String key, boolean defaultVal) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(json);
        return m.find() ? Boolean.parseBoolean(m.group(1)) : defaultVal;
    }

    /**
     * Cycles cell type between EMPTY -> WALL -> START -> GOAL -> EMPTY.
     */
    public void cycleCellType(GridMap map, int x, int y) {
        if (map == null) return;
        int index = y * map.getWidth() + x;
        if (index < 0 || index >= map.getCells().size()) return;
        Cell cell = map.getCells().get(index);

        switch (cell.getType()) {
            case EMPTY:
                cell.setType(CellType.WALL);
                break;
            case WALL:
                setUniqueCellType(map, cell, CellType.START);
                break;
            case START:
                setUniqueCellType(map, cell, CellType.GOAL);
                break;
            case GOAL:
                cell.setType(CellType.EMPTY);
                break;
            default:
                cell.setType(CellType.EMPTY);
                break;
        }
    }

    /**
     * Sets a cell to a unique type (START or GOAL), replacing any existing cell of that type with EMPTY.
     */
    public void setUniqueCellType(GridMap map, Cell targetCell, CellType type) {
        if (map == null || targetCell == null) return;
        for (Cell c : map.getCells()) {
            if (c.getType() == type) {
                c.setType(CellType.EMPTY);
            }
        }
        targetCell.setType(type);
    }

    /**
     * Builds the 2D grid representation for WebView consumption:
     * values 0=WALL, 1=EMPTY, 2=START, 3=GOAL.
     * Dimensions are [height][width] matching JS X[row][col].
     */
    public int[][] buildGridForWebView(GridMap map) {
        if (map == null) return new int[0][0];
        int w = map.getWidth();
        int h = map.getHeight();
        int[][] grid = new int[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                grid[y][x] = 0; // default WALL
            }
        }
        for (Cell c : map.getCells()) {
            int x = c.getX();
            int y = c.getY();
            if (y >= 0 && y < h && x >= 0 && x < w) {
                switch (c.getType()) {
                    case WALL:  grid[y][x] = 0; break;
                    case EMPTY: grid[y][x] = 1; break;
                    case START: grid[y][x] = 2; break;
                    case GOAL:  grid[y][x] = 3; break;
                    case DMG:   grid[y][x] = 1; break; // DMG is a walkable path tile in the webview
                    default:    grid[y][x] = 1; break;
                }
            }
        }
        return grid;
    }

    /**
     * Finds the START cell in the map.
     */
    public Cell getStartCell(GridMap map) {
        if (map == null || map.getCells() == null) return null;
        for (Cell c : map.getCells()) {
            if (c.getType() == CellType.START) return c;
        }
        return null;
    }

    /**
     * Finds the GOAL cell in the map.
     */
    public Cell getGoalCell(GridMap map) {
        if (map == null || map.getCells() == null) return null;
        for (Cell c : map.getCells()) {
            if (c.getType() == CellType.GOAL) return c;
        }
        return null;
    }

    /**
     * Finds the Direct Manipulation Goal (DMG) cell in the map (if any).
     */
    public Cell getDmgCell(GridMap map) {
        if (map == null || map.getCells() == null) return null;
        for (Cell c : map.getCells()) {
            if (c.getType() == CellType.DMG) return c;
        }
        return null;
    }

    /**
     * Converts EMF Direction to Blockly T code: NORTH=0, EAST=1, SOUTH=2, WEST=3.
     */
    public static int directionToT(Direction d) {
        if (d == null) return 1;
        switch (d) {
            case NORTH: return 0;
            case EAST:  return 1;
            case SOUTH: return 2;
            case WEST:  return 3;
            default:    return 1;
        }
    }

    /**
     * Finds adjacent cell in the given direction.
     */
    public static Cell getAdjacent(Cell c, Direction d) {
        return SimulationEngine.getAdjacent(c, d);
    }
}
