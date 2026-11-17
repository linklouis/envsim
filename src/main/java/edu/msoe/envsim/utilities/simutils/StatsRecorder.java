package edu.msoe.envsim.utilities.simutils;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.entities.MobileEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

/**
 * Keeps the history of a run that the stats screen draws: a {@link StatsSample} every so often, holding the
 * population, how old and how evolved the agents are, how many species there are, and how many births, deaths
 * and predations happened since the last sample.
 * <p>
 * A run can last for tens of millions of ticks, so the history can't keep every sample. Once there are
 * {@link #MAX_SAMPLES} of them, neighbouring samples are merged into one and samples are taken half as often.
 * That keeps the whole run on the charts, at a resolution that gets coarser the longer the run goes on.
 * </p>
 * <p>
 * <b>Threading:</b> {@link #recordBirth()}, {@link #recordDeath(int)} and {@link #recordPredation()} can be called
 * from any thread, including the parallel part of a simulation step. Everything that records or replaces the history
 * has to be called while holding the environment's simulation lock, so it never overlaps a step.
 * The getters hand out immutable data and can be called from any thread.
 * </p>
 */
public class StatsRecorder {
    public static final int INITIAL_SAMPLE_INTERVAL_TICKS = 50;
    public static final int MAX_SAMPLES = 800;
    /**
     * The most agents the species count looks at. Comparing genomes is the expensive part of taking a sample,
     * so a big population is sampled instead of counted agent by agent.
     */
    public static final int SPECIES_SAMPLE_SIZE = 600;

    /**
     * The newest sample exactly as it was taken (samples in the history may have been merged),
     * together with the agents that hold its records, so the stats screen can take the player to them.
     * An agent may have died since the sample was taken.
     */
    public record Census(@NotNull StatsSample sample, @Nullable Agent oldestAgent,
                         @Nullable Agent earliestGenerationAgent, @Nullable Agent latestGenerationAgent) {
    }

    private record PopulationScan(int population, int earliestGeneration, double averageGeneration, int latestGeneration,
                                  int oldestAge, double averageAge, @Nullable Agent oldestAgent,
                                  @Nullable Agent earliestGenerationAgent, @Nullable Agent latestGenerationAgent) {
    }

    private record SpeciesCensus(int speciesCount, double largestSpeciesShare) {
    }

    // Counted as things happen, and handed over to the next sample
    private final LongAdder birthCounter = new LongAdder();
    private final LongAdder deathCounter = new LongAdder();
    private final LongAdder deathAgeCounter = new LongAdder();
    private final LongAdder predationCounter = new LongAdder();

    // Only touched while holding the simulation lock
    private final List<StatsSample> samples = new ArrayList<>();
    private final List<Agent> speciesCandidates = new ArrayList<>();
    private int sampleIntervalTicks = INITIAL_SAMPLE_INTERVAL_TICKS;
    private int lastSampleTick = 0;

    // What the JavaFX thread reads. Replaced as a whole every time a sample is taken.
    private volatile @NotNull List<StatsSample> publishedSamples = List.of();
    private volatile @Nullable Census publishedCensus = null;


    /*
     * Counting. Safe to call from any thread.
     */

    public void recordBirth() {
        birthCounter.increment();
    }

    /**
     * @param ageInTicks How old the agent was when it died
     */
    public void recordDeath(int ageInTicks) {
        deathCounter.increment();
        deathAgeCounter.add(ageInTicks);
    }

    public void recordPredation() {
        predationCounter.increment();
    }


    /*
     * Recording. Hold the simulation lock.
     */

    /**
     * Throws away the old history and starts a new one with a sample of the world as it is right now.
     * Meant for a world that has just been populated, so the births counted while populating it are discarded.
     */
    public void startNewHistory(int tick, @NotNull Collection<MobileEntity> mobileEntities) {
        samples.clear();
        sampleIntervalTicks = INITIAL_SAMPLE_INTERVAL_TICKS;
        resetCounters();
        lastSampleTick = tick;
        recordSample(tick, mobileEntities);
    }

    /**
     * Replaces the history with one that was saved, and carries on from the world as it is right now.
     * Meant for a world that has just been loaded. A history with no samples (such as from an old save) starts fresh.
     */
    public void continueHistory(@NotNull StatsHistory history, int tick, @NotNull Collection<MobileEntity> mobileEntities) {
        if (history.samples().isEmpty()) {
            startNewHistory(tick, mobileEntities);
            return;
        }

        samples.clear();
        samples.addAll(history.samples());
        sampleIntervalTicks = history.sampleIntervalTicks();
        resetCounters();
        // The counts from before the save weren't kept, so the next window starts now instead of at the last sample
        lastSampleTick = tick;
        recordSample(tick, mobileEntities);
    }

    /**
     * Takes a sample if it has been long enough since the last one. Cheap to call every tick.
     * Call it when every entity in {@code mobileEntities} is alive and every newborn is in it.
     */
    public void recordSampleIfDue(int tick, @NotNull Collection<MobileEntity> mobileEntities) {
        if (tick - lastSampleTick >= sampleIntervalTicks) {
            recordSample(tick, mobileEntities);
        }
    }

