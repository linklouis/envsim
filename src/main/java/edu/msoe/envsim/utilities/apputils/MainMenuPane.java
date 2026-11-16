package edu.msoe.envsim.utilities.apputils;

import edu.msoe.envsim.Environment;
import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.utilities.apputils.GameSnapshot.Header;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
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
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * The main menu. It is an overlay that gets added on top of the game pane (like {@link EnvironmentManagerDialog}),
 * so the single {@link Environment} and its simulation thread keep living underneath it.
 * <p>
 * Pages: home (Resume / New Game / Save Game / Load Game / Quit), New Game (pick the starting terrain),
 * Save Game (name a save or overwrite one), and Load Game (pick a save).
 * </p>
 */
public class MainMenuPane extends StackPane {
    /**
     * What the menu asks the rest of the app to do.
     */
    public interface Actions {
        /** Replaces the world with a fresh one on {@code terrainFile} and starts it. */
        void startNewGame(@NotNull File terrainFile);

        /**
         * Writes the current game to {@code saveFile}. The returned future completes when the file is written,
         * or fails if it couldn't be.
         */
        @NotNull CompletableFuture<Void> saveGame(@NotNull File saveFile);

        /**
         * Replaces the current game with the one in {@code saveFile} and starts it. The returned future completes
         * once the game is running, or fails (leaving the current game alone) if the file couldn't be loaded.
         */
        @NotNull CompletableFuture<Void> loadGame(@NotNull File saveFile);

        void quit();
    }

    private static final File TERRAIN_DIRECTORY = new File("environments");
    private static final String TERRAIN_EXTENSION = ".terrain";
    private static final File SAVE_DIRECTORY = new File("saves");
    private static final String SAVE_EXTENSION = ".envsave";

    private static final int THUMBNAIL_WIDTH = 200;
    private static final int THUMBNAIL_HEIGHT = 120;
    private static final int MAX_SAVE_NAME_LENGTH = 80;

    private static final String CARD_SHAPE = "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 12;";
    private static final String CARD_COLORS_NORMAL = "-fx-background-color: rgba(45, 45, 45, 0.9); -fx-border-color: #555;";
    private static final String CARD_COLORS_SELECTED = "-fx-background-color: rgba(60, 100, 140, 0.9); -fx-border-color: #88c0d0; -fx-border-width: 2;";
    private static final String CARD_COLORS_INCOMPATIBLE = "-fx-background-color: rgba(90, 45, 45, 0.9); -fx-border-color: #a55;";
    private static final String STATUS_STYLE_NORMAL = "-fx-text-fill: #88c0d0;";
    private static final String STATUS_STYLE_ERROR = "-fx-text-fill: #ff8888;";

