package edu.msoe.envsim.utilities.apputils;

import edu.msoe.envsim.Environment;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * The main menu. It is an overlay that gets added on top of the game pane (like {@link EnvironmentManagerDialog}),
 * so the single {@link Environment} and its simulation thread keep living underneath it.
 * <p>
 * It has two pages: the home page (Resume / New Game / Load Game / Quit) and the New Game page,
 * where the starting terrain is chosen.
 * </p>
 */
public class MainMenuPane extends StackPane {
    private static final File TERRAIN_DIRECTORY = new File("environments");
    private static final String TERRAIN_EXTENSION = ".terrain";

    private static final int THUMBNAIL_WIDTH = 200;
    private static final int THUMBNAIL_HEIGHT = 120;

    private static final String CARD_SHAPE = "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 12;";
    private static final String CARD_COLORS_NORMAL = "-fx-background-color: rgba(45, 45, 45, 0.9); -fx-border-color: #555;";
    private static final String CARD_COLORS_SELECTED = "-fx-background-color: rgba(60, 100, 140, 0.9); -fx-border-color: #88c0d0; -fx-border-width: 2;";
    private static final String CARD_COLORS_INCOMPATIBLE = "-fx-background-color: rgba(90, 45, 45, 0.9); -fx-border-color: #a55;";

    private record TerrainCard(File file, String name, boolean compatible, VBox node, Label nameLabel) {
    }

    private final StackPane gamePane;
    private final Environment environment;
    private final Consumer<File> onStartNewGame;

    private final VBox homePage;
    private final VBox newGamePage;
    private final Button resumeButton;
    private final Button startGameButton;
    private final Button setDefaultTerrainButton;
    private final TilePane terrainTilePane = new TilePane();
    private final List<TerrainCard> terrainCards = new ArrayList<>();

    private boolean gameInProgress = false;
    private boolean simulationWasRunning = false;
    private File selectedTerrainFile = null;

    /**
     * @param gamePane       The pane this menu covers while it is open
     * @param environment    The environment to pause while the menu is open
     * @param onStartNewGame Called with the chosen terrain file when the player presses Start
     * @param onQuit         Called when the player presses Quit
     */
    public MainMenuPane(@NotNull StackPane gamePane, @NotNull Environment environment,
                        @NotNull Consumer<File> onStartNewGame, @NotNull Runnable onQuit) {
        this.gamePane = gamePane;
        this.environment = environment;
        this.onStartNewGame = onStartNewGame;

        // Opaque on purpose: before the first game starts the canvas underneath is still blank
        setStyle("-fx-background-color: #151515;");
        setAlignment(Pos.CENTER);
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        // ---- Home page ----
        Label titleLabel = new Label("EnvSim");
        titleLabel.getStyleClass().add("title-label");
        titleLabel.setStyle("-fx-font-size: 40px;");

        Label subtitleLabel = new Label("Evolving agent simulation");
        subtitleLabel.getStyleClass().add("caption-label");

        resumeButton = createMenuButton("Resume", this::resume);
        Button newGameButton = createMenuButton("New Game", this::showNewGamePage);
        Button loadGameButton = createMenuButton("Load Game (coming soon)", () -> {
        });
        loadGameButton.setDisable(true);
        Button quitButton = createMenuButton("Quit", onQuit);
        quitButton.getStyleClass().remove("sim-button");
        quitButton.getStyleClass().add("danger-button");

        homePage = new VBox(14, titleLabel, subtitleLabel, resumeButton, newGameButton, loadGameButton, quitButton);
        homePage.setAlignment(Pos.CENTER);
        homePage.setPadding(new Insets(40, 60, 40, 60));
        homePage.getStyleClass().add("panel");
        homePage.setMaxWidth(USE_PREF_SIZE);
        homePage.setMaxHeight(USE_PREF_SIZE);
        VBox.setMargin(subtitleLabel, new Insets(0, 0, 18, 0));

        // ---- New Game page ----
        Label newGameTitleLabel = new Label("New Game");
        newGameTitleLabel.getStyleClass().add("title-label");
        newGameTitleLabel.setStyle("-fx-font-size: 18px;");

        Label terrainPromptLabel = new Label("Choose a starting terrain");
        terrainPromptLabel.getStyleClass().add("caption-label");

        terrainTilePane.setHgap(20);
        terrainTilePane.setVgap(20);
        terrainTilePane.setPrefColumns(3);
        terrainTilePane.setAlignment(Pos.TOP_CENTER);

        ScrollPane terrainScrollPane = new ScrollPane(terrainTilePane);
        terrainScrollPane.getStyleClass().add("transparent-scroll-pane");
        terrainScrollPane.setFitToWidth(true);
        terrainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(terrainScrollPane, Priority.ALWAYS);

        Button backButton = createSmallButton("Back", this::showHomePage);
        setDefaultTerrainButton = createSmallButton("Set as Default", this::saveSelectedTerrainAsDefault);
        startGameButton = createSmallButton("Start", this::startSelectedTerrain);
        startGameButton.getStyleClass().remove("sim-button");
        startGameButton.getStyleClass().add("selected-button");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttonRow = new HBox(12, backButton, spacer, setDefaultTerrainButton, startGameButton);
        buttonRow.setAlignment(Pos.CENTER);

        newGamePage = new VBox(16, newGameTitleLabel, terrainPromptLabel, terrainScrollPane, buttonRow);
        newGamePage.setPadding(new Insets(25));
        newGamePage.setAlignment(Pos.TOP_CENTER);
        newGamePage.getStyleClass().add("panel");
        newGamePage.setStyle("-fx-background-color: rgba(25, 25, 25, 0.95);");
        newGamePage.prefWidthProperty().bind(widthProperty().multiply(0.65));
        newGamePage.prefHeightProperty().bind(heightProperty().multiply(0.70));
        newGamePage.setMaxWidth(950);
        newGamePage.setMaxHeight(700);

        getChildren().addAll(homePage, newGamePage);
        showHomePage();
    }

