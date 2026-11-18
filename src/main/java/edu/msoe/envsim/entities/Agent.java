package edu.msoe.envsim.entities;

import edu.msoe.envsim.Environment;
import edu.msoe.envsim.utilities.Vector;
import edu.msoe.envsim.utilities.simutils.*;
import edu.msoe.envsim.utilities.apputils.GameSnapshot.AgentState;
import edu.msoe.envsim.utilities.NeuralNetwork;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;

import static edu.msoe.envsim.utilities.simutils.PhysicsMath.*;

public class Agent extends MobileEntity {
    // WEIGHT PARAMETERS
    public static final double MIN_SIZE = 1;
    /**
     * The minimum portion of its max health an agent must have to be eligible for reproduction.
     */
    private static final double MIN_HEALTH_FOR_REPRODUCTION = 0.8;
    /**
     * The energy at which an entity will clone.
     * The actual threshold is calculated using this and radius.
     */
    private static final int BASE_CLONE_THRESHOLD = 20;
    /**
     * In the real world, this should likely be proportional to mass, so this should be 3.
     * However, reproduction and mutation happen significantly faster here, and having this be any value above 1 causes
     * the smaller agents to reproduce, and therefore evolve, much faster than the large ones, giving them a significant advantage.
     */
    private static final double CLONE_THRESHOLD_EXPONENT = 0.8;
    /**
     * Sets the max energy at a clone event to be the clone threshold times this value.
     */
    private static final int SINGLE_CLONE_ENERGY_LIMIT = 3;
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
    private static final double PREDATION_COST_FACTOR = 0.05;    // TODO: Can maybe get rid of since we have predation efficiency?
    /**
     * The portion of the prey's energy that the predator actually receives.
     */
    private static final double PREDATION_EFFICIENCY = 0.999; // Should be much more beneficial than eating plants, so if you get huge you have to eat others, plants can't sustain you.
    /**
     * The portion of energy to spend when trying to attempt predation and not touching another agent
     */
    private static final double FALSE_PREDATION_PUNISHMENT_FACTOR = 0.001;
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
    private static final double PASSIVE_VISION_QUALITY_COST = 0.0003;
    /**
     * The energy cost per time step per point of defense
     */
    private static final double PASSIVE_DEFENSE_COST = 0.01;
    /**
     * The passive energy an Agent loses each tick per pixel of its radius squared.
     */
    private static final double PASSIVE_SIZE_COST = 0.0003;
    /**
     * Kleiber's Law dictates that basal metabolic rate scales with mass to the 3/4 power.
     * In 3D, mass scales with R^3.
     * So basal metabolic rate should scale to R^{9/4} = R^2.25
     */
    private static final double PASSIVE_SIZE_EXPONENT = 2.25;
    /**
     * The amount of energy lost each tick regardless of the Agent's actions.
     */
    public static final double PASSIVE_LIFE_COST = 0.012;
    /**
     * The amount of energy lost each tick per unit of max turn speed the Agent is capable of.
     */
    public static final double PASSIVE_TURN_SPEED_COST = 0.00004;
    /**
     * The cost per tick of having a 100% preference for a single food type.
     */
    private static final double PASSIVE_FOOD_PREFERENCE_COST = 4;
    private static final double PASSIVE_FOOD_PREFERENCE_EXPONENT = 4;
    /**
     * TODO: Actually acceleration now
     */
    private static final double SPEED_COST = 0.12;
    /**
     * The exponent the effort the Agent puts into accelerating is raised to when calculating the energy cost
     */
    private static final int EFFORT_COST_EXPONENT = 2;
    /**
     * The exponent the Agents' turn choice is raised to when calculating the energy cost
     */
    private static final int TURN_COST_EXPONENT = 2;
    /**
     * The factor the Agents' turn choice is raised to when calculating the energy cost.
     */
    private static final double TURN_COST = 0.2;
    /**
     * The number of energy points it costs an Agent to attack per attack point
     */
    private static final double ATTACK_COST = 0.25;


