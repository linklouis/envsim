package edu.msoe.envsim;

import edu.msoe.envsim.nn.ActivationFunction;
import edu.msoe.envsim.nn.AgentDecision;
import edu.msoe.envsim.nn.NeuralNetwork;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
    public static final int CLONE_THRESHOLD = 100;
    /**
     * The maximum ratio between a predator's radius and its prey for the predation to be a success.
     */
    private static final double MIN_RAD_DIFF_FOR_PREDATION = 0.8;
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
    public static final double SIZE_COST = 0.0004;
    public static final int NUM_INPUTS = 6;
    public static final double MUTATION_CHANCE = 0.1;
    public static final double MUTATION_SIZE = 0.02;
    /**
     * The smallest value the NN can output that's still considered true for a boolean output
     */
    private static final double BOOLEAN_DECISION_THRESHOLD = 0.3;
    public static final double GLOBAL_ENERGY_LOSS_FACTOR = 0.06;

    private static final SpeciesColorMapper COLOR_MAPPER = new SpeciesColorMapper();



    private final Color baseSpeciesColor;

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
     * TODO: Should the agent be able to see obstacles? (Probably not) [Why did I say probably not?]
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
    /**
     * TODO: Can get rid of if we keep only cloning
     */
    private double timeSinceReproduction = 100000;
    private Food justAte;
    private boolean alive = true;
    /*
     * radius is the health of the Agent. If it hits 0, it dies.
     * This value is **not** inherited by children.
     * TODO: Maybe the agent can decide to spend energy to regain health?
     */

    /**
     * Inputs (For now): energy, health, bestFood, bestPrey, worstThreat, desiresRep
     * Outputs (For now): speed, repDesire, predDesire, targetFood, targetPrey, runFromThreat, turn
     */
    public final @NotNull NeuralNetwork brain;
    /**
     * Inputs: food size, distance
     * Output: value
     */
    private final @NotNull NeuralNetwork foodEvaluator;
    /**
     * Inputs: self size, agent size, distance // TODO: In the future, maybe add whether the other wants to mate, if it's the same species, and what direction it's moving in?
     * Outputs: Value (1 = invaluble, 0 = useless, -1 = deadly threat)
     */
    private final @NotNull NeuralNetwork preyEvaluator;
    private final @NotNull NeuralNetwork predatorEvaluator;

    private Food bestFood;
    private Agent bestPrey;
    private Agent worstThreat;



    public Agent(double radius, Color color, @NotNull Environment env) {
        Vector position = Vector.random(Vector.zero, env.getSize());
        AgentData agentData = new AgentData(
                position,
                color,
                100,
                Math.random() * 1.7 + 0.2,
                radius,
                Math.random() * 200 + 80,
                Math.random(),
                newBrain(),
                newFoodEvaluator(),
                newAgentEvaluator(),
                newAgentEvaluator()
                );

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
        brain =  agentData.brain();
        foodEvaluator = agentData.foodEvaluator();
        preyEvaluator = agentData.preyEvaluator();
        predatorEvaluator = agentData.predatorEvaluator();

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
        this(parent1Data.energy() > parent2Data.energy() ? parent1Data : parent2Data, env); // TODO
    }

    @Override
    public void step() {
        if (!alive) return;

        timeSinceReproduction++;
        AgentDecision agentDecision = getAction();

        direction = agentDecision.direction();
        move(agentDecision.turn(), agentDecision.speed() * maxSpeed);

        for (Entity entity : environment.getCollisions(this)) {
            if (entity instanceof Food food) {
                eat(food);
            } else if (entity instanceof Agent agent) {
                interact(agent, agentDecision.desiresPredation());
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

        double functionalSpeed = speed / (Math.pow(getRadius() / 18, 2) + 1); // TODO: DOES THIS MAKE SENSE AND WHAT SHOULD I CALL THE CONSTANT?
        double cost = getMovementCost(functionalSpeed);
        if (energy >= cost) {
            setPosition(getPosition().add(direction.atMagnitude(functionalSpeed)));
            changeEnergy(-cost);
        }

        setPosition(getPosition().clamp(Vector.zero, getBounds()));
    }

    private void eat(@NotNull Food food) {
        if (food == justAte) return;
        double consumptionSize = Math.min(food.getValue(), getRadius());    // TODO: Maybe switch to max energy once it relies on radius?
        energy += consumptionSize;
        food.getEaten(consumptionSize);
        justAte = food;
    }

    private void interact(Agent agent, boolean attemptPredation) {
        if (!this.alive || !agent.alive) return;

        if (desiresReproduction && agent.desiresReproduction && isSameSpecies(this, agent)) {
//            reproduce(this, agent);
        } else if (attemptPredation) {
            attemptPredation(agent);
        }
    }

    private void updateColor() {
        double brightness = Math.clamp(getRadius() / size, 0.0, 1.0);
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
        // TODO: Punish agents for outputting the predation signal when not touching another agent?`

        // TODO: INSTEAD OF A THRESHOLD, HAVE THE CHANCES FOR AN AGENT TO KILL ANOTHER BE BASED ON THE SIZE DIFFERENCE. (OR EVEN JUST HOW MUCH DAMAGE IT DOES, SO THAT A SMALL AGENT CAN DO DAMAGE TO A LARGE ONE, EVEN IF IT DOESN'T KILL IT? [Is that actually something we want?])
        // TODO: Pass the radius ratio for whatever other agent this one is seeing as input for NN.
        changeEnergy(-(PREDATION_COST * prey.getRadius() / getRadius()));
        if (prey.getRadius() / getRadius() <= MIN_RAD_DIFF_FOR_PREDATION) {
            energy += prey.energy * PREDATION_EFFICIENCY;
            prey.die();
            System.out.println("Predation!");
            timeSinceReproduction = 0; // TODO: Remove once decisions aren't made randomly every frame.
        } else {
            // TODO: If we fail, but the other dies, we should still gain the energy.
            // TODO: Maybe that wouldn't happen cuz the decision of whether we succeed or not is based on health?
            setRadius(Math.max(0.001, getRadius() - FAILED_PREDATION_COST * prey.getRadius() / getRadius()));
            prey.setRadius(Math.max(0.001, prey.getRadius() - FAILED_PREDATION_COST * getRadius() / prey.getRadius()));
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
        if (parent1.prepareForReproduction(false) && parent2.prepareForReproduction(false)) {
            new Agent(parent1.getAgentData(), parent2.getAgentData(), env);
            parent1.timeSinceReproduction = 0;
            parent2.timeSinceReproduction = 0;
//            System.out.println("Reproduction!");
        }
    }

    private static void reproduce(@NotNull Agent parent1) {
        if (parent1.prepareForReproduction(true)) {
            new Agent(parent1.getAgentData(), parent1.environment);
            parent1.timeSinceReproduction = 0;
//            System.out.println("Cloning!");
        }
    }

    private static boolean isSameSpecies(Agent agent1, Agent agent2) {
        return true; // TODO
    }

    private static @NotNull NeuralNetwork newBrain() {
        NeuralNetwork neuralNetwork = new NeuralNetwork(NUM_INPUTS);
//        neuralNetwork.newLayer(16);
        neuralNetwork.newLayer(5);
        neuralNetwork.newLayer(7, ActivationFunction.sigmoid());

        return neuralNetwork;
    }

    private static @NotNull NeuralNetwork newFoodEvaluator() {
        NeuralNetwork neuralNetwork = new NeuralNetwork(2);
        neuralNetwork.newLayer(3);
        neuralNetwork.newLayer(1,ActivationFunction.sigmoid());

        return neuralNetwork;
    }

    private static @NotNull NeuralNetwork newAgentEvaluator() {
        NeuralNetwork neuralNetwork = new NeuralNetwork(2);
        neuralNetwork.newLayer(3);
        neuralNetwork.newLayer(1,ActivationFunction.sigmoid());

        return neuralNetwork;
    }


    /**
     * Inputs (For now): energy, health, bestFood, bestPrey, worstThreat, desiresRep
     * Outputs (For now): speed, repDesire, predDesire, targetFood, targetPrey, runFromThreat, turn
     */
    @Contract(" -> new")
    private float @NotNull [] getInput() {
        return new float[] {
                (float) (energy / MAX_EXPECTED_ENERGY),
                (float) (getRadius() / size),
                evaluateFood(),
                evaluatePrey(),
                evaluateThreats(),
                desiresReproduction ? 1f : 0f
        };
    }

    private float evaluateFood() {
        List<Entity> entitiesInRange = environment.getEntitiesInRange(getPosition(),
                entity -> entity instanceof Food && entity.getRadius() > 0, visionRadius);

        float maxValue = 0;
        Entity mostValuableFood = null;

        for  (Entity entity : entitiesInRange) {
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

    private float evaluatePrey() {
        List<Entity> entitiesInRange = environment.getEntitiesInRange(getPosition(),
                entity -> entity instanceof Agent, visionRadius);

        float maxValue = 0;
        Entity mostValuablePrey = null;

        for  (Entity entity : entitiesInRange) {
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

    private float evaluateThreats() {
        List<Entity> entitiesInRange = environment.getEntitiesInRange(getPosition(),
                entity -> entity instanceof Agent, visionRadius);

        float maxValue = 0;
        Entity mostThreateningPredator = null;

        for  (Entity entity : entitiesInRange) {
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
     * @return
     */
    @Contract(" -> new")
    private @NotNull AgentDecision getAction() {
        float[] inputs = getInput();
        float[] output = brain.predict(inputs);

        double reproductionDesire = energy >= REPRODUCTON_ENERGY_THRESHOLD ? output[1] - 1/timeSinceReproduction : 0;   // TODO: Ideally, they should handle the threshold thing themselves, but I'll be nice to them
        double predationDesire = output[2] * 0.9 - 1/timeSinceReproduction;// TODO: Remove time since reproduction

        boolean desiresPredation;

        //if (reproductionDesire > 0.6 && energy > 80) {
        if (energy > CLONE_THRESHOLD && getRadius() / size > 0.8) {
            reproduce(this); // TODO: IF WE KEEP THIS, MOVE IT TO STEP. ONLY HERE FOR TESTING BALANCE.
        }

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

        double targetFood = bestFood == null ? 0 : output[3];
        double targetPrey = bestPrey == null ? 0 : output[4];
        double runFromThreat = worstThreat == null ? 0 :output[5];

        Vector target;

        if (targetFood > targetPrey && targetFood > runFromThreat && targetFood > BOOLEAN_DECISION_THRESHOLD) {
            target = directionTo(bestFood);
        } else if (targetPrey > targetFood && targetPrey > runFromThreat && targetPrey > BOOLEAN_DECISION_THRESHOLD) {
            target = directionTo(bestPrey);
        } else if (runFromThreat > targetFood && runFromThreat > targetPrey && runFromThreat > BOOLEAN_DECISION_THRESHOLD) {
            target = directionTo(worstThreat).scale(-1);
        } else {
            target = direction;
        }

        return new AgentDecision(
                output[0],// * 2 - 1,
                target,
                target == direction ? output[6] * 80 - 40 : 0,
                desiresReproduction,
                desiresPredation
        );
    }


    @Override
    public void paint(@NotNull GraphicsContext gc) {
        gc.setFill(getColor());
        gc.fillOval(x() - getRadius(), y() - getRadius(), getRadius() * 2, getRadius() * 2);
        gc.setStroke(Color.WHITE);
        gc.strokeOval(x() - getRadius(), y() - getRadius(), getRadius() * 2, getRadius() * 2);

        gc.strokeOval(x() - visionRadius, y() - visionRadius, visionRadius * 2, visionRadius * 2);
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

    @Contract(" -> new")
    private @NotNull AgentData getAgentData() {
        return new AgentData(
                getPosition(),
                getColor(),
                this.energy, // TODO: Children should actually get the cost subtracted from each parent's health
                this.maxSpeed + Math.random() * 2 - 1,
                this.size + Math.random() * 2 - 1,
                this.visionRadius + Math.random() * 2 - 1,
                this.healthRegenSpeed + Math.random() * 2 - 1,
                this.brain.child(MUTATION_CHANCE, MUTATION_SIZE),
                this.foodEvaluator.child(MUTATION_CHANCE, MUTATION_SIZE),
                this.preyEvaluator.child(MUTATION_CHANCE, MUTATION_SIZE),
                this.predatorEvaluator.child(MUTATION_CHANCE, MUTATION_SIZE)
        );
    }

    private void changeEnergy(double change) {
        energy += change * GLOBAL_ENERGY_LOSS_FACTOR;
    }
}
