package edu.msoe.envsim;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class Agent  extends Entity {
    /**
     * TODO: Should maybe scale with radius?
     */
    private static final double MAX_EXPECTED_ENERGY = 200;
    private static final double REPRODUCTON_ENERGY_THRESHOLD = 30;
    private static final double REPRODUCTION_COST = 0.3;
    /**
     * The maximum ratio between a predator's radius and its prey for the predation to be a success.
     */
    private static final double MIN_RAD_DIFF_FOR_PREDATION = 0.3;
    /**
     * The amount of energy wasted when catching and eating prey.
     */
    private static final double PREDATION_COST = 5;
    /**
     * The amount of health lost from attacking an Agent larger than yourself.
     */
    private static final double FAILED_PREDATION_COST = 5;
    private static final double PREDATION_EFFICIENCY = 0.6;
    /**
     * The amount of energy an agent needs to start regenerating health.
     */
    private static final double HEALTH_REGENERATION_THRESHOLD = 25;
    private static final double HEALTH_REGEN_COST = 1;
    /**
     * The smallest value the NN can output that's still considered true for a boolean output
     */
    private static final double BOOLEAN_DECISION_THRESHOLD = 0.3;
    /**
     * The amount of energy cost per time step per pixel of vision radius
     */
    private static final double VISION_QUALITY_COST = 0.001;
    public static final double SPEED_COST = 0.005; // TODO: Increase this once NN in implemented


    private @NotNull Environment environment;

    // Genetic Attributes:
    /**
     * How far the Agent can move each time step.
     * Higher values consume more {@link energy} each time step.
     * TODO: Should the agent always move at max speed? Maybe add sprinting?
     */
    private double maxSpeed;
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
    private double healthRegenSpeed;


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
    private boolean desiresReproduction = true;
    private double timeSinceReproduction = 100000;
    private boolean alive = true;
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
                100,
                Math.random() * 4,
                radius,
                Math.random() * 100,
                Math.random() * 2);

        this(agentData, env);
    }


    public Agent(@NotNull AgentData agentData, @NotNull Environment env) {
        super(agentData.position().add(Vector.random(20)), agentData.size(), agentData.color(), env);

        environment = env;
        energy = agentData.energy();
        maxSpeed = agentData.speed();
        size = agentData.size();
        visionRadius = agentData.visionRadius();
        healthRegenSpeed = agentData.healthRegenSpeed();

        direction = Vector.random(1);
    }

    /**
     * TODO
     * @param parent1Data
     * @param parent2Data
     * @param env
     */
    public Agent(@NotNull AgentData parent1Data, @NotNull AgentData parent2Data, @NotNull Environment env) {
        this(parent1Data.energy() > parent2Data.energy() ? parent1Data : parent2Data, env); // TODO
    }

    @Override
    public void step() {
        if (!alive) return;

        timeSinceReproduction++;
        boolean desiresPredation = getOutput();

        move((Math.random() - 0.5) * 30, (Math.random() + 0.1) * maxSpeed);

        for (Entity entity : environment.getCollisions(this)) {
            if (entity instanceof Food food) {
                eat(food);
            } else if (entity instanceof Agent agent) {
                interact(agent, desiresPredation);
                // TODO: Add attribute for whether predator or not, or maybe just have the larger agent always eat a smaller one if they collide unless both want to mate?
                // TODO: Actually, just have the network output a decision of whether or not to try to eat another agent, and it only actually works if it's touching another agent that is sufficiently smaller than itself. If it's not, it gets punished.
            }
        }

        // TODO: Health regen could instead be something the NN can decide to consume energy to do.
        if (energy > HEALTH_REGENERATION_THRESHOLD && getRadius() < size) {
            // TODO: Have passive health regen speed be partially based on how low health is?
            setRadius(getRadius() + (Math.random() * healthRegenSpeed));
            energy -= HEALTH_REGEN_COST * healthRegenSpeed; // TODO: Have the cost be based on how successful the regen was?
            if (getRadius() > size) {
                setRadius(size);
            }
        }

        energy -= getPassiveEnergyCost();

        if (energy <= 0) {
            die();
            return;
        }

        updateColor();
    }

    /**
     * TODO: Implement NN
     * TODO: Pass in how related the current agent is to the one it's seeing
     * @return
     */
    private boolean getOutput() {
        double reproductionDesire = Math.random() - 1/timeSinceReproduction;
        double predationDesire = Math.random() * 0.9 - 1/timeSinceReproduction;// TODO: Remove time since reproduction

        boolean desiresPredation;

        if (reproductionDesire > predationDesire && reproductionDesire > BOOLEAN_DECISION_THRESHOLD) {
            desiresReproduction = true;
            desiresPredation = false;
        } else if (reproductionDesire < predationDesire && predationDesire > BOOLEAN_DECISION_THRESHOLD) {
            desiresReproduction = false;
            desiresPredation = true;
        } else {
            desiresReproduction = false;
            desiresPredation = false;
        }

        return desiresPredation;
    }

    private void move(double turn, double speed) {
        direction = direction.turn(turn);
        if (energy >= getMovementCost(speed)) {
            setPosition(getPosition().add(direction.scale(speed)));
            energy -= getMovementCost(speed);
        }
        setPosition(getPosition().clamp(Vector.zero, getBounds()));
    }

    private void eat(@NotNull Food food) {
        energy += food.getValue();
        food.getEaten();
    }

    private void interact(Agent agent, boolean attemptPredation) {
        if (desiresReproduction && agent.desiresReproduction && isSameSpecies(this, agent)) {
            reproduce(this, agent);
        } else if (attemptPredation) {
            attemptPredation(agent);
        }
    }

    private void updateColor() {
        double hue = getColor().getHue();
        double saturation = getColor().getSaturation();

        Color newColor = Color.hsb(hue, saturation, Math.clamp(energy / MAX_EXPECTED_ENERGY, 0, 1));
        setColor(newColor);
    }

    private boolean prepareForReproduction() {
        energy -= energy * REPRODUCTION_COST;
        return desiresReproduction && energy * 2 >= REPRODUCTON_ENERGY_THRESHOLD;
    }

    private void attemptPredation(@NotNull Agent prey) {
        // TODO: INSTEAD OF A THRESHOLD, HAVE THE CHANCES FOR AN AGENT TO KILL ANOTHER BE BASED ON THE SIZE DIFFERENCE. (OR EVEN JUST HOW MUCH DAMAGE IT DOES, SO THAT A SMALL AGENT CAN DO DAMAGE TO A LARGE ONE, EVEN IF IT DOESN'T KILL IT? [Is that actually something we want?])
        // TODO: Pass the radius ratio for whatever other agent this one is seeing as input for NN.
        energy -= PREDATION_COST;
        if (prey.getRadius() / getRadius() <= MIN_RAD_DIFF_FOR_PREDATION) {
            energy += prey.energy * PREDATION_EFFICIENCY;
            prey.die();
            System.out.println("Predation!");
            timeSinceReproduction = 0; // TODO: Remove once decisions aren't made randomly every frame.
        } else {
            setRadius(getRadius() - FAILED_PREDATION_COST);
            prey.setRadius(prey.getRadius() - FAILED_PREDATION_COST);
            // TODO: Also lose energy?
            System.out.println("Failed Predation!");
        }
    }


    private static void reproduce(@NotNull Agent parent1, @NotNull Agent parent2) {
        Environment env = parent1.environment;
        if (env != parent2.environment) {
            throw new IllegalStateException("Agents in different environments cannot reproduce.");
        }

        // TODO: Maybe have failed reproduction take less energy?
        if (parent1.prepareForReproduction() && parent2.prepareForReproduction()) {
            new Agent(parent1.getAgentData(), parent2.getAgentData(), env);
            parent1.timeSinceReproduction = 0;
            parent2.timeSinceReproduction = 0;
            System.out.println("Reproduction!");
        }
    }

    private static boolean isSameSpecies(Agent agent1, Agent agent2) {
        return true; // TODO
    }


    @Override
    public void paint(@NotNull GraphicsContext gc) {
        gc.setFill(getColor());
        gc.fillOval(x() - getRadius(), y() - getRadius(), getRadius() * 2, getRadius() * 2);
        gc.setStroke(Color.WHITE);
        gc.strokeOval(x() - getRadius(), y() - getRadius(), getRadius() * 2, getRadius() * 2);
    }

    protected void die() {
        environment.removeEntity(this);
        alive = false;
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

    private double getMovementCost(double actualSpeed) {
        return SPEED_COST * actualSpeed;
    }

    private double getPassiveEnergyCost() {
        return visionRadius * VISION_QUALITY_COST;
    }

    @Contract(" -> new")
    private @NotNull AgentData getAgentData() {
        return new AgentData(
                getPosition(),
                getColor(),
                this.energy, // TODO: Children should actually get the cost subtracted from each parent's health
                this.maxSpeed,
                this.size,
                this.visionRadius,
                this.healthRegenSpeed
        );
    }
}
