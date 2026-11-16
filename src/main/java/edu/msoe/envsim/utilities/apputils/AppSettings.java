package edu.msoe.envsim.utilities.apputils;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
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
    private static final String UNIT_SYSTEM_KEY = "unitSystem";

    /**
     * The unit system the UI shows physical quantities in. Changing it saves it, so it is remembered for next time.
     * Panels listen to this property so they all update when any one of them changes it.
     */
    private static final ObjectProperty<UnitSystem> UNIT_SYSTEM = createUnitSystemProperty();

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

        storeProperties(properties);
    }

    public static @NotNull ObjectProperty<UnitSystem> unitSystemProperty() {
        return UNIT_SYSTEM;
    }

    public static @NotNull UnitSystem getUnitSystem() {
        return UNIT_SYSTEM.get();
    }

    public static void setUnitSystem(@NotNull UnitSystem unitSystem) {
        UNIT_SYSTEM.set(unitSystem);
    }

    private static @NotNull ObjectProperty<UnitSystem> createUnitSystemProperty() {
        UnitSystem savedUnitSystem = UnitSystem.METRIC;
        try {
            savedUnitSystem = UnitSystem.valueOf(loadProperties().getProperty(UNIT_SYSTEM_KEY, UnitSystem.METRIC.name()));
        } catch (IllegalArgumentException exception) {
            // An unknown value in the file just means we use the default
        }

        ObjectProperty<UnitSystem> property = new SimpleObjectProperty<>(savedUnitSystem);
        property.addListener((_, _, newUnitSystem) -> {
            try {
                Properties properties = loadProperties();
                properties.setProperty(UNIT_SYSTEM_KEY, newUnitSystem.name());
                storeProperties(properties);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        });
        return property;
    }

    private static void storeProperties(@NotNull Properties properties) throws IOException {
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