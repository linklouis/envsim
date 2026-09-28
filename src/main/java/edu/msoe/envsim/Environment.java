package edu.msoe.envsim;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class Environment extends Canvas {
    private List<Entity> entities = new ArrayList<>();
    private final List<Entity> pendingEntities = new java.util.concurrent.CopyOnWriteArrayList<>(); // TODO
    private final List<Entity> toRemove = new ArrayList<>();
    private final GraphicsContext gc;

    private final int worldWidth;
    private final int worldHeight;
    private double scale = 1/2.5;
    private double translateX = 0.0;
    private double translateY = 0.0;
    private double lastMouseX;
    private double lastMouseY;

    public Environment(int viewWidth, int viewHeight, int worldWidth, int worldHeight) {
        super(viewWidth, viewHeight);
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        gc = getGraphicsContext2D();

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

        reset();

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                step();
            }
        };
        timer.start();
    }

    public void reset() {
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

    private void step() {
        // Flush newly created entities into main list before iterating
        if (!pendingEntities.isEmpty()) {
            entities.addAll(pendingEntities);
            pendingEntities.clear();
        }

        if (!toRemove.isEmpty()) {
            entities.removeAll(toRemove);
            toRemove.clear();
        }

        reset();

//        entities.parallelStream().forEach(Entity::step);
        for (Entity entity : entities) {
            entity.step();
            entity.paint(gc);
        }

//        for (Entity entity : entities) {
//            if (entity instanceof Agent) {
//                ((Agent) entity).brain.display(gc, 500, 500);
//                break;
//            }
//        }
    }

    public void addEntity(Entity entity) {
        pendingEntities.add(entity);
    }

    public void removeEntity(Entity entity) {
        toRemove.add(entity);
    }

    public Vector getSize() {
        return new Vector(worldWidth, worldHeight);
    }

    public List<Entity> getCollisions(Entity entity) {
        return getCollisions(entity, (_, _) -> true);
    }

    public List<Entity> getCollisions(Entity entity, CollisionRequirement collisionRequirement) {
        if (!entities.contains(entity)) {
            return Collections.emptyList();
        }

        List<Entity> collisions = new ArrayList<>();
        for (Entity entity2 : entities) {
            if (entity != entity2 && entity.isColliding(entity2) && collisionRequirement.checkCollision(entity, entity2)) {
                collisions.add(entity2);
            }
        }
        return collisions;
    }

    public List<Entity> getEntitiesInRange(Vector position, Predicate<Entity> predicate, double maxDistance) {
        List<Entity> entitiesInRange = new ArrayList<>();

        for (Entity entity : entities) {
            if (!predicate.test(entity)) {
                continue;
            }

            double dist = entity.getPosition().distance(position);
            if (dist < maxDistance) {
                entitiesInRange.add(entity);
            }
        }

        return entitiesInRange;
    }
}
