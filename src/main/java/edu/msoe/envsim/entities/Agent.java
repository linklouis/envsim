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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;

public class Agent extends MobileEntity {
    public static final double MIN_SIZE = 1;
    /**
     * The minimum portion of its max health an agent must have to be eligible for reproduction.
     */
    private static final double MIN_HEALTH_FOR_REPRODUCTION = 0.8;
    /**
     * The energy at which an entity will clone.
     * The actual threshold is calculated using this and radius.
     */
    private static final int CLONE_THRESHOLD = 20;
    private static final double CLONE_THRESHOLD_EXPONENT = 0.8;
    /**
     * The size of bite an Agent can take relative to its radius.
     */
    private static final double MAX_BITE_SIZE = 0.8;
    /**
     * The portion of max energy wasted when catching and eating prey.
     * <p>
     * Scales with the size ratio (so it's easier to eat something smaller.)
     * </p>
     */
    private static final double PREDATION_COST_FACTOR = 0.1;    // TODO: Can maybe get rid of since we have predation efficiency?
    /**
     * The portion of the prey's energy that the predator actually receives.
     */
    private static final double PREDATION_EFFICIENCY = 0.99; // Should be much more beneficial than eating plants, so if you get huge you have to eat others, plants can't sustain you.
    /**
     * The portion of energy to spend when trying to attempt predation and not touching another agent
     */
    private static final double FALSE_PREDATION_PUNISHMENT_FACTOR = 0.02;
    /**
     * The portion of total energy an agent needs to start regenerating health.
     */
    private static final double HEALTH_REGENERATION_THRESHOLD = 0.3;
    /**
     * The energy an agent spends to regain a single pixel of radius.
     */
    private static final double HEALTH_REGEN_COST = 1;
    /**
     * The amount of energy cost per time step per pixel of vision radius
     */
    private static final double PASSIVE_VISION_QUALITY_COST = 0.001;
    /**
     * The energy cost per time step per point of defense
     */
    private static final double PASSIVE_DEFENSE_COST = 0.01;
    /**
     * The passive energy an Agent loses each tick per pixel of its radius squared.
     */
    private static final double PASSIVE_SIZE_COST = 0.01;
    private static final double PASSIVE_SIZE_EXPONENT = 2.5;
    public static final double PASSIVE_LIFE_COST = 0.01;
    public static final double PASSIVE_TURN_SPEED_COST = 0.003;
    /**
     * The number of energy points it costs an Agent to move one pixel in a tick.
     */
    private static final double SPEED_COST = 0.07;
    private static final int EFFORT_COST_EXPONENT = 2;
    /**
     * TODO
     */
    private static final double TURN_COST = 6;
    /**
     * The number of energy points it costs an Agent to attack per attack point
     */
    private static final double ATTACK_COST = 0.3;
    /**
     * Sets the max energy at a clone event to be the clone threshold times this value.
     * TODO: make better name
     */
    private static final int MAX_TIME_CLONE_THRESHOLD_ENERGY_LIMIT = 3;
    /**
     * The multiplier for how much energy an Agent actually loses each tick.
     * Allows for overall simulation difficulty control without messing with other parameters
     */
    private static double GLOBAL_ENERGY_LOSS_FACTOR = 0.000001;
    private final static double FINAL_GLOBAL_ENERGY_LOSS_FACTOR = 0.0045;
    private final static int GLOBAL_ENERGY_LOSS_ANNEALING_TIME = 4000000;
    private final static double GLOBAL_ENERGY_LOSS_ANNEALING_RATE = (FINAL_GLOBAL_ENERGY_LOSS_FACTOR - GLOBAL_ENERGY_LOSS_FACTOR) / GLOBAL_ENERGY_LOSS_ANNEALING_TIME;
    /**
     * The maximum number of degrees that an Agent's direction can be randomly changed by each tick when wandering.
     * Prevents looping.
     */
    private static final double WANDER_JITTER_DEGREES = 20;

    private static final AtomicLong NEXT_ID = new AtomicLong(1);


