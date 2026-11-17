package edu.msoe.envsim.utilities.apputils;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.utilities.apputils.TimeSeriesChart.SeriesStyle;
import edu.msoe.envsim.utilities.simutils.StatsRecorder;
import edu.msoe.envsim.utilities.simutils.StatsSample;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/**
 * The stats screen: an overlay (like {@link MainMenuPane}) with the current state of the world at the top and
 * charts of how the run has gone below.
 * <p>
 * The simulation keeps running underneath, and the screen updates as new samples come in. The cards for the
 * oldest, earliest and latest agents can take the player straight to that agent.
 * </p>
 */
public class StatsPane extends StackPane {
    private static final double REFRESH_INTERVAL_MILLIS = 500;
    private static final double CARD_WIDTH = 190;
    private static final String CARD_STYLE = "-fx-background-color: rgba(45, 45, 45, 0.9); -fx-border-color: #555; "
            + "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 10 14;";

    private static final Color POPULATION_COLOR = Color.web("#88c0d0");
    private static final Color DIFFICULTY_COLOR = Color.web("#ebcb8b");
    private static final Color EARLIEST_GENERATION_COLOR = Color.web("#81a1c1");
    private static final Color AVERAGE_GENERATION_COLOR = Color.web("#a3be8c");
    private static final Color LATEST_GENERATION_COLOR = Color.web("#b48ead");
    private static final Color BIRTH_COLOR = Color.web("#a3be8c");
    private static final Color DEATH_COLOR = Color.web("#bf616a");
    private static final Color PREDATION_COLOR = Color.web("#d08770");
    private static final Color SPECIES_COLOR = Color.web("#b48ead");
    private static final Color LARGEST_SPECIES_COLOR = Color.web("#ebcb8b");
    private static final Color OLDEST_AGE_COLOR = Color.web("#ebcb8b");
    private static final Color AVERAGE_AGE_COLOR = Color.web("#88c0d0");
    private static final Color AGE_AT_DEATH_COLOR = Color.web("#bf616a");

    private final StackPane gamePane;
    private final StatsRecorder statsRecorder;

    private final Label tickLabel = new Label();
    private final StatCard populationCard;
    private final StatCard earliestGenerationCard;
    private final StatCard latestGenerationCard;
    private final StatCard oldestAgentCard;
    private final StatCard speciesCard;

    private final TimeSeriesChart populationChart;
    private final TimeSeriesChart generationChart;
    private final TimeSeriesChart birthsAndDeathsChart;
    private final TimeSeriesChart predationChart;
    private final TimeSeriesChart diversityChart;
    private final TimeSeriesChart ageChart;

    private final Timeline refreshTimeline;
    /**
     * The list of samples the charts were last given, to tell whether there is anything new to draw.
     */
    private @Nullable List<StatsSample> samplesLastDrawn = null;

    /**
     * A headline number with a short description under it, and optionally a button to go to the agent it is about.
     */
    private static final class StatCard {
        private static final String NO_VALUE = "—";

        private final VBox node;
        private final Label valueLabel = new Label(NO_VALUE);
        private final Label detailLabel = new Label();
        private final @Nullable Button goToButton;
        private @Nullable Agent targetAgent = null;

        /**
         * @param onGoToAgent Called when the button is pressed, if this card has one. {@code null} for a card without a button.
         */
        private StatCard(@NotNull String caption, @Nullable Consumer<Agent> onGoToAgent) {
            Label captionLabel = new Label(caption);
            captionLabel.getStyleClass().add("telemetry-category-label");

            valueLabel.getStyleClass().add("title-label");
            valueLabel.setStyle("-fx-font-size: 22px;");

            detailLabel.getStyleClass().add("caption-label");
            detailLabel.setStyle("-fx-font-size: 10px;");
            detailLabel.setWrapText(true);

            node = new VBox(4, captionLabel, valueLabel, detailLabel);
            node.setStyle(CARD_STYLE);
            node.setMinWidth(CARD_WIDTH);
            node.setPrefWidth(CARD_WIDTH);

            if (onGoToAgent != null) {
                goToButton = new Button("Go to agent");
                goToButton.getStyleClass().add("sim-button");
                goToButton.setFocusTraversable(false);
                goToButton.setStyle("-fx-font-size: 10px; -fx-padding: 2 8;");
                goToButton.setDisable(true);
                goToButton.setOnAction(_ -> {
                    // The agent may have died since the card was last updated
                    if (targetAgent != null && targetAgent.isAlive()) {
                        onGoToAgent.accept(targetAgent);
                    }
                });
                node.getChildren().add(goToButton);
            } else {
                goToButton = null;
            }
        }

