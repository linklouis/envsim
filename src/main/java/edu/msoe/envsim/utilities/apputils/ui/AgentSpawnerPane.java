package edu.msoe.envsim.utilities.apputils.ui;

import edu.msoe.envsim.Environment;
import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.utilities.Vector;
import edu.msoe.envsim.utilities.apputils.AppSettings;
import edu.msoe.envsim.utilities.apputils.UnitSystem;
import edu.msoe.envsim.utilities.simutils.AgentData;
import edu.msoe.envsim.utilities.simutils.SpeciesColorMapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The agent library: every agent saved in the {@code agents} folder, with its details and the controls to
 * spawn it into the world or delete it.
 * <p>
 * Clicking a card shows that agent's attributes and food preferences before anything is spawned.
 * Cards can also be dragged straight onto the world to spawn the agent exactly where it is dropped.
 * </p>
 */
public class AgentSpawnerPane extends VBox {
    public static final double PANEL_WIDTH = 400;

    private static final File AGENT_DIRECTORY = new File("agents");
    private static final String AGENT_EXTENSION = ".agent";

    private static final int THUMBNAIL_WIDTH = 80;
    private static final int THUMBNAIL_HEIGHT = 48;
    private static final double THUMBNAIL_MAX_RADIUS = 20;
    private static final double THUMBNAIL_SIZE_SCALE = 0.6;

    private static final String CARD_SHAPE = "-fx-background-radius: 6; -fx-border-radius: 6; -fx-padding: 6 8; -fx-cursor: hand;";
    private static final String CARD_COLORS_NORMAL = "-fx-background-color: rgba(45, 45, 45, 0.9); -fx-border-color: #555;";
    private static final String CARD_COLORS_SELECTED = "-fx-background-color: rgba(60, 100, 140, 0.9); -fx-border-color: #88c0d0; -fx-border-width: 2;";
    private static final String CARD_COLORS_UNREADABLE = "-fx-background-color: rgba(90, 45, 45, 0.9); -fx-border-color: #a55;";

    /**
     * @param data    {@code null} if the file couldn't be read
     * @param problem {@code null} if the file could be read, otherwise why it couldn't
     */
    private record SavedAgent(File file, String name, @Nullable AgentData data, @Nullable String problem,
                              HBox card, Label detailLabel) {
    }

    private final Environment environment;
    private final List<SavedAgent> savedAgents = new ArrayList<>();
    private final VBox cardsBox = new VBox(8);
    private final Label countLabel = new Label();

    private final VBox detailsBox = new VBox(8);
    private final Label detailsNameLabel = new Label();
    private final GridPane statsGrid = new GridPane();
    private final FoodRadarChart foodRadarChart = new FoodRadarChart(200, 130);
    private final Button spawnInViewButton;
    private final Button spawnRandomlyButton;

    private @Nullable File selectedFile = null;

