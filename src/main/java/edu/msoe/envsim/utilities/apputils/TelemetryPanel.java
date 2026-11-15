package edu.msoe.envsim.utilities.apputils;

import edu.msoe.envsim.Environment;
import edu.msoe.envsim.entities.Agent;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Source: Google Gemini
 */
public class TelemetryPanel extends HBox {
    private final List<MetricBinding> bindings = new ArrayList<>();

    public TelemetryPanel(@NotNull Environment environment) {
        getStyleClass().add("telemetry-panel");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(24);

        // Build organized telemetry groups
        getChildren().addAll(
                createGroup("SIMULATION",
                        createMetric("Tick", environment::getCurrentTick, "%,d"),
                        createMetric("TpS", environment::getSimUpdateRate, "%,.1f"),
                        createMetric("FpS", environment::getFrameRate, "%,.1f")
                ),
                createSeparator(),
                createGroup("POPULATION & STATS",
                        createMetric("Agents", environment::getNumAgents, "%,d"),
                        createMetric("Predations", environment::getNumPredations, "%,d"),
                        createMetric("Difficulty", Agent::getDifficulty, "%,.3f")
                ),
                createSeparator(),
                createGroup("PERFORMANCE (MS)",
                        createMetric("List", environment::getListMs, "%,.2f"),
                        createMetric("Decide", environment::getDecideMs, "%,.2f"),
                        createMetric("Step", environment::getStepMs, "%,.2f")
                )
        );
    }

    public void updateMetrics() {
        for (MetricBinding binding : bindings) {
            binding.update();
        }
    }

    private @NotNull VBox createGroup(String title, MetricBinding @NotNull ... metrics) {
        VBox group = new VBox(2);
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("telemetry-category-label");
        group.getChildren().add(titleLbl);

        for (MetricBinding m : metrics) {
            bindings.add(m);
            group.getChildren().add(m.container);
        }
        return group;
    }

    private @NotNull Label createSeparator() {
        Label sep = new Label("|");
        sep.getStyleClass().add("telemetry-key-label");
        return sep;
    }

    @Contract("_, _, _ -> new")
    private @NotNull MetricBinding createMetric(String key, Supplier<Object> supplier, String format) {
        Label keyLbl = new Label(key + ":");
        keyLbl.getStyleClass().add("telemetry-key-label");
        keyLbl.setMinWidth(65);

        Label valLbl = new Label("-");
        valLbl.getStyleClass().add("telemetry-val-label");

        HBox row = new HBox(6, keyLbl, valLbl);
        row.setAlignment(Pos.CENTER_LEFT);

        return new MetricBinding(row, valLbl, supplier, format);
    }

    private record MetricBinding(HBox container, Label valueLabel, Supplier<Object> supplier, String format) {

        void update() {
            try {
                Object val = supplier.get();
                if (val == null) {
                    valueLabel.setText("null");
                } else if (val instanceof Number num) {
                    // Using Locale.US ensures consistent comma grouping regardless of host system locale settings
                    if (format.contains("f")) {
                        valueLabel.setText(String.format(Locale.US, format, num.doubleValue()));
                    } else {
                        valueLabel.setText(String.format(Locale.US, format, num.longValue()));
                    }
                } else {
                    valueLabel.setText(val.toString());
                }
            } catch (Exception e) {
                valueLabel.setText("ERR");
            }
        }
    }
}