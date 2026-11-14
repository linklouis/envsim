package edu.msoe.envsim.utilities.apputils;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.utilities.simutils.AgentData;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

import static javafx.scene.control.PopupControl.USE_PREF_SIZE;

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

        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("agent_gen" + currentAgent.getAgentData().generation());
        dialog.setTitle("Save Agent");
        dialog.setHeaderText("Save Agent to Library");
        dialog.setContentText("Enter agent name:");

        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                File targetFile = new File(dir, name.trim() + ".agent");
                try {
                    currentAgent.getAgentData().saveToFile(targetFile);
                } catch (Exception e) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Save Failed");
                    alert.setHeaderText("Could not save agent");
                    alert.setContentText(e.getMessage());
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