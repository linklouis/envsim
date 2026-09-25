package edu.msoe.envsim;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Agent  extends Entity {
    private @NotNull Environment environment;

    // Genetic Attributes:
    /**
     * How far the Agent can move each time step.
     * Higher values consume more {@link energy} each time step.
     * TODO: Should the agent always move at max speed? Maybe add sprinting?
     */
    private double speed;
    /**
     * The "Max Health" of the Agent.
     * The actual size gene that gets passed to descendants.
     * Higher values consume more {@link energy} each time step.
     */
    private double size;
    /**
     * How close an entity must be for the Agent to see it.
     * Higher values consume more {@link energy} each time step.
     * TODO: Should the agent be able to see obstacles? (Probably not)
     */
    private double visionRadius;

    // State:
    private Vector direction;
    /**
     * The amount of energy the Agent currently has.
     * Energy is consumed passively each time step based on the {@link size} and {@link visionRadius} attributes,
     * and actively consumed by the moving and reproducing.
     * When reproducing, half of the Agent's current energy is passed to the child.
     * Can be replenished by eating.
     */
    private double energy;
    /*
     * radius is the health of the Agent. If it hits 0, it dies.
     * This value is **not** inherited by children.
     * TODO: Maybe the agent can decide to spend energy to regain health?
     */

    public Agent(double radius, Color color, @NotNull Environment env) {
        Vector position = Vector.random(new Vector(0, 0), new Vector(env.getWidth(), env.getHeight()));
        AgentData agentData = new AgentData(
                position,
                color,
                Math.random() * 100,
                Math.random() * 7,
                radius,
                Math.random() * 100);

        this(agentData, env);
    }


    public Agent(@NotNull AgentData agentData, @NotNull Environment env) {
        super(agentData.position(), agentData.size(), agentData.color(), env);

        environment = env;
        energy = agentData.energy();
        speed = agentData.speed();
        size = agentData.size();
        visionRadius = agentData.visionRadius();

        direction = Vector.random(1);
    }

    /**
     * TODO
     * @param parent1Data
     * @param parent2Data
     * @param env
     */
    public Agent(@NotNull AgentData parent1Data, @NotNull AgentData parent2Data, @NotNull Environment env) {
        this(parent1Data, env); // TODO
    }

    @Override
    public void step() {
        setPosition(getPosition().add(direction.scale(speed)));
        direction = direction.turn((Math.random() - 0.5) * 20);

        setPosition(getPosition().clamp(Vector.zero, getBounds()));

        // TODO: Update energy based on actions and attributes

        // TODO: Add attribute for whether predator or not, or maybe just have the larger agent always eat a smaller one if they collide unless both want to mate?

        List<Entity> collisions = environment.getCollisions(
                this,
                (_, entity2) -> entity2 instanceof Food
        );
        for  (Entity entity : collisions) {
            energy += ((Food) entity).getValue();
            ((Food) entity).getEaten();
        }
    }

    @Override
    public void paint(@NotNull GraphicsContext gc) {
        gc.setFill(getColor());
        gc.fillOval(x() - getRadius(), y() - getRadius(), getRadius() * 2, getRadius() * 2);
    }

    /**
     * TODO: In the future, have this require a mate?
     */
    public void makeChild() {
        this.energy /= 2;
        AgentData childData = new AgentData(
                getPosition(),
                getColor(),
                this.energy,
                this.speed,
                this.size,
                this.visionRadius
        );

        new Agent(childData, environment);
    }


    /*
     * Getters and Setters
     */

    @Override
    public Vector getPosition() {
        return super.getPosition();
    }

    public Vector getDirection() {
        return direction;
    }

    private Vector getBounds() {
        return environment.getSize();
    }
}
