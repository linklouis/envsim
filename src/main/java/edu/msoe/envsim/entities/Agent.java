package edu.msoe.envsim.entities;

import edu.msoe.envsim.Environment;
import edu.msoe.envsim.utilities.Vector;
import edu.msoe.envsim.utilities.simutils.*;
import edu.msoe.envsim.utilities.NeuralNetwork;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;

public class Agent extends MobileEntity {
    public static final double MIN_HEALTH_FOR_REPRODUCTION = 0.8;
    /**
     * The energy at which an entity will clone.
     * TODO: Have this depend on max size?
     */
    public static final int CLONE_THRESHOLD = 30;
    /**
     * The maximum ratio between a predator's radius and its prey for the predation to be a success.
     */
    private static final double MAX_RAD_RATIO_FOR_PREDATION = 0.8;
    /**
     * The portion of max energy wasted when catching and eating prey.
     * <p>
     * Scales with the size ratio (so it's easier to eat something smaller.)
     * </p>
     */
    private static final double PREDATION_COST_FACTOR = 0.2;
    /**
     * The portion of health lost from attacking an Agent larger than yourself.
     */
    private static final double FAILED_PREDATION_COST_FACTOR = 0.2;
    private static final double PREDATION_EFFICIENCY = 0.95; // Should be much more beneficial than eating plants, so if you get huge you have to eat others, plants can't sustain you.
    /**
     * The portion of energy to spend when trying to attempt predation and not touching another agent
     */
    public static final double FALSE_PREDATION_PUNISHMENT_FACTOR = 0.1;
    /**
     * The portion of total energy an agent needs to start regenerating health.
     */
    private static final double HEALTH_REGENERATION_THRESHOLD = 0.3;
    private static final double HEALTH_REGEN_COST = 1;
    /**
     * The amount of energy cost per time step per pixel of vision radius
     */
    private static final double VISION_QUALITY_COST = 0.0006;
    public static final double SPEED_COST = 0.01;
    public static final double SIZE_COST = 0.002;
    public static final int NUM_INPUTS = 6;

    /**
     * The smallest value the NN can output that's still considered true for a boolean output
     */
    private static final double BOOLEAN_DECISION_THRESHOLD = 0.32;
    public static final double GLOBAL_ENERGY_LOSS_FACTOR = 0.05;
    private static final double WANDER_JITTER_DEGREES = 20;

    private static final SpeciesColorMapper COLOR_MAPPER = new SpeciesColorMapper();
    public static final double SPECIES_SIMILARITY_EXPONENTIAL_DECAY_FACTOR = 2;
    public static final double SPECIES_SIMILARITY_THRESHOLD = 0.95;

    private final Color baseSpeciesColor;

    private final @NotNull Environment environment;

    // Genetic Attributes:
    /**
     * The maximum distance an Agent can move each time step in pixels.
     * <p>Higher values consume more {@link #energy} each time step.</p>
     * <p>The Agent can decide to move any speed from 0 to this value.</p>
     */
    private final double maxSpeed;
    /**
     * The "Max Health" of the Agent.
     * The actual size gene that gets passed to descendants.
     * Higher values consume more {@link #energy} each time step.
     */
    private final double size;
    /**
     * How close an entity must be for the Agent to see it.
     * Higher values consume more {@link #energy} each time step.
     * TODO: Should the agent be able to see obstacles? (Probably not) [Why did I say probably not? (Cuz it's slow?)]
     */
    private final double visionRadius;
    /**
     * Caching the squared radius so it doesn't have to be recalculated in {@link Environment#visitEntitiesInRange(Entity, double, double, SpatialGrid.Visitor)} every tick.
     */
    private final double visionRadiusSquared;
    private final double healthRegenSpeed;
    private final double turnSpeed;
    private final double swimSkill;

    private final float[] genome;
    private final float genomeNormSq;


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


    // State:
    private Vector direction;
    /**
     * The amount of energy the Agent currently has.
     * Energy is consumed passively each time step based on the {@link #size} and {@link #visionRadius} attributes,
     * and actively consumed by the moving and reproducing.
     * When reproducing, half of the Agent's current energy is passed to the child.
     * Can be replenished by eating.
     */
    private double energy;
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

    private Food bestFood;
    private Agent bestPrey;
    private Agent worstThreat;

    private float maxFoodValue;
    private float maxPreyValue;
    private float maxThreatValue;

    private final Agent parent;
    private AgentDecision decision;
    private String action = "WANDER";

