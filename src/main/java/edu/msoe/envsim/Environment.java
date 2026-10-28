package edu.msoe.envsim;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.entities.Entity;
import edu.msoe.envsim.entities.MobileEntity;
import edu.msoe.envsim.utilities.Vector;
import edu.msoe.envsim.utilities.apputils.Clock;
import edu.msoe.envsim.utilities.apputils.DebugMessage;
import edu.msoe.envsim.utilities.simutils.SpatialGrid;
import edu.msoe.envsim.utilities.simutils.TerrainType;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Environment extends Canvas {
    public static final int NUM_CELLS = 80;
    /**
     * The number of screen pixels per terrain pixel on each axis.
     */
    public static final int TERRAIN_RESOLUTION_RATIO = 20;
    private static final String DEFAULT_ENVIRONMENT = "environments/Standard.terrain";

    /**
     * Food and any other entity that never moves. Rebuilt only when something is added.
     * Entities here must never grow beyond the radius they were registered with.
     */
    private final SpatialGrid staticGrid;
    /** All mobile entities. Rebuilt in bulk whenever positions or membership change. */
    private final SpatialGrid agentGrid;
    private volatile boolean staticGridDirty = true;
    /** Only touched by the simulation thread. */
    private boolean agentGridCurrent = false;
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
     * {@link #simRunning} may be `false` even if this is `false`, if the game is in edit mode.
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

    private double currentMouseX;
    private double currentMouseY;
    private boolean mouseInside = false;

    private int lastTerrainX = -1;
    private int lastTerrainY = -1;
    private boolean isPainting = false;

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
        this.staticGrid = new SpatialGrid(NUM_CELLS, cellSize.x(), cellSize.y(), true);
        this.agentGrid = new SpatialGrid(NUM_CELLS, cellSize.x(), cellSize.y(), false);
        gc = getGraphicsContext2D();
        scale = zoom;

        int terrainWidth = worldWidth / TERRAIN_RESOLUTION_RATIO;
        int terrainHeight = worldHeight / TERRAIN_RESOLUTION_RATIO;
        terrainData = new TerrainType[terrainWidth][terrainHeight];
        terrainCacheImage = new WritableImage(terrainWidth, terrainHeight);
        terrainPixelWriter = terrainCacheImage.getPixelWriter();
        try {
            loadTerrain(new File(DEFAULT_ENVIRONMENT));
        } catch (Exception e) {
            fillTerrain();
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Startup Load Failed");
                alert.setHeaderText("Could not load default environment");
                Label contentLabel = new Label(e.getMessage() != null ? e.getMessage() : "An error occurred while loading " + DEFAULT_ENVIRONMENT);
                contentLabel.setWrapText(true);
                contentLabel.setMaxWidth(380);
                alert.getDialogPane().setContent(contentLabel);
                alert.getDialogPane().setPrefWidth(450);
                alert.showAndWait();
            });
        }

        this.onSimModeStart = onSimModeStart;
        this.onEditModeStart = onEditModeStart;

        this.debugMode = debugMode;

        simClock = new Clock();
        graphicsClock = new Clock();

        this.onAgentSelected = onAgentSelected;

        setupMouseEvents();
        resetCanvas();
        setupSimulationThread();
    }

    /**
     *  Source: Google Gemini
     */
    private void setupMouseEvents() {
        setOnMouseEntered(e -> mouseInside = true);
        setOnMouseExited(e -> mouseInside = false);

        setOnMouseMoved(e -> {
            currentMouseX = e.getX();
            currentMouseY = e.getY();
            mouseInside = true;
        });

        setOnMousePressed(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            currentMouseX = e.getX();
            currentMouseY = e.getY();
            mouseInside = true;

            if (mode == Mode.EDIT && e.isPrimaryButtonDown()) {
                isPainting = true;
                lastTerrainX = -1; // Reset so the first click starts fresh
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

        setOnMouseDragged(e -> {
            currentMouseX = e.getX();
            currentMouseY = e.getY();
            if (mode == Mode.EDIT && e.isPrimaryButtonDown()) {
                if (!isPainting) {
                    isPainting = true;
                    lastTerrainX = -1;
                }
                handleTerrainPaint(e.getX(), e.getY(), currentBrush);
            } else {
                // Normal panning
                translateX += e.getX() - lastMouseX;
                translateY += e.getY() - lastMouseY;
                lastMouseX = e.getX();
                lastMouseY = e.getY();
            }
        });

        setOnMouseReleased(e -> {
            isPainting = false;
            lastTerrainX = -1;
            lastTerrainY = -1;
        });

        // Zooming controls
        setOnScroll(e -> {
            double rawZoomFactor = e.getDeltaY() > 0 ? 1.05 : 1 / 1.05;
            double newScale = Math.clamp(scale * rawZoomFactor, 0.07, 10.0);
            double actualZoomFactor = newScale / scale;

            double mouseX = e.getX();
            double mouseY = e.getY();

            translateX = mouseX - (mouseX - translateX) * actualZoomFactor;
            translateY = mouseY - (mouseY - translateY) * actualZoomFactor;
            scale = newScale;
        });
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

    private void fillTerrain() {
        for (int x = 0; x < terrainData.length; x++) {
            for (int y = 0; y < terrainData[0].length; y++) {
                if (x < terrainData.length / 2) {
                    setTerrainTile(x, y, TerrainType.STANDARD);
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
        if (debugMode) {
            gc.setStroke(Color.web("#333333"));
            gc.setLineWidth(1.0 / scale);
            for (int x = 0; x <= worldWidth; x += worldWidth / NUM_CELLS) {
                gc.strokeLine(x, 0, x, worldHeight);
            }
            for (int y = 0; y <= worldHeight; y += worldHeight / NUM_CELLS) {
                gc.strokeLine(0, y, worldWidth, y);
            }
        }

        // Draw world border
        gc.setStroke(Color.web("#555555"));
        gc.strokeRect(0, 0, worldWidth, worldHeight);

        // --- DRAW BRUSH OUTLINE IN EDIT MODE ---
        if (mode == Mode.EDIT && mouseInside) {
            double worldX = (currentMouseX - translateX) / scale;
            double worldY = (currentMouseY - translateY) / scale;

            int centerTerrainX = (int) (worldX / TERRAIN_RESOLUTION_RATIO);
            int centerTerrainY = (int) (worldY / TERRAIN_RESOLUTION_RATIO);

            // Calculate world coordinates for a square brush
            double brushWorldX = (centerTerrainX - brushSize) * TERRAIN_RESOLUTION_RATIO;
            double brushWorldY = (centerTerrainY - brushSize) * TERRAIN_RESOLUTION_RATIO;
            double brushWorldSize = (2 * brushSize + 1) * TERRAIN_RESOLUTION_RATIO;

            gc.setStroke(Color.WHITE);
            // Divide by scale so the outline stays 1 pixel wide regardless of zoom level
            gc.setLineWidth(1.0 / scale);

            // Option A: Square brush outline (matches your current square painting loop)
            gc.strokeRect(brushWorldX, brushWorldY, brushWorldSize, brushWorldSize);

            /*
             * Option B: If you decide to switch to a circular brush later,
             * use this instead of strokeRect:
             *
             * double radiusWorld = (brushSize + 0.5) * TERRAIN_RESOLUTION_RATIO;
             * gc.strokeOval(
             *     (centerTerrainX + 0.5) * TERRAIN_RESOLUTION_RATIO - radiusWorld,
             *     (centerTerrainY + 0.5) * TERRAIN_RESOLUTION_RATIO - radiusWorld,
             *     radiusWorld * 2, radiusWorld * 2
             * );
             */
        }

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

        if (staticGridDirty) {
            staticGridDirty = false;
            staticGrid.rebuild(stationaryEntities);
        }

        // Flush newly created entities into main list before iterating
        if (!pendingMobileEntities.isEmpty()) {
            for (Entity entity : pendingMobileEntities) {
                entity.onRegistered(); // TODO: Can I modify the add function of the collection to do the registration?
            }
            mobileEntities.addAll(pendingMobileEntities);
            pendingMobileEntities.clear();
            agentGridCurrent = false;
        }

        if (!mobileEntitiesToRemove.isEmpty()) {
            mobileEntities.removeAll(mobileEntitiesToRemove);
            mobileEntitiesToRemove.clear();
            agentGridCurrent = false;
        }

        // Positions only change in the move pass, and the grid is rebuilt right after it,
        // so the grid is still valid here unless agents were added or removed above.
        if (!agentGridCurrent) {
            agentGrid.rebuild(mobileEntities);
            agentGridCurrent = true;
        }
        long t1 = System.nanoTime();

        mobileEntities.parallelStream().forEach(MobileEntity::getDecision);
        long t2 = System.nanoTime();
        Stream.concat(mobileEntities.stream(), stationaryEntities.stream())
                .parallel().forEach(Entity::parallelStep);
        agentGrid.rebuild(mobileEntities);   // positions just changed; collision queries need the new ones
        mobileEntities.parallelStream().forEach(MobileEntity::findCollisions);
        for (MobileEntity e : mobileEntities) e.step();
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

        int currentTerrainX = (int) (worldX / TERRAIN_RESOLUTION_RATIO);
        int currentTerrainY = (int) (worldY / TERRAIN_RESOLUTION_RATIO);

        // If this is the start of a stroke, initialize last position to current position
        if (lastTerrainX == -1 || lastTerrainY == -1) {
            lastTerrainX = currentTerrainX;
            lastTerrainY = currentTerrainY;
        }

        // Paint an unbroken line of brush shapes from the last position to the current position
        paintLine(lastTerrainX, lastTerrainY, currentTerrainX, currentTerrainY, brushType);

        // Update last position
        lastTerrainX = currentTerrainX;
        lastTerrainY = currentTerrainY;

        resetCanvas();
    }

    /**
     * Bresenham's line algorithm adapted for grid-based terrain painting.
     * Ensures zero gaps no matter how fast the mouse moves.
     * Source: Google Gemini
     */
    private void paintLine(int x0, int y0, int x1, int y1, TerrainType brushType) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            paintBrushAt(x0, y0, brushType);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /**
     * Source: Google Gemini
     * @param centerTerrainX
     * @param centerTerrainY
     * @param brushType
     */
    private void paintBrushAt(int centerTerrainX, int centerTerrainY, TerrainType brushType) {
        for (int x = centerTerrainX - brushSize; x <= centerTerrainX + brushSize; x++) {
            for (int y = centerTerrainY - brushSize; y <= centerTerrainY + brushSize; y++) {
                // OPTIONAL: If you want a circular brush instead of square, change this to:
                // if (Math.hypot(x - centerTerrainX, y - centerTerrainY) <= brushSize) {
                setTerrainTile(x, y, brushType);
                // }
            }
        }
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

    public static void createDefaultEnvironments(int worldWidth, int worldHeight) {
        File dir = new File("environments");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        int terrainWidth = worldWidth / TERRAIN_RESOLUTION_RATIO;
        int terrainHeight = worldHeight / TERRAIN_RESOLUTION_RATIO;

        File standardFile = new File(dir, "Standard.terrain");
        if (!standardFile.exists()) {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(standardFile)))) {
                out.writeInt(terrainWidth);
                out.writeInt(terrainHeight);
                for (int x = 0; x < terrainWidth; x++) {
                    for (int y = 0; y < terrainHeight; y++) {
                        TerrainType type = (x < terrainWidth / 2) ? TerrainType.STANDARD : TerrainType.WATER;
                        out.writeInt(type.ordinal());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        File islandFile = new File(dir, "Island.terrain");
        if (!islandFile.exists()) {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(islandFile)))) {
                out.writeInt(terrainWidth);
                out.writeInt(terrainHeight);
                double centerX = terrainWidth / 2.0;
                double centerY = terrainHeight / 2.0;
                double radius = Math.min(terrainWidth, terrainHeight) * 0.35;
                for (int x = 0; x < terrainWidth; x++) {
                    for (int y = 0; y < terrainHeight; y++) {
                        double dist = Math.hypot(x - centerX, y - centerY);
                        TerrainType type = (dist <= radius) ? TerrainType.STANDARD : TerrainType.WATER;
                        out.writeInt(type.ordinal());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        File grasslandFile = new File(dir, "Grassland.terrain");
        if (!grasslandFile.exists()) {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(grasslandFile)))) {
                out.writeInt(terrainWidth);
                out.writeInt(terrainHeight);
                for (int x = 0; x < terrainWidth; x++) {
                    for (int y = 0; y < terrainHeight; y++) {
                        out.writeInt(TerrainType.STANDARD.ordinal());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static @NotNull WritableImage loadTerrainThumbnail(File file, int thumbW, int thumbH) {
        TerrainType[] values = TerrainType.values();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int width = in.readInt();
            int height = in.readInt();
            WritableImage img = new WritableImage(thumbW, thumbH);
            PixelWriter writer = img.getPixelWriter();

            TerrainType[][] data = new TerrainType[width][height];
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int ordinal = in.readInt();
                    data[x][y] = values[ordinal];
                }
            }

            for (int tx = 0; tx < thumbW; tx++) {
                for (int ty = 0; ty < thumbH; ty++) {
                    int srcX = (int) ((double) tx / thumbW * width);
                    int srcY = (int) ((double) ty / thumbH * height);
                    TerrainType t = data[Math.min(srcX, width - 1)][Math.min(srcY, height - 1)];
                    writer.setColor(tx, ty, t.getColor());
                }
            }
            return img;
        } catch (IOException e) {
            WritableImage fallback = new WritableImage(thumbW, thumbH);
            PixelWriter writer = fallback.getPixelWriter();
            for (int x = 0; x < thumbW; x++) {
                for (int y = 0; y < thumbH; y++) {
                    writer.setColor(x, y, Color.DARKGRAY);
                }
            }
            return fallback;
        }
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
        if (entity instanceof MobileEntity mEntity) {
            pendingMobileEntities.add(mEntity);
        } else {
            stationaryEntities.add(entity);
            entity.onRegistered();
            staticGridDirty = true;
        }
    }

    public void removeEntity(Entity entity) {
        if (entity instanceof MobileEntity mEntity) {
            mobileEntitiesToRemove.add(mEntity);
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

    /**
     * Calls {@code visitor} for every entity other than {@code self} that is within {@code maxDistance} of it.
     * Static entities (food) are visited first, then mobile ones.
     * <p>
     * Safe to call from many threads at once, as long as nothing is rebuilding the grids.
     * </p>
     *
     * @param maxDistSquared {@code maxDistance * maxDistance}, passed in so callers can cache it.
     */
    public void visitEntitiesInRange(@NotNull Entity self, double maxDistance, double maxDistSquared,
                                     @NotNull SpatialGrid.Visitor visitor) {
        double px = self.x();
        double py = self.y();
        staticGrid.forEachInRange(self, px, py, maxDistance, maxDistSquared, visitor);
        agentGrid.forEachInRange(self, px, py, maxDistance, maxDistSquared, visitor);
    }

    /**
     * Adds every entity that {@code entity} is colliding with, and that satisfies {@code requirement}, to {@code out}.
     * Lets callers reuse one list instead of allocating one per query.
     */
    public void collectCollisions(@NotNull Entity entity, BiPredicate<Entity, Entity> collisionRequirement, @NotNull List<Entity> out) {
        if (!entity.isRegistered()) {
            return;
        }
        staticGrid.collectCollisions(entity, collisionRequirement, out);
        agentGrid.collectCollisions(entity, collisionRequirement, out);
    }

    public List<Entity> getCollisions(@NotNull Entity entity, BiPredicate<Entity, Entity> collisionRequirement) {
        List<Entity> collisions = new ArrayList<>();
        collectCollisions(entity, collisionRequirement, collisions);
        return collisions;
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

    public int getTerrainWidth() {
        return terrainData.length;
    }

    public int getTerrainHeight() {
        return terrainData[0].length;
    }
}