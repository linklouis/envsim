package edu.msoe.envsim.utilities;

import edu.msoe.envsim.Environment;
import javafx.scene.control.Label;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class DebugMessage extends Label {
    private final Supplier<String> messageSupplier;
    public DebugMessage(Supplier<Object> messageSupplier, @NotNull Environment environment) {
        this.messageSupplier = () -> messageSupplier.get().toString();
        environment.addDebugMessage(this);
//        this.setPadding(new Insets(0, 10, 0, 0));
        this.setMinWidth(70);
    }

    public void update() {
        setText(messageSupplier.get());
    }
}

