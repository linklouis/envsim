package edu.msoe.envsim.utilities.apputils;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.entities.Food;
import edu.msoe.envsim.utilities.simutils.AgentData;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

public class AgentInspectorPane extends VBox {
    @FXML private Label generationHeaderLabel;
    @FXML private Button unitsButton;
    @FXML private Label energyLabel, healthLabel, massLabel, terrainLabel, ageLabel;
    @FXML private Label actionLabel, jitterLabel, speedLabel, turnRateLabel, effortLabel, accelerationLabel;
    @FXML private Label maxSpeedLabel, turnSpeedLabel, visionLabel, regenLabel, swimSkillLabel;
    @FXML private Label attackLabel, defenseLabel, fovLabel, food1Label, food2Label, food3Label;
    @FXML private FoodRadarChart foodRadarChart;

    // Collapsible sections
    @FXML private HBox vitalsHeader, dynamicsHeader, attributesHeader, foodHeader;
    @FXML private VBox vitalsContent, dynamicsContent, attributesContent, foodContent;
    @FXML private Label vitalsArrowLabel, dynamicsArrowLabel, attributesArrowLabel, foodArrowLabel;

    private final Runnable onCloseCallback;
    private final Consumer<Agent> onGoToAgentCallback;
    private Agent currentAgent;

    private final DoubleProperty maximumHeightLimit = new SimpleDoubleProperty(Double.MAX_VALUE);

    /**
     * @param onCloseCallback     Called when the inspector is closed
     * @param onGoToAgentCallback Called with the inspected agent when "Go To Agent" is pressed
     */
    public AgentInspectorPane(@Nullable Runnable onCloseCallback, @Nullable Consumer<Agent> onGoToAgentCallback) {
        this.onCloseCallback = onCloseCallback;
        this.onGoToAgentCallback = onGoToAgentCallback;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/edu/msoe/envsim/AgentInspector.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load AgentInspector.fxml", e);
        }

        setupCollapsibleSections();
        setupFoodLegend();
        setupUnitsButton();