    private final List<Entity> collisions = new ArrayList<>();
    private static final BiPredicate<Entity, Entity> STILL_PRESENT = (_, other) -> other.getRadius() > 0;
    /** Created once so {@link #getInput()} doesn't allocate a method reference every tick. */
    private final SpatialGrid.Visitor entityEvaluator = this::evaluateEntity;

    private TerrainType cachedTerrain;
    private final Map<Integer, Double> genomeComparisonCache = new ConcurrentHashMap<>();


    public Agent(double radius, Color color, @NotNull Environment env) {
//        Vector position = Vector.random(new Vector(env.getSize().x() / 2, 0), env.getSize());
        Vector position = Vector.random(new Vector(0, 0), env.getSize());
        AgentData agentData = new AgentData(
                position,
                color,
                calculateCloneThreshold(radius) - 1,
                Math.random() * 2.5 + 0.4,
                Math.random() * 180 + 50,
                radius,
                Math.random() * 500 + 150,
                Math.random(),
//                Math.pow(Math.random(), 0.2),
                Math.random(),
                newBrain(),
                newFoodEvaluator(),
                newAgentEvaluator(),
                newAgentEvaluator()
        );

        this(agentData, env, null);
    }


    public Agent(@NotNull AgentData agentData, @NotNull Environment env, @Nullable Agent parent) {
        super(agentData.position().add(Vector.random(20)), agentData.size(), agentData.color(), env);

        cachedTerrain = env.getStandingTerrainType(this);

        environment = env;
        energy = agentData.energy();
        maxSpeed = agentData.speed();
        turnSpeed = agentData.turnSpeed();
        size = agentData.size();
        visionRadius = agentData.visionRadius();
        visionRadiusSquared = visionRadius * visionRadius;
        healthRegenSpeed = agentData.healthRegenSpeed();
        swimSkill = agentData.swimSkill();
        brain =  agentData.brain();
        foodEvaluator = agentData.foodEvaluator();
        preyEvaluator = agentData.preyEvaluator();
        predatorEvaluator = agentData.predatorEvaluator();

        this.parent = parent;

        direction = Vector.random(1);

        genome = extractGenome();
        float norm = 0;
        for (float val : genome) { norm += val * val; }
        this.genomeNormSq = norm;

        this.baseSpeciesColor = COLOR_MAPPER.getBaseSpeciesColor(genome);
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

    private float @NotNull [] extractGenome() {
        List<Float> traits = new ArrayList<>();

        traits.add((float) maxSpeed);
        traits.add((float) turnSpeed);
        traits.add((float) size);
        traits.add((float) visionRadius);
        traits.add((float) healthRegenSpeed);
        traits.add((float) swimSkill);

        traits.addAll(brain.getWeights());
        traits.addAll(foodEvaluator.getWeights());
        traits.addAll(preyEvaluator.getWeights());
        traits.addAll(predatorEvaluator.getWeights());

        float[] genome = new float[traits.size()];
        for (int i = 0; i < traits.size(); i++) {
            genome[i] = traits.get(i);
        }
        return genome;
    }

    private double compareGenome(@NotNull Agent other) {
        double totalRelativeDiff = 0;
        for (int i = 0; i < this.genome.length; i++) {
            float val1 = this.genome[i];
            float val2 = other.genome[i];

            // Proportional difference formula that handles any magnitude gracefully
            double diff = (val1 - val2) / (Math.abs(val1) + Math.abs(val2) + 1e-5);
            totalRelativeDiff += diff * diff;
        }

        // Return RMS of the relative differences
        return Math.sqrt(totalRelativeDiff / this.genome.length);
    }

    /**
     * Source: Claude
     * @param other
     * @return
     */
    private double compareGenomeOld(@NotNull Agent other) {
        float[] g1 = this.genome;
        float[] g2 = other.genome;

        double d0 = 0, d1 = 0, d2 = 0, d3 = 0;
        int i = 0;
        for (; i <= g1.length - 4; i += 4) {
            d0 += g1[i] * g2[i];
            d1 += g1[i + 1] * g2[i + 1];
            d2 += g1[i + 2] * g2[i + 2];
            d3 += g1[i + 3] * g2[i + 3];
        }
        for (; i < g1.length; i++) d0 += g1[i] * g2[i];
        double dot = d0 + d1 + d2 + d3;

        double squaredDistance = this.genomeNormSq + other.genomeNormSq - (2 * dot);

        // Normalize by dividing by the number of genes to get RMS distance
        return Math.sqrt(Math.max(0.0, squaredDistance) / g1.length);
    }

    private double getGenomeComparison(@NotNull Agent other) {
        int otherHash = other.hashCode();

        Double cached = genomeComparisonCache.get(otherHash);
        if (cached != null) {
            return cached;
        }

        double comparison = compareGenome(other);
        genomeComparisonCache.put(otherHash, comparison);

        return comparison;
    }

    public void parallelStep() {
        if (!alive) return;

        timeSinceReproduction++;
        //if (reproductionDesire > 0.6 && energy > 80) {
        if (energy > calculateCloneThreshold() && getRadius() / size > MIN_HEALTH_FOR_REPRODUCTION) {
            reproduce();
        }

        move(decision.turn(), decision.speed() * maxSpeed);
        changeEnergy(-getPassiveEnergyCost());

        // TODO: Health regen could instead be something the NN can decide to consume energy to do.
        if (energy > HEALTH_REGENERATION_THRESHOLD * calculateCloneThreshold() && getRadius() < size) {
            // TODO: Have passive health regen speed be partially based on how low health is?
            setRadius(getRadius() + (ThreadLocalRandom.current().nextDouble() * healthRegenSpeed));
            changeEnergy(-(HEALTH_REGEN_COST * healthRegenSpeed)); // TODO: Have the cost be based on how successful the regen was?
            if (getRadius() > size) {
                setRadius(size);
            }
        }
    }

    @Override
    public void findCollisions() {
        collisions.clear();
        environment.collectCollisions(this, STILL_PRESENT, collisions);
    }

    @Override
    public void step() {
        if (!alive) return;

        if (collisions.isEmpty()) {
            if (decision.desiresPredation()) {
                energy -= FALSE_PREDATION_PUNISHMENT_FACTOR * getRadius();
            }
        } else {
            for (Entity entity : collisions) {
                if (entity.getRadius() <= 0) continue;
                if (entity instanceof Food food) {
                    eat(food);
                } else if (entity instanceof Agent agent) {
                    interact(agent, decision.desiresPredation());
                }
            }
        }

        if (energy <= 0 || getRadius() <= 0) die();
    }

    private void move(double turn, double speed) {
        direction = direction.turn(turn);
        double speedModifier = Math.pow(getRadius() * cachedTerrain.getSpeedSizeCostFactor(), 2) + 1;
        double effort = speed / speedModifier;
        double cost = SPEED_COST * Math.pow(effort, 2); // TODO: Have moving in your non-preferred terrain cost more energy?

        if (energy >= cost) {
            double functionalSpeed = effort * Math.abs(isOnLand() - swimSkill);
            Vector next = getPosition().addScaledInBounds(direction, Vector.ZERO, getBounds(), functionalSpeed);
            setPosition(next);
            changeEnergy(-cost);
        }
    }

    /**
     * @return 1 if the {@link Agent} is on a land tile, otherwise 0.
     */
    private int isOnLand() {
        return cachedTerrain == TerrainType.STANDARD ? 1 : 0; // TODO
    }

    private void eat(@NotNull Food food) {
        if (food == lastEatenFood) return;
        double consumptionSize = Math.min(1, getRadius() * MAX_RAD_RATIO_FOR_PREDATION / food.getRadius());    // TODO: Does this make any sense? The idea is for agents to not be able to eat an entire food if they are smaller than it. They should only be able to eat MAX_DIF... % of their  // TODO: Maybe switch to max energy once it relies on radius?
        energy += consumptionSize * food.getValue();
        food.getEaten(consumptionSize);
        lastEatenFood = food;
    }

    private void interact(Agent other, boolean attemptPredation) {
        if (!this.alive || !other.alive) return;

        if (attemptPredation) {
            attemptPredation(other);
        }
    }

    private void updateColor() {
        double brightness = Math.clamp(energy / calculateCloneThreshold(), 0.0, 1.0);
        setColor(Color.hsb(baseSpeciesColor.getHue(), baseSpeciesColor.getSaturation(), brightness));
    }

    /**
     * TODO: ADD ATTACK AND DEFENSE STATS. DEFENSE COSTS MORE TO LIVE EACH STEP, ATTACK COSTS MORE EACH ATTACK (SO ATTACKING IS MORE RISKY IF YOU LOSE, BUT YOU'RE LESS LIKELY TO)
     *  INCORPORATE THESE INTO WHETHER AN ATTACK IS SUCCESSFUL OR NOT, JUST AS MUCH AS SIZE.
     * @param prey
     */
    private void attemptPredation(@NotNull Agent prey) {
//        if (this.parent == prey || prey.parent == this) return;  // Should be covered by the genome comparison
        // TODO: Punish agents for outputting the predation signal when not touching another agent?`

        if (prey.decision.desiresPredation() && prey.getRadius() > getRadius()) {
            prey.attemptPredation(this);
            return;
        }

        // TODO: INSTEAD OF A THRESHOLD, HAVE THE CHANCES FOR AN AGENT TO KILL ANOTHER BE BASED ON THE SIZE DIFFERENCE. (OR EVEN JUST HOW MUCH DAMAGE IT DOES, SO THAT A SMALL AGENT CAN DO DAMAGE TO A LARGE ONE, EVEN IF IT DOESN'T KILL IT? [Is that actually something we want?])
        // TODO: Pass the radius ratio for whatever other agent this one is seeing as input for NN.
        changeEnergy(-(PREDATION_COST_FACTOR * calculateCloneThreshold() * prey.getRadius() / getRadius()));
        if (prey.getRadius() / getRadius() <= MAX_RAD_RATIO_FOR_PREDATION) {
            succeedPredation(prey);
            prey.die();
        } else {
            // Health loss = FPCF * radius * other/self_size_ratio = FPCF * radius * otherRadius / radius = FPCF * otherRadius
            double selfHealth = getRadius() - FAILED_PREDATION_COST_FACTOR * prey.getRadius();
            double otherHealth = prey.getRadius() - FAILED_PREDATION_COST_FACTOR * getRadius();
            this.setRadius(selfHealth);
            prey.setRadius(otherHealth);
            // TODO: Also lose energy?

            if (selfHealth <= 0 && otherHealth <= 0) {
                die();
                prey.die();
            } else if (selfHealth <= 0) {
                if (otherHealth > 0 && prey.decision.desiresPredation()) {
                    prey.succeedPredation(this);
                }
                die();
            } else if (otherHealth <= 0) {
                succeedPredation(prey);
                prey.die();
            }
        }
    }

    private void succeedPredation(@NotNull Agent prey) {
        if (!prey.alive) return;
        energy += prey.energy * PREDATION_EFFICIENCY;
    }

    private void reproduce() {
        energy /= 2;
        new Agent(getAgentData().mutate(), environment, parent);
        timeSinceReproduction = 0;
    }

    private static boolean isSameSpecies(@NotNull Agent agent1, @NotNull Agent agent2) {
        Double cached = agent1.genomeComparisonCache.get(agent2.hashCode());
        double comparison = Objects.requireNonNullElseGet(cached, () -> agent2.getGenomeComparison(agent1));

        return Math.exp(-SPECIES_SIMILARITY_EXPONENTIAL_DECAY_FACTOR * Math.pow(comparison, 2)) > SPECIES_SIMILARITY_THRESHOLD;
    }

    private static @NotNull NeuralNetwork newBrain() {
        return new NeuralNetwork(new int[] {NUM_INPUTS, 10, 7});    // TODO: Probably make the hidden layer a little bigger
    }

    private static @NotNull NeuralNetwork newFoodEvaluator() {
        return new NeuralNetwork(new int[] {3, 3, 1});
    }

    /**
     * TODO: consider velocity/direction?
     */
    private static @NotNull NeuralNetwork newAgentEvaluator() {
        return new NeuralNetwork(new int[] {3, 3, 1});
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
        resetEntityEvaluation();
        environment.visitEntitiesInRange(this, visionRadius, visionRadiusSquared, entityEvaluator);
        return new float[] {
                (float) (energy / calculateCloneThreshold()),
                (float) (getRadius() / size),
                maxFoodValue,
                maxPreyValue,
                maxThreatValue,
                cachedTerrain.ordinal()
        };
    }

    public void resetEntityEvaluation() {
        maxFoodValue = 0;
        bestFood = null;
        maxPreyValue = 0;
        bestPrey = null;
        maxThreatValue = 0;
        worstThreat = null;
    }

    public void evaluateEntity(Entity entity, double distanceSquared) {
        if (entity instanceof Food food) {
            if (food.getRadius() <= 0 || food == lastEatenFood) return;

            float value = evaluateFood(food);
            if (value > maxFoodValue) {
                maxFoodValue = value;
                bestFood = food;
            }
        } else if (entity instanceof Agent agent) {
            if (isSameSpecies(this, agent)) return;

            float input1 = (float) (agent.getRadius() / this.getRadius());
            float input2 = (float) (distanceSquared / visionRadiusSquared);
            float input3 = (float) getGenomeComparison(agent); // TODO: Could store a map in Environment of genome comparisons, and delete then when the agents die.
//            System.out.println("input1: " + input1 + " input2: " + input2 + " input3: " + input3);

            float preyValue = preyEvaluator.predict3x3x1(input1, input2, input3);
            if (preyValue > maxPreyValue) {
                maxPreyValue = preyValue;
                bestPrey = agent;
            }

            float threatValue = predatorEvaluator.predict3x3x1(input1, input2, input3);
            if (threatValue > maxThreatValue) {
                maxThreatValue = threatValue;
                worstThreat = agent;
            }
        }
    }

    /**
     * TODO: ACTUALLY HAVE THIS BE A NN, AND HAVE IT ALSO GET WHETHER THE FOOD IS IN THE WATER OR ON LAND, THAT WAY WE DON'T NEED RAYCASTS
     * @param food
     * @return
     */
    private float evaluateFood(@NotNull Food food) {
        return (float) (food.getRadius() / this.distance(food));
    }

    /**
     * TODO: Implement NN
     * TODO: Pass in how related the current agent is to the one it's seeing
     */
    @Override
    public void getDecision() {
        float[] inputs = getInput();
//        System.out.println(Arrays.toString(inputs));
        float[] output = brain.predict6x10x7(inputs[0], inputs[1], inputs[2], inputs[3], inputs[4], inputs[5]);

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
            action = "FORAGE";
        } else if (targetPrey > targetFood && targetPrey > runFromThreat && targetPrey > BOOLEAN_DECISION_THRESHOLD) {
            target = directionTo(bestPrey);
            turn = getTurnToTarget(target);
            desiresPredation = true;
            action = "HUNT";
        } else if (runFromThreat > targetFood && runFromThreat > targetPrey && runFromThreat > BOOLEAN_DECISION_THRESHOLD) {
            target = directionTo(worstThreat).scale(-1);
            turn = getTurnToTarget(target);
            desiresPredation = false;
            action = "ESCAPE";
        } else {
            target = direction;
            double brainTurn = ((output[6] * 2) - 1) * turnSpeed;
            double jitter = ThreadLocalRandom.current().nextDouble(-1, 1) * WANDER_JITTER_DEGREES;
            turn = brainTurn * 0.25 + jitter;
            desiresPredation = false;
            action = "WANDER";
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
    public void paint(@NotNull GraphicsContext gc, boolean drawVisionRadius) {
        updateColor();
        gc.setFill(getColor());
        gc.fillOval(x() - getRadius(), y() - getRadius(), getRadius() * 2, getRadius() * 2);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(3);
        gc.strokeOval(x() - getRadius(), y() - getRadius(), getRadius() * 2, getRadius() * 2);

        if (drawVisionRadius) {
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

    private Vector getBounds() {
        return environment.getSize();
    }

    private double getPassiveEnergyCost() {
        return visionRadius * VISION_QUALITY_COST + Math.pow(getRadius(), 2) * SIZE_COST;
    }

    @Contract(" -> new")
    public @NotNull AgentData getAgentData() {
        return new AgentData(
                getPosition(),
                getColor(),
                this.energy,
                this.maxSpeed,
                this.turnSpeed,
                this.size,
                this.visionRadius,
                this.healthRegenSpeed,
                this.swimSkill,
                this.brain,
                this.foodEvaluator,
                this.preyEvaluator,
                this.predatorEvaluator
        );
    }

    private void changeEnergy(double change) {
        energy += change * GLOBAL_ENERGY_LOSS_FACTOR;
    }

    public static double calculateCloneThreshold(double radius) {
        return CLONE_THRESHOLD * Math.pow(radius, 1);
    }

    public double calculateCloneThreshold() {
        return calculateCloneThreshold(getRadius());
    }

    public boolean desiresPredation() {
        return decision.desiresPredation();
    }

    public String getAction() {
        return action;
    }

    public TerrainType getTerrainType() {
        return cachedTerrain;
    }

    protected void setPosition(Vector position) {
        super.setPosition(position);
        cachedTerrain = environment.getStandingTerrainType(this);
    }
}