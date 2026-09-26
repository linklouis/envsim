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

    public Environment(int width, int height) {
        super(width, height);
        gc = getGraphicsContext2D();

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
        // Clear background
        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, getWidth(), getHeight());

        // Grid overlay
        gc.setStroke(Color.web("#333333"));
        gc.setLineWidth(1.0);
        for (int x = 0; x < getWidth(); x += 40) {
            gc.strokeLine(x, 0, x, getHeight());
        }
        for (int y = 0; y < getHeight(); y += 40) {
            gc.strokeLine(0, y, getWidth(), y);
        }
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
    }

    public void addEntity(Entity entity) {
        pendingEntities.add(entity);
    }

    public void removeEntity(Entity entity) {
        toRemove.add(entity);
    }

    public Vector getSize() {
        return new Vector(getWidth(), getHeight());
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

    public Entity getClosestEntity(Vector position, Predicate<Entity> predicate) {
        double minDist = Double.MAX_VALUE;
        Entity closestEntity = null;

        for (Entity entity : entities) {
            if (!predicate.test(entity)) continue;

            double dist = entity.getPosition().distance(position);
            if (dist < minDist) {
                minDist = dist;
                closestEntity = entity;
            }
        }

        return closestEntity;
    }
}
