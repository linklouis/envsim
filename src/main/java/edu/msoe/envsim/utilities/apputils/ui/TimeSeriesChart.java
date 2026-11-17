package edu.msoe.envsim.utilities.apputils.ui;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * A line chart of one or more values over time, drawn to fit the space it is given.
 * <p>
 * Every series is drawn against the left axis, except those marked as being on the secondary axis, which is on the
 * right and whose lines are dashed. That is meant for laying a second quantity over the first (such as the population
 * over the births and deaths) without the two needing the same scale. The two axes share their grid lines.
 * </p>
 * <p>
 * All axes start at zero. Hovering over the chart shows the exact values of the sample nearest the mouse.
 * </p>
 */
public class TimeSeriesChart extends Region {
    /**
     * @param name            Shown in the legend and the hover readout
     * @param color           The color of the line
     * @param onSecondaryAxis Whether the series uses the right axis (and is drawn dashed) instead of the left one
     * @param unitSuffix      Added after the series' values in the hover readout, such as "%"
     */
    public record SeriesStyle(@NotNull String name, @NotNull Color color, boolean onSecondaryAxis,
                              @NotNull String unitSuffix) {
        public SeriesStyle(@NotNull String name, @NotNull Color color, boolean onSecondaryAxis) {
            this(name, color, onSecondaryAxis, "");
        }
    }

    private record PlotArea(double left, double top, double right, double bottom) {
        double width() {
            return right - left;
        }

        double height() {
            return bottom - top;
        }
    }

    /**
     * @param maximum   The value at the top of the axis
     * @param step      The distance in value between two grid lines
     * @param divisions How many gaps the grid lines make, which is {@code maximum / step}
     */
    private record Axis(double maximum, double step, int divisions) {
    }

    private static final double MIN_HEIGHT = 150;
    private static final double PREFERRED_WIDTH = 300;
    private static final double PREFERRED_HEIGHT = 190;
    private static final double MIN_PLOT_SIZE = 40;

    private static final double MARGIN_LEFT = 46;
    private static final double MARGIN_TOP = 24;
    private static final double MARGIN_BOTTOM = 22;
    private static final double MARGIN_RIGHT = 14;
    private static final double MARGIN_RIGHT_WITH_SECONDARY_AXIS = 46;

    private static final int MAX_GRID_DIVISIONS = 5;
    private static final int X_LABEL_COUNT = 5;
    /**
     * The grid lines land on multiples of these (times a power of ten), so the axis labels are round numbers.
     */
    private static final double[] NICE_STEP_FACTORS = {1, 2, 2.5, 5, 10};

    private static final double LINE_WIDTH = 1.75;
    private static final double DASH_LENGTH = 5;
    private static final double DASH_GAP = 4;
    private static final double LEGEND_CENTER_Y = 10;
    private static final double LEGEND_SWATCH_LENGTH = 14;
    private static final double LEGEND_SPACING = 16;

    private static final double TOOLTIP_PADDING = 7;
    private static final double TOOLTIP_LINE_HEIGHT = 14;
    private static final double TOOLTIP_SWATCH_SIZE = 6;
    private static final double TOOLTIP_OFFSET_FROM_CURSOR = 10;

    private static final Font CHART_FONT = Font.font(10);
    private static final Color PLOT_BACKGROUND_COLOR = Color.web("#000000", 0.2);
    private static final Color GRID_COLOR = Color.web("#444444");
    private static final Color LABEL_COLOR = Color.web("#aaaaaa");
    private static final Color HOVER_LINE_COLOR = Color.web("#ffffff", 0.4);
    private static final Color TOOLTIP_BACKGROUND_COLOR = Color.web("#141414", 0.92);
    private static final Color TOOLTIP_BORDER_COLOR = Color.web("#555555");
    private static final Color TOOLTIP_HEADER_COLOR = Color.web("#88c0d0");
    private static final Color TOOLTIP_TEXT_COLOR = Color.web("#eceff4");

    private final Canvas canvas = new Canvas();
    private final List<SeriesStyle> seriesStyles;
    private final boolean hasSecondaryAxis;