    // DIFFICULTY PARAMETERS
    private static final double INITIAL_GELF = 0.00000000001;//0.00001;//
    /**
     * The multiplier for how much energy an Agent actually loses each tick.
     * Allows for overall simulation difficulty control without messing with other parameters
     */
    private static double GLOBAL_ENERGY_LOSS_FACTOR = INITIAL_GELF;
    private final static double FINAL_GLOBAL_ENERGY_LOSS_FACTOR = 0.013;
    private final static int TARGET_GLOBAL_ENERGY_LOSS_ANNEALING_TIME = 10000000;
    private final static double GLOBAL_ENERGY_LOSS_ANNEALING_RATE = (FINAL_GLOBAL_ENERGY_LOSS_FACTOR - GLOBAL_ENERGY_LOSS_FACTOR) / TARGET_GLOBAL_ENERGY_LOSS_ANNEALING_TIME;
    private final static int TARGET_NUM_AGENTS_WHILE_ANNEALING = 3000;
    private final static int MIN_NUM_AGENTS_WHILE_ANNEALING = 800;
    private final static int TARGET_MEAN_RADIUS_WHILE_ANNEALING = 15;


    // AGENT STATE PARAMETERS
    private static final AtomicLong NEXT_ID = new AtomicLong(1);
    public final static double MAX_BODY_ELONGATION = 2.5;
    public final static double MIN_BODY_ELONGATION = 0.15;
    /**
     * The maximum number of degrees that an Agent's wander offset can be randomly changed by each tick.
     * Prevents looping.
     */
    private static final double RANDOM_WANDER_CHANGE_SPEED = 0.17;
    private static final double MAX_WANDER_ANGLE = 0.8;


    // ARCHITECTURE PARAMETERS
    private static final int NUM_RAYS = 11;
    public static final int NUM_INPUTS = 4 + NUM_RAYS * 7;
    /**
     * The smallest value the NN can output that's still considered true for a boolean output
     */
    private static final double BOOLEAN_DECISION_THRESHOLD = 0.32;
    private static final double SPECIES_SIMILARITY_EXPONENTIAL_DECAY_FACTOR = 2;
    private static final double SPECIES_SIMILARITY_THRESHOLD = 0.999;
    private static final double SPECIES_MAX_DIFFERENCE = Math.sqrt(-Math.log(SPECIES_SIMILARITY_THRESHOLD) / SPECIES_SIMILARITY_EXPONENTIAL_DECAY_FACTOR);


    // MISC VALUES
    private final @NotNull Environment environment;
    private final Color baseColor;
    private final int generation;
    private final long id;
    /**
     * The simulation tick this agent was created on. How many ticks have passed since is its age.
     * Not final because {@link #restore} puts back the birth tick an agent had when it was saved.
     */
    private int birthTick;


    // GENETIC ATTRIBUTES
    /**
     * The maximum distance an Agent can move each time step in pixels.
     * <p>Higher values consume more {@link #energy} each time step.</p>
     * <p>The Agent can decide to move any maxAcceleration from 0 to this value.</p>
     */
    private final double maxAcceleration;
    /**
     * The "Max Health" of the Agent.
     * The actual size gene that gets passed to descendants.
     * Higher values consume more {@link #energy} each time step.
     */
    private final double size;
    /**
     * How close an entity must be for the Agent to see it.
     * Higher values consume more {@link #energy} each time step.
     */
    private final double visionRadius;
    /**
     * Caching the squared radius so it doesn't have to be recalculated in {@link Environment#visitEntitiesInRange(Entity, double, double, SpatialGrid.Visitor, SpatialGrid.Visitor)} every tick.
     */
    private final double visionRadiusSquared;
    private final double healthRegenSpeed;
    private final double turnSpeed;
    private final double swimSkill;
    private final double attack;
    private final double defense;
    private final double fieldOfView;
    private final Food.FoodPreference foodPreference;

    private final @NotNull NeuralNetwork brain;

    private final float[] genome;
    private final float genomeNormSq;