    public AgentSpawnerPane(@NotNull Environment environment) {
        this.environment = environment;

        getStyleClass().add("panel");
        setSpacing(10);
        setPadding(new Insets(12));
        setPrefWidth(PANEL_WIDTH);
        setMinWidth(PANEL_WIDTH);
        setMaxWidth(PANEL_WIDTH);

        Label titleLabel = new Label("Agent Library");
        titleLabel.getStyleClass().add("title-label");

        countLabel.getStyleClass().add("telemetry-category-label");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox headerRow = new HBox(10, titleLabel, spacer, countLabel);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label hintLabel = new Label("Click an agent for details. Drag one onto the world to spawn it there.");
        hintLabel.getStyleClass().add("caption-label");
        hintLabel.setStyle("-fx-font-size: 10px;");
        hintLabel.setWrapText(true);

        ScrollPane cardsScrollPane = new ScrollPane(cardsBox);
        cardsScrollPane.getStyleClass().add("transparent-scroll-pane");
        cardsScrollPane.setFitToWidth(true);
        cardsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        cardsScrollPane.setPrefHeight(190);
        VBox.setVgrow(cardsScrollPane, Priority.ALWAYS);

        // ---- Details of the selected agent ----
        detailsNameLabel.getStyleClass().add("title-label");
        detailsNameLabel.setWrapText(true);

        statsGrid.setHgap(10);
        statsGrid.setVgap(3);

        spawnInViewButton = createButton("Spawn in View", this::spawnInView);
        spawnRandomlyButton = createButton("Spawn Randomly", this::spawnRandomly);
        Button deleteButton = createButton("Delete", this::deleteSelected);
        deleteButton.getStyleClass().remove("sim-button");
        deleteButton.getStyleClass().add("danger-button");

        HBox buttonRow = new HBox(8, spawnInViewButton, spawnRandomlyButton, deleteButton);
        buttonRow.setAlignment(Pos.CENTER);
        for (Button button : new Button[]{spawnInViewButton, spawnRandomlyButton, deleteButton}) {
            HBox.setHgrow(button, Priority.ALWAYS);
            button.setMaxWidth(Double.MAX_VALUE);
        }

        detailsBox.getChildren().addAll(detailsNameLabel, statsGrid, foodRadarChart, buttonRow);
        detailsBox.setAlignment(Pos.TOP_CENTER);
        detailsBox.setStyle("-fx-background-color: rgba(40, 40, 40, 0.6); -fx-background-radius: 6; -fx-border-color: #444; -fx-border-radius: 6; -fx-padding: 10;");
        detailsBox.setFillWidth(true);
        statsGrid.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(headerRow, hintLabel, cardsScrollPane, detailsBox);
        showDetailsFor(null);

        // Lengths and speeds in the cards and the details follow the app-wide unit setting
        AppSettings.unitSystemProperty().addListener((_, _, _) -> {
            for (SavedAgent savedAgent : savedAgents) {
                savedAgent.detailLabel().setText(describeCard(savedAgent.data(), savedAgent.problem()));
            }
            renderDetails();
        });
    }

    /**
     * Rereads the agents folder. Call this whenever the library is shown.
     */
    public void refresh() {
        cardsBox.getChildren().clear();
        savedAgents.clear();

        File[] agentFiles = AGENT_DIRECTORY.listFiles((_, fileName) -> fileName.endsWith(AGENT_EXTENSION));
        if (agentFiles == null || agentFiles.length == 0) {
            Label emptyLabel = new Label("No saved agents yet. Select an agent in the world and press Save Agent.");
            emptyLabel.getStyleClass().add("caption-label");
            emptyLabel.setWrapText(true);
            cardsBox.getChildren().add(emptyLabel);
            countLabel.setText("0 saved");
            selectedFile = null;
            showDetailsFor(null);
            return;
        }
        Arrays.sort(agentFiles);

        boolean selectedStillExists = false;
        for (File agentFile : agentFiles) {
            SavedAgent savedAgent = buildSavedAgent(agentFile);
            savedAgents.add(savedAgent);
            cardsBox.getChildren().add(savedAgent.card());
            if (agentFile.equals(selectedFile)) selectedStillExists = true;
        }
        countLabel.setText(savedAgents.size() + " saved");

        if (!selectedStillExists) selectedFile = null;
        restyleCards();
        renderDetails();
    }


    /*
     * Cards:
     */

