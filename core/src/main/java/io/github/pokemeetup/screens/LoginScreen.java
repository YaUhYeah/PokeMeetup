package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.config.ServerConfigManager;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.screens.otherui.ServerListEntry;
import io.github.pokemeetup.utils.AssetManagerSingleton;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.TextureManager;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoginScreen implements Screen {
    public static final String SERVERS_PREFS = "ServerPrefs";
    public static final float MIN_WIDTH = 300f;
    public static final float MAX_WIDTH = 500f;
    private static final String DEFAULT_SERVER_ICON = "ui/default-server-icon.png";
    private static final int MIN_HEIGHT = 600;
    private static final float LOGIN_TIMEOUT = 15f; // 15 seconds for total login process
    public final Stage stage;
    public final Skin skin;
    public final CreatureCaptureGame game;
    private final Preferences prefs;
    private final AtomicBoolean isProcessingLoginResponse = new AtomicBoolean(false);
    public Array<ServerConnectionConfig> servers; // Adding the servers variable
    public TextField usernameField;
    public TextField passwordField;
    public CheckBox rememberMeBox;
    public Label feedbackLabel;
    public SelectBox<ServerConnectionConfig> serverSelect;
    public ServerConnectionConfig selectedServer;
    private float fadeAlpha = 0f;
    private boolean isTransitioning = false;
    private Screen nextScreen = null;
    private Action fadeAction = null;
    private volatile GameClient gameClient;
    private boolean isDisposed = false;
    private ScrollPane serverListPane;
    private Table serverListTable;
    private Array<ServerListEntry> serverEntries;
    private Table mainTable;
    private TextButton loginButton;
    private TextButton registerButton;
    private TextButton backButton;
    private ProgressBar connectionProgress;
    private Label statusLabel;
    // State management
    private float connectionTimer;
    private boolean isConnecting = false;
    private float connectionTimeout = 10f;
    private int connectionAttempts = 0;
    private Label selectedServerInfoLabel;
    private ScrollPane serverListScrollPane;


    public LoginScreen(CreatureCaptureGame game) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
        this.skin = new Skin(Gdx.files.internal("atlas/ui-gfx-atlas.json"));
        this.prefs = Gdx.app.getPreferences("LoginPrefs");
        loadServers();

        // Create all UI components
        createUIComponents();

        // Setup listeners after components are created
        setupListeners();

        // Initialize UI layout
        initializeUI();

        // Load saved credentials
        loadSavedCredentials();

        Gdx.input.setInputProcessor(stage);
    }

    private void handleLoginButtonPressed(ServerConnectionConfig serverConfig) {
        String username = usernameField.getText();
        String password = passwordField.getText();


        // Instantiate GameClient
        gameClient = new GameClient(serverConfig, false, serverConfig.getServerIP(), serverConfig.getTcpPort(), serverConfig.getUdpPort(), null);

        // Set the pending credentials
        gameClient.setPendingCredentials(username, password);

        // Set login response listener
        gameClient.setLoginResponseListener(response -> {
            if (response.success) {
                // Login successful
                Gdx.app.postRunnable(() -> {
                    // Create GameScreen and switch to it
                    GameScreen gameScreen = new GameScreen(game, username, gameClient, response.worldName);
                    game.setScreen(gameScreen);
                    // Dispose of the login screen resources if needed
                    dispose();
                });
            } else {
                // Login failed
                Gdx.app.postRunnable(() -> {
                    showError(response.message);
                });
            }
        });

        // Connect to the server
        gameClient.connect();
    }

    private void createUIComponents() {
        // Create main container
        mainTable = new Table();
        mainTable.setFillParent(true);

        // Create buttons first
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = skin.getDrawable("button");
        buttonStyle.down = skin.getDrawable("button-pressed");
        buttonStyle.over = skin.getDrawable("button-over");
        buttonStyle.font = skin.getFont("default-font");

        loginButton = new TextButton("Login", buttonStyle);
        registerButton = new TextButton("Register", buttonStyle);
        backButton = new TextButton("Back", buttonStyle);

        // Create custom text field style
        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle(skin.get(TextField.TextFieldStyle.class));
        textFieldStyle.font = skin.getFont("default-font");
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = new TextureRegionDrawable(TextureManager.ui.findRegion("textfield"));
        textFieldStyle.cursor = skin.getDrawable("cursor");
        textFieldStyle.selection = skin.getDrawable("selection");
        textFieldStyle.messageFontColor = new Color(0.7f, 0.7f, 0.7f, 1f);

        // Create input fields
        usernameField = new TextField("", textFieldStyle);
        usernameField.setMessageText("Enter username");

        passwordField = new TextField("", textFieldStyle);
        passwordField.setMessageText("Enter password");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('*');

        // Create checkbox
        rememberMeBox = new CheckBox(" Remember Me", skin);

        // Create labels
        feedbackLabel = new Label("", skin);
        feedbackLabel.setWrap(true);

        statusLabel = new Label("", skin);
        statusLabel.setWrap(true);

        // Create progress bar
        ProgressBar.ProgressBarStyle progressStyle = new ProgressBar.ProgressBarStyle();
        progressStyle.background = skin.getDrawable("progress-bar-bg");
        progressStyle.knob = skin.getDrawable("progress-bar-knob");
        progressStyle.knobBefore = skin.getDrawable("progress-bar-bg");

        connectionProgress = new ProgressBar(0, 1, 0.01f, false, progressStyle);
        connectionProgress.setVisible(false);

        // Create server list
        serverListScrollPane = createServerList();
    }

    private ScrollPane createServerList() {
        serverListTable = new Table();
        serverListTable.top();

        // Add servers
        for (ServerConnectionConfig server : servers) {
            Table serverEntry = createServerEntry(server);
            serverListTable.add(serverEntry).expandX().fillX().padBottom(2).row();
        }

        // Create scroll pane with styling
        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        scrollStyle.background = new TextureRegionDrawable(TextureManager.ui.findRegion("textfield"));
        scrollStyle.vScroll = skin.getDrawable("scrollbar-v");
        scrollStyle.vScrollKnob = skin.getDrawable("scrollbar-knob-v");

        ScrollPane scrollPane = new ScrollPane(serverListTable, scrollStyle);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setOverscroll(false, false);

        return scrollPane;
    }

    private Table createServerEntry(final ServerConnectionConfig server) {
        Table entry = new Table();
        entry.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("info-box-bg")));
        entry.pad(10);

        // Server icon with error handling
        Table iconContainer = new Table();
        try {
            if (server.getIconPath() != null && !server.getIconPath().isEmpty()) {
                FileHandle iconFile = Gdx.files.internal(server.getIconPath());
                if (iconFile.exists()) {
                    Image icon = new Image(new TextureRegionDrawable(new TextureRegion(new Texture(iconFile))));
                    iconContainer.add(icon).size(32, 32);
                } else {
                    // Use default icon
                    Image defaultIcon = new Image(TextureManager.ui.findRegion("default-server-icon"));
                    iconContainer.add(defaultIcon).size(32, 32);
                }
            } else {
                // Use default icon
                Image defaultIcon = new Image(TextureManager.ui.findRegion("default-server-icon"));
                iconContainer.add(defaultIcon).size(32, 32);
            }
        } catch (Exception e) {
            GameLogger.error("Failed to load server icon: " + e.getMessage());
            // Use default icon
            Image defaultIcon = new Image(TextureManager.ui.findRegion("default-server-icon"));
            iconContainer.add(defaultIcon).size(32, 32);
        }

        // Server info
        Table infoTable = new Table();
        infoTable.left();

        Label nameLabel = new Label(server.getServerName(), skin);
        nameLabel.setFontScale(1.1f);

        Label motdLabel = new Label(server.getMotd() != null ? server.getMotd() : "Welcome!", skin);
        motdLabel.setColor(0.8f, 0.8f, 0.8f, 1f);

        Label addressLabel = new Label(server.getServerIP() + ":" + server.getTcpPort(), skin);
        addressLabel.setColor(0.7f, 0.7f, 0.7f, 1f);

        infoTable.add(nameLabel).left().row();
        infoTable.add(motdLabel).left().padTop(2).row();
        infoTable.add(addressLabel).left().padTop(2);

        entry.add(iconContainer).padRight(10);
        entry.add(infoTable).expandX().fillX().left();

        // Selection handling
        entry.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedServer = server;
                updateServerSelection(entry);
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (selectedServer != server) {
                    entry.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("textfield-active")));
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (selectedServer != server) {
                    entry.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("textfield")));
                }
            }
        });

        return entry;
    }

    private void updateServerSelection(Table selectedEntry) {
        // Update visual selection for all entries
        for (Cell<?> cell : serverListTable.getCells()) {
            Actor actor = cell.getActor();
            if (actor instanceof Table) {
                Table entry = (Table) actor;
                TextureRegionDrawable background;
                if (entry == selectedEntry) {
                    background = new TextureRegionDrawable(TextureManager.ui.findRegion("textfield-active"));
                    background.setMinWidth(entry.getWidth());
                    background.setMinHeight(entry.getHeight());
                    entry.setBackground(background);
                } else {
                    background = new TextureRegionDrawable(TextureManager.ui.findRegion("textfield"));
                    background.setMinWidth(entry.getWidth());
                    background.setMinHeight(entry.getHeight());
                    entry.setBackground(background);
                }
            }
        }
    }

    private void initializeUI() {
        float screenWidth = Gdx.graphics.getWidth();
        float contentWidth = Math.min(500, screenWidth * 0.9f);

        // Create dark panel container
        Table darkPanel = new Table();
        darkPanel.setBackground(new TextureRegionDrawable(TextureManager.ui.findRegion("window")));
        darkPanel.pad(20);

        // Title
        Label titleLabel = new Label("PokeMeetup", skin);
        titleLabel.setFontScale(2f);
        darkPanel.add(titleLabel).padBottom(30).row();

        // Server selection section
        Label serverLabel = new Label("Select Server", skin);
        serverLabel.setFontScale(1.2f);
        darkPanel.add(serverLabel).left().padBottom(10).row();

        // Server list container
        darkPanel.add(serverListScrollPane)
            .width(contentWidth - 40)
            .height(180)
            .padBottom(20)
            .row();

        // Login fields
        Table fieldsTable = new Table();
        fieldsTable.defaults().width(contentWidth - 40).padBottom(10);

        // Username field with label
        Table usernameRow = new Table();
        Label usernameLabel = new Label("Username:", skin);
        usernameRow.add(usernameLabel).width(80).right().padRight(10);
        usernameRow.add(usernameField).expandX().fillX().height(36);
        fieldsTable.add(usernameRow).row();

        // Password field with label
        Table passwordRow = new Table();
        Label passwordLabel = new Label("Password:", skin);
        passwordRow.add(passwordLabel).width(80).right().padRight(10);
        passwordRow.add(passwordField).expandX().fillX().height(36);
        fieldsTable.add(passwordRow).row();

        // Remember me checkbox
        rememberMeBox = new CheckBox(" Remember Me", skin);
        fieldsTable.add(rememberMeBox).left().padTop(5).row();

        darkPanel.add(fieldsTable).row();

        // Buttons
        Table buttonTable = new Table();
        float buttonWidth = (contentWidth - 60) / 2;

        // Login and Register buttons
        Table actionButtons = new Table();
        actionButtons.add(loginButton).width(buttonWidth).padRight(10);
        actionButtons.add(registerButton).width(buttonWidth);
        buttonTable.add(actionButtons).padBottom(10).row();

        // Back button
        buttonTable.add(backButton).width(buttonWidth);

        darkPanel.add(buttonTable).padTop(20).padBottom(10).row();

        // Status elements
        Table statusTable = new Table();
        statusTable.add(statusLabel).width(contentWidth - 40).padBottom(5).row();
        statusTable.add(connectionProgress).width(contentWidth - 40).height(4).padBottom(5).row();
        statusTable.add(feedbackLabel).width(contentWidth - 40);

        darkPanel.add(statusTable).row();

        // Add to main table
        mainTable.add(darkPanel);
        // Select first server by default if available

        stage.addActor(mainTable);
        if (servers.isEmpty()) {
            return;
        }
        if (servers != null && servers.size > 0) {
            selectedServer = servers.first();
            updateServerSelection((Table) serverListTable.getCells().first().getActor());
        }
        // Add to stage
    }

    private TextButton createStyledButton(String text) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.up = skin.getDrawable("button");
        style.down = skin.getDrawable("button-pressed");
        style.over = skin.getDrawable("button-over");
        style.font = skin.getFont("default-font");

        TextButton button = new TextButton(text, style);
        button.getLabel().setWrap(true);
        return button;
    }

    private TextField createStyledTextField(String placeholder) {
        TextField.TextFieldStyle style = new TextField.TextFieldStyle(skin.get(TextField.TextFieldStyle.class));
        style.font = skin.getFont("default-font");
        style.fontColor = Color.WHITE;
        style.background = new TextureRegionDrawable(TextureManager.ui.findRegion("textfield"));

        TextField field = new TextField("", style);
        field.setMessageText(placeholder);
        return field;
    }

    private void attemptLogin() {
        if (isConnecting) {
            return;
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (!validateInput(username, password)) {
            return;
        }

        if (selectedServer == null) {
            showError("Please select a server");
            return;
        }

        isConnecting = true;
        setUIEnabled(false);
        statusLabel.setText("Connecting to server...");
        statusLabel.setColor(Color.WHITE);
        connectionProgress.setVisible(true);
        feedbackLabel.setText("");

        try {
            if (gameClient != null) {
                gameClient.dispose();
                // Do not set gameClient to null here
            }
            GameClientSingleton.resetInstance();

            // Create new client
            gameClient = new GameClient(
                selectedServer,
                false,
                selectedServer.getServerIP(),
                selectedServer.getTcpPort(),
                selectedServer.getUdpPort(),
                null
            );

            if (gameClient == null) {
                throw new Exception("Failed to initialize GameClient.");
            }

            // Set the pending credentials
            gameClient.setPendingCredentials(username, password);

            // **Capture gameClient in a final local variable**
            final GameClient localGameClient = gameClient;

            // Set login response listener
            localGameClient.setLoginResponseListener(response -> {
                Gdx.app.postRunnable(() -> {
                    try {
                        if (response.success) {
                            handleSuccessfulLogin(response, username, localGameClient);
                        } else {
                            handleLoginFailure(response.message);
                        }
                    } catch (Exception e) {
                        GameLogger.error("Error in login response handler: " + e.getMessage());
                        handleLoginFailure("Internal error: " + e.getMessage());
                    } finally {
                        isProcessingLoginResponse.set(false);
                        localGameClient.disposeIfNeeded();
                    }
                });
            });

            // Connect to the server
            localGameClient.connect();

            GameLogger.info("Connection attempt started for: " + username);

        } catch (Exception e) {
            GameLogger.error("Failed to start login: " + e.getMessage());
            handleLoginFailure("Connection failed: " + e.getMessage());
        }
    }

    private void handleSuccessfulLogin(NetworkProtocol.LoginResponse response, String username, GameClient localGameClient) {
        if (localGameClient == null) {
            handleLoginFailure("Internal error: game client is null");
            return;
        }

        isConnecting = false;
        connectionProgress.setVisible(false);

        // Save credentials if checked
        if (rememberMeBox.isChecked()) {
            saveCredentials(usernameField.getText(), passwordField.getText(), true);
        }

        // Create loading screen FIRST
        LoadingScreen loadingScreen = new LoadingScreen(game, null); // Pass null initially

        // Switch to loading screen before initializing
        game.setScreen(loadingScreen);

        // Now let GameClient handle initialization with a completion callback
        localGameClient.setInitializationListener(success -> {
            if (success) {
                Gdx.app.postRunnable(() -> {
                    try {
                        GameScreen gameScreen = new GameScreen(
                            game,
                            response.username,
                            localGameClient,
                            response.worldName
                        );

                        // Update loading screen's target
                        loadingScreen.setNextScreen(gameScreen);

                        // Now dispose login screen
                        dispose();

                    } catch (Exception e) {
                        GameLogger.error("Failed to create game screen: " + e.getMessage());
                        handleLoginFailure("Failed to initialize game: " + e.getMessage());
                    }
                });
            } else {
                GameLogger.error("Game initialization failed");
                handleLoginFailure("Failed to initialize game");
            }
        });
    }


    private void handleLoginFailure(String message) {
        Gdx.app.postRunnable(() -> {

            isConnecting = false;
            setUIEnabled(true);
            connectionProgress.setVisible(false);
            showError(message);
        });
    }

    private void handleConnectionError(Exception e) {
        Gdx.app.postRunnable(() -> {
            showError("Connection failed: " + e.getMessage());
            setUIEnabled(true);
            isConnecting = false;
            connectionProgress.setVisible(false);


        });
    }

    private void startConnection(String username, String password, ServerConnectionConfig server) {
        isConnecting = true;
        connectionTimer = 0;
        setUIEnabled(false);

        // Update UI feedback
        statusLabel.setText("Connecting to server...");
        statusLabel.setColor(Color.WHITE);
        connectionProgress.setVisible(true);
        connectionProgress.setValue(0);
        feedbackLabel.setText("");

        try {
            GameLogger.info("Starting connection to: " + server.getServerIP() + ":" +
                server.getTcpPort() + "/" + server.getUdpPort());

            // Reset existing client
            if (gameClient != null) {
                gameClient.dispose();
            }
            GameClientSingleton.resetInstance();

            // Create new client instance
            gameClient = GameClientSingleton.getInstance(server);
            if (gameClient == null) {
                GameLogger.error("Failed to initialize GameClient.");
                handleConnectionError(new Exception("Failed to initialize GameClient."));
                return;
            }

            // Set up login response listener BEFORE connecting
            gameClient.setLoginResponseListener(this::handleLoginResponse);

            // Set up initialization listener
            gameClient.setInitializationListener(success -> {
                if (success) {
                    GameLogger.info("Game client initialization successful");

                    // CRITICAL: Create and switch to game screen immediately
                    Gdx.app.postRunnable(() -> {
                        try {
                            GameScreen gameScreen = new GameScreen(
                                game,
                                username,
                                gameClient,
                                "multiplayer_world"
                            );
                            game.setScreen(gameScreen);
                            dispose(); // Clean up login screen
                        } catch (Exception e) {
                            GameLogger.error("Failed to create game screen: " + e.getMessage());
                            showError("Failed to start game: " + e.getMessage());
                            setUIEnabled(true);
                            isConnecting = false;
                        }
                    });
                } else {
                    GameLogger.error("Game client initialization failed");
                    showError("Failed to initialize game");
                    setUIEnabled(true);
                    isConnecting = false;
                }
            });

            // Set credentials and connect
            gameClient.setPendingCredentials(username, password);
            gameClient.connect();

            GameLogger.info("Connection attempt started for user: " + username);

        } catch (Exception e) {
            GameLogger.error("Connection error: " + e.getMessage());
            handleConnectionError(e);
        }
    }


    private void updateConnectionStatus(String status, boolean showProgress) {
        statusLabel.setText(status);
        connectionProgress.setVisible(showProgress);
        if (!showProgress) {
            connectionProgress.setValue(0);
        }
    }

    private void setUIEnabled(boolean enabled) {
        float alpha = enabled ? 1f : 0.6f;

        // Disable/enable input fields
        usernameField.setDisabled(!enabled);
        passwordField.setDisabled(!enabled);

        // Disable/enable checkbox
        rememberMeBox.setDisabled(!enabled);

        // Disable/enable buttons
        loginButton.setDisabled(!enabled);
        registerButton.setDisabled(!enabled);
        backButton.setDisabled(!enabled);

        // Disable/enable server list entries
        if (serverListTable != null) {
            for (Cell<?> cell : serverListTable.getCells()) {
                Actor actor = cell.getActor();
                if (actor instanceof Table) {
                    actor.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
                    actor.setColor(1, 1, 1, alpha);
                }
            }
        }

        // Update visual feedback
        usernameField.setColor(1, 1, 1, alpha);
        passwordField.setColor(1, 1, 1, alpha);
        loginButton.setColor(1, 1, 1, alpha);
        registerButton.setColor(1, 1, 1, alpha);
        backButton.setColor(1, 1, 1, alpha);
        rememberMeBox.setColor(1, 1, 1, alpha);
    }

    private void setupListeners() {
        if (loginButton != null) {
            loginButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (!isConnecting) {
                        attemptLogin();
                    } else {
                        GameLogger.info("Login already in progress");
                    }
                }
            });

        }

        if (registerButton != null) {
            registerButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (!isConnecting) {
                        attemptRegistration();
                    }
                }
            });
        }

        if (backButton != null) {
            backButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (!isConnecting) {
                        game.setScreen(new ModeSelectionScreen(game));
                    }
                }
            });
        }

        // Add enter key listener
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER && !isConnecting) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void dispose() {
        isDisposed = true;
        stage.dispose();
    }

    @Override
    public void render(float delta) {
        // Update connection timeout if connecting
        if (isConnecting) {
            connectionTimer += delta;
            connectionProgress.setValue(connectionTimer / LOGIN_TIMEOUT);

            if (connectionTimer >= LOGIN_TIMEOUT) {
                handleConnectionTimeout();
                return;
            }
        }

        // Clear screen
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Update stage
        stage.act(delta);
        stage.draw();

        // Update client if exists
        if (gameClient != null) {
            gameClient.tick(delta);
        }
    }

    @Override
    public void resize(int width, int height) {
        // Enforce minimum dimensions
        width = (int) Math.max(width, MIN_WIDTH);
        height = Math.max(height, MIN_HEIGHT);

        GameLogger.info("Resizing login screen to: " + width + "x" + height);

        stage.getViewport().update(width, height, true);

        // Recalculate UI dimensions
        float contentWidth = Math.min(MAX_WIDTH, width * 0.9f);

        // Update main container size
        if (mainTable != null) {
            mainTable.setWidth(contentWidth);
            mainTable.invalidateHierarchy();
        }

        // Update server list if it exists
        if (serverListScrollPane != null) {
            serverListScrollPane.setWidth(contentWidth - 40);
            serverListScrollPane.invalidateHierarchy();
        }

    }

    private void updateStatusLabel(String status, Color color) {
        statusLabel.setText(status);
        statusLabel.setColor(color);
    }

    private void showError(String error) {
        feedbackLabel.setColor(Color.RED);
        feedbackLabel.setText(error);
        GameLogger.error(error);

        // Vibrate screen effect for feedback
        stage.addAction(Actions.sequence(
            Actions.moveBy(5f, 0f, 0.05f),
            Actions.moveBy(-10f, 0f, 0.05f),
            Actions.moveBy(5f, 0f, 0.05f)
        ));
    }

    private void handleConnectionTimeout() {
        GameLogger.error("Connection timed out after " + LOGIN_TIMEOUT + " seconds");
        isConnecting = false;
        connectionTimer = 0;
        connectionProgress.setVisible(false);

        if (gameClient != null) {
            gameClient.dispose();
            gameClient = null;
        }

        setUIEnabled(true);
        showError("Connection timed out. Please check your server settings and try again.");

        // Show retry dialog
        Dialog dialog = new Dialog("Connection Failed", skin) {
            @Override
            protected void result(Object obj) {
                if ((Boolean) obj) {
                    // Retry with same credentials
                    String username = usernameField.getText().trim();
                    String password = passwordField.getText().trim();
                    startConnection(username, password, selectedServer);
                }
            }
        };
        dialog.text("Would you like to try connecting again?");
        dialog.button("Retry", true);
        dialog.button("Cancel", false);
        dialog.show(stage);
    }


    private void saveServerConfiguration(ServerConnectionConfig config) {
        try {
            Preferences serverPrefs = Gdx.app.getPreferences(SERVERS_PREFS);
            Json json = new Json();

            // Create proper JSON object with all required fields
            String serverJson = json.toJson(new ServerEntry(
                config.getServerName(),
                config.getServerIP(),
                config.getTcpPort(),
                config.getUdpPort(),
                config.getMotd(),
                config.isDefault(),
                config.getMaxPlayers(),
                config.getIconPath() != null ? config.getIconPath() : DEFAULT_SERVER_ICON
            ));

            // Update or add new server
            boolean updated = false;
            Array<String> savedServers = new Array<>();
            String existingServers = serverPrefs.getString("servers", "");

            if (!existingServers.isEmpty()) {
                for (String existing : existingServers.split("\\|")) {
                    try {
                        ServerEntry entry = json.fromJson(ServerEntry.class, existing);
                        if (entry.ip.equals(config.getServerIP()) && entry.tcpPort == config.getTcpPort()) {
                            savedServers.add(serverJson); // Replace with updated config
                            updated = true;
                        } else {
                            savedServers.add(existing); // Keep existing entry
                        }
                    } catch (Exception e) {
                        GameLogger.error("Error parsing existing server: " + e.getMessage());
                    }
                }
            }

            if (!updated) {
                savedServers.add(serverJson); // Add as new entry
            }

            // Save back to preferences
            serverPrefs.putString("servers", String.join("|", savedServers));
            serverPrefs.putString("lastServer", serverJson);
            serverPrefs.flush();

            GameLogger.info("Saved server configuration: " + config.getServerName());

            // Refresh the server list UI
            refreshServerList(serverSelect
            );
        } catch (Exception e) {
            GameLogger.error("Error saving server configuration: " + e.getMessage());
        }
    }

    private void setupServerList() {
        serverListTable = new Table();
        serverListTable.top();
        serverEntries = new Array<>();

        // Load saved servers
        Array<ServerConnectionConfig> configs = loadServers();

        // Create server list entries
        for (ServerConnectionConfig config : configs) {
            ServerListEntry entry = new ServerListEntry(config, skin);
            entry.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedServer = config;
                    updateSelectedServerInfo();
                }
            });
            serverEntries.add(entry);
            serverListTable.add(entry).expandX().fillX().pad(5).row();
        }

        // Create ScrollPane
        serverListPane = new ScrollPane(serverListTable, skin);
        serverListPane.setScrollingDisabled(true, false);
        serverListPane.setFadeScrollBars(false);
        serverListPane.setOverscroll(false, false);

        // Add to main table
        mainTable.add(serverListPane).width(500).height(300).pad(20).row();
    }

    public Array<ServerConnectionConfig> loadServers() {
        try {
            if (servers == null) {
                servers = new Array<>();
            }
            servers.clear();

            // Always add default server first
            ServerConnectionConfig defaultServer = ServerConnectionConfig.getInstance();
            defaultServer.setIconPath(DEFAULT_SERVER_ICON);
            servers.add(defaultServer);

            // Load saved servers from preferences
            Preferences serverPrefs = Gdx.app.getPreferences(SERVERS_PREFS);
            String savedServers = serverPrefs.getString("servers", "");

            if (!savedServers.isEmpty()) {
                Json json = new Json();
                for (String serverString : savedServers.split("\\|")) {
                    try {
                        if (!serverString.trim().isEmpty()) {
                            ServerEntry entry = json.fromJson(ServerEntry.class, serverString);
                            if (entry != null && !isDefaultServer(entry)) {
                                ServerConnectionConfig config = new ServerConnectionConfig(
                                    entry.ip,
                                    entry.tcpPort,
                                    entry.udpPort,
                                    entry.name,
                                    entry.isDefault,
                                    entry.maxPlayers
                                );
                                config.setMotd(entry.motd);
                                config.setIconPath(entry.iconPath != null ? entry.iconPath : DEFAULT_SERVER_ICON);
                                servers.add(config);
                                GameLogger.info("Loaded server: " + config.getServerName());
                            }
                        }
                    } catch (Exception e) {
                        GameLogger.error("Error loading saved server: " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            GameLogger.error("Error loading servers: " + e.getMessage());
            // Ensure we at least have the default server
            if (servers == null || servers.isEmpty()) {
                servers = new Array<>();
                ServerConnectionConfig defaultServer = ServerConnectionConfig.getInstance();
                defaultServer.setIconPath(DEFAULT_SERVER_ICON);
                servers.add(defaultServer);
            }
        }
        return servers;
    }

    private boolean isDefaultServer(ServerEntry entry) {
        return entry.isDefault && "localhost".equals(entry.ip) && entry.tcpPort == 54555;
    }

    private void saveCredentials(String username, String password, boolean remember) {
        GameLogger.info("Saving credentials for: " + username + ", remember: " + remember);
        prefs.putBoolean("rememberMe", remember);
        if (remember) {
            prefs.putString("username", username);
            prefs.putString("password", password);
            GameLogger.info("Credentials saved to preferences");
        } else {
            prefs.remove("username");
            prefs.remove("password");
            GameLogger.info("Credentials removed from preferences");
        }
        prefs.flush();
    }

    private void loadSavedCredentials() {
        GameLogger.info("Loading saved credentials");
        boolean rememberMe = prefs.getBoolean("rememberMe", false);

        if (rememberMe) {
            String savedUsername = prefs.getString("username", "");
            String savedPassword = prefs.getString("password", "");

            GameLogger.info("Found saved credentials for: " + savedUsername +
                " (Has password: " + !savedPassword.isEmpty() + ")");

            usernameField.setText(savedUsername);
            passwordField.setText(savedPassword);
            rememberMeBox.setChecked(true);
        } else {
            GameLogger.info("No saved credentials found");
        }
    }

    private void debugLogin(String username, String password) {
        try {
            ServerConnectionConfig config = serverSelect.getItems().first();
            gameClient = GameClientSingleton.getInstance(config);
            gameClient.setLocalUsername(username);
            gameClient.connectToServer(config);
            gameClient.setLoginResponseListener(response -> {
                Gdx.app.postRunnable(() -> {
                    if (response.success) {
                        game.setMultiplayerMode(true);
                        try {
                            game.initializeWorld(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME, true);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        fadeToScreen(new GameScreen(game, username, gameClient,
                            CreatureCaptureGame.MULTIPLAYER_WORLD_NAME));

                    } else {
                        feedbackLabel.setColor(Color.RED);
                        feedbackLabel.setText("Debug login failed: " + response.message);
                    }
                });
            });

            gameClient.sendLoginRequest(username, password);

        } catch (Exception e) {
            feedbackLabel.setColor(Color.RED);
            feedbackLabel.setText("Debug login error: " + e.getMessage());
        }
    }


    void updateSelectedServerInfo() {
        if (selectedServer == null) {
            selectedServerInfoLabel.setText("No server selected");
            return;
        }

        StringBuilder info = new StringBuilder();
        info.append("Server: ").append(selectedServer.getServerName()).append("\n");
        info.append("Address: ").append(selectedServer.getServerIP()).append(":").append(selectedServer.getTcpPort()).append("\n");
        info.append("Status: Checking...");

        selectedServerInfoLabel.setText(info.toString());

        // Start a background thread to check server status
        new Thread(() -> {
            Client tempClient = null;
            try {
                tempClient = new Client(16384, 2048);
                NetworkProtocol.registerClasses(tempClient.getKryo());
                tempClient.start();

                final long startTime = System.currentTimeMillis();
                final Client finalClient = tempClient;

                tempClient.addListener(new Listener() {
                    @Override
                    public void received(Connection connection, Object object) {
                        if (object instanceof NetworkProtocol.ServerInfoResponse) {
                            NetworkProtocol.ServerInfoResponse response =
                                (NetworkProtocol.ServerInfoResponse) object;
                            long ping = System.currentTimeMillis() - startTime;

                            Gdx.app.postRunnable(() -> {
                                updateServerInfoLabel(response.serverInfo, ping);
                            });

                            closeClientSafely(finalClient);
                        }
                    }
                });

                tempClient.connect(5000, selectedServer.getServerIP(),
                    selectedServer.getTcpPort(), selectedServer.getUdpPort());

                NetworkProtocol.ServerInfoRequest request = new NetworkProtocol.ServerInfoRequest();
                request.timestamp = System.currentTimeMillis();
                tempClient.sendTCP(request);

            } catch (Exception e) {
                final Client finalClient = tempClient;
                Gdx.app.postRunnable(() -> {
                    updateServerInfoError(e.getMessage());
                    closeClientSafely(finalClient);
                });
            }
        }).start();
    }

    private void updateServerInfoLabel(NetworkProtocol.ServerInfo info, long ping) {
        StringBuilder infoText = new StringBuilder();
        infoText.append("Server: ").append(selectedServer.getServerName()).append("\n");
        infoText.append("Status: Online (").append(ping).append("ms)\n");
        infoText.append("Players: ").append(info.playerCount).append("/").append(info.maxPlayers).append("\n");
        if (info.motd != null && !info.motd.isEmpty()) {
            infoText.append("MOTD: ").append(info.motd);
        }
        selectedServerInfoLabel.setText(infoText.toString());
    }

    private void updateServerInfoError(String error) {
        StringBuilder infoText = new StringBuilder();
        infoText.append("Server: ").append(selectedServer.getServerName()).append("\n");
        infoText.append("Status: Offline\n");
        infoText.append("Error: ").append(error);
        selectedServerInfoLabel.setText(infoText.toString());
    }

    private void closeClientSafely(Client client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                GameLogger.error("Error closing temporary client: " + e.getMessage());
            }
        }
    }


    private void refreshServerList(SelectBox<ServerConnectionConfig> serverSelectBox) {
        if (serverSelectBox != null) {
            serverSelectBox.setItems(servers);
        }
    }

    private void handleLoginResponse(NetworkProtocol.LoginResponse response) {
        if (isDisposed) {
            GameLogger.error("Login screen already disposed, ignoring login response");
            return;
        }

        if (gameClient == null) {
            GameLogger.error("GameClient is null in handleLoginResponse!");
            handleLoginFailure("Internal error occurred");
            return;
        }

        Gdx.app.postRunnable(() -> {
            try {
                if (response.success) {
                    // Prevent multiple transitions
                    isConnecting = false;
                    connectionProgress.setVisible(false);

                    // Save credentials if checked
                    if (rememberMeBox.isChecked()) {
                        saveCredentials(usernameField.getText(), passwordField.getText(), true);
                    }

                    // Create loading screen FIRST
                    LoadingScreen loadingScreen = new LoadingScreen(game, null); // Pass null initially

                    // Important: Switch to loading screen before initializing
                    game.setScreen(loadingScreen);

                    // Now let GameClient handle initialization with a completion callback
                    gameClient.setInitializationListener(success -> {
                        if (success) {
                            Gdx.app.postRunnable(() -> {
                                try {
                                    GameScreen gameScreen = new GameScreen(
                                        game,
                                        response.username,
                                        gameClient,
                                        response.worldName
                                    );

                                    // Update loading screen's target
                                    loadingScreen.setNextScreen(gameScreen);

                                    // Now dispose login screen
                                    dispose();

                                } catch (Exception e) {
                                    GameLogger.error("Failed to create game screen: " + e.getMessage());
                                    handleLoginFailure("Failed to initialize game: " + e.getMessage());
                                }
                            });
                        } else {
                            GameLogger.error("Game initialization failed");
                            handleLoginFailure("Failed to initialize game");
                        }
                    });

                } else {
                    handleLoginFailure(response.message);
                }
            } catch (Exception e) {
                GameLogger.error("Error handling login response: " + e.getMessage());
                handleLoginFailure("Internal error occurred");
            }
        });
    }

    private void startGameTransition(NetworkProtocol.LoginResponse response) {
        try {
            // Set game mode
            game.setMultiplayerMode(true);

            try {
                // Initialize world first
                game.initializeWorld(response.worldName, true);

                // Create game screen
                GameScreen gameScreen = new GameScreen(
                    game,
                    response.username,
                    gameClient,
                    response.worldName
                );

                // Create loading screen with game screen as target
                LoadingScreen loadingScreen = new LoadingScreen(game, gameScreen);

                // Transition to loading screen
                game.setScreen(loadingScreen);

                // Clean up login screen
                dispose();

                GameLogger.info("Started transition to game for user: " + response.username);

            } catch (Exception e) {
                GameLogger.error("Failed to initialize game: " + e.getMessage());
                throw new RuntimeException("Failed to initialize game", e);
            }

        } catch (Exception e) {
            GameLogger.error("Failed to start game transition: " + e.getMessage());
            handleLoginFailure("Failed to start game: " + e.getMessage());
        }
    }

    private void forceScreenTransition(Screen newScreen) {
        GameLogger.info("Forcing transition to: " + newScreen.getClass().getSimpleName());

        try {
            // Set screen immediately
            game.setScreen(newScreen);

            // Clean up login screen
            isDisposed = true;
            dispose();

            GameLogger.info("Forced transition complete");
        } catch (Exception e) {
            GameLogger.error("Failed to force screen transition: " + e.getMessage());
            e.printStackTrace();
            showError("Failed to switch screens: " + e.getMessage());
            setUIEnabled(true);
            isConnecting = false;
        }
    }

    private void showErrorMessage(String title, String message) {
        Dialog dialog = new Dialog(title, skin) {
            @Override
            protected void result(Object obj) {
                feedbackLabel.setColor(Color.RED);
                feedbackLabel.setText(message);
            }
        };
        dialog.text(message);
        dialog.button("OK", true);
        dialog.show(stage);
    }

    private void setupButtonListeners(TextButton loginButton, TextButton registerButton, TextButton backButton) {
        loginButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                attemptLogin();
            }
        });

        registerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                attemptRegistration();
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                fadeToScreen(new ModeSelectionScreen(game));
            }
        });
    }

    private void attemptRegistration() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (!validateRegistrationInput(username, password)) {
            return;
        }

        if (selectedServer == null) {
            showError("Please select a server");
            return;
        }

        // Disable UI during registration
        isConnecting = true;
        setUIEnabled(false);
        statusLabel.setText("Processing registration...");
        statusLabel.setColor(Color.WHITE);
        connectionProgress.setVisible(true);
        connectionTimer = 0;

        try {
            // Clean up existing client
            if (gameClient != null) {
                gameClient.dispose();
                GameClientSingleton.resetInstance();
            }

            // Initialize new client
            gameClient = GameClientSingleton.getInstance(selectedServer);

            // Set up registration response listener
            gameClient.setRegistrationResponseListener(response -> {
                Gdx.app.postRunnable(() -> handleRegistrationResponse(response));
            });

            // Connect and send registration request
            gameClient.connectToServer(selectedServer);
            gameClient.sendRegisterRequest(username, password);

        } catch (Exception e) {
            GameLogger.error("Registration error: " + e.getMessage());
            handleRegistrationError(e);
        }
    }

    private void handleRegistrationResponse(NetworkProtocol.RegisterResponse response) {
        if (response.success) {
            // Show success message
            statusLabel.setText("Registration successful!");
            statusLabel.setColor(Color.GREEN);
            feedbackLabel.setColor(Color.GREEN);
            feedbackLabel.setText("Account created successfully! You can now log in.");

            // Clear password field
            passwordField.setText("");

            // Reset state
            isConnecting = false;
            connectionProgress.setVisible(false);
            setUIEnabled(true);

            // Show success dialog
            showSuccessDialog();
        } else {
            // Show error
            showError(response.message != null ? response.message : "Registration failed");
            setUIEnabled(true);
            isConnecting = false;
        }

        // Clean up client
        if (gameClient != null) {
            gameClient.dispose();
            GameClientSingleton.resetInstance();
        }
    }

    private void handleRegistrationError(Exception e) {
        Gdx.app.postRunnable(() -> {
            showError("Registration failed: " + e.getMessage());
            setUIEnabled(true);
            isConnecting = false;
            connectionProgress.setVisible(false);

            if (gameClient != null) {
                gameClient.dispose();
                gameClient = null;
            }
        });
    }

    private void showSuccessDialog() {
        Dialog dialog = new Dialog("Registration Successful", skin) {
            @Override
            protected void result(Object obj) {
                if ((Boolean) obj) {
                    // Clear password and update UI
                    passwordField.setText("");
                    statusLabel.setText("Ready to login");
                    statusLabel.setColor(Color.WHITE);
                    feedbackLabel.setText("");
                }
            }
        };

        dialog.text("Your account has been created successfully!\nYou can now log in with your credentials.");
        dialog.button("OK", true);
        dialog.setMovable(false);
        dialog.setModal(true);

        // Center the dialog
        dialog.setPosition(
            (stage.getWidth() - dialog.getWidth()) / 2,
            (stage.getHeight() - dialog.getHeight()) / 2
        );

        dialog.show(stage);
    }

    private void showRetryDialog(String title, String message) {
        Dialog dialog = new Dialog(title, skin) {
            @Override
            protected void result(Object obj) {
                if ((Boolean) obj) {
                    attemptRegistration(); // Retry
                }
            }
        };
        dialog.text(message);
        dialog.button("Retry", true);
        dialog.button("Cancel", false);
        dialog.show(stage);
    }

    private boolean validateRegistrationInput(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            showErrorMessage("Invalid Input", "Username and password cannot be empty.");
            return false;
        }

        if (username.length() < 3 || username.length() > 20) {
            showErrorMessage("Invalid Username",
                "Username must be between 3 and 20 characters.");
            return false;
        }

        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            showErrorMessage("Invalid Username",
                "Username can only contain letters, numbers, and underscores.");
            return false;
        }

        String passwordError = validatePassword(password);
        if (passwordError != null) {
            showErrorMessage("Invalid Password", passwordError);
            return false;
        }

        return true;
    }

    private String validatePassword(String password) {
        if (password.length() < 8) {
            return "Password must be at least 8 characters long.";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter.";
        }
        if (!password.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter.";
        }
        if (!password.matches(".*\\d.*")) {
            return "Password must contain at least one number.";
        }
        if (!password.matches(".*[!@#$%^&*()\\[\\]{}_+=\\-.,].*")) {
            return "Password must contain at least one special character.";
        }
        return null;
    }


    private void showErrorDialog(String title, String message) {
        Dialog dialog = new Dialog(title, skin) {
            @Override
            protected void result(Object obj) {
                if ((Boolean) obj) {
                    attemptRegistration();
                }
            }
        };

        Table contentTable = dialog.getContentTable();
        contentTable.pad(20);

        Label messageLabel = new Label(message, skin);
        messageLabel.setWrap(true);
        contentTable.add(messageLabel).width(300f).row();

        dialog.button("Retry", true);
        dialog.button("Cancel", false);
        dialog.show(stage);
    }

    private void updateConnectionStatus(float progress) {
        if (progress < 0.3f) {
            updateStatusLabel("Connecting to server...", Color.WHITE);
        } else if (progress < 0.6f) {
            updateStatusLabel("Authenticating...", Color.WHITE);
        } else if (progress < 0.9f) {
            updateStatusLabel("Loading game data...", Color.WHITE);
        }
    }

    private boolean validateInput(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            feedbackLabel.setText("Username and password are required");
            return false;
        }
        return true;
    }

    private void fadeToScreen(Screen next) {
        game.setScreen(next);
        dispose();
    }

    @Override
    public void hide() {
    }

    @Override
    public void show() {
    }

    @Override
    public void pause() {
        // Implement if needed
    }

    @Override
    public void resume() {
        // Implement if needed
    }

    private ServerConnectionConfig getSelectedServerConfig() {
        return selectedServer != null ? selectedServer : ServerConfigManager.getDefaultServerConfig();
    }

    // Updated ServerEntry class with icon support
    private static class ServerEntry {
        public String name;
        public String ip;
        public int tcpPort;
        public int udpPort;
        public String motd;
        public boolean isDefault;
        public int maxPlayers;
        public String iconPath;

        public ServerEntry() {
        }

        public ServerEntry(String name, String ip, int tcpPort, int udpPort,
                           String motd, boolean isDefault, int maxPlayers, String iconPath) {
            this.name = name;
            this.ip = ip;
            this.tcpPort = tcpPort;
            this.udpPort = udpPort;
            this.motd = motd;
            this.isDefault = isDefault;
            this.maxPlayers = maxPlayers;
            this.iconPath = iconPath;
        }
    }


}
