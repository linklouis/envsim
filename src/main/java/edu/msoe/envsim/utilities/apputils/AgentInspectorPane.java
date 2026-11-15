package edu.msoe.envsim.utilities.apputils;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.utilities.simutils.AgentData;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

public class AgentInspectorPane extends VBox {
    @FXML private Label generationHeaderLabel;
    @FXML private Label energyLabel, healthLabel, massLabel, terrainLabel;
    @FXML private Label //predationLabel,
            actionLabel, jitterLabel, speedLabel, effortLabel, accelerationLabel;
    @FXML private Label maxSpeedLabel, turnSpeedLabel, visionLabel, regenLabel, swimSkillLabel;
    @FXML private Label attackLabel, defenseLabel, fovLabel, food1Label, food2Label, food3Label;
    @FXML private Canvas foodRadarCanvas;

    // Collapsible sections
    @FXML private HBox vitalsHeader, dynamicsHeader, attributesHeader, foodHeader;
    @FXML private VBox vitalsContent, dynamicsContent, attributesContent, foodContent;
    @FXML private Label vitalsArrowLabel, dynamicsArrowLabel, attributesArrowLabel, foodArrowLabel;

    private final Runnable onCloseCallback;
    private Agent currentAgent;

    private final DoubleProperty maximumHeightLimit = new SimpleDoubleProperty(Double.MAX_VALUE);

    public AgentInspectorPane(@Nullable Runnable onCloseCallback) {
        this.onCloseCallback = onCloseCallback;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/edu/msoe/envsim/AgentInspector.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load AgentInspector.fxml", e);
        }

        setupCollapsibleSections();

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

    public void showAgent(@NotNull Agent agent) {
        this.currentAgent = agent;
        AgentData data = agent.getAgentData();

        generationHeaderLabel.setText("Gen " + data.generation());
        energyLabel.setText(String.format("%6.2f / %.2f", data.energy(), agent.calculateCloneThreshold()));
        healthLabel.setText(String.format("%6.2f / %.2f", agent.getRadius(), data.size()));
        massLabel.setText(String.format("%.2f", agent.getMass()));
        terrainLabel.setText(agent.getTerrainType().name());
//        predationLabel.setText(agent.desiresPredation() ? "True" : "False");
        actionLabel.setText(agent.getPriority());
        jitterLabel.setText(String.format("%.2f", agent.getJitterRequested()));
        speedLabel.setText(String.format("%.2f", agent.getSpeed()));
        effortLabel.setText(String.format("%.2f", agent.getMovementEffort()));
        accelerationLabel.setText(String.format("%.2f", agent.getAcceleration()));

        maxSpeedLabel.setText(String.format("%.2f", data.maxAcceleration()));
        turnSpeedLabel.setText(String.format("%.2f", data.turnSpeed()));
        visionLabel.setText(String.format("%.2f", data.visionRadius()));
        regenLabel.setText(String.format("%.2f", data.healthRegenSpeed()));
        swimSkillLabel.setText(String.format("%.2f", data.swimSkill()));
        attackLabel.setText(String.format("%.2f", data.attack()));
        defenseLabel.setText(String.format("%.2f", data.defense()));
        fovLabel.setText(String.format("%.2f", data.fieldOfView()));

        double f1 = data.foodPreference().type1Preference();
        double f2 = data.foodPreference().type2Preference();
        double f3 = data.foodPreference().type3Preference();

        food1Label.setText(String.format("F1: %.2f", f1));
        food2Label.setText(String.format("F2: %.2f", f2));
        food3Label.setText(String.format("F3: %.2f", f3));

        drawRadarChart(f1, f2, f3);

        setVisible(true);
    }

    private void drawRadarChart(double f1, double f2, double f3) {
        if (foodRadarCanvas == null) return;
        GraphicsContext gc = foodRadarCanvas.getGraphicsContext2D();
        double width = foodRadarCanvas.getWidth();
        double height = foodRadarCanvas.getHeight();

        gc.clearRect(0, 0, width, height);

        double cx = width / 2.0;
        double cy = height / 2.0 + 4;
        double radius = 40.0;

        double[] angles = { -Math.PI / 2.0, Math.PI / 6.0, 5.0 * Math.PI / 6.0 };

        // Background grid circles/polygons
        gc.setStroke(Color.web("#444444"));
        gc.setLineWidth(1.0);
        for (double scale : new double[]{0.25, 0.5, 0.75, 1.0}) {
            double r = radius * scale;
            for (int i = 0; i < 3; i++) {
                double x1 = cx + r * Math.cos(angles[i]);
                double y1 = cy + r * Math.sin(angles[i]);
                double x2 = cx + r * Math.cos(angles[(i + 1) % 3]);
                double y2 = cy + r * Math.sin(angles[(i + 1) % 3]);
                gc.strokeLine(x1, y1, x2, y2);
            }
        }

        // Axis rays
        for (int i = 0; i < 3; i++) {
            double vx = cx + radius * Math.cos(angles[i]);
            double vy = cy + radius * Math.sin(angles[i]);
            gc.strokeLine(cx, cy, vx, vy);
        }

        double maxVal = 1;//Math.max(0.01, Math.max(Math.abs(f1), Math.max(Math.abs(f2), Math.abs(f3))));
        double norm1 = Math.clamp(f1 / maxVal, 0.1, 1.0);
        double norm2 = Math.clamp(f2 / maxVal, 0.1, 1.0);
        double norm3 = Math.clamp(f3 / maxVal, 0.1, 1.0);
        double[] norms = { norm1, norm2, norm3 };

        double[] px = new double[3];
        double[] py = new double[3];
        for (int i = 0; i < 3; i++) {
            px[i] = cx + radius * norms[i] * Math.cos(angles[i]);
            py[i] = cy + radius * norms[i] * Math.sin(angles[i]);
        }

        // Fill radar shape
        gc.setFill(Color.web("#88c0d0", 0.35));
        gc.fillPolygon(px, py, 3);

        // Outline radar shape
        gc.setStroke(Color.web("#88c0d0"));
        gc.setLineWidth(2.0);
        gc.strokePolygon(px, py, 3);

        // Labels
        String[] labels = { "Food 1", "Food 2", "Food 3" };
        gc.setFont(javafx.scene.text.Font.font(9));
        gc.setFill(Color.web("#aaaaaa"));

        for (int i = 0; i < 3; i++) {
            gc.setFill(Color.web("#88c0d0"));
            gc.fillOval(px[i] - 2.5, py[i] - 2.5, 5, 5);

            double labelX = cx + (radius + 15) * Math.cos(angles[i]);
            double labelY = cy + (radius + 12) * Math.sin(angles[i]);

            Text textNode = new Text(labels[i]);
            textNode.setFont(gc.getFont());
            double textW = textNode.getLayoutBounds().getWidth();

            if (i == 0) {
                gc.fillText(labels[i], labelX - textW / 2.0, labelY - 2);
            } else if (i == 1) {
                gc.fillText(labels[i], labelX - 2, labelY + 3);
            } else {
                gc.fillText(labels[i], labelX - textW + 2, labelY + 3);
            }
        }
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