    private double[] ticks = new double[0];
    /**
     * One array per series, in the same order as {@link #seriesStyles}, each as long as {@link #ticks}.
     */
    private double[][] seriesValues;
    private double hoverMouseX = Double.NaN;

    /**
     * @param seriesStyles How each series looks. {@link #setData} takes its values in this order.
     */
    public TimeSeriesChart(@NotNull List<SeriesStyle> seriesStyles) {
        this.seriesStyles = List.copyOf(seriesStyles);
        this.hasSecondaryAxis = seriesStyles.stream().anyMatch(SeriesStyle::onSecondaryAxis);
        this.seriesValues = new double[seriesStyles.size()][0];

        // Stated outright so the size the chart asks for doesn't depend on the canvas, which is sized to match the chart
        setMinHeight(MIN_HEIGHT);
        setPrefWidth(PREFERRED_WIDTH);
        setPrefHeight(PREFERRED_HEIGHT);

        // The canvas always fills the chart, and the chart gets the mouse so the canvas doesn't have to
        canvas.setMouseTransparent(true);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener(_ -> redraw());
        canvas.heightProperty().addListener(_ -> redraw());
        getChildren().add(canvas);

        setOnMouseMoved(event -> {
            hoverMouseX = event.getX();
            redraw();
        });
        setOnMouseExited(_ -> {
            hoverMouseX = Double.NaN;
            redraw();
        });
    }

    /**
     * Replaces what the chart shows and redraws it.
     *
     * @param ticks        When each sample was taken, oldest first. Plotted along the bottom.
     * @param seriesValues The value of each series at each of those ticks, in the order the series were given to the
     *                     constructor. {@code NaN} means there is no value at that tick, and the line skips it.
     */
    public void setData(double @NotNull [] ticks, double[]... seriesValues) {
        if (seriesValues.length != seriesStyles.size()) {
            throw new IllegalArgumentException("Expected values for " + seriesStyles.size() + " series but got " + seriesValues.length + ".");
        }
        for (double[] values : seriesValues) {
            if (values.length != ticks.length) {
                throw new IllegalArgumentException("Every series needs one value for each of the " + ticks.length + " ticks.");
            }
        }

        this.ticks = ticks;
        this.seriesValues = seriesValues;
        redraw();
    }


    /*
     * Drawing:
     */