    // ARCHITECTURE PARAMETERS
    private static final int NUM_RAYS = 11;
    private static final int NUM_INPUTS = 9 + NUM_RAYS * 6;
    /**
     * The smallest value the NN can output that's still considered true for a boolean output
     */
    private static final double BOOLEAN_DECISION_THRESHOLD = 0.32;
    private static final double SPECIES_SIMILARITY_EXPONENTIAL_DECAY_FACTOR = 2;
    private static final double SPECIES_SIMILARITY_THRESHOLD = 0.95;
    private static final double SPECIES_MAX_DIFFERENCE = Math.sqrt(-Math.log(SPECIES_SIMILARITY_THRESHOLD) / SPECIES_SIMILARITY_EXPONENTIAL_DECAY_FACTOR);


    // MISC VALUES
    private final @NotNull Environment environment;
    private final Color baseColor;
    private final int generation;
    private final long id;

    public record RaySnapshot(Vector direction, double distance, @Nullable Entity entity, TerrainType terrain) {}
    private final RaySnapshot[] raySnapshots = new RaySnapshot[NUM_RAYS];


    // GENETIC ATTRIBUTES
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
    /**
     * TODO: IS THIS EVEN REALLY DOING ANYTHING?
     */
    private final double turnSpeed;
    private final double swimSkill;
    private final double attack;
    private final double defense;
    private final double fieldOfView;

    private final @NotNull NeuralNetwork brain;
    /**
     * Inputs: food size, distance
     * Output: value
     */
    private final @NotNull NeuralNetwork foodEvaluator;
    /**
     * Inputs: self size, agent size, distance // TODO: In the future, maybe add what direction it's moving in?
     * Outputs: Value (1 = invaluable, 0 = useless, -1 = deadly threat)
     */
    private final @NotNull NeuralNetwork preyEvaluator;
    private final @NotNull NeuralNetwork predatorEvaluator;

    private final float[] genome;
    private final float genomeNormSq;


    // STATE
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

    private Food bestFood;
    private Agent bestPrey;
    private Agent worstThreat;

    private float maxFoodValue;
    private float maxPreyValue;
    private float maxThreatValue;

    private AgentDecision decision;
    private String priority = "WANDER";

    // COLLISION HANDLING
    private final List<Entity> collisions = new ArrayList<>();
    private static final BiPredicate<Entity, Entity> STILL_PRESENT = (_, other) -> other.getRadius() > 0;
    /** Created once so {@link #getInput()} doesn't allocate a method reference every tick. */
    private final SpatialGrid.Visitor entityEvaluator = this::evaluateEntity;

    // CACHES
    private TerrainType cachedTerrain;
    private double[] rayDirections;
    private final RayCaster[] rayCasters;
    private final Map<Long, Double> genomeComparisonCache = new HashMap<>();
    private final long[] genomeComparisonIds = new long[256];     // ids start at 1, so 0 = empty
    private final double[] genomeComparisonValues = new double[256];


    public Agent(double radius, Color color, @NotNull Environment env) {
//        Vector position = Vector.random(new Vector(env.getSize().x() / 2, 0), env.getSize());
        Vector position = Vector.random(new Vector(0, 0), env.getSize());
        AgentData agentData = new AgentData(
                position,
                color,
                calculateCloneThreshold(radius) - 1,
                Math.random() * 2.5 + 0.6,
                Math.random() * 180 + 50,
                radius,
                Math.random() * 1000 + 400,
                Math.random(),
//                Math.pow(Math.random(), 0.2),
                Math.random(),
                Math.random() * 10,
                Math.random() * 5,
                Math.random() * Math.PI,
                newBrain(),
                newFoodEvaluator(),
                newAgentEvaluator(),
                newAgentEvaluator(),
                0
        );

        this(agentData, env);
    }