    private @NotNull SavedAgent buildSavedAgent(@NotNull File agentFile) {
        String agentName = agentFile.getName().substring(0, agentFile.getName().length() - AGENT_EXTENSION.length());

        AgentData data = null;
        String problem = null;
        try {
            data = AgentData.loadFromFile(agentFile);
        } catch (IOException | RuntimeException exception) {
            problem = "Could not read this agent: " + exception.getMessage();
        }

        WritableImage thumbnail = renderThumbnail(data);
        ImageView thumbnailView = new ImageView(thumbnail);
        thumbnailView.setFitWidth(THUMBNAIL_WIDTH);
        thumbnailView.setFitHeight(THUMBNAIL_HEIGHT);

        Label nameLabel = new Label(agentName);
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;");
        nameLabel.setWrapText(true);

        Label detailLabel = new Label(describeCard(data, problem));
        detailLabel.getStyleClass().add("caption-label");
        detailLabel.setStyle("-fx-font-size: 10px;");
        detailLabel.setWrapText(true);

        VBox textBox = new VBox(2, nameLabel, detailLabel);
        textBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox card = new HBox(10, thumbnailView, textBox);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setOnMouseClicked(_ -> {
            selectedFile = agentFile;
            restyleCards();
            renderDetails();
        });

        if (problem != null) {
            Tooltip.install(card, new Tooltip(problem));
        } else {
            card.setOnDragDetected(event -> {
                Dragboard dragboard = card.startDragAndDrop(TransferMode.COPY);
                ClipboardContent content = new ClipboardContent();
                content.putString(agentFile.getAbsolutePath());
                dragboard.setContent(content);
                dragboard.setDragView(thumbnail);
                event.consume();
            });
        }

        return new SavedAgent(agentFile, agentName, data, problem, card, detailLabel);
    }

    private static @NotNull String describeCard(@Nullable AgentData data, @Nullable String problem) {
        if (data == null) {
            return (problem != null) ? problem : "Unreadable";
        }
        UnitSystem unitSystem = AppSettings.getUnitSystem();
        return "Gen " + UnitSystem.formatWholeNumber(data.generation()) + "  ·  size " + unitSystem.formatLength(data.size());
    }

    private void restyleCards() {
        for (SavedAgent savedAgent : savedAgents) {
            String colors;
            if (savedAgent.problem() != null) {
                colors = CARD_COLORS_UNREADABLE;
            } else if (savedAgent.file().equals(selectedFile)) {
                colors = CARD_COLORS_SELECTED;
            } else {
                colors = CARD_COLORS_NORMAL;
            }
            savedAgent.card().setStyle(CARD_SHAPE + " " + colors);
        }
    }

    /**
     * Draws the agent the way it looks when it is fully healthy and fully fed: its full size, and the species color
     * at full brightness. (An agent's saved color is its color at the moment it was saved, which is dimmed when
     * it was low on energy, so that color isn't used here.)
     */
    private @NotNull WritableImage renderThumbnail(@Nullable AgentData data) {
        Canvas canvas = new Canvas(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);

        double centerX = THUMBNAIL_WIDTH / 2.0;
        double centerY = THUMBNAIL_HEIGHT / 2.0;

        if (data == null) {
            gc.setStroke(Color.web("#a55"));
            gc.setLineWidth(2);
            gc.strokeLine(centerX - 10, centerY - 10, centerX + 10, centerY + 10);
            gc.strokeLine(centerX - 10, centerY + 10, centerX + 10, centerY - 10);
        } else {
            Color speciesColor = SpeciesColorMapper.getBaseSpeciesColor(data.extractGenome());
            Color fullBrightnessColor = Color.hsb(speciesColor.getHue(), speciesColor.getSaturation(), 1.0);
            double radius = Math.clamp(data.size() * THUMBNAIL_SIZE_SCALE, 3.0, THUMBNAIL_MAX_RADIUS);

            gc.setFill(fullBrightnessColor);
            gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.5);
            gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        }