        private @NotNull VBox node() {
            return node;
        }

        private void show(@NotNull String value, @NotNull String detail, @Nullable Agent agent) {
            valueLabel.setText(value);
            detailLabel.setText(detail);
            targetAgent = agent;
            if (goToButton != null) {
                goToButton.setDisable(agent == null || !agent.isAlive());
            }
        }

        private void clear() {
            show(NO_VALUE, "", null);
        }
    }

    /**
     * @param gamePane      The pane this screen covers while it is open
     * @param statsRecorder Where the history comes from
     * @param onGoToAgent   Called with an agent when the player asks to go to it. The screen doesn't close itself,
     *                      so the callback decides what happens to it.
     */
    public StatsPane(@NotNull StackPane gamePane, @NotNull StatsRecorder statsRecorder, @NotNull Consumer<Agent> onGoToAgent) {
        this.gamePane = gamePane;
        this.statsRecorder = statsRecorder;

        getStyleClass().add("dialog-backdrop");
        setAlignment(Pos.CENTER);
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        // ---- Header ----
        Label titleLabel = new Label("Statistics");
        titleLabel.getStyleClass().add("title-label");
        titleLabel.setStyle("-fx-font-size: 18px;");
        tickLabel.getStyleClass().add("telemetry-category-label");
        HBox headerSpacer = new HBox();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        Button closeButton = createSmallButton("Close", this::close);
        HBox headerRow = new HBox(12, titleLabel, headerSpacer, tickLabel, closeButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label hintLabel = new Label("Hover over a chart for exact values. Charts cover the whole run, "
                + "and older history is condensed as the run gets longer.");
        hintLabel.getStyleClass().add("caption-label");
        hintLabel.setStyle("-fx-font-size: 10px;");
        hintLabel.setWrapText(true);

        // ---- Cards ----
        populationCard = new StatCard("POPULATION", null);
        earliestGenerationCard = new StatCard("EARLIEST GENERATION", onGoToAgent);
        latestGenerationCard = new StatCard("LATEST GENERATION", onGoToAgent);
        oldestAgentCard = new StatCard("OLDEST AGENT", onGoToAgent);
        speciesCard = new StatCard("SPECIES", null);
        Tooltip.install(speciesCard.node(), new Tooltip("Species are counted with the same rule agents use to decide who they "
                + "can hunt, on a random sample of up to " + StatsRecorder.SPECIES_SAMPLE_SIZE + " agents."));

        FlowPane cardsRow = new FlowPane(10, 10);
        cardsRow.getChildren().addAll(populationCard.node(), earliestGenerationCard.node(), latestGenerationCard.node(),
                oldestAgentCard.node(), speciesCard.node());

        // ---- Charts ----
        populationChart = new TimeSeriesChart(List.of(
                new SeriesStyle("Population", POPULATION_COLOR, false),
                new SeriesStyle("Difficulty", DIFFICULTY_COLOR, true, "%")));
        generationChart = new TimeSeriesChart(List.of(
                new SeriesStyle("Earliest", EARLIEST_GENERATION_COLOR, false),
                new SeriesStyle("Average", AVERAGE_GENERATION_COLOR, false),
                new SeriesStyle("Latest", LATEST_GENERATION_COLOR, false)));
        birthsAndDeathsChart = new TimeSeriesChart(List.of(
                new SeriesStyle("Births", BIRTH_COLOR, false),
                new SeriesStyle("Deaths", DEATH_COLOR, false),
                new SeriesStyle("Population", POPULATION_COLOR, true)));
        predationChart = new TimeSeriesChart(List.of(
                new SeriesStyle("Predations", PREDATION_COLOR, false),
                new SeriesStyle("Population", POPULATION_COLOR, true)));
        diversityChart = new TimeSeriesChart(List.of(
                new SeriesStyle("Species", SPECIES_COLOR, false),
                new SeriesStyle("Largest species' share", LARGEST_SPECIES_COLOR, true, "%")));
        ageChart = new TimeSeriesChart(List.of(
                new SeriesStyle("Oldest alive", OLDEST_AGE_COLOR, false),
                new SeriesStyle("Average alive", AVERAGE_AGE_COLOR, false),
                new SeriesStyle("Average at death", AGE_AT_DEATH_COLOR, false)));

        List<CollapsibleSection> chartSections = List.of(
                createChartSection("POPULATION", populationChart),
                createChartSection("GENERATIONS", generationChart),
                createChartSection("BIRTHS AND DEATHS (PER 1,000 TICKS)", birthsAndDeathsChart),
                createChartSection("PREDATIONS (PER 1,000 TICKS)", predationChart),
                createChartSection("GENETIC DIVERSITY", diversityChart),
                createChartSection("AGES (TICKS)", ageChart));

        GridPane chartGrid = new GridPane();
        chartGrid.setHgap(12);
        chartGrid.setVgap(12);
        for (int column = 0; column < 2; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(50);
            chartGrid.getColumnConstraints().add(constraints);
        }
        for (int sectionIndex = 0; sectionIndex < chartSections.size(); sectionIndex++) {
            chartGrid.add(chartSections.get(sectionIndex), sectionIndex % 2, sectionIndex / 2);
        }

        ScrollPane chartScrollPane = new ScrollPane(chartGrid);
        chartScrollPane.getStyleClass().add("transparent-scroll-pane");
        chartScrollPane.setFitToWidth(true);
        chartScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(chartScrollPane, Priority.ALWAYS);

        // ---- Dialog ----
        VBox dialog = new VBox(14, headerRow, hintLabel, cardsRow, chartScrollPane);
        dialog.setPadding(new Insets(25));
        dialog.getStyleClass().add("panel");
        dialog.setStyle("-fx-background-color: rgba(25, 25, 25, 0.95);");
        dialog.prefWidthProperty().bind(gamePane.widthProperty().multiply(0.85));
        dialog.prefHeightProperty().bind(gamePane.heightProperty().multiply(0.88));
        dialog.setMaxWidth(1200);
        dialog.setMaxHeight(850);
        dialog.setAlignment(Pos.TOP_CENTER);
        getChildren().add(dialog);

        setOnMouseClicked(event -> {
            if (event.getTarget() == this) {
                close();
            }
        });

        refreshTimeline = new Timeline(new KeyFrame(Duration.millis(REFRESH_INTERVAL_MILLIS), _ -> refreshDisplays()));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);

        // Ages are shown in the app-wide unit system
        AppSettings.unitSystemProperty().addListener((_, _, _) -> {
            if (isShowing()) refreshDisplays();
        });
    }