    // STATE
    /**
     * Should always be a unit vector
     */
    private Vector direction;
    private Vector velocity = Vector.ZERO;
    private double speed;
    /**
     * The amount of energy the Agent currently has.
     * Energy is consumed passively each time step based on the {@link #size} and {@link #visionRadius} attributes,
     * and actively consumed by the moving and reproducing.
     * When reproducing, half of the Agent's current energy is passed to the child.
     * Can be replenished by eating.
     */
    private double energy;
    private Food lastEatenFood; // TODO: Probably don't need this anymore with raycasts
    private boolean alive = true;

    private record AgentDecision(double speed, double turn, boolean desiresPredation) {}
    private AgentDecision decision = new AgentDecision(0, 0, false);
    private String priority = "WANDER";
    private double jitterRequested = 0;
    private double currentWanderOffset = 0;

    // COLLISION HANDLING
    private final List<Entity> collisions = new ArrayList<>();
    // TODO: Do we need this? What is it doing?
    private static final BiPredicate<Entity, Entity> STILL_PRESENT = (_, other) -> other.getRadius() > 0;

    // CACHES
    private TerrainType cachedTerrain;
    private final RayCaster rayCaster;
    private final Map<Long, Double> genomeComparisonCache = new HashMap<>();
    private final long[] genomeComparisonIds = new long[256];     // ids start at 1, so 0 = empty
    private final double[] genomeComparisonValues = new double[256];
    private final float[] inputBuffer = new float[NUM_INPUTS];
    private final double perTickFoodPreferenceCost;
    private final double bodyElongation;