        WritableImage image = new WritableImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        return canvas.snapshot(null, image);
    }


    /*
     * Details:
     */

    private @Nullable SavedAgent findSelected() {
        for (SavedAgent savedAgent : savedAgents) {
            if (savedAgent.file().equals(selectedFile)) return savedAgent;
        }
        return null;
    }

    private void showDetailsFor(@Nullable SavedAgent savedAgent) {
        boolean show = (savedAgent != null);
        detailsBox.setVisible(show);
        detailsBox.setManaged(show);
    }

    private void renderDetails() {
        SavedAgent selected = findSelected();
        showDetailsFor(selected);
        if (selected == null) return;

        AgentData data = selected.data();
        detailsNameLabel.setText(selected.name());

        boolean spawnable = (data != null);
        spawnInViewButton.setDisable(!spawnable);
        spawnRandomlyButton.setDisable(!spawnable);

        statsGrid.getChildren().clear();
        if (data == null) {
            foodRadarChart.setVisible(false);
            foodRadarChart.setManaged(false);
            addStat(0, "Problem:", selected.problem());
            return;
        }

        UnitSystem unitSystem = AppSettings.getUnitSystem();
        addStat(0, "Generation:", UnitSystem.formatWholeNumber(data.generation()));
        addStat(1, "Size:", unitSystem.formatLength(data.size()));
        addStat(2, "Energy:", UnitSystem.formatNumber(data.energy()));
        addStat(3, "Max Accel:", UnitSystem.formatNumber(data.maxAcceleration()));
        addStat(4, "Turn Speed:", UnitSystem.formatNumber(data.turnSpeed()));
        addStat(5, "Vision Radius:", unitSystem.formatLength(data.visionRadius()));
        addStat(6, "FOV:", UnitSystem.formatAngle(data.fieldOfView()));
        addStat(7, "Health Regen:", unitSystem.formatSpeed(data.healthRegenSpeed()));
        addStat(8, "Swim Skill:", UnitSystem.formatNumber(data.swimSkill()));
        addStat(9, "Attack:", UnitSystem.formatNumber(data.attack()));
        addStat(10, "Defense:", UnitSystem.formatNumber(data.defense()));

        foodRadarChart.setVisible(true);
        foodRadarChart.setManaged(true);
        foodRadarChart.draw(data.foodPreference().type1Preference(),
                data.foodPreference().type2Preference(),
                data.foodPreference().type3Preference());
    }

    private void addStat(int row, @NotNull String label, @NotNull String value) {
        Label captionLabel = new Label(label);
        captionLabel.getStyleClass().add("caption-label");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("value-label");
        valueLabel.setWrapText(true);
        statsGrid.add(captionLabel, 0, row);
        statsGrid.add(valueLabel, 1, row);
    }


    /*
     * Actions:
     */

    private void spawnInView() {
        spawnSelected(environment.getViewCenterWorldPosition());
    }

    private void spawnRandomly() {
        spawnSelected(Vector.random(Vector.ZERO, environment.getSize()));
    }

    private void spawnSelected(@NotNull Vector position) {
        SavedAgent selected = findSelected();
        if (selected == null || selected.data() == null) return;

        try {
            environment.spawnSelectedAgent(selected.data(), position);
            Agent spawnedAgent = environment.getSelectedAgent();
            if (spawnedAgent != null) {
                environment.goToAgent(spawnedAgent);
            }
        } catch (RuntimeException exception) {
            showError("Spawn Failed", "Could not spawn '" + selected.name() + "'.", exception.getMessage());
        }
    }

    private void deleteSelected() {
        SavedAgent selected = findSelected();
        if (selected == null) return;

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Agent");
        confirmation.setHeaderText("Delete '" + selected.name() + "'?");

        Label contentLabel = new Label("This permanently deletes the saved agent file. It can't be undone.");
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(380);
        confirmation.getDialogPane().setContent(contentLabel);
        confirmation.getDialogPane().setPrefWidth(450);

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            Files.deleteIfExists(selected.file().toPath());
            selectedFile = null;
            refresh();
        } catch (IOException exception) {
            showError("Delete Failed", "Could not delete '" + selected.name() + "'.", exception.getMessage());
        }
    }

    private static void showError(@NotNull String title, @NotNull String header, @Nullable String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);

        Label contentLabel = new Label(message != null ? message : "An unexpected error occurred.");
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(380);
        alert.getDialogPane().setContent(contentLabel);
        alert.getDialogPane().setPrefWidth(450);
        alert.showAndWait();
    }

    private static @NotNull Button createButton(@NotNull String text, @NotNull Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("sim-button");
        button.setFocusTraversable(false);
        button.setStyle("-fx-font-size: 11px; -fx-padding: 6 8;");
        button.setOnAction(_ -> action.run());
        return button;
    }
}