    /*
     * Opening and closing:
     */

    /**
     * Shows the screen on top of the game. The simulation keeps running.
     */
    public void open() {
        if (isShowing()) return;

        refreshDisplays();
        gamePane.getChildren().add(this);
        refreshTimeline.play();
    }

    public void close() {
        refreshTimeline.stop();
        gamePane.getChildren().remove(this);
    }

    public boolean isShowing() {
        return getParent() != null;
    }


    /*
     * Updating what is shown:
     */

    private void refreshDisplays() {
        List<StatsSample> samples = statsRecorder.getSamples();
        updateCards(statsRecorder.getLatestCensus(), samples);

        if (samples != samplesLastDrawn) {
            samplesLastDrawn = samples;
            updateCharts(samples);
        }
    }

    private void updateCards(@Nullable StatsRecorder.Census census, @NotNull List<StatsSample> samples) {
        if (census == null || samples.isEmpty()) {
            tickLabel.setText("");
            populationCard.clear();
            earliestGenerationCard.clear();
            latestGenerationCard.clear();
            oldestAgentCard.clear();
            speciesCard.clear();
            return;
        }

        StatsSample current = census.sample();
        tickLabel.setText("Tick " + UnitSystem.formatWholeNumber(current.tick()));

        populationCard.show(formatRounded(current.population()), describeTurnover(samples.getLast()), null);
        earliestGenerationCard.show("Gen " + formatRounded(current.earliestGeneration()),
                "The oldest bloodline still alive", census.earliestGenerationAgent());
        latestGenerationCard.show("Gen " + formatRounded(current.latestGeneration()),
                "The furthest-evolved agent alive", census.latestGenerationAgent());

        Agent oldestAgent = census.oldestAgent();
        String oldestAgentDetail = (oldestAgent != null) ? "Generation " + UnitSystem.formatWholeNumber(oldestAgent.getGeneration()) : "";
        oldestAgentCard.show(AppSettings.getUnitSystem().formatDuration(current.oldestAge()), oldestAgentDetail, oldestAgent);

        speciesCard.show(formatRounded(current.speciesCount()),
                String.format(Locale.US, "The largest holds %.0f%% of agents", current.largestSpeciesShare() * 100.0), null);
    }

