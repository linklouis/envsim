package edu.msoe.envsim.utilities.apputils;

import edu.msoe.envsim.Environment;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;

public class EnvironmentManagerDialog extends StackPane {

    public EnvironmentManagerDialog(@NotNull StackPane gamePane, @NotNull Environment environment) {
        getStyleClass().add("dialog-backdrop");
        setAlignment(Pos.CENTER);
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        VBox dialog = new VBox(20);
        dialog.setPadding(new Insets(25));
        dialog.getStyleClass().add("panel");
        dialog.setStyle("-fx-background-color: rgba(25, 25, 25, 0.95);");

        // Scalable with screen size but bounded
        dialog.prefWidthProperty().bind(gamePane.widthProperty().multiply(0.65));
        dialog.prefHeightProperty().bind(gamePane.heightProperty().multiply(0.70));
        dialog.setMaxWidth(950);
        dialog.setMaxHeight(700);
        dialog.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("Environment Manager");
        title.getStyleClass().add("title-label");
        title.setStyle("-fx-font-size: 18px;");

        TilePane tilePane = new TilePane();
        tilePane.setHgap(20);
        tilePane.setVgap(20);
        tilePane.setPrefColumns(3);
        tilePane.setAlignment(Pos.TOP_CENTER);

        TextField nameField = new TextField();
        nameField.setPromptText("Enter or select environment name...");
        nameField.setStyle("-fx-control-inner-background: #333; -fx-text-fill: white; -fx-prompt-text-fill: #aaa; -fx-font-size: 14px; -fx-padding: 8;");
        nameField.setFocusTraversable(false);

        BiConsumer<File, String> confirmAndSave = (targetFile, textName) -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Overwrite Environment");
            alert.setHeaderText("Environment Already Exists");

            Label contentLabel = new Label("An environment named '" + textName + "' already exists. Do you want to overwrite it?");
            contentLabel.setWrapText(true);
            contentLabel.setMaxWidth(380);
            alert.getDialogPane().setContent(contentLabel);
            alert.getDialogPane().setPrefWidth(450);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    environment.saveTerrain(targetFile);
                } catch (Exception ex) {
                    showErrorPopup("Save Failed", "Could not overwrite environment file.", ex.getMessage());
                }
            }
        };

        Runnable refreshThumbnails = () -> {
            tilePane.getChildren().clear();
            File dir = new File("environments");
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((_, name) -> name.endsWith(".terrain"));
                if (files != null) {
                    Arrays.sort(files);
                    for (File file : files) {
                        String name = file.getName().replace(".terrain", "");

                        boolean isValid = true;
                        String invalidReason = "";
                        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                            int fileWidth = in.readInt();
                            int fileHeight = in.readInt();
                            if (fileWidth != environment.getTerrainWidth() || fileHeight != environment.getTerrainHeight()) {
                                isValid = false;
                                invalidReason = "Dimension mismatch: File is " + fileWidth + "x" + fileHeight + ", but world requires " + environment.getTerrainWidth() + "x" + environment.getTerrainHeight() + ".";
                            }
                        } catch (Exception e) {
                            isValid = false;
                            invalidReason = "Could not read terrain file: " + e.getMessage();
                        }

                        WritableImage thumb = Environment.loadTerrainThumbnail(file, 200, 120);
                        ImageView imageView = new ImageView(thumb);
                        imageView.setFitWidth(200);
                        imageView.setFitHeight(120);
                        imageView.setPreserveRatio(true);

                        Label nameLabel = new Label(name);
                        nameLabel.getStyleClass().add("title-label");
                        nameLabel.setStyle("-fx-font-size: 14px;");

                        Button loadCardBtn = new Button("Load");
                        loadCardBtn.getStyleClass().add("sim-button");
                        loadCardBtn.setStyle("-fx-font-size: 12px;");
                        loadCardBtn.setFocusTraversable(false);
                        loadCardBtn.setDisable(!isValid);
                        loadCardBtn.setOnAction(_ -> {
                            try {
                                environment.loadTerrain(file);
                                gamePane.getChildren().remove(this);
                            } catch (Exception ex) {
                                showErrorPopup("Load Failed", "Could not load environment '" + name + "'.", ex.getMessage());
                            }
                        });

                        Button overrideCardBtn = new Button("Override");
                        overrideCardBtn.getStyleClass().add("danger-button");
                        overrideCardBtn.setStyle("-fx-font-size: 12px;");
                        overrideCardBtn.setFocusTraversable(false);
                        overrideCardBtn.setOnAction(_ -> {
                            nameField.setText(name);
                            confirmAndSave.accept(file, name);
                        });

                        HBox cardActions = new HBox(8, loadCardBtn, overrideCardBtn);
                        cardActions.setAlignment(Pos.CENTER);

                        VBox card = new VBox(8, imageView, nameLabel, cardActions);
                        card.setAlignment(Pos.CENTER);

                        String cardStyle = isValid
                                ? "-fx-background-color: rgba(45, 45, 45, 0.9); -fx-background-radius: 8; -fx-padding: 12; -fx-cursor: hand; -fx-border-color: #555; -fx-border-radius: 8;"
                                : "-fx-background-color: rgba(90, 45, 45, 0.9); -fx-background-radius: 8; -fx-padding: 12; -fx-cursor: hand; -fx-border-color: #a55; -fx-border-radius: 8;";
                        card.setStyle(cardStyle);

                        if (!isValid) {
                            Tooltip.install(card, new Tooltip(invalidReason));
                        }

                        boolean finalIsValid = isValid;
                        card.setOnMouseClicked(_ -> {
                            nameField.setText(name);
                            tilePane.getChildren().forEach(n -> {
                                boolean nValid = n.getProperties().get("isValid") == null || (boolean) n.getProperties().get("isValid");
                                String baseStyle = nValid
                                        ? "-fx-background-color: rgba(45, 45, 45, 0.9); -fx-background-radius: 8; -fx-padding: 12; -fx-cursor: hand; -fx-border-color: #555; -fx-border-radius: 8;"
                                        : "-fx-background-color: rgba(90, 45, 45, 0.9); -fx-background-radius: 8; -fx-padding: 12; -fx-cursor: hand; -fx-border-color: #a55; -fx-border-radius: 8;";
                                n.setStyle(baseStyle);
                            });
                            String selectedStyle = finalIsValid
                                    ? "-fx-background-color: rgba(60, 100, 140, 0.9); -fx-background-radius: 8; -fx-padding: 12; -fx-cursor: hand; -fx-border-color: #88c0d0; -fx-border-radius: 8; -fx-border-width: 2;"
                                    : "-fx-background-color: rgba(110, 50, 50, 0.9); -fx-background-radius: 8; -fx-padding: 12; -fx-cursor: hand; -fx-border-color: #ff8888; -fx-border-radius: 8; -fx-border-width: 2;";
                            card.setStyle(selectedStyle);
                        });
                        card.getProperties().put("isValid", isValid);

                        tilePane.getChildren().add(card);
                    }
                }
            }
        };

        refreshThumbnails.run();

        ScrollPane scrollPane = new ScrollPane(tilePane);
        scrollPane.getStyleClass().add("transparent-scroll-pane");
        scrollPane.setFitToWidth(true);
        scrollPane.vbarPolicyProperty().setValue(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Button saveCurrentBtn = new Button("Save / Overwrite");
        saveCurrentBtn.getStyleClass().add("sim-button");
        saveCurrentBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8 15;");
        saveCurrentBtn.setFocusTraversable(false);
        saveCurrentBtn.setOnAction(_ -> {
            String text = nameField.getText().trim();
            if (!text.isEmpty()) {
                File targetFile = new File("environments", text + ".terrain");
                if (targetFile.exists()) {
                    confirmAndSave.accept(targetFile, text);
                } else {
                    try {
                        environment.saveTerrain(targetFile);
                        refreshThumbnails.run();
                    } catch (Exception ex) {
                        showErrorPopup("Save Failed", "Could not save environment file.", ex.getMessage());
                    }
                }
            }
        });

        HBox saveBox = new HBox(12, nameField, saveCurrentBtn);
        saveBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(nameField, Priority.ALWAYS);

        Button closeBtn = new Button("Close Manager");
        closeBtn.getStyleClass().add("sim-button");
        closeBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8 20;");
        closeBtn.setFocusTraversable(false);
        closeBtn.setOnAction(_ -> gamePane.getChildren().remove(this));

        dialog.getChildren().addAll(title, scrollPane, new Separator(), saveBox, closeBtn);
        getChildren().add(dialog);

        setOnMouseClicked(e -> {
            if (e.getTarget() == this) {
                gamePane.getChildren().remove(this);
            }
        });
    }

    private void showErrorPopup(String titleText, String headerText, String contentText) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titleText);
        alert.setHeaderText(headerText);

        Label contentLabel = new Label(contentText != null ? contentText : "An unexpected error occurred.");
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(380);
        alert.getDialogPane().setContent(contentLabel);
        alert.getDialogPane().setPrefWidth(450);

        alert.showAndWait();
    }
}