package edu.msoe.envsim.utilities.apputils;

import edu.msoe.envsim.entities.Food;
import edu.msoe.envsim.utilities.Vector;
import edu.msoe.envsim.utilities.simutils.AgentData;
import edu.msoe.envsim.utilities.apputils.stats.StatsHistory;
import edu.msoe.envsim.utilities.simutils.TerrainType;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything needed to bring a game back exactly as it was: the terrain, every food and living agent
 * (including their movement state), the tick counter, the difficulty, and the camera.
 * <p>
 * A snapshot is plain data. It is captured from an {@code Environment} (quick, under the simulation lock),
 * can then be written to disk on any thread, and can be read from disk on any thread before being applied.
 * </p>
 * <p>
 * <b>File layout</b> (all values big-endian, via {@link DataOutputStream}):
 * </p>
 * <ol>
 *     <li>{@link Header}, which is small enough to read on its own to list saves without loading them</li>
 *     <li>Terrain: one byte per tile holding the {@link TerrainType} ordinal, in {@code terrainData[x][y]} order</li>
 *     <li>Food: {@link Header#foodCount()} entries</li>
 *     <li>Agents: {@link Header#agentCount()} entries, each an {@link AgentData} followed by its movement state and birth tick</li>
 *     <li>Stats: the {@link StatsHistory} the stats screen draws (version 2 and later)</li>
 * </ol>
 * <p>
 * Bump {@link #FORMAT_VERSION} whenever the layout changes. Versions from {@link #MIN_SUPPORTED_FORMAT_VERSION} up can still
 * be read, and any other file is rejected with a clear message instead of being misread.
 * </p>
 *
 * @param header          Summary values, also written at the very start of the file
 * @param terrainOrdinals One byte per terrain tile
 * @param foods           Every piece of food
 * @param agents          Every living agent
 * @param stats           The history behind the stats screen's charts
 */
public record GameSnapshot(@NotNull Header header, byte @NotNull [] terrainOrdinals,
                           @NotNull List<FoodState> foods, @NotNull List<AgentState> agents,
                           @NotNull StatsHistory stats) {
    public static final int FORMAT_VERSION = 2;
    /** Version 1 saves have no birth ticks and no stats history, so they load with agents at age 0 and fresh charts. */
    public static final int MIN_SUPPORTED_FORMAT_VERSION = 1;
    private static final int FIRST_FORMAT_VERSION_WITH_STATS = 2;

    /** Spells "ENVS". Lets us reject files that aren't saves at all. */
    private static final int MAGIC_NUMBER = 0x454E5653;
    private static final int WRITE_BUFFER_BYTES = 1 << 16;
    private static final int MAX_TERRAIN_TILES = 100_000_000;
    private static final int MAX_INITIAL_LIST_CAPACITY = 1_000_000;

    /**
     * @param formatVersion       Which version of the file layout this is
     * @param savedAtEpochMillis  When the game was captured
     * @param worldWidth          World width in world units. A save only loads into a world of the same size.
     * @param worldHeight         World height in world units
     * @param terrainWidth        Number of terrain tiles along x
     * @param terrainHeight       Number of terrain tiles along y
     * @param brainInputCount     How many inputs the agents' brains take. A save only loads if this matches the current build.
     * @param tick                The simulation tick the game was on
     * @param energyLossFactor    The global energy loss factor, which is the difficulty
     * @param cameraScale         Camera zoom
     * @param cameraTranslateX    Camera x offset in screen pixels
     * @param cameraTranslateY    Camera y offset in screen pixels
     * @param agentCount          Number of {@link AgentState}s in the file
     * @param foodCount           Number of {@link FoodState}s in the file
     */
    public record Header(int formatVersion, long savedAtEpochMillis, int worldWidth, int worldHeight,
                         int terrainWidth, int terrainHeight, int brainInputCount, int tick, double energyLossFactor,
                         double cameraScale, double cameraTranslateX, double cameraTranslateY,
                         int agentCount, int foodCount) {
    }

    /**
     * @param foodTypeIndex Which food type this is (its ordinal in the Food's type enum)
     */
    public record FoodState(double x, double y, double maxSize, double radius, int foodTypeIndex, int regrowthTimer) {
    }

    /**
     * @param data       The agent's genes, brain, energy and position
     * @param health     The agent's current radius, which {@link AgentData} doesn't hold
     * @param direction  The way the agent is facing, as a unit vector
     * @param velocity   The agent's current velocity
     * @param speed      The agent's current speed
     * @param wanderOffset The agent's current random-wander offset
     * @param birthTick  The simulation tick the agent was born on, which is what its age is counted from
     */
    public record AgentState(@NotNull AgentData data, double health, @NotNull Vector direction,
                             @NotNull Vector velocity, double speed, double wanderOffset, int birthTick) {
    }


    /*
     * Writing:
     */

    /**
     * Writes {@code snapshot} to {@code file}. The data goes to a temporary file first and replaces the real one
     * only once everything has been written, so a failure part way through can't destroy an existing save.
     */
    public static void write(@NotNull GameSnapshot snapshot, @NotNull File file) throws IOException {
        Header header = snapshot.header();
        if (header.agentCount() != snapshot.agents().size() || header.foodCount() != snapshot.foods().size()) {
            throw new IllegalArgumentException("Snapshot header counts don't match its contents.");
        }
        if (header.terrainWidth() * header.terrainHeight() != snapshot.terrainOrdinals().length) {
            throw new IllegalArgumentException("Snapshot terrain size doesn't match its header.");
        }

        File directory = file.getAbsoluteFile().getParentFile();
        if (directory != null) {
            Files.createDirectories(directory.toPath());
        }

        Path temporaryPath = Path.of(file.getPath() + ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temporaryPath), WRITE_BUFFER_BYTES))) {
                writeHeader(out, header);
                out.write(snapshot.terrainOrdinals());

                for (FoodState food : snapshot.foods()) {
                    out.writeDouble(food.x());
                    out.writeDouble(food.y());
                    out.writeDouble(food.maxSize());
                    out.writeDouble(food.radius());
                    out.writeInt(food.foodTypeIndex());
                    out.writeInt(food.regrowthTimer());
                }

                for (AgentState agent : snapshot.agents()) {
                    agent.data().writeTo(out);
                    out.writeDouble(agent.health());
                    out.writeDouble(agent.direction().x());
                    out.writeDouble(agent.direction().y());
                    out.writeDouble(agent.velocity().x());
                    out.writeDouble(agent.velocity().y());
                    out.writeDouble(agent.speed());
                    out.writeDouble(agent.wanderOffset());
                    out.writeInt(agent.birthTick());
                }

                snapshot.stats().writeTo(out);
            }
            Files.move(temporaryPath, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporaryPath);
            throw exception;
        }
    }

    private static void writeHeader(@NotNull DataOutputStream out, @NotNull Header header) throws IOException {
        out.writeInt(MAGIC_NUMBER);
        out.writeInt(header.formatVersion());
        out.writeLong(header.savedAtEpochMillis());
        out.writeInt(header.worldWidth());
        out.writeInt(header.worldHeight());
        out.writeInt(header.terrainWidth());
        out.writeInt(header.terrainHeight());
        out.writeInt(header.brainInputCount());
        out.writeInt(header.tick());
        out.writeDouble(header.energyLossFactor());
        out.writeDouble(header.cameraScale());
        out.writeDouble(header.cameraTranslateX());
        out.writeDouble(header.cameraTranslateY());
        out.writeInt(header.agentCount());
        out.writeInt(header.foodCount());
    }


    /*
     * Reading:
     */

    /**
     * Reads only the header, which is fast. Use it to list saves and check whether they can be loaded.
     *
     * @throws IOException if the file isn't a readable save of a supported version
     */
    public static @NotNull Header readHeader(@NotNull File file) throws IOException {
        try (DataInputStream in = openForReading(file)) {
            return readHeader(in);
        }
    }

    /**
     * Reads a whole save. Nothing in the game is touched, so this is safe to call on a background thread.
     *
     * @param expectedBrainInputCount The brain input count of the running build; saves with a different one are rejected
     * @throws IOException if the file is corrupt, from another version, or from a build with different brains
     */
    public static @NotNull GameSnapshot read(@NotNull File file, int expectedBrainInputCount) throws IOException {
        try (DataInputStream in = openForReading(file)) {
            Header header = readHeader(in);
            if (header.brainInputCount() != expectedBrainInputCount) {
                throw new IOException("This save was made with a different agent brain size ("
                        + header.brainInputCount() + " inputs, but this build uses " + expectedBrainInputCount + ").");
            }

            byte[] terrainOrdinals = readTerrain(in, header);

            List<FoodState> foods = new ArrayList<>(Math.min(header.foodCount(), MAX_INITIAL_LIST_CAPACITY));
            int foodTypeCount = Food.getFoodTypeCount();
            for (int foodIndex = 0; foodIndex < header.foodCount(); foodIndex++) {
                double x = in.readDouble();
                double y = in.readDouble();
                double maxSize = in.readDouble();
                double radius = in.readDouble();
                int foodTypeIndex = in.readInt();
                int regrowthTimer = in.readInt();
                if (foodTypeIndex < 0 || foodTypeIndex >= foodTypeCount) {
                    throw new IOException("Corrupt save file: unknown food type " + foodTypeIndex + ".");
                }
                foods.add(new FoodState(x, y, maxSize, radius, foodTypeIndex, regrowthTimer));
            }

            List<AgentState> agents = new ArrayList<>(Math.min(header.agentCount(), MAX_INITIAL_LIST_CAPACITY));
            for (int agentIndex = 0; agentIndex < header.agentCount(); agentIndex++) {
                AgentData data = AgentData.readFrom(in);
                double health = in.readDouble();
                Vector direction = new Vector(in.readDouble(), in.readDouble());
                Vector velocity = new Vector(in.readDouble(), in.readDouble());
                double speed = in.readDouble();
                double wanderOffset = in.readDouble();
                // Older saves didn't record when agents were born, so their agents start counting their age from the save
                int birthTick = (header.formatVersion() >= FIRST_FORMAT_VERSION_WITH_STATS) ? in.readInt() : header.tick();
                agents.add(new AgentState(data, health, direction, velocity, speed, wanderOffset, birthTick));
            }

            // Older saves have no history, so the charts start fresh from the loaded game
            StatsHistory stats = (header.formatVersion() >= FIRST_FORMAT_VERSION_WITH_STATS)
                    ? StatsHistory.readFrom(in) : StatsHistory.EMPTY;

            return new GameSnapshot(header, terrainOrdinals, foods, agents, stats);
        }
    }

    private static @NotNull Header readHeader(@NotNull DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC_NUMBER) {
            throw new IOException("Not an EnvSim save file.");
        }
        int formatVersion = in.readInt();
        if (formatVersion < MIN_SUPPORTED_FORMAT_VERSION || formatVersion > FORMAT_VERSION) {
            throw new IOException("Unsupported save format version " + formatVersion
                    + " (this build reads versions " + MIN_SUPPORTED_FORMAT_VERSION + " to " + FORMAT_VERSION + ").");
        }

        Header header = new Header(
                formatVersion,
                in.readLong(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble(),
                in.readInt(),
                in.readInt()
        );
        if (header.agentCount() < 0 || header.foodCount() < 0) {
            throw new IOException("Corrupt save file: negative entity count.");
        }
        return header;
    }

    private static byte @NotNull [] readTerrain(@NotNull DataInputStream in, @NotNull Header header) throws IOException {
        long tileCount = (long) header.terrainWidth() * header.terrainHeight();
        if (tileCount <= 0 || tileCount > MAX_TERRAIN_TILES) {
            throw new IOException("Corrupt save file: bad terrain size " + header.terrainWidth() + "x" + header.terrainHeight() + ".");
        }

        byte[] terrainOrdinals = new byte[(int) tileCount];
        in.readFully(terrainOrdinals);

        int terrainTypeCount = TerrainType.values().length;
        for (byte ordinal : terrainOrdinals) {
            if (ordinal < 0 || ordinal >= terrainTypeCount) {
                throw new IOException("Corrupt save file: unknown terrain type " + ordinal + ".");
            }
        }
        return terrainOrdinals;
    }

    private static @NotNull DataInputStream openForReading(@NotNull File file) throws IOException {
        return new DataInputStream(new BufferedInputStream(Files.newInputStream(file.toPath()), WRITE_BUFFER_BYTES));
    }
}