    private void recordSample(int tick, @NotNull Collection<MobileEntity> mobileEntities) {
        PopulationScan scan = scanPopulation(mobileEntities);
        SpeciesCensus speciesCensus = takeSpeciesCensus();

        StatsSample sample = new StatsSample(
                tick, tick - lastSampleTick, scan.population(), Agent.getDifficulty(),
                (int) birthCounter.sumThenReset(),
                (int) deathCounter.sumThenReset(),
                (int) predationCounter.sumThenReset(),
                deathAgeCounter.sumThenReset(),
                scan.earliestGeneration(), scan.averageGeneration(), scan.latestGeneration(),
                scan.oldestAge(), scan.averageAge(),
                speciesCensus.speciesCount(), speciesCensus.largestSpeciesShare());

        samples.add(sample);
        lastSampleTick = tick;
        if (samples.size() >= MAX_SAMPLES) {
            mergeNeighbouringSamples();
        }

        publishedSamples = List.copyOf(samples);
        publishedCensus = new Census(sample, scan.oldestAgent(), scan.earliestGenerationAgent(), scan.latestGenerationAgent());
    }

    /**
     * Goes through every agent once, working out the population, the generation and age records, and picking
     * a random handful of agents for the species count to look at.
     */
    private @NotNull PopulationScan scanPopulation(@NotNull Collection<MobileEntity> mobileEntities) {
        speciesCandidates.clear();
        double speciesKeepProbability = Math.min(1.0, (double) SPECIES_SAMPLE_SIZE / Math.max(mobileEntities.size(), 1));
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int population = 0;
        long generationTotal = 0;
        long ageTotal = 0;
        int oldestAge = 0;
        Agent oldestAgent = null;
        Agent earliestGenerationAgent = null;
        Agent latestGenerationAgent = null;

        for (MobileEntity entity : mobileEntities) {
            if (entity instanceof Agent agent && agent.isAlive()) {
                population++;

                int generation = agent.getGeneration();
                generationTotal += generation;
                if (earliestGenerationAgent == null || generation < earliestGenerationAgent.getGeneration()) {
                    earliestGenerationAgent = agent;
                }
                if (latestGenerationAgent == null || generation > latestGenerationAgent.getGeneration()) {
                    latestGenerationAgent = agent;
                }

                int age = agent.getAgeInTicks();
                ageTotal += age;
                if (oldestAgent == null || age > oldestAge) {
                    oldestAge = age;
                    oldestAgent = agent;
                }

                if (random.nextDouble() < speciesKeepProbability) {
                    speciesCandidates.add(agent);
                }
            }
        }

        if (population == 0) {
            return new PopulationScan(0, 0, 0, 0, 0, 0, null, null, null);
        }
        return new PopulationScan(population,
                earliestGenerationAgent.getGeneration(), (double) generationTotal / population,
                latestGenerationAgent.getGeneration(),
                oldestAge, (double) ageTotal / population,
                oldestAgent, earliestGenerationAgent, latestGenerationAgent);
    }

    /**
     * Counts the species among the agents that {@link #scanPopulation} picked.
     * <p>
     * Each agent joins the first species whose founder it is the same species as, using the same rule the agents
     * use to decide who they can hunt. An agent that matches nobody founds a new species.
     * </p>
     */
    private @NotNull SpeciesCensus takeSpeciesCensus() {
        if (speciesCandidates.isEmpty()) {
            return new SpeciesCensus(0, 0);
        }

        List<Agent> speciesFounders = new ArrayList<>();
        int[] memberCounts = new int[speciesCandidates.size()];
        for (Agent candidate : speciesCandidates) {
            boolean joinedExistingSpecies = false;
            for (int founderIndex = 0; founderIndex < speciesFounders.size(); founderIndex++) {
                if (speciesFounders.get(founderIndex).isSameSpecies(candidate)) {
                    memberCounts[founderIndex]++;
                    joinedExistingSpecies = true;
                    break;
                }
            }
            if (!joinedExistingSpecies) {
                speciesFounders.add(candidate);
                memberCounts[speciesFounders.size() - 1] = 1;
            }
        }

        int largestSpeciesSize = 0;
        for (int founderIndex = 0; founderIndex < speciesFounders.size(); founderIndex++) {
            largestSpeciesSize = Math.max(largestSpeciesSize, memberCounts[founderIndex]);
        }
        return new SpeciesCensus(speciesFounders.size(), (double) largestSpeciesSize / speciesCandidates.size());
    }

    /**
     * Halves the number of samples by merging each pair, and takes new samples half as often from now on.
     */
    private void mergeNeighbouringSamples() {
        List<StatsSample> mergedSamples = new ArrayList<>(samples.size() / 2 + 1);
        for (int sampleIndex = 0; sampleIndex + 1 < samples.size(); sampleIndex += 2) {
            mergedSamples.add(samples.get(sampleIndex).mergedWith(samples.get(sampleIndex + 1)));
        }
        if (samples.size() % 2 == 1) {
            mergedSamples.add(samples.getLast());
        }

        samples.clear();
        samples.addAll(mergedSamples);
        sampleIntervalTicks *= 2;
    }

    private void resetCounters() {
        birthCounter.reset();
        deathCounter.reset();
        deathAgeCounter.reset();
        predationCounter.reset();
    }


    /*
     * Reading
     */

    /**
     * @return The history, oldest sample first. A new list every time a sample is taken, so it is safe to hold on to,
     * and comparing it with the one you drew last tells you whether there is anything new to draw.
     */
    public @NotNull List<StatsSample> getSamples() {
        return publishedSamples;
    }

    /**
     * @return The newest sample, or {@code null} if nothing has been recorded yet
     */
    public @Nullable Census getLatestCensus() {
        return publishedCensus;
    }

    /**
     * @return The history in the form that goes into a save file. Hold the simulation lock.
     */
    public @NotNull StatsHistory captureHistory() {
        return new StatsHistory(sampleIntervalTicks, publishedSamples);
    }
}