    /**
     * Shows the menu on top of the game and pauses the simulation if it was running.
     *
     * @param gameInProgress Whether a game has been started, which decides if the Resume button is offered
     */
    public void open(boolean gameInProgress) {
        if (isShowing()) return;

        this.gameInProgress = gameInProgress;
        simulationWasRunning = environment.isSimulationRunning();
        if (simulationWasRunning) {
            environment.pauseSimulation();
        }

        resumeButton.setVisible(gameInProgress);
        resumeButton.setManaged(gameInProgress);
        showHomePage();

        gamePane.getChildren().add(this);
    }

    public void close() {
        gamePane.getChildren().remove(this);
    }

    public boolean isShowing() {
        return getParent() != null;
    }

    /**
     * Escape goes back from the New Game page, and otherwise resumes the game if there is one.
     */
    public void onEscapePressed() {
        if (newGamePage.isVisible()) {
            showHomePage();
        } else if (gameInProgress) {
            resume();
        }
    }

    private void resume() {
        close();
        // Only restart it if it was running when the menu opened, so edit mode and a manual pause are left alone
        if (simulationWasRunning) {
            environment.startSimulation();
        }
    }

    private void showHomePage() {
        newGamePage.setVisible(false);
        newGamePage.setManaged(false);
        homePage.setVisible(true);
        homePage.setManaged(true);
    }

    private void showNewGamePage() {
        homePage.setVisible(false);
        homePage.setManaged(false);
        newGamePage.setVisible(true);
        newGamePage.setManaged(true);
        refreshTerrainCards();
    }

    private void startSelectedTerrain() {
        if (selectedTerrainFile != null) {
            onStartNewGame.accept(selectedTerrainFile);
        }
    }

