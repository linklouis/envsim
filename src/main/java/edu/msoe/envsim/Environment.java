package edu.msoe.envsim;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.entities.Entity;
import edu.msoe.envsim.utilities.*;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Predicate;

public class Environment extends Canvas {
    public static final int NUM_CELLS = 50;
    /**
     * The number of screen pixels per terrain pixel on each axis.
     */
    public static final int TERRAIN_RESOLUTION_RATIO = 20;

    @SuppressWarnings("unchecked")
    private final Set<Entity>[][] cells = (Set<Entity>[][]) new CopyOnWriteArraySet[NUM_CELLS][NUM_CELLS];
    private final TerrainType[][] terrainData;
    private final WritableImage terrainCacheImage;
    private final PixelWriter terrainPixelWriter;

    private final List<Entity> entities = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Entity> pendingEntities = new java.util.concurrent.CopyOnWriteArrayList<>(); // TODO
    private final List<Entity> toRemove = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final List<DebugMessage> debugMessages = new ArrayList<>();
    private boolean debugMode;

    private final GraphicsContext gc;
    private final Clock simClock;
    private final Clock graphicsClock;
    private final AnimationTimer graphicsTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            visualStep();
        }
    };

    /**
     * Synchronization anchor for the simulation thread.
     * Helps with pausing.
     */
    private final Object pauseLock = new Object();
    /**
     * A flag for if the simulation is actually running.
     */
    private volatile boolean simRunning = false;
    /**
     * A flag for if the user has paused the simulation.
     * {@link simRunning} may be `false` even if this is `false`, if the game is in edit mode.
     */
    private boolean simPaused = false;

    private Mode mode = Mode.EDIT;
    private TerrainType currentBrush = TerrainType.STANDARD;
    private int brushSize = 5;

    private final int worldWidth;
    private final int worldHeight;
    private final Vector cellSize;
    private double scale;
    private double translateX = 0.0;
    private double translateY = 0.0;
    private double lastMouseX;
    private double lastMouseY;

    private final Runnable onSimModeStart;
    private final Runnable onEditModeStart;

    private enum Mode {
        SIMULATION,
        EDIT
    }


    public Environment(int viewWidth, int viewHeight, int worldWidth, int worldHeight, double zoom, boolean debugMode, Runnable onSimModeStart, Runnable onEditModeStart) {
        super(viewWidth, viewHeight);
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.cellSize = new Vector((double) worldWidth / (NUM_CELLS - 1), (double) worldHeight / (NUM_CELLS - 1));
        gc = getGraphicsContext2D();
        scale = zoom;

        int terrainWidth = worldWidth / TERRAIN_RESOLUTION_RATIO;
        int terrainHeight = worldHeight / TERRAIN_RESOLUTION_RATIO;
        terrainData = new TerrainType[terrainWidth][terrainHeight];
        terrainCacheImage = new WritableImage(terrainWidth, terrainHeight);
        terrainPixelWriter = terrainCacheImage.getPixelWriter();
        fillTerrain(TerrainType.STANDARD);

        this.onSimModeStart = onSimModeStart;
        this.onEditModeStart = onEditModeStart;

        this.debugMode = debugMode;

        simClock = new Clock();
        graphicsClock = new Clock();

        setupCells();
        setupCameraControls();
        resetCanvas();
        setupSimulationThread();
    }

    private void setupCameraControls() {
        // Panning controls
        setOnMousePressed(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();

            if (mode == Mode.EDIT && e.isPrimaryButtonDown()) {
                handleTerrainPaint(e.getX(), e.getY(), currentBrush);
            }
        });

        setOnMouseDragged(e -> {
            if (mode == Mode.EDIT && e.isPrimaryButtonDown()) {
                handleTerrainPaint(e.getX(), e.getY(), currentBrush);
            } else {
                // Normal panning
                translateX += e.getX() - lastMouseX;
                translateY += e.getY() - lastMouseY;
                lastMouseX = e.getX();
                lastMouseY = e.getY();
            }
        });

        // Zooming controls
        setOnScroll(e -> {
            double rawZoomFactor = e.getDeltaY() > 0 ? 1.05 : 1 / 1.05;
            double newScale = Math.clamp(scale * rawZoomFactor, 0.1, 10.0);
            double actualZoomFactor = newScale / scale;

            double mouseX = e.getX();
            double mouseY = e.getY();

            // Adjust translation to keep the zoom focused on the cursor
            translateX = mouseX - (mouseX - translateX) * actualZoomFactor;
            translateY = mouseY - (mouseY - translateY) * actualZoomFactor;
            scale = newScale;
        });
    }

    private void setupCells() {
        for (int i = 0; i < NUM_CELLS; i++) {
            for (int j = 0; j < NUM_CELLS; j++) {
                cells[i][j] = new CopyOnWriteArraySet<>();
            }
        }
    }

    /**
     * Source: Google Gemini
     * @param terrainX
     * @param terrainY
     * @param brushType
     */
    private void setTerrainTile(int terrainX, int terrainY, TerrainType brushType) {
        if (terrainX >= 0 && terrainX < terrainData.length && terrainY >= 0 && terrainY < terrainData[0].length) {
            terrainData[terrainX][terrainY] = brushType;
            terrainPixelWriter.setColor(terrainX, terrainY, brushType.getColor());
        }
    }

    private void fillTerrain(TerrainType terrainType) {
        for (int x = 0; x < terrainData.length; x++) {
            for (int y = 0; y < terrainData[0].length; y++) {
                if (x < terrainData.length / 2) {
                    setTerrainTile(x, y, terrainType);
                } else {
                    setTerrainTile(x, y, TerrainType.WATER);
                }
            }
        }
    }

    private void setupSimulationThread() {
        Thread simulationThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (pauseLock) {
                    while (!simRunning) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }

                simulationStep();
            }
        });
        simulationThread.start();
    }

    public void startSimulation() {
        if (simRunning) return;
        if (mode == Mode.EDIT) simMode();
        graphicsTimer.start();

        simPaused = false;
        synchronized (pauseLock) {
            simRunning = true;
            pauseLock.notifyAll();
        }
        simClock.start();
    }

    private void pauseStateless() {
        simRunning = false;
    }

    public void pauseSimulation() {
        pauseStateless();
        simPaused = true;
    }

    public void toggleSimulation() {
        if (simRunning) {
            pauseSimulation();
        } else {
            startSimulation();
        }
    }

    public void simMode() {
        mode = Mode.SIMULATION;
        if (simPaused) {
            pauseStateless();
        } else {
            startSimulation();
        }
        onSimModeStart.run();
    }

    public void editMode() {
        mode = Mode.EDIT;
        pauseStateless();
        onEditModeStart.run();
    }

    public void toggleEditMode() {
        if (mode == Mode.EDIT) {
            mode = Mode.SIMULATION;
            simMode();
        } else {
            mode = Mode.EDIT;
            editMode();
        }
    }

    /**
     * Source: Google Gemini
     */
    public void resetCanvas() {
        // Clear the entire viewport
        gc.setTransform(1, 0, 0, 1, 0, 0);
        gc.setFill(Color.web("#151515"));
        gc.fillRect(0, 0, getWidth(), getHeight());

        // Apply camera transforms
        gc.setTransform(scale, 0, 0, scale, translateX, translateY);

        // 3. Draw base world background
//        gc.setFill(Color.web("#1e1e1e"));
//        gc.fillRect(0, 0, worldWidth, worldHeight);

        // 4. Draw terrain
        gc.setImageSmoothing(false); // Keeps pixel art/grid lines crisp when scaled up
        gc.drawImage(terrainCacheImage, 0, 0, worldWidth, worldHeight);

        // 5. Draw grid lines overlay
//        gc.setStroke(Color.web("#333333"));
//        gc.setLineWidth(1.0 / scale);
//        for (int x = 0; x <= worldWidth; x += worldWidth / NUM_CELLS) {
//            gc.strokeLine(x, 0, x, worldHeight);
//        }
//        for (int y = 0; y <= worldHeight; y += worldHeight / NUM_CELLS) {
//            gc.strokeLine(0, y, worldWidth, y);
//        }

        // Draw world border
        gc.setStroke(Color.web("#555555"));
        gc.strokeRect(0, 0, worldWidth, worldHeight);

        // Reset stroke width for entities
        gc.setLineWidth(1.0);
    }

    private void visualStep() {
        resetCanvas();

        for (Entity entity : entities) {
            entity.paint(gc);
        }

        if (debugMode) {
            for (DebugMessage message : debugMessages) {
                message.update();
            }
        }

        graphicsClock.update();
    }

    /**
     * TODO: Add a way to reduce precision for speed
     */
    private void simulationStep() {
        // Flush newly created entities into main list before iterating
        if (!pendingEntities.isEmpty()) {
            for (Entity entity : pendingEntities) {
                entity.onRegistered();
            }
            entities.addAll(pendingEntities);
            pendingEntities.clear();
        }

        if (!toRemove.isEmpty()) {
            for (Entity entity : toRemove) {
                getCell(entity.getCellIndex()).remove(entity);
            }
            entities.removeAll(toRemove);
            toRemove.clear();
        }

        entities.parallelStream().forEach(Entity::getAction);

        for (Entity entity : entities) {
            entity.step();
        }

        simClock.update();
    }


    /**
     * Source: Google Gemini
     * @param screenX
     * @param screenY
     * @param brushType
     */
    private void handleTerrainPaint(double screenX, double screenY, TerrainType brushType) {
        if (mode != Mode.EDIT) return;

        double worldX = (screenX - translateX) / scale;
        double worldY = (screenY - translateY) / scale;

        int centerTerrainX = (int) (worldX / TERRAIN_RESOLUTION_RATIO);
        int centerTerrainY = (int) (worldY / TERRAIN_RESOLUTION_RATIO);

        for (int x = centerTerrainX - brushSize; x <= centerTerrainX + brushSize; x++) {
            for (int y = centerTerrainY - brushSize; y <= centerTerrainY + brushSize; y++) {

                // OPTIONAL: Uncomment the line below if you want a circular brush instead of a square one
                // if (Math.hypot(x - centerTerrainX, y - centerTerrainY) <= brushRadius) {
                setTerrainTile(x, y, brushType);
                // }

            }
        }
        resetCanvas();
    }


    public void addEntity(Entity entity) {
        pendingEntities.add(entity);
    }

    public void removeEntity(Entity entity) {
        toRemove.add(entity);
    }

    public void addDebugMessage(DebugMessage debugMessage) {
        debugMessages.add(debugMessage);
    }

    public Vector getSize() {
        return new Vector(worldWidth, worldHeight);
    }

    public List<Entity> getCollisions(Entity entity) {
        return getCollisions(entity, (_, _) -> true);
    }

    public List<Entity> getEntitiesInRange(@NotNull Vector position, Predicate<Entity> predicate, double maxDistance) {
        List<Entity> entitiesInRange = new ArrayList<>();

        // Calculate the min and max cell indices that overlap with the vision bounding box
        int minCellX = (int) Math.max(0, (position.x() - maxDistance) / cellSize.x());
        int maxCellX = (int) Math.min(NUM_CELLS - 1, (position.x() + maxDistance) / cellSize.x());
        int minCellY = (int) Math.max(0, (position.y() - maxDistance) / cellSize.y());
        int maxCellY = (int) Math.min(NUM_CELLS - 1, (position.y() + maxDistance) / cellSize.y());

        // Iterate through only the cells within that bounding box
        for (int x = minCellX; x <= maxCellX; x++) {
            for (int y = minCellY; y <= maxCellY; y++) {
                for (Entity entity : cells[x][y]) {
                    if (!predicate.test(entity)) {
                        continue;
                    }

                    double dist = entity.getPosition().distance(position);
                    if (dist <= maxDistance) {
                        entitiesInRange.add(entity);
                    }
                }
            }
        }

        return entitiesInRange;
    }

    public List<Entity> getCollisions(@NotNull Entity entity, CollisionRequirement collisionRequirement) {
        if (!entity.isRegistered()) {
            return Collections.emptyList();
        }

        List<Entity> collisions = new ArrayList<>();

        // Add a buffer to account for the other entity's radius (assuming max entity radius is ~100)
        double searchRadius = entity.getRadius() + 100;

        // Calculate bounding box for collision checks
        int minCellX = (int) Math.max(0, (entity.x() - searchRadius) / cellSize.x());
        int maxCellX = (int) Math.min(NUM_CELLS - 1, (entity.x() + searchRadius) / cellSize.x());
        int minCellY = (int) Math.max(0, (entity.y() - searchRadius) / cellSize.y());
        int maxCellY = (int) Math.min(NUM_CELLS - 1, (entity.y() + searchRadius) / cellSize.y());

        for (int x = minCellX; x <= maxCellX; x++) {
            for (int y = minCellY; y <= maxCellY; y++) {
                for (Entity entity2 : cells[x][y]) {
                    // Check strict collision math for entities in adjacent cells
                    if (entity != entity2 && entity.isColliding(entity2) && collisionRequirement.checkCollision(entity, entity2)) {
                        collisions.add(entity2);
                    }
                }
            }
        }

        return collisions;
    }

    /**
     * Updates the cell placement for the given {@link Entity}.
     *
     * @param entity The {@link Entity} to be updated
     * @return {@link Vector} representing the x and y index of the new cell the {@link Entity} was assigned.
     */
    public Vector updateCell(@NotNull Entity entity) {
        Vector currentCellIndex = getCellIndexFromPosition(entity.getPosition());
        Vector previousCellIndex = entity.getCellIndex();

        if (currentCellIndex.equals(previousCellIndex)) {
            return currentCellIndex;
        }

        if (previousCellIndex != null) {
            getCell(previousCellIndex).remove(entity);
        }
        getCell(currentCellIndex).add(entity);

        return currentCellIndex;
    }

    @Contract("_ -> new")
    private @NotNull Vector getCellIndexFromPosition(@NotNull Vector position) {
        Vector rawIndex = position.elementWiseDivision(cellSize);
        return new Vector(
                Math.clamp(rawIndex.x(), 0, NUM_CELLS - 1),
                Math.clamp(rawIndex.y(), 0, NUM_CELLS - 1)
        );
    }

    private Set<Entity> getCellFromPosition(@NotNull Vector position) {
        return getCell(getCellIndexFromPosition(position));
    }

    private Set<Entity> getCell(@NotNull Vector indices) {
        return cells[(int) indices.x()][(int) indices.y()];
    }

    public TerrainType getTerrainType(@NotNull Vector position) {
        int terrainX = (int) (position.x() / TERRAIN_RESOLUTION_RATIO);
        int terrainY = (int) (position.y() / TERRAIN_RESOLUTION_RATIO);

        if (terrainX >= 0 && terrainX < terrainData.length && terrainY >= 0 && terrainY < terrainData[0].length) {
            return terrainData[terrainX][terrainY];
        }
        return TerrainType.STANDARD; // Or return a default out-of-bounds terrain type
    }

    public TerrainType getStandingTerrainType(@NotNull Entity entity) {
        return getTerrainType(entity.getPosition());
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public double prefWidth(double height) {
        return getWidth();
    }

    @Override
    public double prefHeight(double width) {
        return getHeight();
    }

    public double getFrameRate() {
        return graphicsClock.getFormattedUpdateRate(2);
    }

    public double getSimUpdateRate() {
        return simClock.getFormattedUpdateRate(2);
    }

    public int getNumAgents() {
        int num = 0;
        for (Entity entity : entities) {
            if (entity instanceof Agent) {
                num++;
            }
        }
        return num;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public void setBrushSize(int brushRadius) {
        this.brushSize = Math.max(0, brushRadius); // Prevent negative radius
    }

    public void setBrush(TerrainType newBrush) {
        this.currentBrush = newBrush;
    }

    public void setMaxSimUpdateRate(double maxSimUpdateRate) {
        simClock.setMaxRate(maxSimUpdateRate);
    }
}
