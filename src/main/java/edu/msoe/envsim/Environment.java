package edu.msoe.envsim;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.entities.Entity;
import edu.msoe.envsim.utilities.*;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class Environment extends Canvas {
    public static final int NUM_CELLS = 50;

    @SuppressWarnings("unchecked")
    private final Cell[][] cells = new Cell[NUM_CELLS][NUM_CELLS];

    private final List<Entity> entities = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Entity> pendingEntities = new java.util.concurrent.CopyOnWriteArrayList<>(); // TODO
    private final List<Entity> toRemove = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final List<DebugMessage> debugMessages = new ArrayList<>();
    private boolean debugMode;

    private final GraphicsContext gc;
    private final Clock clock;
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

        this.onSimModeStart = onSimModeStart;
        this.onEditModeStart = onEditModeStart;

        this.debugMode = debugMode;

        clock = new Clock();

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
        });

        setOnMouseDragged(e -> {
            translateX += e.getX() - lastMouseX;
            translateY += e.getY() - lastMouseY;
            lastMouseX = e.getX();
            lastMouseY = e.getY();
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
                cells[i][j] = new Cell();
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
    }

    private void pauseStateless() {
        graphicsTimer.stop();
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


    public void resetCanvas() {
        // 1. Clear the entire viewport (outside the world bounds)
        gc.setTransform(1, 0, 0, 1, 0, 0);
        gc.setFill(Color.web("#151515")); // Darker void color
        gc.fillRect(0, 0, getWidth(), getHeight());

        // 2. Apply camera transforms
        gc.setTransform(scale, 0, 0, scale, translateX, translateY);

        // 3. Draw the actual world background
        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, worldWidth, worldHeight);

        // 4. Draw grid overlay
        gc.setStroke(Color.web("#333333"));
        gc.setLineWidth(1.0 / scale); // Prevent grid lines from thickening when zooming
        for (int x = 0; x <= worldWidth; x += 40) {
            gc.strokeLine(x, 0, x, worldHeight);
        }
        for (int y = 0; y <= worldHeight; y += 40) {
            gc.strokeLine(0, y, worldWidth, y);
        }

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
    }

    private void simulationStep() {
        // Flush newly created entities into main list before iterating
        if (!pendingEntities.isEmpty()) {
            for (Entity entity : pendingEntities) {
                entities.add(entity);
                updateCell(entity);
            }
            pendingEntities.clear();
        }

        if (!toRemove.isEmpty()) {
            for (Entity entity : toRemove) {
                entities.remove(entity);
                getCell(entity.getCell()).remove(entity);
            }
            toRemove.clear();
        }

        entities.parallelStream().forEach(Entity::getAction);

        for (Entity entity : entities) {
            entity.step();
        }

        clock.update();
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

    public List<Entity> getCollisions(Entity entity, CollisionRequirement collisionRequirement) {
        if (!entities.contains(entity)) {
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

    public Vector updateCell(@NotNull Entity entity) {
        if (entity.getCell() != null) {
            getCell(entity.getCell()).remove(entity);
        }

        Vector cell = getCellIndexFromPosition(entity.getPosition());
        getCell(cell).add(entity);

        return cell;
    }

    @Contract("_ -> new")
    private @NotNull Vector getCellIndexFromPosition(@NotNull Vector position) {
        Vector rawIndex = position.elementWiseDivision(cellSize);
        return new Vector(
                Math.clamp(rawIndex.x(), 0, NUM_CELLS - 1),
                Math.clamp(rawIndex.y(), 0, NUM_CELLS - 1)
        );
    }

    private Cell getCellFromPosition(@NotNull Vector position) {
        return getCell(getCellIndexFromPosition(position));
    }

    private Cell getCell(@NotNull Vector indices) {
        return cells[(int) indices.x()][(int) indices.y()];
    }

    public TerrainType getTerrainType(Vector position) {
        return getCellFromPosition(position).getTerrainType();
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

    public double getSimUpdateRate() {
        return clock.getFormattedUpdateRate(2);
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
}
