package edu.msoe.envsim.utilities.apputils;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

/**
 * Tiny key/value store for settings that should survive between launches.
 * Lives in {@code settings.properties} next to the {@code agents} and {@code environments} folders.
 * Source: Claude
 */
public final class AppSettings {
    private static final File SETTINGS_FILE = new File("settings.properties");
    private static final String DEFAULT_TERRAIN_KEY = "defaultTerrain";

    /**
     * What the game used before the default became selectable, so nothing changes until the user picks one.
     */
    private static final String FALLBACK_DEFAULT_TERRAIN_NAME = "marsh & desert huge";

    private AppSettings() {
    }

    /**
     * @return The name (without the {@code .terrain} extension) of the terrain that should be preselected for a new game.
     */
    public static @NotNull String getDefaultTerrainName() {
        return loadProperties().getProperty(DEFAULT_TERRAIN_KEY, FALLBACK_DEFAULT_TERRAIN_NAME);
    }

    public static void setDefaultTerrainName(@NotNull String terrainName) throws IOException {
        Properties properties = loadProperties();
        properties.setProperty(DEFAULT_TERRAIN_KEY, terrainName);

        try (BufferedWriter writer = Files.newBufferedWriter(SETTINGS_FILE.toPath(), StandardCharsets.UTF_8)) {
            properties.store(writer, "EnvSim settings");
        }
    }

    private static @NotNull Properties loadProperties() {
        Properties properties = new Properties();
        if (SETTINGS_FILE.isFile()) {
            try (BufferedReader reader = Files.newBufferedReader(SETTINGS_FILE.toPath(), StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException exception) {
                // A missing or unreadable settings file just means we use the defaults
                exception.printStackTrace();
            }
        }
        return properties;
    }
}