    private void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);
        gc.setFont(CHART_FONT);
        gc.setTextBaseline(VPos.CENTER);

        PlotArea plotArea = new PlotArea(MARGIN_LEFT, MARGIN_TOP,
                width - (hasSecondaryAxis ? MARGIN_RIGHT_WITH_SECONDARY_AXIS : MARGIN_RIGHT), height - MARGIN_BOTTOM);
        drawLegend(gc);
        if (plotArea.width() < MIN_PLOT_SIZE || plotArea.height() < MIN_PLOT_SIZE) return;

        gc.setFill(PLOT_BACKGROUND_COLOR);
        gc.fillRect(plotArea.left(), plotArea.top(), plotArea.width(), plotArea.height());

        if (ticks.length == 0) {
            gc.setFill(LABEL_COLOR);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("Waiting for data...", (plotArea.left() + plotArea.right()) / 2, (plotArea.top() + plotArea.bottom()) / 2);
            return;
        }

        double firstTick = ticks[0];
        double lastTick = ticks[ticks.length - 1];
        Axis primaryAxis = createAxis(maximumValueOnAxis(false), MAX_GRID_DIVISIONS);
        Axis secondaryAxis = hasSecondaryAxis ? createAxisWithDivisions(maximumValueOnAxis(true), primaryAxis.divisions()) : null;

        drawGridAndLabels(gc, plotArea, primaryAxis, secondaryAxis, firstTick, lastTick);
        for (int seriesIndex = 0; seriesIndex < seriesStyles.size(); seriesIndex++) {
            SeriesStyle style = seriesStyles.get(seriesIndex);
            Axis axis = style.onSecondaryAxis() ? secondaryAxis : primaryAxis;
            drawSeries(gc, style, seriesValues[seriesIndex], plotArea, axis, firstTick, lastTick);
        }
        drawHoverReadout(gc, plotArea, primaryAxis, secondaryAxis, firstTick, lastTick);
    }

    private void drawLegend(@NotNull GraphicsContext gc) {
        gc.setTextAlign(TextAlignment.LEFT);
        double legendX = MARGIN_LEFT;
        for (SeriesStyle style : seriesStyles) {
            gc.setStroke(style.color());
            gc.setLineWidth(LINE_WIDTH);
            if (style.onSecondaryAxis()) {
                gc.setLineDashes(3, 2);
            }
            gc.strokeLine(legendX, LEGEND_CENTER_Y, legendX + LEGEND_SWATCH_LENGTH, LEGEND_CENTER_Y);
            gc.setLineDashes();

            gc.setFill(LABEL_COLOR);
            gc.fillText(style.name(), legendX + LEGEND_SWATCH_LENGTH + 5, LEGEND_CENTER_Y);
            legendX += LEGEND_SWATCH_LENGTH + 5 + measureTextWidth(style.name()) + LEGEND_SPACING;
        }
    }

    private void drawGridAndLabels(@NotNull GraphicsContext gc, @NotNull PlotArea plotArea, @NotNull Axis primaryAxis,
                                   @Nullable Axis secondaryAxis, double firstTick, double lastTick) {
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(1);
        gc.setFill(LABEL_COLOR);
        for (int divisionIndex = 0; divisionIndex <= primaryAxis.divisions(); divisionIndex++) {
            double y = plotArea.bottom() - plotArea.height() * divisionIndex / primaryAxis.divisions();
            gc.strokeLine(plotArea.left(), y, plotArea.right(), y);

            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(formatAxisValue(primaryAxis.step() * divisionIndex), plotArea.left() - 6, y);
            if (secondaryAxis != null) {
                gc.setTextAlign(TextAlignment.LEFT);
                gc.fillText(formatAxisValue(secondaryAxis.step() * divisionIndex), plotArea.right() + 6, y);
            }
        }

        for (int labelIndex = 0; labelIndex < X_LABEL_COUNT; labelIndex++) {
            double fraction = (double) labelIndex / (X_LABEL_COUNT - 1);
            // The outer labels are aligned inwards so they don't run off the chart
            if (labelIndex == 0) {
                gc.setTextAlign(TextAlignment.LEFT);
            } else if (labelIndex == X_LABEL_COUNT - 1) {
                gc.setTextAlign(TextAlignment.RIGHT);
            } else {
                gc.setTextAlign(TextAlignment.CENTER);
            }
            gc.fillText(formatAxisValue(firstTick + fraction * (lastTick - firstTick)),
                    plotArea.left() + fraction * plotArea.width(), plotArea.bottom() + 11);
        }
    }

    private void drawSeries(@NotNull GraphicsContext gc, @NotNull SeriesStyle style, double @NotNull [] values,
                            @NotNull PlotArea plotArea, @NotNull Axis axis, double firstTick, double lastTick) {
        gc.setStroke(style.color());
        gc.setLineWidth(LINE_WIDTH);
        if (style.onSecondaryAxis()) {
            gc.setLineDashes(DASH_LENGTH, DASH_GAP);
        }

        int drawnPointCount = 0;
        double lastX = 0;
        double lastY = 0;
        gc.beginPath();
        for (int sampleIndex = 0; sampleIndex < values.length; sampleIndex++) {
            if (Double.isNaN(values[sampleIndex])) continue;

            double x = horizontalPosition(ticks[sampleIndex], plotArea, firstTick, lastTick);
            double y = verticalPosition(values[sampleIndex], plotArea, axis);
            if (drawnPointCount == 0) {
                gc.moveTo(x, y);
            } else {
                gc.lineTo(x, y);
            }
            drawnPointCount++;
            lastX = x;
            lastY = y;
        }
        gc.stroke();
        gc.setLineDashes();

        // A line needs two points, so a lone first sample would otherwise be invisible
        if (drawnPointCount == 1) {
            gc.setFill(style.color());
            gc.fillOval(lastX - 3, lastY - 3, 6, 6);
        }
    }

    private void drawHoverReadout(@NotNull GraphicsContext gc, @NotNull PlotArea plotArea, @NotNull Axis primaryAxis,
                                  @Nullable Axis secondaryAxis, double firstTick, double lastTick) {
        if (Double.isNaN(hoverMouseX) || hoverMouseX < plotArea.left() || hoverMouseX > plotArea.right()) return;

        int sampleIndex = findNearestSampleIndex(hoverMouseX, plotArea, firstTick, lastTick);
        double sampleX = horizontalPosition(ticks[sampleIndex], plotArea, firstTick, lastTick);

        gc.setStroke(HOVER_LINE_COLOR);
        gc.setLineWidth(1);
        gc.strokeLine(sampleX, plotArea.top(), sampleX, plotArea.bottom());

        String headerText = "Tick " + String.format(Locale.US, "%,d", Math.round(ticks[sampleIndex]));
        String[] lineTexts = new String[seriesStyles.size()];
        double widestTextWidth = measureTextWidth(headerText);
        for (int seriesIndex = 0; seriesIndex < seriesStyles.size(); seriesIndex++) {
            SeriesStyle style = seriesStyles.get(seriesIndex);
            double value = seriesValues[seriesIndex][sampleIndex];

            String valueText = Double.isNaN(value) ? "—" : formatTooltipValue(value, style.unitSuffix());
            lineTexts[seriesIndex] = style.name() + ": " + valueText;
            widestTextWidth = Math.max(widestTextWidth, TOOLTIP_SWATCH_SIZE + 6 + measureTextWidth(lineTexts[seriesIndex]));

            if (!Double.isNaN(value)) {
                Axis axis = style.onSecondaryAxis() ? secondaryAxis : primaryAxis;
                gc.setFill(style.color());
                gc.fillOval(sampleX - 3.5, verticalPosition(value, plotArea, axis) - 3.5, 7, 7);
            }
        }

        double boxWidth = widestTextWidth + TOOLTIP_PADDING * 2;
        double boxHeight = TOOLTIP_LINE_HEIGHT * (1 + seriesStyles.size()) + TOOLTIP_PADDING * 2;
        double boxX = sampleX + TOOLTIP_OFFSET_FROM_CURSOR;
        if (boxX + boxWidth > plotArea.right()) {
            boxX = sampleX - TOOLTIP_OFFSET_FROM_CURSOR - boxWidth;
        }
        boxX = Math.max(boxX, plotArea.left());
        double boxY = plotArea.top() + 4;

        gc.setFill(TOOLTIP_BACKGROUND_COLOR);
        gc.fillRect(boxX, boxY, boxWidth, boxHeight);
        gc.setStroke(TOOLTIP_BORDER_COLOR);
        gc.strokeRect(boxX, boxY, boxWidth, boxHeight);

        gc.setTextAlign(TextAlignment.LEFT);
        double lineCenterY = boxY + TOOLTIP_PADDING + TOOLTIP_LINE_HEIGHT / 2;
        gc.setFill(TOOLTIP_HEADER_COLOR);
        gc.fillText(headerText, boxX + TOOLTIP_PADDING, lineCenterY);
        for (int seriesIndex = 0; seriesIndex < seriesStyles.size(); seriesIndex++) {
            lineCenterY += TOOLTIP_LINE_HEIGHT;
            gc.setFill(seriesStyles.get(seriesIndex).color());
            gc.fillRect(boxX + TOOLTIP_PADDING, lineCenterY - TOOLTIP_SWATCH_SIZE / 2, TOOLTIP_SWATCH_SIZE, TOOLTIP_SWATCH_SIZE);
            gc.setFill(TOOLTIP_TEXT_COLOR);
            gc.fillText(lineTexts[seriesIndex], boxX + TOOLTIP_PADDING + TOOLTIP_SWATCH_SIZE + 6, lineCenterY);
        }
    }


    /*
     * Positions and axes:
     */

    private double horizontalPosition(double tick, @NotNull PlotArea plotArea, double firstTick, double lastTick) {
        if (lastTick <= firstTick) {
            return plotArea.left() + plotArea.width() / 2;
        }
        return plotArea.left() + (tick - firstTick) / (lastTick - firstTick) * plotArea.width();
    }

    private double verticalPosition(double value, @NotNull PlotArea plotArea, @NotNull Axis axis) {
        return plotArea.bottom() - value / axis.maximum() * plotArea.height();
    }

    private int findNearestSampleIndex(double mouseX, @NotNull PlotArea plotArea, double firstTick, double lastTick) {
        int nearestSampleIndex = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int sampleIndex = 0; sampleIndex < ticks.length; sampleIndex++) {
            double distance = Math.abs(horizontalPosition(ticks[sampleIndex], plotArea, firstTick, lastTick) - mouseX);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestSampleIndex = sampleIndex;
            }
        }
        return nearestSampleIndex;
    }

    private double maximumValueOnAxis(boolean secondaryAxis) {
        double maximum = 0;
        for (int seriesIndex = 0; seriesIndex < seriesStyles.size(); seriesIndex++) {
            if (seriesStyles.get(seriesIndex).onSecondaryAxis() != secondaryAxis) continue;
            for (double value : seriesValues[seriesIndex]) {
                if (value > maximum) {
                    maximum = value;
                }
            }
        }
        return maximum;
    }

    /**
     * Makes an axis that reaches {@code maximumValue}, with round-numbered grid lines and as few of them as fit well.
     */
    private static @NotNull Axis createAxis(double maximumValue, int maxDivisions) {
        double safeMaximumValue = (maximumValue > 0) ? maximumValue : 1;
        double step = smallestNiceStepCovering(safeMaximumValue, maxDivisions);
        int divisions = Math.max(1, (int) Math.ceil(safeMaximumValue / step - 1e-9));
        return new Axis(step * divisions, step, divisions);
    }

    /**
     * Makes an axis that reaches {@code maximumValue} using exactly {@code divisions} gaps, so its grid lines
     * line up with another axis' grid lines.
     */
    private static @NotNull Axis createAxisWithDivisions(double maximumValue, int divisions) {
        double safeMaximumValue = (maximumValue > 0) ? maximumValue : 1;
        double step = smallestNiceStepCovering(safeMaximumValue, divisions);
        return new Axis(step * divisions, step, divisions);
    }

    /**
     * @return The smallest round number (1, 2, 2.5 or 5 times a power of ten) that {@code divisions} steps of
     * would reach {@code maximumValue}
     */
    private static double smallestNiceStepCovering(double maximumValue, int divisions) {
        double minimumStep = maximumValue / divisions;
        double magnitude = Math.pow(10, Math.floor(Math.log10(minimumStep)));
        for (double factor : NICE_STEP_FACTORS) {
            // The tiny allowance keeps a maximum that is an exact multiple from jumping to the next step up
            if (factor * magnitude >= minimumStep * (1 - 1e-9)) {
                return factor * magnitude;
            }
        }
        return 10 * magnitude;
    }


    /*
     * Text:
     */

    private static double measureTextWidth(@NotNull String text) {
        Text measuringText = new Text(text);
        measuringText.setFont(CHART_FONT);
        return measuringText.getLayoutBounds().getWidth();
    }

    /**
     * Short enough to fit beside the chart: 1,500,000 becomes 1.5M, and 12,000 becomes 12k.
     */
    private static @NotNull String formatAxisValue(double value) {
        double magnitude = Math.abs(value);
        if (magnitude >= 1_000_000) {
            return trimTrailingZeros(String.format(Locale.US, "%.1f", value / 1_000_000)) + "M";
        }
        if (magnitude >= 1_000) {
            return trimTrailingZeros(String.format(Locale.US, "%.1f", value / 1_000)) + "k";
        }
        return trimTrailingZeros(String.format(Locale.US, "%.2f", value));
    }

    /**
     * Full detail for the hover readout, with fewer decimals the bigger the number is.
     */
    private static @NotNull String formatTooltipValue(double value, @NotNull String unitSuffix) {
        double magnitude = Math.abs(value);
        String format = (magnitude >= 100) ? "%,.0f" : (magnitude >= 10) ? "%,.1f" : "%,.2f";
        return String.format(Locale.US, format, value) + unitSuffix;
    }

    private static @NotNull String trimTrailingZeros(@NotNull String number) {
        if (!number.contains(".")) return number;
        return number.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}