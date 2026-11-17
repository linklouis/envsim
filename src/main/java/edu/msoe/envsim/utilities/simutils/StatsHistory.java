package edu.msoe.envsim.utilities.simutils;

import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The history of a run as the {@link StatsRecorder} keeps it, in the form that goes into a save file.
 * <p>
 * A loaded game continues its charts from where the saved game left off.
 * </p>
 *
 * @param sampleIntervalTicks How many ticks apart new samples are taken. Grows as the history gets merged.
 * @param samples             The samples, oldest first
 */
public record StatsHistory(int sampleIntervalTicks, @NotNull List<StatsSample> samples) {
    /**
     * History for a save that doesn't have any, so the charts start fresh.
     */
    public static final StatsHistory EMPTY = new StatsHistory(StatsRecorder.INITIAL_SAMPLE_INTERVAL_TICKS, List.of());

    /**
     * Far more than a recorder ever keeps. Only here so a corrupt file can't make us allocate something enormous.
     */
    private static final int MAX_SAMPLE_COUNT = 100_000;

    public void writeTo(@NotNull DataOutputStream out) throws IOException {
        out.writeInt(sampleIntervalTicks);
        out.writeInt(samples.size());
        for (StatsSample sample : samples) {
            sample.writeTo(out);
        }
    }

    /**
     * Reads a history written by {@link #writeTo(DataOutputStream)}.
     *
     * @throws IOException if the data is corrupt
     */
    public static @NotNull StatsHistory readFrom(@NotNull DataInputStream in) throws IOException {
        int sampleIntervalTicks = in.readInt();
        int sampleCount = in.readInt();
        if (sampleIntervalTicks <= 0 || sampleCount < 0 || sampleCount > MAX_SAMPLE_COUNT) {
            throw new IOException("Corrupt save file: bad statistics history.");
        }

        List<StatsSample> samples = new ArrayList<>(sampleCount);
        for (int sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++) {
            samples.add(StatsSample.readFrom(in));
        }
        return new StatsHistory(sampleIntervalTicks, samples);
    }
}