    private void saveSelectedTerrainAsDefault() {
        if (selectedTerrainFile == null) return;

        try {
            AppSettings.setDefaultTerrainName(terrainNameOf(selectedTerrainFile));
            updateTerrainCardAppearance();
        } catch (IOException exception) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Save Failed");
            alert.setHeaderText("Could not save the default terrain");
            alert.setContentText(exception.getMessage());
            alert.showAndWait();
        }
    }

    private void refreshTerrainCards() {
        terrainTilePane.getChildren().clear();
        terrainCards.clear();

        File[] terrainFiles = TERRAIN_DIRECTORY.listFiles((_, fileName) -> fileName.endsWith(TERRAIN_EXTENSION));
        if (terrainFiles == null || terrainFiles.length == 0) {
            terrainTilePane.getChildren().add(new Label("No terrain files found in the 'environments' folder."));
            selectedTerrainFile = null;
            updateButtonStates();
            return;
        }
        Arrays.sort(terrainFiles);

        for (File terrainFile : terrainFiles) {
            TerrainCard card = buildTerrainCard(terrainFile);
            terrainCards.add(card);
            terrainTilePane.getChildren().add(card.node());
        }

        chooseInitialSelection();
        updateTerrainCardAppearance();
        updateButtonStates();
    }

    /**
     * Keeps the previous selection if it is still there, otherwise falls back to the saved default,
     * and finally to the first terrain that fits the world.
     */
    private void chooseInitialSelection() {
        for (TerrainCard card : terrainCards) {
            if (card.compatible() && card.file().equals(selectedTerrainFile)) return;
        }

        String defaultTerrainName = AppSettings.getDefaultTerrainName();
        TerrainCard firstCompatibleCard = null;
        for (TerrainCard card : terrainCards) {
            if (!card.compatible()) continue;
            if (firstCompatibleCard == null) firstCompatibleCard = card;
            if (card.name().equals(defaultTerrainName)) {
                selectedTerrainFile = card.file();
                return;
            }
        }
        selectedTerrainFile = (firstCompatibleCard != null) ? firstCompatibleCard.file() : null;
    }

    private @NotNull TerrainCard buildTerrainCard(@NotNull File terrainFile) {
        String terrainName = terrainNameOf(terrainFile);
        String incompatibilityReason = describeIncompatibility(terrainFile);
        boolean compatible = (incompatibilityReason == null);

        WritableImage thumbnail = Environment.loadTerrainThumbnail(terrainFile, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        ImageView thumbnailView = new ImageView(thumbnail);
        thumbnailView.setFitWidth(THUMBNAIL_WIDTH);
        thumbnailView.setFitHeight(THUMBNAIL_HEIGHT);
        thumbnailView.setPreserveRatio(true);

        Label nameLabel = new Label(terrainName);
        nameLabel.getStyleClass().add("title-label");
        nameLabel.setStyle("-fx-font-size: 14px;");

        VBox cardNode = new VBox(8, thumbnailView, nameLabel);
        cardNode.setAlignment(Pos.CENTER);

        if (compatible) {
            cardNode.setOnMouseClicked(_ -> {
                selectedTerrainFile = terrainFile;
                updateTerrainCardAppearance();
                updateButtonStates();
            });
        } else {
            Tooltip.install(cardNode, new Tooltip(incompatibilityReason));
        }

        return new TerrainCard(terrainFile, terrainName, compatible, cardNode, nameLabel);
    }

    private void updateTerrainCardAppearance() {
        String defaultTerrainName = AppSettings.getDefaultTerrainName();
        for (TerrainCard card : terrainCards) {
            boolean isSelected = card.file().equals(selectedTerrainFile);

            String colors;
            if (!card.compatible()) {
                colors = CARD_COLORS_INCOMPATIBLE;
            } else if (isSelected) {
                colors = CARD_COLORS_SELECTED;
            } else {
                colors = CARD_COLORS_NORMAL;
            }
            String cursor = card.compatible() ? "hand" : "default";
            card.node().setStyle(CARD_SHAPE + " -fx-cursor: " + cursor + "; " + colors);

            boolean isDefault = card.name().equals(defaultTerrainName);
            card.nameLabel().setText(isDefault ? card.name() + "  \u2605 default" : card.name());
        }
    }

    private void updateButtonStates() {
        boolean nothingSelected = (selectedTerrainFile == null);
        startGameButton.setDisable(nothingSelected);
        setDefaultTerrainButton.setDisable(nothingSelected);
    }

    /**
     * @return {@code null} if the terrain file fits the current world, otherwise a message saying why it doesn't.
     */
    private @Nullable String describeIncompatibility(@NotNull File terrainFile) {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(terrainFile)))) {
            int fileWidth = input.readInt();
            int fileHeight = input.readInt();
            int requiredWidth = environment.getTerrainWidth();
            int requiredHeight = environment.getTerrainHeight();
            if (fileWidth != requiredWidth || fileHeight != requiredHeight) {
                return "Dimension mismatch: file is " + fileWidth + "x" + fileHeight
                        + ", but the world requires " + requiredWidth + "x" + requiredHeight + ".";
            }
            return null;
        } catch (IOException exception) {
            return "Could not read terrain file: " + exception.getMessage();
        }
    }

    private static @NotNull String terrainNameOf(@NotNull File terrainFile) {
        String fileName = terrainFile.getName();
        return fileName.substring(0, fileName.length() - TERRAIN_EXTENSION.length());
    }

    private static @NotNull Button createMenuButton(@NotNull String text, @NotNull Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("sim-button");
        button.setFocusTraversable(false);
        button.setPrefWidth(260);
        button.setStyle("-fx-font-size: 14px; -fx-padding: 10 20;");
        button.setOnAction(_ -> action.run());
        return button;
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