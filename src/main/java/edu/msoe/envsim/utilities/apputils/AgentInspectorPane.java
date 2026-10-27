package edu.msoe.envsim.utilities.apputils;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.utilities.simutils.AgentData;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Source: Google Gemini
 */
public class AgentInspectorPane extends VBox {
    private final Label energyLabel = new Label();
    private final Label healthLabel = new Label();
    private final Label speedLabel = new Label();
    private final Label turnSpeedLabel = new Label();
    private final Label visionLabel = new Label();
    private final Label regenLabel = new Label();
    private final Label swimSkillLabel = new Label();
    private final Label terrainLabel = new Label();
    private final Label predationLabel = new Label();
    private final Label actionLabel = new Label();

    private final Runnable onCloseCallback;

    public AgentInspectorPane(@Nullable Runnable onCloseCallback) {
        super(10);
        this.onCloseCallback = onCloseCallback;
        setPadding(new Insets(15));
        setPrefWidth(USE_COMPUTED_SIZE);
        setMaxWidth(USE_PREF_SIZE);
        setMaxHeight(USE_PREF_SIZE);
        setMinHeight(USE_PREF_SIZE);
        setStyle("-fx-background-color: rgba(30, 30, 30, 0.9); -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8;");
        setVisible(false);
        setFocusTraversable(false);

        Label title = new Label("Agent Inspector");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: white;");

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(6);

        // Apply monospace font to fractional value labels so fixed-width spacing aligns perfectly
        energyLabel.setStyle("-fx-text-fill: white; -fx-font-family: monospace;");
        healthLabel.setStyle("-fx-text-fill: white; -fx-font-family: monospace;");

        int row = 0;
        addStandardRow(grid, row++, "Energy:", energyLabel);
        addStandardRow(grid, row++, "Health:", healthLabel);
        addStandardRow(grid, row++, "Max Speed:", speedLabel);
        addStandardRow(grid, row++, "Turn Speed:", turnSpeedLabel);
        addStandardRow(grid, row++, "Vision:", visionLabel);
        addStandardRow(grid, row++, "Regen:", regenLabel);
        addStandardRow(grid, row++, "Swim Skill:", swimSkillLabel);
        addStandardRow(grid, row++, "Terrain:", terrainLabel);
        addStandardRow(grid, row++, "Desires Predation:", predationLabel);
        addStandardRow(grid, row++, "Current Action:", actionLabel);

        Button closeButton = new Button("Close");
        closeButton.setOnAction(_ -> hide());
        closeButton.setStyle("-fx-background-color: #444; -fx-text-fill: white; -fx-cursor: hand;");
        closeButton.setFocusTraversable(false);

        getChildren().addAll(title, grid, closeButton);
    }

    private void addStandardRow(@NotNull GridPane grid, int row, String labelText, @NotNull Label valLabel) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-text-fill: #aaa; -fx-font-weight: bold;");
        if (valLabel.getStyle().isEmpty()) {
            valLabel.setStyle("-fx-text-fill: white;");
        }
        grid.add(lbl, 0, row);
        grid.add(valLabel, 1, row);
    }

    public void showAgent(@NotNull Agent agent) {
        AgentData data = agent.getAgentData();

        // %6.2f ensures the first value reserves a fixed 6-character width, right-aligned with spaces
        energyLabel.setText(String.format("%6.2f / %.2f", data.energy(), agent.calculateCloneThreshold()));
        healthLabel.setText(String.format("%6.2f / %.2f", agent.getRadius(), data.size()));

        speedLabel.setText(String.format("%.2f", data.speed()));
        turnSpeedLabel.setText(String.format("%.2f", data.turnSpeed()));
        visionLabel.setText(String.format("%.2f", data.visionRadius()));
        regenLabel.setText(String.format("%.2f", data.healthRegenSpeed()));
        swimSkillLabel.setText(String.format("%.2f", data.swimSkill()));
        terrainLabel.setText(agent.getTerrainType().name());
        predationLabel.setText(agent.desiresPredation() ? "True" : "False");
        actionLabel.setText(agent.getAction());

        setVisible(true);
    }

    public void hide() {
        if (isVisible()) {
            setVisible(false);
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
        }
    }
}