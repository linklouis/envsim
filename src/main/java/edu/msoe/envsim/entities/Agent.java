package edu.msoe.envsim.entities;

import edu.msoe.envsim.utilities.AgentData;
import edu.msoe.envsim.Environment;
import edu.msoe.envsim.nn.ActivationFunction;
import edu.msoe.envsim.nn.AgentDecision;
import edu.msoe.envsim.nn.NeuralNetwork;
import edu.msoe.envsim.utilities.SpeciesColorMapper;
import edu.msoe.envsim.utilities.TerrainType;
import edu.msoe.envsim.utilities.Vector;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Agent  extends Entity {
    /**
     * TODO: Should maybe scale with radius?
     * TODO: Should definitely scale with radius.
     * TODO: Can get rid of this if we stick with only cloning
     */
    private static final double MAX_EXPECTED_ENERGY = 120;
    private static final double REPRODUCTON_ENERGY_THRESHOLD = 50;
    private static final double REPRODUCTION_COST = 0.3;
    /**
     * The energy at which an entity will clone.
     * TODO: Have this depend on max size?
     */
    public static final int CLONE_THRESHOLD = 10;
    /**
     * The maximum ratio between a predator's radius and its prey for the predation to be a success.
     */
    private static final double MAX_RAD_RATIO_FOR_PREDATION = 0.8;
    /**
     * The amount of energy wasted when catching and eating prey.
     * <p>
     * Scales with the size ratio (so it's easier to eat something smaller.)
     * </p>
     */
    private static final double PREDATION_COST = 7;
    /**
     * The amount of health lost from attacking an Agent larger than yourself.
     */
    private static final double FAILED_PREDATION_COST = 3;
    private static final double PREDATION_EFFICIENCY = 0.7;
    /**
     * The amount of energy an agent needs to start regenerating health.
     */
    private static final double HEALTH_REGENERATION_THRESHOLD = 25;
    private static final double HEALTH_REGEN_COST = 1;
    /**
     * The amount of energy cost per time step per pixel of vision radius
     */
    private static final double VISION_QUALITY_COST = 0.0006;
    public static final double SPEED_COST = 0.006;
    public static final double SIZE_COST = 0.0003;
    public static final int NUM_INPUTS = 6;
    public static final double MUTATION_CHANCE = 0.1;
    public static final double NEURON_MUTATION_SIZE = 0.02;
    public static final double ATTRIBUTE_MUTATION_SIZE = 0.2;
    /**
     * The smallest value the NN can output that's still considered true for a boolean output
     */
    private static final double BOOLEAN_DECISION_THRESHOLD = 0.35;
    public static final double GLOBAL_ENERGY_LOSS_FACTOR = 0.1;
    private static final double WANDER_JITTER_DEGREES = 4;

    private static final SpeciesColorMapper COLOR_MAPPER = new SpeciesColorMapper();


    private final Color baseSpeciesColor;

    private final @NotNull Environment environment;

    // Genetic Attributes:
    /**
     * The maximum distance an Agent can move each time step in pixels.
     * <p>Higher values consume more {@link energy} each time step.</p>
     * <p>The Agent can decide to move any speed from 0 to this value.</p>
     */
    private final double maxSpeed;
    /**
     * The "Max Health" of the Agent.
     * The actual size gene that gets passed to descendants.
     * Higher values consume more {@link energy} each time step.
     */
    private final double size;
    /**
     * How close an entity must be for the Agent to see it.
     * Higher values consume more {@link energy} each time step.
     * TODO: Should the agent be able to see obstacles? (Probably not) [Why did I say probably not?]
     */
    private final double visionRadius;
    private final double healthRegenSpeed;
    private final double turnSpeed;
    private double swimSkill;


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
    /**
     * TODO: Can get rid of if we keep only cloning
     * TODO: Actually maybe don't so carnivores don't keep eating their kids?
     */
    private double timeSinceReproduction = 100000;
    private Food lastEatenFood;
    private boolean alive = true;
    /*
     * radius is the health of the Agent. If it hits 0, it dies.
     * This value is **not** inherited by children.
     */


    public final @NotNull NeuralNetwork brain;
    /**
     * Inputs: food size, distance
     * Output: value
     */
    private final @NotNull NeuralNetwork foodEvaluator;
    /**
     * Inputs: self size, agent size, distance // TODO: In the future, maybe add whether the other wants to mate, if it's the same species, and what direction it's moving in?
     * Outputs: Value (1 = invaluable, 0 = useless, -1 = deadly threat)
     */
    private final @NotNull NeuralNetwork preyEvaluator;
    private final @NotNull NeuralNetwork predatorEvaluator;

    private Food bestFood;
    private Agent bestPrey;
    private Agent worstThreat;

    private final Agent parent;
    private AgentDecision decision;



    public Agent(double radius, Color color, @NotNull Environment env) {
        Vector position = Vector.random(new Vector(env.getSize().x() / 2, 0), env.getSize());
        AgentData agentData = new AgentData(
                position,
                color,
                CLONE_THRESHOLD * radius - 1,
                Math.random() * 3 + 0.4,
                Math.random() * 180 + 50,
                radius,
                Math.random() * 500 + 150,
                Math.random(),
                Math.pow(Math.random(), 0.2),
                newBrain(),
                newFoodEvaluator(),
                newAgentEvaluator(),
                newAgentEvaluator()
                );

        this(agentData, env, null);
    }


    public Agent(@NotNull AgentData agentData, @NotNull Environment env, @Nullable Agent parent) {
        super(agentData.position().add(Vector.random(20)), agentData.size(), agentData.color(), env);

        environment = env;
        energy = agentData.energy();
        maxSpeed = agentData.speed();
        turnSpeed = agentData.turnSpeed();
        size = agentData.size();
        visionRadius = agentData.visionRadius();
        healthRegenSpeed = agentData.healthRegenSpeed();
        swimSkill = agentData.swimSkill();
        brain =  agentData.brain();
        foodEvaluator = agentData.foodEvaluator();
        preyEvaluator = agentData.preyEvaluator();
        predatorEvaluator = agentData.predatorEvaluator();

        this.parent = parent;

        direction = Vector.random(1);

        this.baseSpeciesColor = COLOR_MAPPER.getBaseSpeciesColor(
                maxSpeed, size, visionRadius, healthRegenSpeed,
                brain, foodEvaluator, preyEvaluator, predatorEvaluator
        );
        updateColor();
    }

    /**
     * TODO
     * @param parent1Data
     * @param parent2Data
     * @param env
     */
    public Agent(@NotNull AgentData parent1Data, @NotNull AgentData parent2Data, @NotNull Environment env) {
        this(parent1Data.energy() > parent2Data.energy() ? parent1Data : parent2Data, env, null); // TODO
    }

    @Override
    public void step() {
        if (!alive) return;

        timeSinceReproduction++;
        //if (reproductionDesire > 0.6 && energy > 80) {
        if (energy > calculateCloneThreshold() && getRadius() / size > 0.8) {
            reproduce(this);
        }

//        direction = agentDecision.direction();
        move(decision.turn(), decision.speed() * maxSpeed);

        for (Entity entity : environment.getCollisions(this, (_, entity2) -> entity2.getRadius() > 0)) {
            if (entity instanceof Food food) {
                eat(food);
            } else if (entity instanceof Agent agent) {
                interact(agent, decision.desiresPredation());
            }
        }

        // TODO: Health regen could instead be something the NN can decide to consume energy to do.
        if (energy > HEALTH_REGENERATION_THRESHOLD && getRadius() < size) {
            // TODO: Have passive health regen speed be partially based on how low health is?
            setRadius(getRadius() + (Math.random() * healthRegenSpeed));
            changeEnergy(-(HEALTH_REGEN_COST * healthRegenSpeed)); // TODO: Have the cost be based on how successful the regen was?
            if (getRadius() > size) {
                setRadius(size);
            }
        }

        changeEnergy(-getPassiveEnergyCost());

        if (energy <= 0 || getRadius() <= 0) {
            die();
            return;
        }

        updateColor();
    }

    private void move(double turn, double speed) {
        direction = direction.turn(turn);

        double functionalSpeed = speed / (Math.pow(getRadius() / 35, 2) + 1); // TODO: DOES THIS MAKE SENSE AND WHAT SHOULD I CALL THE CONSTANT?
        double cost = getMovementCost(functionalSpeed); // TODO: Have moving in your non-preferred terrain cost more energy?
        // TODO: Have size matter less in the water?
        functionalSpeed = functionalSpeed * Math.abs(isOnLand() - swimSkill);
        Vector next = energy >= cost ? getPosition().add(direction.atMagnitude(functionalSpeed)) : getPosition();

        if (energy >= cost) changeEnergy(-cost);

        setPosition(next.clamp(Vector.zero, getBounds()));
    }

    /**
     * @return 1 if the {@link Agent} is on a land tile, otherwise 0.
     */
    private int isOnLand() {
        return environment.getStandingTerrainType(this) == TerrainType.STANDARD ? 1 : 0; // TODO
    }

    private void eat(@NotNull Food food) {
        if (food == lastEatenFood) return;
        double consumptionSize = Math.min(1, getRadius() * MAX_RAD_RATIO_FOR_PREDATION / food.getRadius());    // TODO: Does this make any sense? The idea is for agents to not be able to eat an entire food if they are smaller than it. They should only be able to eat MAX_DIF... % of their  // TODO: Maybe switch to max energy once it relies on radius?
        energy += consumptionSize * food.getValue();
        food.getEaten(consumptionSize);
        lastEatenFood = food;
    }

    private void interact(Agent agent, boolean attemptPredation) {
        if (!this.alive || !agent.alive) return;

//        if (desiresReproduction && agent.desiresReproduction && isSameSpecies(this, agent)) {
//            reproduce(this, agent);
//        } else
        if (attemptPredation) {
            attemptPredation(agent);
        }
    }

    private void updateColor() {
        double brightness = Math.clamp(energy / calculateCloneThreshold(), 0.0, 1.0);
        setColor(Color.hsb(baseSpeciesColor.getHue(), baseSpeciesColor.getSaturation(), brightness));
    }

    private boolean prepareForReproduction(boolean isClone) {
        if (isClone) {
            energy /= 2;
        } else {
            energy -= energy * REPRODUCTION_COST;
        }
        return desiresReproduction && energy * 2 >= REPRODUCTON_ENERGY_THRESHOLD;
    }

    private void attemptPredation(@NotNull Agent prey) {
        if (this.parent == prey || prey.parent == this) return;
        if (!this.alive || !prey.alive) return;
        // TODO: Punish agents for outputting the predation signal when not touching another agent?`

        if (prey.decision.desiresPredation() && prey.getRadius() > getRadius()) {
            prey.attemptPredation(this);
            return;
        }

        // TODO: INSTEAD OF A THRESHOLD, HAVE THE CHANCES FOR AN AGENT TO KILL ANOTHER BE BASED ON THE SIZE DIFFERENCE. (OR EVEN JUST HOW MUCH DAMAGE IT DOES, SO THAT A SMALL AGENT CAN DO DAMAGE TO A LARGE ONE, EVEN IF IT DOESN'T KILL IT? [Is that actually something we want?])
        // TODO: Pass the radius ratio for whatever other agent this one is seeing as input for NN.
        changeEnergy(-(PREDATION_COST * prey.getRadius() / getRadius()));
        if (prey.getRadius() / getRadius() <= MAX_RAD_RATIO_FOR_PREDATION) {
            energy += prey.energy * PREDATION_EFFICIENCY;
            prey.die();
            timeSinceReproduction = 0; // TODO: Remove once decisions aren't made randomly every frame.
        } else {
            // TODO: If we fail, but the other dies, we should still gain the energy.
            // TODO: Maybe that wouldn't happen cuz the decision of whether we succeed or not is based on health?
            setRadius(Math.max(0.001, getRadius() - FAILED_PREDATION_COST * prey.getRadius() / getRadius()));
            prey.setRadius(Math.max(0.001, prey.getRadius() - FAILED_PREDATION_COST * getRadius() / prey.getRadius()));
            // TODO: Also lose energy?
        }
    }

    private static void reproduce(@NotNull Agent parent) {
        if (parent.prepareForReproduction(true)) {
            new Agent(parent.getAgentData(), parent.environment, parent);
            parent.timeSinceReproduction = 0;
//            System.out.println("Cloning!");
        }
    }

    private static boolean isSameSpecies(Agent agent1, Agent agent2) {
        return true; // TODO
    }

    private static @NotNull NeuralNetwork newBrain() {
        NeuralNetwork neuralNetwork = new NeuralNetwork(NUM_INPUTS);
//        neuralNetwork.newLayer(16);
        neuralNetwork.newLayer(10);
        neuralNetwork.newLayer(7, ActivationFunction.sigmoid());

        return neuralNetwork;
    }

    private static @NotNull NeuralNetwork newFoodEvaluator() {
        NeuralNetwork neuralNetwork = new NeuralNetwork(2);
        neuralNetwork.newLayer(3);
        neuralNetwork.newLayer(1,ActivationFunction.sigmoid());

        return neuralNetwork;
    }

    /**
     * TODO: consider velocity?
      */
    private static @NotNull NeuralNetwork newAgentEvaluator() {
        NeuralNetwork neuralNetwork = new NeuralNetwork(2);
        neuralNetwork.newLayer(3);
        neuralNetwork.newLayer(1,ActivationFunction.sigmoid());

        return neuralNetwork;
    }


    /**
     * Inputs (For now): energy, health, bestFood, bestPrey, worstThreat, currentTerrain
     * Outputs (For now): speed, repDesire, predDesire, targetFood, targetPrey, runFromThreat, turn
     */
    @Contract(" -> new")
    private float @NotNull [] getInput() {
        // TODO: Add current terrain
        // TODO: Add way to see what terrain is around it.
        //  Maybe if I add raycasts, have each one also say what the closest new terrain is in that direction?
        List<Entity> entitiesInRange = environment.getEntitiesInRange(getPosition(), entity -> entity != this, visionRadius);
        return new float[] {
                (float) (energy / MAX_EXPECTED_ENERGY),
                (float) (getRadius() / size),
                evaluateFood(entitiesInRange),
                evaluatePrey(entitiesInRange),
                evaluateThreats(entitiesInRange),
                environment.getStandingTerrainType(this).ordinal()
        };
    }

    private float evaluateFood(@NotNull List<Entity> entitiesInRange) {
//        List<Entity> entitiesInRange = environment.getEntitiesInRange(getPosition(),
//                entity -> entity instanceof Food && entity.getRadius() > 0, visionRadius);

        float maxValue = 0;
        Entity mostValuableFood = null;

        for  (Entity entity : entitiesInRange) {
            if (!(entity instanceof Food) || entity.getRadius() < 0) continue;
            if (entity == lastEatenFood) continue;
            float value = (float) (entity.getRadius() / this.distance(entity));
//                    foodEvaluator.predict(new float[] {
//                    (float) this.distance(entity),
//                    (float) entity.getRadius()
//            })[0];

            if (value > maxValue) {
                maxValue = value;
                mostValuableFood = entity;
            }
        }

        this.bestFood = (Food) mostValuableFood;
        return maxValue;
    }

    private float evaluatePrey(@NotNull List<Entity> entitiesInRange) {
//        List<Entity> entitiesInRange = environment.getEntitiesInRange(getPosition(),
//                entity -> entity instanceof Agent, visionRadius);

        float maxValue = 0;
        Entity mostValuablePrey = null;

        for  (Entity entity : entitiesInRange) {
            if (!(entity instanceof Agent)) continue;
            float value = preyEvaluator.predict(new float[] {
                    (float) (entity.getRadius() / this.getRadius()),
                    (float) this.distance(entity)
            })[0];

            if (value > maxValue) {
                maxValue = value;
                mostValuablePrey = entity;
            }
        }

        this.bestPrey = (Agent) mostValuablePrey;
        return maxValue;
    }

    private float evaluateThreats(@NotNull List<Entity> entitiesInRange) {
//        List<Entity> entitiesInRange = environment.getEntitiesInRange(getPosition(),
//                entity -> entity instanceof Agent, visionRadius);

        float maxValue = 0;
        Entity mostThreateningPredator = null;

        for  (Entity entity : entitiesInRange) {
            if (!(entity instanceof Agent)) continue;
            float value = predatorEvaluator.predict(new float[] {
                    (float) (entity.getRadius() / this.getRadius()),
                    (float) this.distance(entity)
            })[0];

            if (value > maxValue) {
                maxValue = value;
                mostThreateningPredator = entity;
            }
        }

        this.worstThreat = (Agent) mostThreateningPredator;
        return maxValue;
    }

    /**
     * TODO: Implement NN
     * TODO: Pass in how related the current agent is to the one it's seeing
     */
    @Override
    public void getAction() {
        float[] inputs = getInput();
        float[] output = brain.predict(inputs);

        double targetFood = bestFood == null ? 0 : output[2];
        double targetPrey = bestPrey == null ? 0 : output[3];
        double runFromThreat = worstThreat == null ? 0 :output[4];

        Vector target;
        double turn;
        boolean desiresPredation;

        if (targetFood > targetPrey && targetFood > runFromThreat && targetFood > BOOLEAN_DECISION_THRESHOLD) {
            target = directionTo(bestFood);
            turn = getTurnToTarget(target);
            desiresPredation = false;
        } else if (targetPrey > targetFood && targetPrey > runFromThreat && targetPrey > BOOLEAN_DECISION_THRESHOLD) {
            target = directionTo(bestPrey);
            turn = getTurnToTarget(target);
            desiresPredation = true;
        } else if (runFromThreat > targetFood && runFromThreat > targetPrey && runFromThreat > BOOLEAN_DECISION_THRESHOLD) {
            target = directionTo(worstThreat).scale(-1);
            turn = getTurnToTarget(target);
            desiresPredation = false;
        } else {
            target = direction;
            double brainTurn = ((output[6] * 2) - 1) * turnSpeed;
            double jitter = ThreadLocalRandom.current().nextDouble(-1, 1) * WANDER_JITTER_DEGREES;
            turn = brainTurn * 0.25 + jitter;
            desiresPredation = false;
        }

        this.decision =  new AgentDecision(
                output[0],// * 2 - 1,
                target, // TODO: Can probably remove
                turn,
                desiresPredation
        );
    }

    /** Max turn per tick, in degrees. */
    private double maxTurnPerTick() {
        return turnSpeed / getRadius();
    }

    private double getTurnToTarget(@NotNull Vector target) {
        double rawChange = target.angle() - direction.angle(); // radians
        double neededDegrees = Math.toDegrees(Math.atan2(Math.sin(rawChange), Math.cos(rawChange)));
        return Math.clamp(neededDegrees, -maxTurnPerTick(), maxTurnPerTick());
    }


    @Override
    public void paint(@NotNull GraphicsContext gc) {
        gc.setFill(getColor());
        gc.fillOval(x() - getRadius(), y() - getRadius(), getRadius() * 2, getRadius() * 2);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(3);
        gc.strokeOval(x() - getRadius(), y() - getRadius(), getRadius() * 2, getRadius() * 2);

        if (environment.isDebugMode()) {
            gc.setLineWidth(1);
            gc.strokeOval(x() - visionRadius, y() - visionRadius, visionRadius * 2, visionRadius * 2);
        }
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
        return SPEED_COST * Math.pow(actualSpeed, 2);
    }

    private double getPassiveEnergyCost() {
        return visionRadius * VISION_QUALITY_COST + Math.pow(getRadius(), 2) * SIZE_COST;
    }

    private double mutateAttribute(double attribute) {
        return attribute + (attribute * (Math.random() - 0.5) * ATTRIBUTE_MUTATION_SIZE);
    }

    @Contract(" -> new")
    private @NotNull AgentData getAgentData() {
        return new AgentData(
                getPosition().add(Vector.random(100 + getRadius() * 2)),
                getColor(),
                this.energy, // TODO: Children should actually get the cost subtracted from each parent's health (What did I mean by this?)
                mutateAttribute(this.maxSpeed),
                mutateAttribute(this.turnSpeed),
                mutateAttribute(this.size),
                mutateAttribute(this.visionRadius),
                mutateAttribute(this.healthRegenSpeed),
                Math.clamp(mutateAttribute(this.swimSkill), 0, 1),
                this.brain.child(MUTATION_CHANCE, NEURON_MUTATION_SIZE),
                this.foodEvaluator.child(MUTATION_CHANCE, NEURON_MUTATION_SIZE),
                this.preyEvaluator.child(MUTATION_CHANCE, NEURON_MUTATION_SIZE),
                this.predatorEvaluator.child(MUTATION_CHANCE, NEURON_MUTATION_SIZE)
        );
    }

    private void changeEnergy(double change) {
        energy += change * GLOBAL_ENERGY_LOSS_FACTOR;
    }

    private double calculateCloneThreshold() {
        return CLONE_THRESHOLD * getRadius();
    }
}