    // INSPECTOR VALUES
    private double mass = 0;
    private double movementEffort = 0;
    private double acceleration = 0;
    /**
     * How far the agent actually turned last tick, in degrees. Unlike {@link #turnSpeed}, which is the most it
     * <i>can</i> ask to turn, this is after terrain resistance and body size are applied. Only used for display.
     */
    private double currentTurnRate = 0;


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
                Math.random() * (Math.PI - 0.5) + 0.5,
                Food.FoodPreference.random(),
                new NeuralNetwork(new int[] {NUM_INPUTS, 32, 4}),
                0
        );

        this(agentData, env);
    }

    public Agent(@NotNull AgentData agentData, @NotNull Environment env) {
        this(agentData, env, agentData.position().add(Vector.random(20)));
    }

    /**
     * @param spawnPosition Where the agent is placed. The public constructor passes a slightly jittered position
     *                      so that children don't stack on top of their parent; {@link #restore} passes the exact one.
     */
    private Agent(@NotNull AgentData agentData, @NotNull Environment env, @NotNull Vector spawnPosition) {
        super(spawnPosition, agentData.size(), agentData.color(), env);

        this.id = NEXT_ID.getAndIncrement();

        cachedTerrain = env.getStandingTerrainType(this);

        environment = env;
        energy = agentData.energy();
        maxAcceleration = agentData.maxAcceleration();
        turnSpeed = agentData.turnSpeed();
        size = agentData.size();
        visionRadius = agentData.visionRadius();
        visionRadiusSquared = visionRadius * visionRadius;
        healthRegenSpeed = agentData.healthRegenSpeed();
        swimSkill = agentData.swimSkill();
        bodyElongation = (MAX_BODY_ELONGATION - MIN_BODY_ELONGATION) * Math.pow(swimSkill, 2) + MIN_BODY_ELONGATION;
        attack = agentData.attack();
        defense = agentData.defense();
        fieldOfView = agentData.fieldOfView();
        foodPreference = agentData.foodPreference();
        brain =  agentData.brain();

        perTickFoodPreferenceCost = Math.pow(foodPreference.getTotalPreferenceAmount(), PASSIVE_FOOD_PREFERENCE_EXPONENT) * PASSIVE_FOOD_PREFERENCE_COST;

        generation = agentData.generation();
        birthTick = env.getCurrentTick();

        direction = Vector.random(1);

//        rayDirections = RayCaster.calculateRayDirections(fieldOfView, NUM_RAYS);
        rayCaster = new RayCaster(getPosition(), fieldOfView, NUM_RAYS, visionRadius, direction.angle(),
                environment::getTerrainType, this::isSameSpecies, environment.getSize());

        genome = agentData.extractGenome();
        float norm = 0;
        for (float val : genome) { norm += val * val; }
        this.genomeNormSq = norm;

        this.baseColor = SpeciesColorMapper.getBaseSpeciesColor(genome);
        updateColor();
    }

    /**
     *
     * @param other The other {@link Agent} to compare genomes with.
     * @return The Root Mean Squared proportional difference between the genes of the two {@link Agent}s.
     */
    private double compareGenomeNormalized(@NotNull Agent other) {
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
    private double compareGenome(@NotNull Agent other) {
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

    public boolean isSameSpecies(@NotNull Agent other) {
        return getGenomeComparison(other) < SPECIES_MAX_DIFFERENCE;
    }

    public void parallelStep() {
        if (!alive) return;

        movementEffort = decision.speed() * maxAcceleration;
        move(decision.turn(), movementEffort);
        changeEnergy(-getPassiveEnergyCost());

        //if (reproductionDesire > 0.6 && energy > 80) {
        if (energy > calculateCloneThreshold() && getRadius() / size > MIN_HEALTH_FOR_REPRODUCTION) {
            reproduce();
        }

        // TODO: Health regen could instead be something the NN can decide to consume energy to do.
        if (energy > HEALTH_REGENERATION_THRESHOLD * calculateCloneThreshold() && getRadius() < size) {
            // TODO: Have passive health regen speed be partially based on how low health is?
            double newRadius = getRadius() + (ThreadLocalRandom.current().nextDouble() * healthRegenSpeed); // TODO: Should this really even be random?
            setRadius(Math.min(newRadius, size));
            changeEnergy(-(HEALTH_REGEN_COST * healthRegenSpeed)); // TODO: Have the cost be based on how successful the regen was?
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
                if (!alive) return;
            } else if (entity instanceof Agent agent) {
                interact(agent);
                collidedAgent = true;
            }
        }

        if (!collidedAgent && desiresPredation()) {
            energy -= FALSE_PREDATION_PUNISHMENT_FACTOR * getRadius();
        }

//        if (energy <= 0 || getRadius() <= 0) die();
    }

    private void move(double turnAmount, double thrustEffort) {
        double energyCostOfMoving = SPEED_COST * Math.pow(thrustEffort, EFFORT_COST_EXPONENT)
                + TURN_COST * Math.pow(turnAmount, TURN_COST_EXPONENT);
        changeEnergy(-energyCostOfMoving);

        if (!alive) return;

        double bodyVolume = (4.0 / 3.0) * Math.PI * Math.pow(getRadius(), 3);
        mass = bodyVolume * WATER_DENSITY;

        double terrainThrustModifier = Math.pow(1.0 - Math.abs(cachedTerrain.getSwimSkillRequired() - swimSkill), 3);   // TODO: Make the exponent a hyperparameter
        double thrustAcceleration = thrustEffort * Math.pow(getRadius(), 2) * terrainThrustModifier / mass;
        acceleration = Math.abs(thrustAcceleration);

        double effectiveTurnAmount = turnAmount * cachedTerrain.getTurningResistance() / getRadius();
        direction = direction.turn(effectiveTurnAmount);
        currentTurnRate = effectiveTurnAmount;

        velocity = velocity.add(direction.scale(thrustAcceleration));

        // Anisotropic drag: the fluid resists sideways motion more than forward motion.
        double frontalArea = Math.PI * Math.pow(getRadius(), 2);
        double sidewaysArea = frontalArea * bodyElongation; // TODO: Is this math really right?
        double forwardDragPerSpeed = 0.5 * cachedTerrain.getFluidDensity() * calculateForwardDragCoefficient(bodyElongation) * frontalArea / mass;
        double sidewaysDragPerSpeed = 0.5 * cachedTerrain.getFluidDensity() * calculateSidewaysDragCoefficient(bodyElongation) * sidewaysArea / mass;
        double walkingDragPerSpeed = cachedTerrain.getWalkingResistance();
        forwardDragPerSpeed += walkingDragPerSpeed;

        double totalSpeed = velocity.magnitude();
        double forwardSpeed = velocity.dot(direction);
        Vector forwardVelocity = direction.scale(forwardSpeed);
        Vector sidewaysVelocity = velocity.subtract(forwardVelocity);

        double forwardSpeedAfterDrag = forwardSpeed / (1 + forwardDragPerSpeed * totalSpeed);
        forwardVelocity = direction.scale(forwardSpeedAfterDrag);
        sidewaysVelocity = sidewaysVelocity.scale(1.0 / (1 + sidewaysDragPerSpeed * totalSpeed));

        sidewaysVelocity = sidewaysVelocity.scale(1.0 - cachedTerrain.getLateralGrip());

        velocity = forwardVelocity.add(sidewaysVelocity);
        speed = forwardSpeedAfterDrag;   // Only used for display now

        Vector nextPosition = getPosition().addInBounds(velocity, Vector.ZERO, getBounds());
        setPosition(nextPosition);
    }

    private void eat(@NotNull Food food) {
        if (desiresPredation() || food == lastEatenFood) return;
        double desiredFraction = (getRadius() * MAX_BITE_SIZE) / food.getRadius();
        double actualFraction = Math.min(1.0, desiredFraction);

        double energyGained = actualFraction * food.getValue(foodPreference);
        if (energyGained > 0) {
            energy += energyGained;
        } else {
            setRadius(getRadius() + getRadius() * energyGained);
            if (!alive) return;
        }

        try {
            food.getEaten(desiredFraction);
        } catch (Exception e) {
            System.out.println(getRadius());
            throw e;
        }
        lastEatenFood = food;
    }

    private void interact(Agent other) {
        if (!this.alive || !other.alive) return;

        if (desiresPredation()) {
            attemptPredation(other);
        }
    }

    /**
     * @param prey The {@link Agent} to attempt to predate upon.
     */
    private void attemptPredation(@NotNull Agent prey) {
//        if (this.parent == prey || prey.parent == this) return;  // Should be covered by the genome comparison
        if (isSameSpecies(prey)) return;

        double selfCost = getPredationCost(prey);
        double selfAttack = this.attack / prey.defense * Math.pow(this.getRadius() / prey.getRadius(), 0.5);

        double otherAttack = 0;
        if (prey.desiresPredation()) {
            if (wouldThisEnergyCostKillMe(selfCost)) {
                // If the other wants to eat me, and the energy cost of attempting predation kills me,
                // the other gets the food without spending energy, since I'm already dead.
                prey.succeedPredation(this);
                die();
                return;
            }
            double preyCost = prey.getPredationCost(this);
            if (prey.wouldThisEnergyCostKillMe(preyCost)) {
                // If the energy cost of fighting back killed the prey,
                // I get the food for free, since the prey is already dead.
                this.succeedPredation(prey);
                prey.die();
                return;
            }
            prey.changeEnergy(preyCost);
            otherAttack = prey.attack / this.defense * Math.pow(prey.getRadius() / this.getRadius(), 0.5);
        }

        changeEnergy(selfCost);

        double selfHealth = this.getRadius() - otherAttack;
        double otherHealth = prey.getRadius() - selfAttack;

        if (selfHealth <= 0 && otherHealth <= 0) {
            die();
            prey.die();
        } else if (selfHealth <= 0) {
            if (otherHealth > 0 && prey.desiresPredation()) {
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
        energy = Math.min(energy, calculateCloneThreshold() * SINGLE_CLONE_ENERGY_LIMIT) / 2;
        new Agent(getAgentData().mutate(), environment);
    }


    private RayCaster.RayData @NotNull [] doRaycasts() {
        rayCaster.reset(getPosition(), this.direction.angle());
        environment.visitEntitiesInRange(this, visionRadius, visionRadiusSquared, rayCaster.getStationaryVisitor(), rayCaster.getMobileVisitor());

        return rayCaster.getRayData();
    }

    /**
     * Inputs (For now): energy, health, bestFood, bestPrey, worstThreat, currentTerrain
     * Outputs (For now): speed, repDesire, predDesire, targetFood, targetPrey, runFromThreat, turn
     */
    @Contract(" -> new")
    private float @NotNull [] getInput() {
        inputBuffer[0] = (float) (energy / calculateCloneThreshold());
        inputBuffer[1] = (float) (getRadius() / size);
        inputBuffer[2] = cachedTerrain.ordinal();
        inputBuffer[3] = (float) speed;

        RayCaster.RayData[] rayData = doRaycasts();
        for (int i = 0; i < NUM_RAYS * 7; i += 7) {
            RayCaster.RayData ray = rayData[i/7];
            Entity entity = ray.entity();

            float sizeRatio = 0;
            float distance = 0;
            float attackDefenseRatio = 0;
            float defenseAttackRatio = 0;
            int entityType = 0;
            float foodType = -1;

            if (entity != null) {
                sizeRatio = (float) (entity.getRadius() / this.getRadius());
                distance = (float) (ray.distance() / visionRadius);

                if (entity instanceof Agent agent) {
                    attackDefenseRatio = (float) (this.attack / agent.defense);
                    defenseAttackRatio = (float) (this.defense / agent.attack);
                    entityType = 1;
                } else if (entity instanceof Food food) {
                    foodType = food.getFoodType();
                    // entityType = 0
                } else {
                    entityType = -1;
                }
            }

            inputBuffer[4 + i] = sizeRatio;
            inputBuffer[5 + i] = distance;
            inputBuffer[6 + i] = attackDefenseRatio;
            inputBuffer[7 + i] = defenseAttackRatio;
            inputBuffer[8 + i] = ray.terrain().ordinal();
            inputBuffer[9 + i] = entityType;    // TODO: Maybe make two, one for isAgent, one for isFood?
            inputBuffer[10 + i] = foodType;
        }
        return inputBuffer;
    }

    @Override
    public void getDecision() {
        float[] inputs = getInput();
        float[] output = brain.predictFast(inputs);

        double targetPrey = output[1];
        boolean desiresPredation;
        if (targetPrey > BOOLEAN_DECISION_THRESHOLD) {
            desiresPredation = true;
            priority = "HUNT";
        } else {
            desiresPredation = false;
            priority = "WANDER";
        }

        currentWanderOffset += ThreadLocalRandom.current().nextDouble(-RANDOM_WANDER_CHANGE_SPEED, RANDOM_WANDER_CHANGE_SPEED);
        currentWanderOffset = Math.clamp(currentWanderOffset, -MAX_WANDER_ANGLE, MAX_WANDER_ANGLE);

        double nnTurn = (output[2] * 2 - 1) * turnSpeed;

        jitterRequested = output[3];
        double turn = nnTurn * (1 - jitterRequested) + (currentWanderOffset * turnSpeed) * jitterRequested;
        turn = Math.clamp(turn, -turnSpeed, turnSpeed);

        this.decision = new AgentDecision(output[0] * 1.75 - 0.75, turn, desiresPredation);
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
            for (RayCaster.RaySnapshot ray : rayCaster.getRaySnapshots()) {
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

    private void updateColor() {
        double brightness = Math.clamp(energy / calculateCloneThreshold(), 0.0, 1.0);
        setColor(Color.hsb(baseColor.getHue(), baseColor.getSaturation(), brightness));
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

    @Override
    public void setRadius(double radius) {
        super.setRadius(radius);
        if (getRadius() <= 0) die(); // TODO: Could this cause issues with parallel stuff?
    }

    private Vector getBounds() {
        return environment.getSize();
    }

    private double getPassiveEnergyCost() {
        return PASSIVE_LIFE_COST
                + turnSpeed * getRadius() * PASSIVE_TURN_SPEED_COST
                + visionRadius * PASSIVE_VISION_QUALITY_COST
                + Math.pow(getRadius(), PASSIVE_SIZE_EXPONENT) * PASSIVE_SIZE_COST
                + defense * PASSIVE_DEFENSE_COST
                + perTickFoodPreferenceCost / Math.pow(GLOBAL_ENERGY_LOSS_FACTOR / FINAL_GLOBAL_ENERGY_LOSS_FACTOR, 0.5);
    }

    @Contract(" -> new")
    public @NotNull AgentData getAgentData() {
        return new AgentData(
                getPosition(),
                getColor(),
                this.energy,
                this.maxAcceleration,
                this.turnSpeed,
                this.size,
                this.visionRadius,
                this.healthRegenSpeed,
                this.swimSkill,
                this.attack,
                this.defense,
                this.fieldOfView,
                this.foodPreference,
                this.brain,
                this.generation
        );
    }

    private boolean wouldThisEnergyCostKillMe(double cost) {
        return calculateEffectiveEnergyCost(cost) >= energy;
    }

    private double calculateEffectiveEnergyCost(double cost) {
        return cost * GLOBAL_ENERGY_LOSS_FACTOR;
    }

    private void changeEnergy(double change) {
        energy += calculateEffectiveEnergyCost(change);
        if (energy <= 0) die(); // TODO: Could this cause issues with parallel stuff?
    }

    private static double calculateCloneThreshold(double radius) {
        return BASE_CLONE_THRESHOLD * Math.pow(radius, CLONE_THRESHOLD_EXPONENT);
    }

    /**
     * @return The amount of energy at which an Agent will automatically clone.
     * Can also be thought of as the "maximum possible energy."
     */
    public double calculateCloneThreshold() {
        return calculateCloneThreshold(getRadius());
    }

    public boolean desiresPredation() {
        return decision != null && decision.desiresPredation();
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

    /**
     * TODO: Make constants / hyperparameters
     * @param numAgents
     * @param step
     */
//    public static void updateDifficulty(double numAgents, double step) {
//        if (step < 50000 && numAgents > MIN_NUM_AGENTS_WHILE_ANNEALING) return;
//        if (GLOBAL_ENERGY_LOSS_FACTOR < FINAL_GLOBAL_ENERGY_LOSS_FACTOR) {
//            double sizeFactor = Environment.getAverageSize() / TARGET_MEAN_RADIUS_WHILE_ANNEALING;
//            System.out.println(sizeFactor + ", " + GLOBAL_ENERGY_LOSS_ANNEALING_RATE);
//            double populationFactor = (Math.min(numAgents * sizeFactor, 5000) - MIN_NUM_AGENTS_WHILE_ANNEALING)/TARGET_NUM_AGENTS_WHILE_ANNEALING;
//            double change = (GLOBAL_ENERGY_LOSS_ANNEALING_RATE) * Math.signum(populationFactor) * Math.pow(Math.abs(populationFactor), 0.6);
//            GLOBAL_ENERGY_LOSS_FACTOR = Math.max(GLOBAL_ENERGY_LOSS_FACTOR + change, INITIAL_GELF);
//        }
//    }

    private static double difficultyVelocity = 0;

    public static void updateDifficulty(double numAgents, double step) {
        if (step < 50000 && numAgents > MIN_NUM_AGENTS_WHILE_ANNEALING) return;

        if (GLOBAL_ENERGY_LOSS_FACTOR < FINAL_GLOBAL_ENERGY_LOSS_FACTOR) {
            double sizeFactor = Environment.getAverageSize() / TARGET_MEAN_RADIUS_WHILE_ANNEALING;
            double populationFactor = (Math.min(numAgents, 50000) - MIN_NUM_AGENTS_WHILE_ANNEALING) / TARGET_NUM_AGENTS_WHILE_ANNEALING;

            // 1. Your target instantaneous change based on current population
            double targetChange = GLOBAL_ENERGY_LOSS_ANNEALING_RATE
                    * Math.signum(populationFactor)
                    * Math.pow(Math.abs(populationFactor), 0.6)
                    * sizeFactor;

            // 2. MOMENTUM / SMOOTHING COMPONENT (Exponential Moving Average / Low-Pass Filter)
            // Adjust 'smoothingAlpha' to tune how sluggish or responsive it is:
            // - 0.005 to 0.02 = Very heavy momentum, ultra-smooth, filters out all population jitter.
            // - 0.05 to 0.1 = Faster response, but still dampens sudden spikes.
            double smoothingAlpha = 0.02;
            difficultyVelocity = (1.0 - smoothingAlpha) * difficultyVelocity + smoothingAlpha * targetChange;

            // 3. Apply the smoothed velocity (allowing it to move both forward and backward smoothly)
            GLOBAL_ENERGY_LOSS_FACTOR = Math.clamp(
                    GLOBAL_ENERGY_LOSS_FACTOR + difficultyVelocity,
                    INITIAL_GELF,
                    FINAL_GLOBAL_ENERGY_LOSS_FACTOR
            );
        }
    }

    public double getJitterRequested() {
        return jitterRequested;
    }

    public double getMass() {
        return mass;
    }

    public double getSpeed() {
        return speed;
    }

    public double getMovementEffort() {
        return movementEffort;
    }

    public double getAcceleration() {
        return acceleration;
    }

    /**
     * @return How far the agent turned last tick, in degrees. Positive and negative are opposite directions.
     */
    public double getTurnRate() {
        return currentTurnRate;
    }

    public static double getDifficulty() {
        return (GLOBAL_ENERGY_LOSS_FACTOR - INITIAL_GELF) / (FINAL_GLOBAL_ENERGY_LOSS_FACTOR - INITIAL_GELF);
    }

    /**
     * Puts the difficulty back to where a brand new game starts.
     * The difficulty is static, so without this a second game would begin at the difficulty the last one ended on.
     */
    public static void resetDifficulty() {
        GLOBAL_ENERGY_LOSS_FACTOR = INITIAL_GELF;
    }

    public static double getGlobalEnergyLossFactor() {
        return GLOBAL_ENERGY_LOSS_FACTOR;
    }

    public static void setGlobalEnergyLossFactor(double globalEnergyLossFactor) {
        GLOBAL_ENERGY_LOSS_FACTOR = globalEnergyLossFactor;
    }

    /**
     * @return How many inputs the agents' brains take. Saves made with a different number can't be loaded.
     */
    public static int getExpectedBrainInputCount() {
        return NUM_INPUTS;
    }

    public boolean isAlive() {
        return alive;
    }

    public int getGeneration() {
        return generation;
    }

    /**
     * @return How many simulation ticks have passed since this agent was born
     */
    public int getAgeInTicks() {
        return environment.getCurrentTick() - birthTick;
    }

    /**
     * @return Everything about this agent that has to be saved: its {@link AgentData} plus the state that
     * {@code AgentData} doesn't hold (current health, heading, velocity, speed, wander offset and birth tick).
     */
    public @NotNull AgentState captureState() {
        return new AgentState(getAgentData(), getRadius(), direction, velocity, speed, currentWanderOffset, birthTick);
    }

    /**
     * Creates an agent that continues exactly where {@code state} left off. Unlike the public constructor,
     * the position isn't jittered, and the agent keeps its health, heading and velocity.
     */
    public static @NotNull Agent restore(@NotNull AgentState state, @NotNull Environment env) {
        Agent agent = new Agent(state.data(), env, state.data().position());
        agent.restoreMovementState(state);
        return agent;
    }

    private void restoreMovementState(@NotNull AgentState state) {
        setRadius(Math.min(state.health(), size));
        direction = state.direction();
        velocity = state.velocity();
        speed = state.speed();
        currentWanderOffset = state.wanderOffset();
        birthTick = state.birthTick();
        updateColor();
    }
}