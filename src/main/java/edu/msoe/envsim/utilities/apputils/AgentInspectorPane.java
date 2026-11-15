package edu.msoe.envsim.utilities.apputils;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.utilities.simutils.AgentData;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class AgentInspectorPane extends VBox {
    @FXML private Label generationLabel, energyLabel, healthLabel, massLabel, terrainLabel;
    @FXML private Label predationLabel, actionLabel, jitterLabel, speedLabel, effortLabel, accelerationLabel;
    @FXML private Label maxSpeedLabel, turnSpeedLabel, visionLabel, regenLabel, swimSkillLabel;
    @FXML private Label attackLabel, defenseLabel, fovLabel, food1Label, food2Label, food3Label;

    private final Runnable onCloseCallback;
    private Agent currentAgent;

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

        // Restore exact sizing, styling, and behavior from the original class
        setPrefWidth(USE_COMPUTED_SIZE);
        setMaxWidth(USE_PREF_SIZE);
        setMaxHeight(USE_PREF_SIZE);
        setMinHeight(USE_PREF_SIZE);
        setStyle("-fx-background-color: rgba(30, 30, 30, 0.9); -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8;");
        setVisible(false);
        setFocusTraversable(false);
    }

    public void showAgent(@NotNull Agent agent) {
        this.currentAgent = agent;
        AgentData data = agent.getAgentData();

        generationLabel.setText(String.valueOf(data.generation()));
        energyLabel.setText(String.format("%6.2f / %.2f", data.energy(), agent.calculateCloneThreshold()));
        healthLabel.setText(String.format("%6.2f / %.2f", agent.getRadius(), data.size()));
        massLabel.setText(String.format("%.2f", agent.getMass()));
        terrainLabel.setText(agent.getTerrainType().name());
        predationLabel.setText(agent.desiresPredation() ? "True" : "False");
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
        food1Label.setText(String.format("%.2f", data.foodPreference().type1Preference()));
        food2Label.setText(String.format("%.2f", data.foodPreference().type2Preference()));
        food3Label.setText(String.format("%.2f", data.foodPreference().type3Preference()));

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

        // Force the dialog pane background and corner radius to match your rounded panel style
        dialogPane.setStyle("-fx-background-color: rgba(30, 30, 30, 0.9); -fx-background-radius: 8; -fx-border-radius: 8;");

        if (getScene() != null) {
            String css = getScene().getStylesheets().stream().findFirst().orElse(null);
            if (css != null && !dialogPane.getStylesheets().contains(css)) {
                dialogPane.getStylesheets().add(css);
            }
        }

        // Custom dark-themed layout to avoid JavaFX's awkward Modena header split
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER_LEFT);

        Label headerLbl = new Label("Save Agent to Library");
        headerLbl.getStyleClass().add("title-label");

        Label promptLbl = new Label("Enter agent name:");
        promptLbl.getStyleClass().add("caption-label");

        javafx.scene.control.TextField textField = new javafx.scene.control.TextField("agent_gen" + currentAgent.getAgentData().generation());
        textField.setStyle("-fx-background-color: #222; -fx-text-fill: white; -fx-border-color: #555; -fx-border-radius: 4; -fx-padding: 6;");

        content.getChildren().addAll(headerLbl, promptLbl, textField);
        dialogPane.setContent(content);

        dialogPane.getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);

        // Style buttons to match app theme
        javafx.scene.Node okButton = dialogPane.lookupButton(javafx.scene.control.ButtonType.OK);
        okButton.getStyleClass().add("sim-button");
        javafx.scene.Node cancelButton = dialogPane.lookupButton(javafx.scene.control.ButtonType.CANCEL);
        cancelButton.getStyleClass().add("sim-button");

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == javafx.scene.control.ButtonType.OK) {
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
}