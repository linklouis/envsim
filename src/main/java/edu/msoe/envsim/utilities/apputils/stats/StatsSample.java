package edu.msoe.envsim.utilities.apputils.stats;

import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * One point in the history of a run, taken by the {@link StatsRecorder}.
 * <p>
 * The counts (births, deaths and predations) are totals over the {@code windowTicks} ticks leading up to this sample.
 * Everything else describes the world at the moment of the sample. Those are {@code double}s, even the ones that are
 * whole numbers for a single sample, because merging two samples averages them.
 * </p>
 *
 * @param tick                  The simulation tick the sample was taken on
 * @param windowTicks           How many ticks the counts cover. 0 for the very first sample of a history.
 * @param population            Living agents
 * @param difficulty            How far the energy loss has been ramped up, from 0 to 1
 * @param birthCount            Agents created during the window, including any the player spawned
 * @param deathCount            Agents that died during the window
 * @param predationCount        Successful predations during the window
 * @param totalAgeAtDeath       The ages, in ticks, of all the agents that died during the window added together
 * @param earliestGeneration    The lowest generation of any living agent
 * @param averageGeneration     The average generation of the living agents
 * @param latestGeneration      The highest generation of any living agent
 * @param oldestAge             The age, in ticks, of the longest-living agent
 * @param averageAge            The average age, in ticks, of the living agents
 * @param speciesCount          How many species the agents belong to (estimated from a sample of the agents)
 * @param largestSpeciesShare   The portion of agents in the biggest species, from 0 to 1
 */
public record StatsSample(int tick, int windowTicks, double population, double difficulty,
                          int birthCount, int deathCount, int predationCount, long totalAgeAtDeath,
                          double earliestGeneration, double averageGeneration, double latestGeneration,
                          double oldestAge, double averageAge,
                          double speciesCount, double largestSpeciesShare, double averageSize) {
    /**
     * Rates are shown per this many ticks, since per single tick they would all be tiny fractions.
     */
    private static final double TICKS_PER_RATE = 1000;

    /**
     * @return {@code NaN} if the window is empty, so there is no rate to report
     */
    public double birthsPerThousandTicks() {
        return perThousandTicks(birthCount);
    }

    public double deathsPerThousandTicks() {
        return perThousandTicks(deathCount);
    }

    public double predationsPerThousandTicks() {
        return perThousandTicks(predationCount);
    }

    /**
     * @return The portion of the population that died each {@link #TICKS_PER_RATE} ticks, as a percentage.
     * High turnover means agents are short-lived, low turnover means they live for a long time.
     */
    public double turnoverPercentPerThousandTicks() {
        return (population > 0) ? deathsPerThousandTicks() / population * 100.0 : Double.NaN;
    }

    /**
     * @return The average age, in ticks, that agents had when they died in this window.
     * {@code NaN} if none died.
     */
    public double averageLifespanAtDeath() {
        return (deathCount > 0) ? (double) totalAgeAtDeath / deathCount : Double.NaN;
    }

    private double perThousandTicks(double count) {
        return (windowTicks > 0) ? count * TICKS_PER_RATE / windowTicks : Double.NaN;
    }

    /**
     * Combines this sample with the one that came right after it, for when the history gets too long to keep as is.
     * Counts and windows are added together so rates stay correct, and everything else is averaged, weighted by how
     * long each sample's window was.
     *
     * @param later The sample directly after this one
     * @return A single sample that stands in for both, taken at the later one's tick
     */
    public @NotNull StatsSample mergedWith(@NotNull StatsSample later) {
        // The first sample of a history has no window, but it still has to count for something
        double earlierWeight = Math.max(windowTicks, 1);
        double laterWeight = Math.max(later.windowTicks, 1);

        return new StatsSample(
                later.tick,
                windowTicks + later.windowTicks,
                weightedAverage(population, later.population, earlierWeight, laterWeight),
                weightedAverage(difficulty, later.difficulty, earlierWeight, laterWeight),
                birthCount + later.birthCount,
                deathCount + later.deathCount,
                predationCount + later.predationCount,
                totalAgeAtDeath + later.totalAgeAtDeath,
                weightedAverage(earliestGeneration, later.earliestGeneration, earlierWeight, laterWeight),
                weightedAverage(averageGeneration, later.averageGeneration, earlierWeight, laterWeight),
                weightedAverage(latestGeneration, later.latestGeneration, earlierWeight, laterWeight),
                weightedAverage(oldestAge, later.oldestAge, earlierWeight, laterWeight),
                weightedAverage(averageAge, later.averageAge, earlierWeight, laterWeight),
                weightedAverage(speciesCount, later.speciesCount, earlierWeight, laterWeight),
                weightedAverage(largestSpeciesShare, later.largestSpeciesShare, earlierWeight, laterWeight),
                weightedAverage(averageSize, later.averageSize, earlierWeight, laterWeight)
        );
    }

    private static double weightedAverage(double earlierValue, double laterValue, double earlierWeight, double laterWeight) {
        return (earlierValue * earlierWeight + laterValue * laterWeight) / (earlierWeight + laterWeight);
    }

    public void writeTo(@NotNull DataOutputStream out) throws IOException {
        out.writeInt(tick);
        out.writeInt(windowTicks);
        out.writeDouble(population);
        out.writeDouble(difficulty);
        out.writeInt(birthCount);
        out.writeInt(deathCount);
        out.writeInt(predationCount);
        out.writeLong(totalAgeAtDeath);
        out.writeDouble(earliestGeneration);
        out.writeDouble(averageGeneration);
        out.writeDouble(latestGeneration);
        out.writeDouble(oldestAge);
        out.writeDouble(averageAge);
        out.writeDouble(speciesCount);
        out.writeDouble(largestSpeciesShare);
    }

    /**
     * Reads one sample written by {@link #writeTo(DataOutputStream)}.
     */
    public static @NotNull StatsSample readFrom(@NotNull DataInputStream in) throws IOException {
        return new StatsSample(
                in.readInt(),
                in.readInt(),
                in.readDouble(),
                in.readDouble(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readLong(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble()
        );
    }
}