    private static @NotNull String describeTurnover(@NotNull StatsSample sample) {
        double turnover = sample.turnoverPercentPerThousandTicks();
        if (Double.isNaN(turnover)) {
            return "Turnover not measured yet";
        }
        return UnitSystem.formatNumber(turnover) + "% of agents die every 1,000 ticks";
    }

    private void updateCharts(@NotNull List<StatsSample> samples) {
        double[] ticks = extract(samples, StatsSample::tick);
        double[] population = extract(samples, StatsSample::population);

        populationChart.setData(ticks, population, extract(samples, sample -> sample.difficulty() * 100.0));
        generationChart.setData(ticks,
                extract(samples, StatsSample::earliestGeneration),
                extract(samples, StatsSample::averageGeneration),
                extract(samples, StatsSample::latestGeneration));
        birthsAndDeathsChart.setData(ticks,
                extract(samples, StatsSample::birthsPerThousandTicks),
                extract(samples, StatsSample::deathsPerThousandTicks),
                population);
        predationChart.setData(ticks, extract(samples, StatsSample::predationsPerThousandTicks), population);
        diversityChart.setData(ticks,
                extract(samples, StatsSample::speciesCount),
                extract(samples, sample -> sample.largestSpeciesShare() * 100.0));
        ageChart.setData(ticks,
                extract(samples, StatsSample::oldestAge),
                extract(samples, StatsSample::averageAge),
                extract(samples, StatsSample::averageLifespanAtDeath));
    }

    private static double @NotNull [] extract(@NotNull List<StatsSample> samples, @NotNull ToDoubleFunction<StatsSample> valueGetter) {
        double[] values = new double[samples.size()];
        for (int sampleIndex = 0; sampleIndex < values.length; sampleIndex++) {
            values[sampleIndex] = valueGetter.applyAsDouble(samples.get(sampleIndex));
        }
        return values;
    }

    private static @NotNull String formatRounded(double value) {
        return UnitSystem.formatWholeNumber(Math.round(value));
    }


    /*
     * Building blocks:
     */

    private static @NotNull CollapsibleSection createChartSection(@NotNull String title, @NotNull TimeSeriesChart chart) {
        CollapsibleSection section = new CollapsibleSection(title, true, chart);
        section.setMaxWidth(Double.MAX_VALUE);
        return section;
    }

    private static @NotNull Button createSmallButton(@NotNull String text, @NotNull Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("sim-button");
        button.setFocusTraversable(false);
        button.setStyle("-fx-font-size: 13px; -fx-padding: 8 20;");
        button.setOnAction(_ -> action.run());
        return button;
    }
}