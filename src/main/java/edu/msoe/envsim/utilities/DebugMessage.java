package edu.msoe.envsim.utilities;

import edu.msoe.envsim.Environment;
import javafx.scene.control.Label;
import org.jetbrains.annotations.NotNull;
import java.util.function.Supplier;

public class DebugMessage extends Label {
    private final Supplier<String> messageSupplier;

    // New constructor supporting custom precision
    public DebugMessage(Supplier<Object> valueSupplier, String label, int precision, @NotNull Environment environment) {
        // Build the dynamic format string once, e.g., "%.3f %s" for precision 3
        String formatString = "%." + precision + "f %s";

        this.messageSupplier = () -> {
            Object val = valueSupplier.get();
            if (val == null) {
                return "null " + label;
            }

            if (val instanceof Double || val instanceof Float) {
                return String.format(formatString, ((Number) val).doubleValue(), label);
            }

            return val.toString() + " " + label;
        };

        environment.addDebugMessage(this);
        this.setMinWidth(70);
    }

    public void update() {
        setText(messageSupplier.get());
    }
}