    public Agent(@NotNull AgentData agentData, @NotNull Environment env) {
        super(agentData.position().add(Vector.random(20)), agentData.size(), agentData.color(), env);

        this.id = NEXT_ID.getAndIncrement();

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
        attack = agentData.attack();
        defense = agentData.defense();
        fieldOfView = agentData.fieldOfView();
        brain =  agentData.brain();
        foodEvaluator = agentData.foodEvaluator();
        preyEvaluator = agentData.preyEvaluator();
        predatorEvaluator = agentData.predatorEvaluator();

        generation = agentData.generation();

        direction = Vector.random(1);

//        rayDirections = RayCaster.calculateRayDirections(fieldOfView, NUM_RAYS);
        rayCasters = RayCaster.getCasters(fieldOfView, NUM_RAYS, getPosition(), visionRadius, direction.angle(), environment::getTerrainType);

        genome = extractGenome();
        float norm = 0;
        for (float val : genome) { norm += val * val; }
        this.genomeNormSq = norm;

        this.baseColor = SpeciesColorMapper.getBaseSpeciesColor(genome);
        updateColor();
    }

    private float @NotNull [] extractGenome() {
        List<Float> traits = new ArrayList<>();

        traits.add((float) maxSpeed);
        traits.add((float) turnSpeed);
        traits.add((float) size);
        traits.add((float) visionRadius);
        traits.add((float) healthRegenSpeed);
        traits.add((float) swimSkill);
        traits.add((float) attack);
        traits.add((float) defense);
        traits.add((float) fieldOfView);

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

    /**
     *
     * @param other The other {@link Agent} to compare genomes with.
     * @return The Root Mean Squared proportional difference between the genes of the two {@link Agent}s.
     */
    private double compareGenome(@NotNull Agent other) {
        // TODO: This was updated to make each gene matter equally, which makes sense for the species check,
        //  but less sense for the color. I think physical traits should probably matter more for that.
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
     * @param other The other {@link Agent} to compare genomes with.
     * @return A `double` representing the similarity score between the genetics of the two {@link Agent}s.
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

    /**
     * TODO
     * @param other
     * @return
     */
    private double getGenomeComparisonOld(@NotNull Agent other) {
        Double cached = genomeComparisonCache.get(other.id);
        if (cached != null) return cached;

        double comparison = compareGenome(other);
        genomeComparisonCache.put(other.id, comparison);

        return comparison;
    }

    private double getGenomeComparison(@NotNull Agent other) {
        int slot = (int) (other.id & 255);
        if (genomeComparisonIds[slot] == other.id) {
            return genomeComparisonValues[slot];
        }
        double comparison = compareGenome(other);
        genomeComparisonIds[slot] = other.id;
        genomeComparisonValues[slot] = comparison;
        return comparison;
    }

    public void parallelStep() {
        if (!alive) return;

        move(decision.turn(), decision.speed() * maxSpeed);
        changeEnergy(-getPassiveEnergyCost());

        timeSinceReproduction++;
        //if (reproductionDesire > 0.6 && energy > 80) {
        if (energy > calculateCloneThreshold() && getRadius() / size > MIN_HEALTH_FOR_REPRODUCTION) {
            reproduce();
        }

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

        boolean collidedAgent = false;
        for (Entity entity : collisions) {
            if (entity.getRadius() <= 0) continue;
            if (entity instanceof Food food) {
                eat(food);
            } else if (entity instanceof Agent agent) {
                interact(agent);
                collidedAgent = true;
            }
        }

        if (!collidedAgent && decision.desiresPredation()) {
            energy -= FALSE_PREDATION_PUNISHMENT_FACTOR * getRadius();
        }

        if (energy <= 0 || getRadius() <= 0) die();
    }

    private void move(double turn, double speed) {
        // TODO: Also add acceleration so larger ones start slower?
        direction = direction.turn(turn);
        double speedModifier = Math.pow(getRadius() * cachedTerrain.getSpeedSizeCostFactor(), 2) + 1;
        double effort = speed / speedModifier;
        double cost = SPEED_COST * Math.pow(effort, EFFORT_COST_EXPONENT) + TURN_COST * turn * turn; // TODO: Have moving in your non-preferred terrain cost more energy?

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
        double consumptionSize = getRadius() * MAX_BITE_SIZE / food.getRadius();//Math.min(1, getRadius() * MAX_RAD_RATIO_FOR_PREDATION / food.getRadius());    // TODO: Does this make any sense? The idea is for agents to not be able to eat an entire food if they are smaller than it. They should only be able to eat MAX_DIF... % of their  // TODO: Maybe switch to max energy once it relies on radius?
        energy += consumptionSize * food.getValue();
        food.getEaten(consumptionSize);
        lastEatenFood = food;
    }

    private void interact(Agent other) {
        if (!this.alive || !other.alive) return;

        if (decision.desiresPredation()) {
            attemptPredation(other);
        }
    }

    private void updateColor() {
        double brightness = Math.clamp(energy / calculateCloneThreshold(), 0.0, 1.0);
        setColor(Color.hsb(baseColor.getHue(), baseColor.getSaturation(), brightness));
    }

    /**
     * @param prey The {@link Agent} to attempt to predate upon.
     */
    private void attemptPredation(@NotNull Agent prey) {
//        if (this.parent == prey || prey.parent == this) return;  // Should be covered by the genome comparison

        changeEnergy(getPredationCost(prey));
        double selfAttack = this.attack / prey.defense;

        double otherAttack = 0;
        if (prey.decision.desiresPredation()) {
            prey.changeEnergy(prey.getPredationCost(this));
            otherAttack = prey.attack / this.defense;
        }

        double selfHealth = this.getRadius() - otherAttack;
        double otherHealth = prey.getRadius() - selfAttack;

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
        } else {
            this.setRadius(selfHealth);
            prey.setRadius(otherHealth);
        }
    }

    private void succeedPredation(@NotNull Agent prey) {
        if (!prey.alive) return;
//        System.out.println("Predation!");
        energy += prey.energy * PREDATION_EFFICIENCY;
        environment.countPredation();
    }

    private double getPredationCost(@NotNull Agent prey) {
        return -(PREDATION_COST_FACTOR * calculateCloneThreshold() * prey.getRadius() / getRadius() + ATTACK_COST * attack);
    }

    private void reproduce() {
        energy = Math.min(energy, calculateCloneThreshold() * MAX_TIME_CLONE_THRESHOLD_ENERGY_LIMIT) / 2;
        new Agent(getAgentData().mutate(), environment);
        timeSinceReproduction = 0;
    }

    private static @NotNull NeuralNetwork newBrain() {
        return new NeuralNetwork(new int[] {NUM_INPUTS, (int) (NUM_INPUTS * 0.4), 5});    // TODO: Probably make the hidden layer a little bigger
    }

    private static @NotNull NeuralNetwork newFoodEvaluator() {
        return new NeuralNetwork(new int[] {4, 3, 1});
    }

    /**
     * TODO: consider velocity/direction?
     * TODO: Add attack and defense
     */
    private static @NotNull NeuralNetwork newAgentEvaluator() {
        return new NeuralNetwork(new int[] {7, 3, 1});
    }


    private RayCaster.RayData @NotNull [] doRaycasts() {
        RayCaster.RayData[] rayData = new RayCaster.RayData[NUM_RAYS];
        double angle = this.direction.angle();

        int i = 0;
        for (RayCaster caster: rayCasters) {
            caster.reset(getPosition(), angle);
            environment.visitEntitiesInRange(this, visionRadius, visionRadiusSquared, caster.getVisitor());
            RayCaster.RayData data = caster.getRayData();
            rayData[i] = data;

            // Capture a thread-safe snapshot for the rendering thread
            raySnapshots[i] = new RaySnapshot(caster.getDirection(), data.distance(), data.entity(), data.terrain());
            i++;
        }

        return rayData;
    }

    /**
     * Inputs (For now): energy, health, bestFood, bestPrey, worstThreat, currentTerrain
     * Outputs (For now): speed, repDesire, predDesire, targetFood, targetPrey, runFromThreat, turn
     */
    @Contract(" -> new")
    private float @NotNull [] getInput() {
        resetEntityEvaluation();
        environment.visitEntitiesInRange(this, visionRadius, visionRadiusSquared, entityEvaluator);
        float[] inputs = new float[NUM_INPUTS];

        inputs[0] = (float) (energy / calculateCloneThreshold());
        inputs[1] = (float) (getRadius() / size);
        inputs[2] = maxFoodValue;
        inputs[3] = maxPreyValue;
        inputs[4] = maxThreatValue;
        inputs[5] = calculateNormalizedRelativeAngle(bestFood);
        inputs[6] = calculateNormalizedRelativeAngle(bestPrey);
        inputs[7] = calculateNormalizedRelativeAngle(worstThreat);
        inputs[8] = cachedTerrain.ordinal();

        RayCaster.RayData[] rayData = doRaycasts();
        for (int i = 0; i < NUM_RAYS * 6; i += 6) {
            RayCaster.RayData ray = rayData[i/6];
            Entity entity = ray.entity();

            float sizeRatio = 0;
            float distance = 0;
            float attackDefenseRatio = 0;
            float defenseAttackRatio = 0;
            int entityType = 0;

            if (entity != null) {
                sizeRatio = (float) (entity.getRadius() / this.getRadius());
                distance = (float) (ray.distance() / visionRadius);

                if (entity instanceof Agent agent) {
                    attackDefenseRatio = (float) (this.attack / agent.defense);
                    defenseAttackRatio = (float) (this.defense / agent.attack);
                    entityType = 1;
                } else {
                    entityType = -1;
                }
            }

            inputs[9 + i] = sizeRatio;
            inputs[10 + i] = distance;
            inputs[11 + i] = attackDefenseRatio;
            inputs[12 + i] = defenseAttackRatio;
            inputs[13 + i] = ray.terrain().ordinal();
            inputs[14 + i] = entityType;    // TODO: Maybe make two, one for isAgent, one for isFood?
        }
        return inputs;
    }

    /**
     * Computes the relative angle between the agent's facing direction and a target entity,
     * normalized directly into the [-1.0, 1.0] range.
     * Returns 0.0 if target is null or at the exact same location.
     * Source: Google Gemini
     */
    private float calculateNormalizedRelativeAngle(Entity target) {
        if (target == null) {
            return 0f;
        }

        // Direct manual vector difference (Target Position - Agent Position)
        double tx = target.x() - x();
        double ty = target.y() - y();

        double distSq = tx * tx + ty * ty;
        if (distSq < 1e-12) {
            return 0f;
        }

        double invDist = 1.0 / Math.sqrt(distSq);
        tx *= invDist;
        ty *= invDist;

        double dirX = direction.x();
        double dirY = direction.y();

        // 2D Vector Cross product (sin of relative angle) & Dot product (cos of relative angle)
        // cross = dirX * ty - dirY * tx
        // dot   = dirX * tx + dirY * ty
        double relativeAngle = Math.atan2(dirX * ty - dirY * tx, dirX * tx + dirY * ty);

        // Normalize [-PI, PI] range into [-1, 1]
        return (float) (relativeAngle / Math.PI);
    }

    public void resetEntityEvaluation() {
        maxFoodValue = 0;
        bestFood = null;
        maxPreyValue = 0;
        bestPrey = null;
        maxThreatValue = 0;
        worstThreat = null;
    }

    public void evaluateEntity(@NotNull Entity entity, double distanceSquared) {
        // TODO: HAVING THE PREFERENCE FOR FOOD/PREY ON ONE TERRAIN TYPE MIGHT MAKE EVOLVING OUT/INTO THE WATER MUCH HARDER
        if (entity instanceof Food food) {
            if (food.getRadius() <= 0 || food == lastEatenFood) return;

            float sizeRatio = (float) (entity.getRadius() / this.getRadius());
            float distance = (float) (distanceSquared / visionRadiusSquared);
            float directionTo = calculateNormalizedRelativeAngle(entity);

//            float foodValue = evaluateFood(food);
            float foodValue = foodEvaluator.predict4x3x1(sizeRatio, distance, food.getTerrainType().ordinal(), directionTo);
            if (foodValue > maxFoodValue) {
                maxFoodValue = foodValue;
                bestFood = food;
            }
        } else if (entity instanceof Agent agent) {
            float genomeComparison = (float) getGenomeComparison(agent);
            if (genomeComparison < SPECIES_MAX_DIFFERENCE) return;

            float sizeRatio = (float) (entity.getRadius() / this.getRadius());
            float distance = (float) (distanceSquared / visionRadiusSquared);
            float directionTo = calculateNormalizedRelativeAngle(entity);
            float attackDefenseRatio = (float) (this.attack / agent.defense);
            float defenseAttackRatio = (float) (this.defense / agent.attack);

            float preyValue = preyEvaluator.predict7x3x1(sizeRatio, distance, genomeComparison, agent.getTerrainType().ordinal(), attackDefenseRatio, defenseAttackRatio, directionTo);
            if (preyValue > maxPreyValue) {
                maxPreyValue = preyValue;
                bestPrey = agent;
            }

            // TODO: Do we even need the terrain type here?
            float threatValue = predatorEvaluator.predict7x3x1(sizeRatio, distance, genomeComparison, agent.getTerrainType().ordinal(), attackDefenseRatio, defenseAttackRatio, directionTo);
            if (threatValue > maxThreatValue) {
                maxThreatValue = threatValue;
                worstThreat = agent;
            }
        }
    }

    /**
     * TODO: ACTUALLY HAVE THIS BE A NN, AND HAVE IT ALSO GET WHETHER THE FOOD IS IN THE WATER OR ON LAND, THAT WAY WE DON'T NEED RAYCASTS
     * @param food The {@link Food} object to evaluate
     * @return The calculated value score for the {@link Food}.
     */
    private float evaluateFood(@NotNull Food food) {
        return (float) (food.getRadius() / this.distance(food));
    }

    @Override
    public void getDecision() {
        // TODO: Update the brain to take in the direction to the best food and the best prey and worst threat, as well as the value of each. Allow the brain to decide how to turn as a value -1 - 1, instead of only allowing it to pick which one to face towards.
        // TODO: When passing the direction to the above things, have it be relative to the Agent's direction, not the map.
        // TODO: ADD OUTPUT FOR AGENTS TO DECIDE HOW MUCH RANDOMNESS TO ADD TO THEIR DIRECTION EACH STEP.
        float[] inputs = getInput();
//        System.out.println(Arrays.toString(inputs));
//        float[] output = brain.predict9x10x5(inputs[0], inputs[1], inputs[2], inputs[3], inputs[4], inputs[5], inputs[6], inputs[7], inputs[8]);   // TODO: ADD BY-HAND METHOD

//        double targetFood = bestFood == null ? 0 : output[1];
//        double targetPrey = bestPrey == null ? 0 : output[2];
//        double runFromThreat = worstThreat == null ? 0 :output[3];
//
//        double turn;
//        boolean desiresPredation;

//        if (targetFood > targetPrey && targetFood > runFromThreat && targetFood > BOOLEAN_DECISION_THRESHOLD) {
//            turn = (output[4] * 2 - 1) * turnSpeed;
//            desiresPredation = false;
//            priority = "FORAGE";
//        } else if (targetPrey > targetFood && targetPrey > runFromThreat && targetPrey > BOOLEAN_DECISION_THRESHOLD) {
//            desiresPredation = true;
//            turn = (output[4] * 2 - 1) * turnSpeed;
//            priority = "HUNT";
//        } else if (runFromThreat > targetFood && runFromThreat > targetPrey && runFromThreat > BOOLEAN_DECISION_THRESHOLD) {
//
//            turn = (output[4] * 2 - 1) * turnSpeed;
//            desiresPredation = false;
//            priority = "ESCAPE";
//        } else {
//            double brainTurn = (output[4] * 2 - 1) * turnSpeed;
//            double jitter = ThreadLocalRandom.current().nextDouble(-1, 1) * WANDER_JITTER_DEGREES;
//            turn = brainTurn * 0.25 + jitter;
//            desiresPredation = false;
//            priority = "WANDER";
//        }

        float[] output = brain.predict(inputs);

        double targetPrey = bestPrey == null ? 0 : output[1];

        double turn;
        boolean desiresPredation;

        if (targetPrey > BOOLEAN_DECISION_THRESHOLD) {
            desiresPredation = true;
            priority = "HUNT";
        } else {
            desiresPredation = false;
            priority = "WANDER";
        }
        turn = (output[2] * 2 - 1) * turnSpeed;

        turn = Math.clamp(turn, -maxTurnPerTick(), maxTurnPerTick());

        this.decision =  new AgentDecision(
                output[0],
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
        return Math.toDegrees(Math.atan2(Math.sin(rawChange), Math.cos(rawChange)));
    }


    @Override
    public void paint(@NotNull GraphicsContext gc, boolean drawVisionRadius) {
        updateColor();
        gc.setFill(getColor());
        gc.fillOval(x() - getRadius(), y() - getRadius(), getDiameter(), getDiameter());
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(3);
        gc.strokeOval(x() - getRadius(), y() - getRadius(), getDiameter(), getDiameter());

        if (drawVisionRadius) {
            gc.setLineWidth(1);
            gc.strokeOval(x() - visionRadius, y() - visionRadius, visionRadius * 2, visionRadius * 2);
        }

        if (this == environment.getSelectedAgent()) {
            gc.setLineWidth(7);
            for (RaySnapshot ray : raySnapshots) {
                if (ray == null) continue;
                Vector start = getPosition();
                Vector end = start.add(ray.direction().scale(ray.distance()));

                if (ray.entity() != null) {
                    // Hit an entity: draw a bright red/orange line and a yellow intersection dot
                    gc.setStroke(Color.TOMATO);
                    gc.strokeLine(start.x(), start.y(), end.x(), end.y());
                    gc.setFill(Color.YELLOW);
                    gc.fillOval(end.x() - 2, end.y() - 2, 4, 4);
                } else {
                    // Miss (max distance): draw a translucent blue/gray line
                    gc.setStroke(Color.web("#4a90e2", 0.6));
                    gc.strokeLine(start.x(), start.y(), end.x(), end.y());
                }
            }
        }
    }

    protected void die() {
        if (!alive) return;
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
        return PASSIVE_LIFE_COST
                + turnSpeed * getRadius() * PASSIVE_TURN_SPEED_COST
                + visionRadius * PASSIVE_VISION_QUALITY_COST
                + Math.pow(getRadius(), PASSIVE_SIZE_EXPONENT) * PASSIVE_SIZE_COST
                + defense * PASSIVE_DEFENSE_COST;
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
                this.attack,
                this.defense,
                this.fieldOfView,
                this.brain,
                this.foodEvaluator,
                this.preyEvaluator,
                this.predatorEvaluator,
                this.generation
        );
    }

    private void changeEnergy(double change) {
        energy += change * GLOBAL_ENERGY_LOSS_FACTOR;
    }

    private static double calculateCloneThreshold(double radius) {
        return CLONE_THRESHOLD * Math.pow(radius, CLONE_THRESHOLD_EXPONENT); // TODO: Make the exponent a constant
    }

    public double calculateCloneThreshold() {
        return calculateCloneThreshold(getRadius());
    }

    public boolean desiresPredation() {
        return decision.desiresPredation();
    }

    public String getPriority() {
        return priority;
    }

    @Override
    public TerrainType getTerrainType() {
        return cachedTerrain;
    }

    protected void setPosition(Vector position) {
        super.setPosition(position);
        cachedTerrain = environment.getStandingTerrainType(this);
    }

    public static void updateDifficulty() {
        if (GLOBAL_ENERGY_LOSS_FACTOR < FINAL_GLOBAL_ENERGY_LOSS_FACTOR) {
            GLOBAL_ENERGY_LOSS_FACTOR += GLOBAL_ENERGY_LOSS_ANNEALING_RATE;
        }
    }
}