        setPrefWidth(USE_COMPUTED_SIZE);
        setMaxWidth(USE_PREF_SIZE);
        setMaxHeight(USE_PREF_SIZE);
        setMinHeight(USE_PREF_SIZE);
        setStyle("-fx-background-color: rgba(30, 30, 30, 0.9); -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8;");
        setVisible(false);
        setFocusTraversable(false);
        maximumHeightLimit.addListener(_ -> requestLayout());
    }

    private void setupCollapsibleSections() {
        makeCollapsible(vitalsHeader, vitalsContent, vitalsArrowLabel);
        makeCollapsible(dynamicsHeader, dynamicsContent, dynamicsArrowLabel);
        makeCollapsible(attributesHeader, attributesContent, attributesArrowLabel);
        makeCollapsible(foodHeader, foodContent, foodArrowLabel);
    }

    private void makeCollapsible(@NotNull HBox header, VBox content, Label arrowLabel) {
        header.setOnMouseClicked(_ -> {
            boolean isVisible = content.isVisible();
            content.setVisible(!isVisible);
            content.setManaged(!isVisible);
            arrowLabel.setText(isVisible ? "▶" : "▼");
        });
    }

    /**
     * Puts a swatch in each food type's own color next to its preference, so it's clear which is which.
     * The swatches never change, so this only has to happen once.
     */
    private void setupFoodLegend() {
        Label[] foodLabels = {food1Label, food2Label, food3Label};
        for (int foodTypeIndex = 0; foodTypeIndex < foodLabels.length; foodTypeIndex++) {
            Circle swatch = new Circle(5, Food.getTypeColor(foodTypeIndex));
            swatch.setStroke(Color.web("#cccccc"));
            swatch.setStrokeWidth(0.75);
            foodLabels[foodTypeIndex].setGraphic(swatch);
            foodLabels[foodTypeIndex].setGraphicTextGap(4);
        }
    }

    private void setupUnitsButton() {
        updateUnitsButton();
        AppSettings.unitSystemProperty().addListener((_, _, _) -> updateUnitsButton());
    }

    private void updateUnitsButton() {
        unitsButton.setText(AppSettings.getUnitSystem().getDisplayName());
    }

    @FXML
    private void cycleUnits() {
        AppSettings.setUnitSystem(AppSettings.getUnitSystem().next());
    }

    @FXML
    private void goToAgent() {
        if (currentAgent != null && onGoToAgentCallback != null) {
            onGoToAgentCallback.accept(currentAgent);
        }
    }

    public void showAgent(@NotNull Agent agent) {
        this.currentAgent = agent;
        AgentData data = agent.getAgentData();
        UnitSystem units = AppSettings.getUnitSystem();

        generationHeaderLabel.setText("Gen " + UnitSystem.formatWholeNumber(data.generation()));

        // Vitals
        energyLabel.setText(UnitSystem.formatNumber(data.energy()) + " / " + UnitSystem.formatNumber(agent.calculateCloneThreshold()));
        healthLabel.setText(units.lengthValue(agent.getRadius()) + " / " + units.formatLength(data.size()));
        massLabel.setText(units.formatMass(agent.getMass()));
        terrainLabel.setText(agent.getTerrainType().name());
        actionLabel.setText(agent.getPriority());
        ageLabel.setText(units.formatDuration(agent.getAgeInTicks()));

        // Dynamics
        speedLabel.setText(units.formatSpeed(agent.getSpeed()));
        accelerationLabel.setText(units.formatAcceleration(agent.getAcceleration()));
        turnRateLabel.setText(units.formatTurnRate(Math.abs(agent.getTurnRate())));
        effortLabel.setText(UnitSystem.formatNumber(agent.getMovementEffort()));
        jitterLabel.setText(UnitSystem.formatNumber(agent.getJitterRequested()));

        // Attributes
        maxSpeedLabel.setText(UnitSystem.formatNumber(data.maxAcceleration()));
        turnSpeedLabel.setText(UnitSystem.formatNumber(data.turnSpeed()));
        visionLabel.setText(units.formatLength(data.visionRadius()));
        fovLabel.setText(UnitSystem.formatAngle(data.fieldOfView()));
        regenLabel.setText(units.formatSpeed(data.healthRegenSpeed()));
        swimSkillLabel.setText(UnitSystem.formatNumber(data.swimSkill()));
        attackLabel.setText(UnitSystem.formatNumber(data.attack()));
        defenseLabel.setText(UnitSystem.formatNumber(data.defense()));

        // Food preferences
        double type1Preference = data.foodPreference().type1Preference();
        double type2Preference = data.foodPreference().type2Preference();
        double type3Preference = data.foodPreference().type3Preference();

        food1Label.setText("F1: " + UnitSystem.formatNumber(type1Preference));
        food2Label.setText("F2: " + UnitSystem.formatNumber(type2Preference));
        food3Label.setText("F3: " + UnitSystem.formatNumber(type3Preference));

        foodRadarChart.draw(type1Preference, type2Preference, type3Preference);

        setVisible(true);
    }

    @FXML
    private void saveCurrentAgent() {
        if (currentAgent == null) return;

        File dir = new File("agents");
        if (!dir.exists()) dir.mkdirs();

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Save Agent");

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStyleClass().add("panel");
        dialogPane.setStyle("-fx-background-color: rgba(30, 30, 30, 0.9); -fx-background-radius: 8; -fx-border-radius: 8;");

        if (getScene() != null) {
            String css = getScene().getStylesheets().stream().findFirst().orElse(null);
            if (css != null && !dialogPane.getStylesheets().contains(css)) {
                dialogPane.getStylesheets().add(css);
            }
        }

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER_LEFT);

        Label headerLbl = new Label("Save Agent to Library");
        headerLbl.getStyleClass().add("title-label");

        Label promptLbl = new Label("Enter agent name:");
        promptLbl.getStyleClass().add("caption-label");

        TextField textField = new TextField("agent_gen" + currentAgent.getAgentData().generation());
        textField.setStyle("-fx-background-color: #222; -fx-text-fill: white; -fx-border-color: #555; -fx-border-radius: 4; -fx-padding: 6;");

        content.getChildren().addAll(headerLbl, promptLbl, textField);
        dialogPane.setContent(content);

        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Node okButton = dialogPane.lookupButton(ButtonType.OK);
        okButton.getStyleClass().add("sim-button");
        Node cancelButton = dialogPane.lookupButton(ButtonType.CANCEL);
        cancelButton.getStyleClass().add("sim-button");

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return textField.getText();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                File targetFile = new File(dir, name.trim() + ".agent");
                try {
                    currentAgent.getAgentData().saveToFile(targetFile);
                } catch (Exception e) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Save Failed");
                    alert.setHeaderText("Could not save agent");

                    Label contentLabel = new Label(e.getMessage() != null ? e.getMessage() : "An unexpected error occurred.");
                    contentLabel.getStyleClass().add("value-label");
                    contentLabel.setWrapText(true);
                    contentLabel.setMaxWidth(380);

                    DialogPane alertPane = alert.getDialogPane();
                    alertPane.setContent(contentLabel);
                    alertPane.setPrefWidth(450);
                    alertPane.getStyleClass().add("panel");
                    if (getScene() != null) {
                        String css = getScene().getStylesheets().stream().findFirst().orElse(null);
                        if (css != null && !alertPane.getStylesheets().contains(css)) {
                            alertPane.getStylesheets().add(css);
                        }
                    }

                    alert.showAndWait();
                }
            }
        });
    }

    @FXML
    public void hide() {
        if (isVisible()) {
            currentAgent = null;
            setVisible(false);
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
        }
    }

    public DoubleProperty maximumHeightLimitProperty() {
        return maximumHeightLimit;
    }

    @Override
    protected double computePrefHeight(double width) {
        double contentHeight = Math.ceil(super.computePrefHeight(width));
        return Math.min(contentHeight, maximumHeightLimit.get());
    }
}