    private static final DateTimeFormatter SAVE_DATE_FORMAT =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.US);
    private static final DateTimeFormatter DEFAULT_SAVE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm");

    private record TerrainCard(File file, String name, boolean compatible, VBox node, Label nameLabel) {
    }

    /**
     * @param header  {@code null} if the file couldn't be read at all
     * @param problem {@code null} if the save can be loaded, otherwise why it can't
     */
    private record SaveCard(File file, String name, @Nullable Header header,
                            @Nullable String problem, VBox node) {
        boolean loadable() {
            return problem == null;
        }
    }

    private final StackPane gamePane;
    private final Environment environment;
    private final Actions actions;

    private final List<VBox> pages = new ArrayList<>();
    private final VBox homePage;
    private final VBox newGamePage;
    private final VBox savePage;
    private final VBox loadPage;

    private final Button resumeButton;
    private final Button saveGameButton;

    private final TilePane terrainTilePane = new TilePane();
    private final List<TerrainCard> terrainCards = new ArrayList<>();
    private final Button startGameButton;
    private final Button setDefaultTerrainButton;

    private final TextField saveNameField = new TextField();
    private final SaveList saveList = new SaveList(this::onSavePageCardClicked);
    private final Label saveStatusLabel = new Label();
    private final Button saveBackButton;
    private final Button confirmSaveButton;

    private final SaveList loadList = new SaveList(this::onLoadPageCardClicked);
    private final Label loadStatusLabel = new Label();
    private final Button loadBackButton;
    private final Button confirmLoadButton;

    private boolean gameInProgress = false;
    private boolean simulationWasRunning = false;
    private boolean busy = false;
    private File selectedTerrainFile = null;

    /**
     * @param gamePane    The pane this menu covers while it is open
     * @param environment The environment to pause while the menu is open
     * @param actions     What the menu's buttons do
     */
    public MainMenuPane(@NotNull StackPane gamePane, @NotNull Environment environment, @NotNull Actions actions) {
        this.gamePane = gamePane;
        this.environment = environment;
        this.actions = actions;

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
        saveGameButton = createMenuButton("Save Game", this::showSavePage);
        Button loadGameButton = createMenuButton("Load Game", this::showLoadPage);
        Button quitButton = createMenuButton("Quit", actions::quit);
        quitButton.getStyleClass().remove("sim-button");
        quitButton.getStyleClass().add("danger-button");

        homePage = new VBox(14, titleLabel, subtitleLabel, resumeButton, newGameButton, saveGameButton, loadGameButton, quitButton);
        homePage.setAlignment(Pos.CENTER);
        homePage.setPadding(new Insets(40, 60, 40, 60));
        homePage.getStyleClass().add("panel");
        homePage.setMaxWidth(USE_PREF_SIZE);
        homePage.setMaxHeight(USE_PREF_SIZE);
        VBox.setMargin(subtitleLabel, new Insets(0, 0, 18, 0));

        // ---- New Game page ----
        Label newGameTitleLabel = createPageTitle("New Game");
        Label terrainPromptLabel = createCaption("Choose a starting terrain");

        terrainTilePane.setHgap(20);
        terrainTilePane.setVgap(20);
        terrainTilePane.setPrefColumns(3);
        terrainTilePane.setAlignment(Pos.TOP_CENTER);

        ScrollPane terrainScrollPane = createScrollPane(terrainTilePane);

        Button newGameBackButton = createSmallButton("Back", this::showHomePage);
        setDefaultTerrainButton = createSmallButton("Set as Default", this::saveSelectedTerrainAsDefault);
        startGameButton = createSmallButton("Start", this::startSelectedTerrain);
        markAsPrimary(startGameButton);

        newGamePage = createDialogPage(newGameTitleLabel, terrainPromptLabel, terrainScrollPane,
                createButtonRow(newGameBackButton, setDefaultTerrainButton, startGameButton));

        // ---- Save Game page ----
        Label saveTitleLabel = createPageTitle("Save Game");
        Label savePromptLabel = createCaption("Name your save, or click an existing one to overwrite it");

        saveNameField.setPromptText("Save name");
        saveNameField.setStyle("-fx-control-inner-background: #333; -fx-text-fill: white; -fx-prompt-text-fill: #aaa; -fx-font-size: 14px; -fx-padding: 8;");
        saveNameField.setFocusTraversable(false);

        saveBackButton = createSmallButton("Back", this::showHomePage);
        confirmSaveButton = createSmallButton("Save", this::saveGame);
        markAsPrimary(confirmSaveButton);

        savePage = createDialogPage(saveTitleLabel, savePromptLabel, saveNameField, createScrollPane(saveList.container),
                saveStatusLabel, createButtonRow(saveBackButton, confirmSaveButton));

        // ---- Load Game page ----
        Label loadTitleLabel = createPageTitle("Load Game");
        Label loadPromptLabel = createCaption("Choose a saved game");

        loadBackButton = createSmallButton("Back", this::showHomePage);
        confirmLoadButton = createSmallButton("Load", this::loadSelectedGame);
        markAsPrimary(confirmLoadButton);

        loadPage = createDialogPage(loadTitleLabel, loadPromptLabel, createScrollPane(loadList.container),
                loadStatusLabel, createButtonRow(loadBackButton, confirmLoadButton));

        pages.addAll(List.of(homePage, newGamePage, savePage, loadPage));
        getChildren().addAll(pages);
        showHomePage();
    }

    /**
     * Shows the menu on top of the game and pauses the simulation if it was running.
     *
     * @param gameInProgress Whether a game has been started, which decides if Resume and Save Game are offered
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
        saveGameButton.setVisible(gameInProgress);
        saveGameButton.setManaged(gameInProgress);
        saveStatusLabel.setText("");
        loadStatusLabel.setText("");
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
     * Escape goes back from any sub-page, and otherwise resumes the game if there is one.
     */
    public void onEscapePressed() {
        if (busy) return;

        if (homePage.isVisible()) {
            if (gameInProgress) resume();
        } else {
            showHomePage();
        }
    }

    private void resume() {
        close();
        // Only restart it if it was running when the menu opened, so edit mode and a manual pause are left alone
        if (simulationWasRunning) {
            environment.startSimulation();
        }
    }


    /*
     * Page switching:
     */

    private void showPage(@NotNull VBox pageToShow) {
        for (VBox page : pages) {
            boolean isTarget = (page == pageToShow);
            page.setVisible(isTarget);
            page.setManaged(isTarget);
        }
    }

    private void showHomePage() {
        showPage(homePage);
    }

    private void showNewGamePage() {
        showPage(newGamePage);
        refreshTerrainCards();
    }

    private void showSavePage() {
        showPage(savePage);
        saveStatusLabel.setText("");
        if (saveNameField.getText().isBlank()) {
            saveNameField.setText("save " + LocalDateTime.now().format(DEFAULT_SAVE_NAME_FORMAT));
        }
        saveList.refresh();
    }

    private void showLoadPage() {
        showPage(loadPage);
        loadStatusLabel.setText("");
        loadList.refresh();
        updateLoadButtonState();
    }


    /*
     * New Game page:
     */

    private void startSelectedTerrain() {
        if (selectedTerrainFile != null) {
            actions.startNewGame(selectedTerrainFile);
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
            updateTerrainButtonStates();
            return;
        }
        Arrays.sort(terrainFiles);

        for (File terrainFile : terrainFiles) {
            TerrainCard card = buildTerrainCard(terrainFile);
            terrainCards.add(card);
            terrainTilePane.getChildren().add(card.node());
        }

        chooseInitialTerrainSelection();
        updateTerrainCardAppearance();
        updateTerrainButtonStates();
    }

    /**
     * Keeps the previous selection if it is still there, otherwise falls back to the saved default,
     * and finally to the first terrain that fits the world.
     */
    private void chooseInitialTerrainSelection() {
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
        String incompatibilityReason = describeTerrainIncompatibility(terrainFile);
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
                updateTerrainButtonStates();
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
            card.nameLabel().setText(isDefault ? card.name() + "  ★ default" : card.name());
        }
    }

    private void updateTerrainButtonStates() {
        boolean nothingSelected = (selectedTerrainFile == null);
        startGameButton.setDisable(nothingSelected);
        setDefaultTerrainButton.setDisable(nothingSelected);
    }

    /**
     * @return {@code null} if the terrain file fits the current world, otherwise a message saying why it doesn't.
     */
    private @Nullable String describeTerrainIncompatibility(@NotNull File terrainFile) {
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
        return stripExtension(terrainFile, TERRAIN_EXTENSION);
    }


    /*
     * Save Game page:
     */

    private void onSavePageCardClicked(@NotNull SaveCard card) {
        saveNameField.setText(card.name());
        saveList.select(card.file());
    }

    private void saveGame() {
        if (busy) return;

        String saveName = sanitizeSaveName(saveNameField.getText());
        if (saveName.isEmpty()) {
            showStatus(saveStatusLabel, "Enter a name for the save.", true);
            return;
        }

        File saveFile = new File(SAVE_DIRECTORY, saveName + SAVE_EXTENSION);
        if (saveFile.exists() && !confirmOverwrite(saveName)) return;

        CompletableFuture<Void> saveOperation;
        try {
            saveOperation = actions.saveGame(saveFile);
        } catch (RuntimeException exception) {
            saveOperation = CompletableFuture.failedFuture(exception);
        }

        trackOperation(saveStatusLabel, "Saving...", saveOperation, "Saved '" + saveName + "'", () -> {
            saveNameField.setText(saveName);
            saveList.refresh();
            saveList.select(saveFile);
        });
    }

    private boolean confirmOverwrite(@NotNull String saveName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Overwrite Save");
        alert.setHeaderText("Save Already Exists");

        Label contentLabel = new Label("A save named '" + saveName + "' already exists. Do you want to overwrite it?");
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(380);
        alert.getDialogPane().setContent(contentLabel);
        alert.getDialogPane().setPrefWidth(450);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private static @NotNull String sanitizeSaveName(@NotNull String rawName) {
        String name = rawName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (name.length() > MAX_SAVE_NAME_LENGTH) {
            name = name.substring(0, MAX_SAVE_NAME_LENGTH);
        }
        // Windows doesn't allow names that end in a dot or space
        return name.replaceAll("[. ]+$", "");
    }


    /*
     * Load Game page:
     */

    private void onLoadPageCardClicked(@NotNull SaveCard card) {
        loadList.select(card.file());
        updateLoadButtonState();
    }

    private void updateLoadButtonState() {
        confirmLoadButton.setDisable(busy || loadList.getSelectedFile() == null);
    }

    private void loadSelectedGame() {
        File saveFile = loadList.getSelectedFile();
        if (busy || saveFile == null) return;

        CompletableFuture<Void> loadOperation;
        try {
            loadOperation = actions.loadGame(saveFile);
        } catch (RuntimeException exception) {
            loadOperation = CompletableFuture.failedFuture(exception);
        }

        // On success the app closes the menu itself, so there is nothing more to do here
        trackOperation(loadStatusLabel, "Loading...", loadOperation, "Loaded.", () -> {
        });
    }


    /*
     * Saves list, shared by the Save and Load pages:
     */

    private @NotNull SaveCard buildSaveCard(@NotNull File saveFile) {
        String saveName = stripExtension(saveFile, SAVE_EXTENSION);

        Header header = null;
        String problem;
        String detailText;
        try {
            header = GameSnapshot.readHeader(saveFile);
            problem = describeSaveProblem(header);
            String savedAt = SAVE_DATE_FORMAT.format(Instant.ofEpochMilli(header.savedAtEpochMillis()).atZone(ZoneId.systemDefault()));
            detailText = String.format(Locale.US, "Tick %,d  ·  %,d agents  ·  %,d food  ·  %s",
                    header.tick(), header.agentCount(), header.foodCount(), savedAt);
        } catch (IOException exception) {
            problem = "Unreadable save: " + exception.getMessage();
            detailText = problem;
        }

        Label nameLabel = new Label(saveName);
        nameLabel.getStyleClass().add("title-label");
        nameLabel.setStyle("-fx-font-size: 14px;");

        Label detailLabel = new Label(detailText);
        detailLabel.getStyleClass().add("caption-label");
        detailLabel.setStyle("-fx-font-size: 11px;");
        detailLabel.setWrapText(true);

        VBox cardNode = new VBox(4, nameLabel, detailLabel);
        cardNode.setAlignment(Pos.CENTER_LEFT);
        cardNode.setMaxWidth(Double.MAX_VALUE);
        if (problem != null) {
            Tooltip.install(cardNode, new Tooltip(problem));
        }

        return new SaveCard(saveFile, saveName, header, problem, cardNode);
    }

    /**
     * @return {@code null} if the save fits the running world and build, otherwise a message saying why it doesn't.
     */
    private @Nullable String describeSaveProblem(@NotNull Header header) {
        int worldWidth = (int) environment.getSize().x();
        int worldHeight = (int) environment.getSize().y();
        if (header.worldWidth() != worldWidth || header.worldHeight() != worldHeight) {
            return "World size mismatch: this save is " + header.worldWidth() + "x" + header.worldHeight()
                    + ", but the current world is " + worldWidth + "x" + worldHeight + ".";
        }
        if (header.brainInputCount() != Agent.getExpectedBrainInputCount()) {
            return "This save was made with a different agent brain size.";
        }
        return null;
    }

    /**
     * A scrolling column of {@link SaveCard}s for the files in the saves folder.
     */
    private final class SaveList {
        private final VBox container = new VBox(10);
        private final List<SaveCard> cards = new ArrayList<>();
        private final Consumer<SaveCard> onCardClicked;
        private File selectedFile = null;

        private SaveList(@NotNull Consumer<SaveCard> onCardClicked) {
            this.onCardClicked = onCardClicked;
        }

        @Nullable File getSelectedFile() {
            return selectedFile;
        }

        void select(@Nullable File file) {
            selectedFile = file;
            restyle();
        }

        void refresh() {
            container.getChildren().clear();
            cards.clear();

            File[] saveFiles = SAVE_DIRECTORY.listFiles((_, fileName) -> fileName.endsWith(SAVE_EXTENSION));
            if (saveFiles == null || saveFiles.length == 0) {
                container.getChildren().add(createCaption("No saved games yet."));
                selectedFile = null;
                return;
            }

            List<SaveCard> newestFirst = Arrays.stream(saveFiles)
                    .map(MainMenuPane.this::buildSaveCard)
                    .sorted(Comparator.comparingLong((SaveCard card) ->
                            card.header() == null ? Long.MIN_VALUE : card.header().savedAtEpochMillis()).reversed())
                    .toList();

            boolean selectedStillExists = false;
            for (SaveCard card : newestFirst) {
                card.node().setOnMouseClicked(_ -> {
                    if (!busy) onCardClicked.accept(card);
                });
                cards.add(card);
                container.getChildren().add(card.node());
                if (card.file().equals(selectedFile)) selectedStillExists = true;
            }
            if (!selectedStillExists) selectedFile = null;
            restyle();
        }

        private void restyle() {
            for (SaveCard card : cards) {
                String colors;
                if (!card.loadable()) {
                    colors = CARD_COLORS_INCOMPATIBLE;
                } else if (card.file().equals(selectedFile)) {
                    colors = CARD_COLORS_SELECTED;
                } else {
                    colors = CARD_COLORS_NORMAL;
                }
                card.node().setStyle(CARD_SHAPE + " -fx-cursor: hand; " + colors);
            }
        }
    }


    /*
     * Background operations:
     */

    /**
     * Locks the menu while {@code operation} runs and reports how it went in {@code statusLabel}.
     */
    private void trackOperation(@NotNull Label statusLabel, @NotNull String busyMessage,
                                @NotNull CompletableFuture<Void> operation, @NotNull String successMessage,
                                @NotNull Runnable afterSuccess) {
        setBusy(true);
        showStatus(statusLabel, busyMessage, false);

        operation.whenCompleteAsync((_, error) -> {
            setBusy(false);
            if (error == null) {
                showStatus(statusLabel, successMessage, false);
                afterSuccess.run();
            } else {
                showStatus(statusLabel, describeError(error), true);
            }
        }, Platform::runLater);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        saveNameField.setDisable(busy);
        saveBackButton.setDisable(busy);
        confirmSaveButton.setDisable(busy);
        saveList.container.setDisable(busy);
        loadBackButton.setDisable(busy);
        loadList.container.setDisable(busy);
        updateLoadButtonState();
    }

    private static void showStatus(@NotNull Label statusLabel, @NotNull String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError ? STATUS_STYLE_ERROR : STATUS_STYLE_NORMAL);
        statusLabel.setWrapText(true);
    }

    private static @NotNull String describeError(@NotNull Throwable error) {
        Throwable root = error;
        while ((root instanceof CompletionException || root instanceof UncheckedIOException) && root.getCause() != null) {
            root = root.getCause();
        }
        return (root.getMessage() != null) ? root.getMessage() : root.getClass().getSimpleName();
    }


    /*
     * Building blocks:
     */

    private static @NotNull String stripExtension(@NotNull File file, @NotNull String extension) {
        String fileName = file.getName();
        return fileName.substring(0, fileName.length() - extension.length());
    }

    private @NotNull VBox createDialogPage(@NotNull Node... contents) {
        VBox page = new VBox(16, contents);
        page.setPadding(new Insets(25));
        page.setAlignment(Pos.TOP_CENTER);
        page.getStyleClass().add("panel");
        page.setStyle("-fx-background-color: rgba(25, 25, 25, 0.95);");
        page.prefWidthProperty().bind(widthProperty().multiply(0.65));
        page.prefHeightProperty().bind(heightProperty().multiply(0.70));
        page.setMaxWidth(950);
        page.setMaxHeight(700);
        return page;
    }

    private static @NotNull ScrollPane createScrollPane(@NotNull Node content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.getStyleClass().add("transparent-scroll-pane");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        return scrollPane;
    }

    /**
     * A row with the first button on the left and the rest on the right.
     */
    private static @NotNull HBox createButtonRow(@NotNull Button leftButton, @NotNull Button... rightButtons) {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, leftButton, spacer);
        row.getChildren().addAll(rightButtons);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static @NotNull Label createPageTitle(@NotNull String text) {
        Label label = new Label(text);
        label.getStyleClass().add("title-label");
        label.setStyle("-fx-font-size: 18px;");
        return label;
    }

    private static @NotNull Label createCaption(@NotNull String text) {
        Label label = new Label(text);
        label.getStyleClass().add("caption-label");
        return label;
    }

    private static void markAsPrimary(@NotNull Button button) {
        button.getStyleClass().remove("sim-button");
        button.getStyleClass().add("selected-button");
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