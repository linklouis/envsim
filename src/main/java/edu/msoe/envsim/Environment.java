package edu.msoe.envsim;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.entities.Entity;
import edu.msoe.envsim.entities.MobileEntity;
import edu.msoe.envsim.utilities.*;
import edu.msoe.envsim.utilities.Vector;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Environment extends Canvas {
    public static final int NUM_CELLS = 40;
    /**
     * The number of screen pixels per terrain pixel on each axis.
     */
    public static final int TERRAIN_RESOLUTION_RATIO = 20;

    @SuppressWarnings("unchecked")
    private final Set<Entity>[][] cells = (Set<Entity>[][]) new CopyOnWriteArraySet[NUM_CELLS][NUM_CELLS];
    private final TerrainType[][] terrainData;
    private final WritableImage terrainCacheImage;
    private final PixelWriter terrainPixelWriter;

    private final List<MobileEntity> mobileEntities = new CopyOnWriteArrayList<>();
    private final Queue<MobileEntity> pendingMobileEntities = new ConcurrentLinkedQueue<>();
    private final Queue<MobileEntity> mobileEntitiesToRemove = new ConcurrentLinkedQueue<>();

    private final List<Entity> stationaryEntities = new ArrayList<>();

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

    private final Consumer<Agent> onAgentSelected;
    private Agent selectedAgent;

    private final Runnable onSimModeStart;
    private final Runnable onEditModeStart;
    private double listMs;
    private double decideMs;
    private double stepMs;

    private enum Mode {
        SIMULATION,
        EDIT
    }


    public Environment(int viewWidth, int viewHeight, int worldWidth, int worldHeight, double zoom, boolean debugMode,
                       Runnable onSimModeStart, Runnable onEditModeStart, Consumer<Agent> onAgentSelected) {
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

        this.onAgentSelected = onAgentSelected;

        setupCells();
        setupMouseEvents();
        resetCanvas();
        setupSimulationThread();
    }

    /**
     *  Source: Google Gemini
     */
    private void setupMouseEvents() {
        setOnMousePressed(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();

            if (mode == Mode.EDIT && e.isPrimaryButtonDown()) {
                handleTerrainPaint(e.getX(), e.getY(), currentBrush);
            } else if (mode == Mode.SIMULATION && e.isPrimaryButtonDown()) {
                double worldX = (e.getX() - translateX) / scale;
                double worldY = (e.getY() - translateY) / scale;
                Vector clickPosition = new Vector(worldX, worldY);

                for (Entity entity : mobileEntities) {
                    if (entity instanceof Agent agent) {
                        if (agent.getPosition().distanceSquared(clickPosition) <= agent.getRadius() * agent.getRadius()) {
                            selectedAgent = agent;
                        }
                    }
                }
            }
        });

        // Panning controls
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
            double newScale = Math.clamp(scale * rawZoomFactor, 0.07, 10.0);
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

        for (Entity entity : stationaryEntities) {
            entity.paint(gc, entity == selectedAgent || debugMode);
        }

        for (MobileEntity entity : mobileEntities) {
            entity.paint(gc, entity == selectedAgent || debugMode);
        }

        if (debugMode) {
            for (DebugMessage message : debugMessages) {
                message.update();
            }
        }

        onAgentSelected.accept(selectedAgent);

        graphicsClock.update();
    }

    private void simulationStep() {
        long t0 = System.nanoTime();

        // Flush newly created entities into main list before iterating
        if (!pendingMobileEntities.isEmpty()) {
            for (Entity entity : pendingMobileEntities) {
                entity.onRegistered(); // TODO: Can I modify the add function of the collection to do the registration?
            }
            mobileEntities.addAll(pendingMobileEntities);
            pendingMobileEntities.clear();
        }

        if (!mobileEntitiesToRemove.isEmpty()) {
            for (Entity entity : mobileEntitiesToRemove) {
                getCell(entity.getCellIndex()).remove(entity); // TODO: Can I modify the remove function of the collection to do the removal?
            }
            mobileEntities.removeAll(mobileEntitiesToRemove);
            mobileEntitiesToRemove.clear();
        }
        long t1 = System.nanoTime();

        mobileEntities.parallelStream().forEach(MobileEntity::getDecision);
        long t2 = System.nanoTime();
        mobileEntities.parallelStream().forEach(MobileEntity::parallelStep);
        mobileEntities.parallelStream().forEach(MobileEntity::findCollisions);
        for (MobileEntity e : mobileEntities) e.step();
        for (Entity e : stationaryEntities) e.step();
        long t3 = System.nanoTime();
        listMs = listMs * 0.99 + (t1 - t0) / 1e6 * 0.01;   // moving averages
        decideMs   = decideMs   * 0.99 + (t2 - t1) / 1e6 * 0.01;
        stepMs = stepMs * 0.99 + (t3 - t2) / 1e6 * 0.01;

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

    /**
     * Saves the current terrain grid data to a file.
     * Source: Google Gemini
     */
    public void saveTerrain(File file) throws IOException {
        int width = terrainData.length;
        int height = terrainData[0].length;

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(width);
            out.writeInt(height);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    out.writeInt(terrainData[x][y].ordinal());
                }
            }
        }
    }

    /**
     * Loads terrain grid data from a file, updates the cache image, and refreshes the canvas.
     * Source: Google Gemini
     */
    public void loadTerrain(File file) throws IOException {
        TerrainType[] values = TerrainType.values();

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int width = in.readInt();
            int height = in.readInt();

            if (width != terrainData.length || height != terrainData[0].length) {
                throw new IllegalArgumentException("Terrain dimensions do not match the current world configuration!");
            }

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int ordinal = in.readInt();
                    TerrainType type = values[ordinal];
                    setTerrainTile(x, y, type);
                }
            }
        }
        resetCanvas();
    }

    public void resetCamera(double windowWidth) {
        scale = windowWidth / worldWidth;
        translateX = 0;
        translateY = 0;
    }


    public void clearSelectedAgent() {
        selectedAgent = null;
    }

    public void addEntity(Entity entity) {
        if (entity instanceof MobileEntity) {
            pendingMobileEntities.add((MobileEntity) entity);
        } else {
            stationaryEntities.add(entity);
            entity.onRegistered();
        }
    }

    public void removeEntity(Entity entity) {
        if (entity instanceof MobileEntity) {
            mobileEntitiesToRemove.add((MobileEntity) entity);
        } else {
            throw new RuntimeException("Cannot remove entity of type " + entity.getClass().getName() + ". Must inherit from MobileEntity.");
        }
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
        double maxDistSq = maxDistance * maxDistance;

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

                    double dist = entity.getPosition().distanceSquared(position);
                    if (dist <= maxDistSq) {
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
                (int) Math.clamp(rawIndex.x(), 0, NUM_CELLS - 1),
                (int) Math.clamp(rawIndex.y(), 0, NUM_CELLS - 1)
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
        for (Entity entity : mobileEntities) {
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

    public double getListMs() {
        return listMs;
    }

    public double getStepMs() {
        return stepMs;
    }

    public double getDecideMs() {
        return decideMs;
    }
}
