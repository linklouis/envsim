package edu.msoe.envsim.utilities.apputils;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

/**
 * A titled card whose contents can be folded away by clicking the header. Matches the look of the
 * collapsible sections in the agent inspector.
 */
public class CollapsibleSection extends VBox {
    private static final String ARROW_EXPANDED = "\u25BC";
    private static final String ARROW_COLLAPSED = "\u25B6";

    private final Label arrowLabel = new Label();
    private final VBox contentBox = new VBox(10);

    public CollapsibleSection(@NotNull String title, boolean expanded, @NotNull Node... contents) {
        setStyle("-fx-background-color: rgba(40, 40, 40, 0.6); -fx-background-radius: 6; -fx-border-color: #444; -fx-border-radius: 6;");

        arrowLabel.getStyleClass().add("telemetry-category-label");
        arrowLabel.setStyle("-fx-font-size: 9px;");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("telemetry-category-label");

        HBox spacer = new HBox();
        spacer.setMinWidth(6);

        HBox header = new HBox(arrowLabel, spacer, titleLabel);
        header.setStyle("-fx-padding: 8 10; -fx-cursor: hand;");
        header.setOnMouseClicked(_ -> setExpanded(!isExpanded()));

        contentBox.setStyle("-fx-padding: 0 10 10 10;");
        contentBox.getChildren().addAll(contents);

        getChildren().addAll(header, contentBox);
        setExpanded(expanded);
    }

    public boolean isExpanded() {
        return contentBox.isVisible();
    }

    public void setExpanded(boolean expanded) {
        contentBox.setVisible(expanded);
        contentBox.setManaged(expanded);
        arrowLabel.setText(expanded ? ARROW_EXPANDED : ARROW_COLLAPSED);
    }
}