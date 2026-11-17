package edu.msoe.envsim.utilities.apputils.ui;

import edu.msoe.envsim.entities.Food;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.jetbrains.annotations.NotNull;

/**
 * A three-axis radar chart of an agent's food preferences. Each axis is drawn in the color of the food type
 * it stands for, so it is clear which preference is which.
 */
public class FoodRadarChart extends Canvas {
    private static final double CHART_RADIUS = 40.0;
    private static final double MIN_NORMALIZED_VALUE = 0.1;
    private static final Color OUTLINE_COLOR = Color.web("#88c0d0");
    private static final Color GRID_COLOR = Color.web("#444444");

    /** Straight up, then clockwise: bottom right, bottom left. */
    private static final double[] AXIS_ANGLES = {-Math.PI / 2.0, Math.PI / 6.0, 5.0 * Math.PI / 6.0};
    private static final String[] AXIS_NAMES = {"Food 1", "Food 2", "Food 3"};

    /**
     * Needed so the chart can be created from FXML.
     */
    public FoodRadarChart() {
        this(200, 150);
    }

    public FoodRadarChart(double width, double height) {
        super(width, height);
    }

    /**
     * Redraws the chart. Each preference is expected to be between 0 and 1.
     */
    public void draw(double type1Preference, double type2Preference, double type3Preference) {
        GraphicsContext gc = getGraphicsContext2D();
        double width = getWidth();
        double height = getHeight();
        gc.clearRect(0, 0, width, height);

        double centerX = width / 2.0;
        double centerY = height / 2.0 + 4;

        drawGrid(gc, centerX, centerY);

        double[] preferences = {type1Preference, type2Preference, type3Preference};
        double[] vertexX = new double[3];
        double[] vertexY = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            double normalized = Math.clamp(preferences[axis], MIN_NORMALIZED_VALUE, 1.0);
            vertexX[axis] = centerX + CHART_RADIUS * normalized * Math.cos(AXIS_ANGLES[axis]);
            vertexY[axis] = centerY + CHART_RADIUS * normalized * Math.sin(AXIS_ANGLES[axis]);
        }

        gc.setFill(Color.web("#88c0d0", 0.35));
        gc.fillPolygon(vertexX, vertexY, 3);
        gc.setStroke(OUTLINE_COLOR);
        gc.setLineWidth(2.0);
        gc.strokePolygon(vertexX, vertexY, 3);

        gc.setFont(Font.font(9));
        for (int axis = 0; axis < 3; axis++) {
            Color foodColor = Food.getTypeColor(axis);

            gc.setFill(foodColor);
            gc.fillOval(vertexX[axis] - 3, vertexY[axis] - 3, 6, 6);
            gc.setStroke(Color.web("#cccccc"));
            gc.setLineWidth(0.75);
            gc.strokeOval(vertexX[axis] - 3, vertexY[axis] - 3, 6, 6);

            drawAxisLabel(gc, axis, centerX, centerY, brighten(foodColor));
        }
    }

    private void drawGrid(@NotNull GraphicsContext gc, double centerX, double centerY) {
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(1.0);
        for (double scale : new double[]{0.25, 0.5, 0.75, 1.0}) {
            double ringRadius = CHART_RADIUS * scale;
            for (int axis = 0; axis < 3; axis++) {
                int nextAxis = (axis + 1) % 3;
                gc.strokeLine(
                        centerX + ringRadius * Math.cos(AXIS_ANGLES[axis]),
                        centerY + ringRadius * Math.sin(AXIS_ANGLES[axis]),
                        centerX + ringRadius * Math.cos(AXIS_ANGLES[nextAxis]),
                        centerY + ringRadius * Math.sin(AXIS_ANGLES[nextAxis]));
            }
        }

        for (int axis = 0; axis < 3; axis++) {
            gc.strokeLine(centerX, centerY,
                    centerX + CHART_RADIUS * Math.cos(AXIS_ANGLES[axis]),
                    centerY + CHART_RADIUS * Math.sin(AXIS_ANGLES[axis]));
        }
    }

    private void drawAxisLabel(@NotNull GraphicsContext gc, int axis, double centerX, double centerY, @NotNull Color labelColor) {
        double labelX = centerX + (CHART_RADIUS + 15) * Math.cos(AXIS_ANGLES[axis]);
        double labelY = centerY + (CHART_RADIUS + 12) * Math.sin(AXIS_ANGLES[axis]);

        Text measuringText = new Text(AXIS_NAMES[axis]);
        measuringText.setFont(gc.getFont());
        double textWidth = measuringText.getLayoutBounds().getWidth();

        gc.setFill(labelColor);
        if (axis == 0) {
            gc.fillText(AXIS_NAMES[axis], labelX - textWidth / 2.0, labelY - 2);
        } else if (axis == 1) {
            gc.fillText(AXIS_NAMES[axis], labelX - 2, labelY + 3);
        } else {
            gc.fillText(AXIS_NAMES[axis], labelX - textWidth + 2, labelY + 3);
        }
    }

    /**
     * The food colors are dark (they have to stand out against terrain), so text in them is hard to read on the
     * panel background. This lightens a color for use as text.
     */
    public static @NotNull Color brighten(@NotNull Color color) {
        return color.interpolate(Color.WHITE, 0.5);
    }
}