package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.audio.AudioManager;
import io.github.pokemeetup.managers.DatabaseManager;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.config.ServerConfigManager;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.utils.AssetManagerSingleton;
import io.github.pokemeetup.utils.GameLogger;

import java.io.IOException;

public class LoginScreen implements Screen {
    private static final float FADE_DURATION = 0.3f;
    private static final boolean DEBUG_MODE = false; // Toggle for debug mode
    private static final String[] DEBUG_USERS = {
        "allidoisgame1:Ferfer44$",
        "yauhyeah:Derder44$",
        "etienne:Derder44$"
    };
    private final Stage stage;
    private final Skin skin;
    private final CreatureCaptureGame game;
    private final Preferences prefs;
    private float fadeAlpha = 0f;
    private boolean isTransitioning = false;
    private Screen nextScreen = null;
    private Action fadeAction = null;
    private Array<ServerConnectionConfig> servers; // Adding the servers variable
    private GameClient gameClient;
    private TextField usernameField;
    private TextField passwordField;
    private CheckBox rememberMeBox;
    private Label feedbackLabel;
    private boolean isDisposed = false;
    private SelectBox<ServerConnectionConfig> serverSelect;


    public LoginScreen(CreatureCaptureGame game) throws IOException {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
        this.prefs = Gdx.app.getPreferences("LoginPrefs");
        this.skin = AssetManagerSingleton.getSkin();
        loadServers();
        setupUI();
        loadSavedCredentials();
        // Remove immediate connection
        // gameClient initialization moves to login button click

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    fadeToScreen(new ModeSelectionScreen(game));
                    if (gameClient != null) {
                        gameClient.disconnect();
                    }
                    return true;
                }
                return false;
            }
        });

        Gdx.input.setInputProcessor(stage);
    }

    private void loadServers() {
        try {
            servers = ServerConfigManager.getInstance().getServers();
        } catch (Exception e) {
            GameLogger.info("Error loading servers: " + e.getMessage());
            servers = new Array<>();

        }
    }

    private void loadSavedCredentials() {
        if (prefs.getBoolean("rememberMe", false)) {
            usernameField.setText(prefs.getString("username", ""));
            passwordField.setText(prefs.getString("password", ""));
            rememberMeBox.setChecked(true);
        }
    }


    // Modified saveCredentials method to ensure preferences are saved immediately:
    private void saveCredentials(String username, String password, boolean remember) {
        prefs.putBoolean("rememberMe", remember);
        if (remember) {
            prefs.putString("username", username);
            prefs.putString("password", password); // In production, encrypt this
        } else {
            prefs.remove("username");
            prefs.remove("password");
        }
        prefs.flush(); // Make sure to call flush() to save immediately
        GameLogger.info("Credentials saved. Remember me: " + remember);
    }

    private void debugLogin(String username, String password) {
        try {
            // Get default server config
            ServerConnectionConfig config = serverSelect.getItems().first();

            // Initialize client
            gameClient = GameClientSingleton.getInstance(config);
            gameClient.setLocalUsername(username);

            // Connect and login
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

    private void setupUI() {
        Table mainTable = new Table();
        mainTable.setFillParent(true);

        // Title
        Label titleLabel = new Label("PokeMeetup", skin);
        titleLabel.setFontScale(2f);

        // Input fields
        usernameField = new TextField("", skin);
        usernameField.setMessageText("Username");

        passwordField = new TextField("", skin);
        passwordField.setMessageText("Password");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('*');

        // Remember me checkbox
        rememberMeBox = new CheckBox(" Remember Me", skin);

        // Server selection
        serverSelect = new SelectBox<>(skin);
        serverSelect.setItems(ServerConfigManager.getInstance().getServers());

        // Add server button
        TextButton addServerButton = new TextButton("Add Server", skin);
        addServerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showAddServerDialog(serverSelect);
            }
        });

        // Login/Register buttons
        TextButton loginButton = new TextButton("Login", skin);
        TextButton registerButton = new TextButton("Register", skin);
        TextButton backButton = new TextButton("Back", skin);

        feedbackLabel = new Label("", skin);
        feedbackLabel.setColor(Color.RED);

        // Layout
        mainTable.add(titleLabel).padBottom(40).row();
        mainTable.add(usernameField).width(300).padBottom(20).row();
        mainTable.add(passwordField).width(300).padBottom(20).row();
        mainTable.add(rememberMeBox).padBottom(20).row();
        mainTable.add(serverSelect).width(300).padBottom(10).row();
        mainTable.add(addServerButton).width(300).padBottom(20).row();

        Table buttonTable = new Table();
        buttonTable.add(loginButton).width(140).padRight(20);
        buttonTable.add(registerButton).width(140);
        mainTable.add(buttonTable).padBottom(20).row();

        mainTable.add(backButton).width(300).padBottom(20).row();
        mainTable.add(feedbackLabel).row();

        // Add listeners
        setupButtonListeners(loginButton, registerButton, backButton);

        stage.addActor(mainTable);
        if (DEBUG_MODE) {
            Table debugTable = new Table();
            debugTable.pad(10);

            Label debugLabel = new Label("Debug Logins:", skin);
            debugTable.add(debugLabel).padBottom(10).row();

            for (String debugUser : DEBUG_USERS) {
                String[] parts = debugUser.split(":");
                TextButton debugButton = new TextButton("Login as " + parts[0], skin);
                debugButton.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        debugLogin(parts[0], parts[1]);
                    }
                });
                debugTable.add(debugButton).width(200).padBottom(5).row();
            }

            mainTable.add(debugTable).padTop(20).row();
        }
    }

    private void showAddServerDialog(SelectBox selectBox) {
        final Dialog dialog = new Dialog("Add Custom Server", skin) {
            private final TextField nameField;
            private final TextField ipField;
            private final TextField portField;
            private final Label errorLabel;

            {
                // Initialize fields
                nameField = new TextField("", skin);
                ipField = new TextField("", skin);
                portField = new TextField("", skin);
                errorLabel = new Label("", skin);
                errorLabel.setColor(Color.RED);

                // Set default values and hints
                nameField.setMessageText("Server Name");
                ipField.setMessageText("IP Address");
                portField.setMessageText("Port Number");

                // Only allow numbers in port field
                portField.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());

                // Layout
                Table contentTable = getContentTable();
                contentTable.pad(20);

                // Server Name
                contentTable.add("Server Name:").left().padRight(10);
                contentTable.add(nameField).expandX().fillX().padBottom(10);
                contentTable.row();

                // IP Address
                contentTable.add("IP Address:").left().padRight(10);
                contentTable.add(ipField).expandX().fillX().padBottom(10);
                contentTable.row();

                // Port
                contentTable.add("Port:").left().padRight(10);
                contentTable.add(portField).expandX().fillX().padBottom(10);
                contentTable.row();

                // Error label
                contentTable.add(errorLabel).colspan(2).padTop(10);
                contentTable.row();

                // Buttons
                button("Add Server", true);
                button("Cancel", false);

                // Set stage keyboard focus
                stage.setKeyboardFocus(nameField);
            }

            @Override
            protected void result(Object object) {
                if ((Boolean) object) {
                    try {
                        // Validate inputs
                        String name = nameField.getText().trim();
                        String ip = ipField.getText().trim();
                        String portText = portField.getText().trim();

                        if (name.isEmpty() || ip.isEmpty() || portText.isEmpty()) {
                            errorLabel.setText("All fields are required");
                            return;
                        }

                        int port = Integer.parseInt(portText);
                        if (port <= 0 || port > 65535) {
                            errorLabel.setText("Port must be between 1 and 65535");
                            return;
                        }


                        // Add server
                        ServerConnectionConfig newServer = new ServerConnectionConfig(
                            ip, port, port + 1, name, false, 100
                        );

                        // Add to both local array and manager
                        servers.add(newServer);
                        ServerConfigManager.getInstance().addServer(newServer);

                        // Refresh UI
                        refreshServerList(selectBox);

                        // Show success message
                        showMessage("Server Added", "Successfully added new server: " + name);

                        hide();
                    } catch (NumberFormatException e) {
                        errorLabel.setText("Invalid port number");
                    } catch (Exception e) {
                        errorLabel.setText("Error adding server: " + e.getMessage());
                    }
                } else {
                    hide();
                }
            }
        };
        dialog.show(stage);
    }

    private void showMessage(String title, String message) {
        Dialog messageDialog = new Dialog(title, skin, "dialog");
        messageDialog.text(message);
        messageDialog.button("OK");
        messageDialog.show(stage);
    }

    private void refreshServerList(SelectBox<ServerConnectionConfig> serverSelectBox) {
        if (serverSelectBox != null) {
            serverSelectBox.setItems(servers);
        }
    }

    private void setupButtonListeners(TextButton loginButton, TextButton registerButton, TextButton backButton) {
        loginButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String username = usernameField.getText().trim();
                String password = passwordField.getText().trim();
                boolean remember = rememberMeBox.isChecked();

                // Validate input
                if (username.isEmpty() || password.isEmpty()) {
                    feedbackLabel.setColor(Color.RED);
                    feedbackLabel.setText("Username and password are required.");
                    return;
                }

                // Get selected server
                ServerConnectionConfig selectedConfig = serverSelect.getSelected();
                if (selectedConfig == null) {
                    feedbackLabel.setColor(Color.RED);
                    feedbackLabel.setText("Please select a server.");
                    return;
                }

                // Disable UI during login attempt
                setUIEnabled(false);
                feedbackLabel.setColor(Color.WHITE);
                feedbackLabel.setText("Connecting...");

                // Clean up any existing client
                GameClientSingleton.clearInstance();

                new Thread(() -> {
                    try {
                        // Initialize new client
                        gameClient = GameClientSingleton.getInstance(selectedConfig);
                        if (gameClient == null) {
                            throw new RuntimeException("Failed to initialize game client");
                        }

                        // Set username before connection
                        gameClient.setLocalUsername(username);

                        // Set up login response listener before connecting
                        gameClient.setLoginResponseListener(response -> {
                            Gdx.app.postRunnable(() -> {
                                handleLoginResponse(response, username, password, remember);
                            });
                        });

                        // Connect to server
                        GameLogger.info("Attempting connection to: " + selectedConfig.getServerIP());
                        gameClient.connectToServer(selectedConfig);

                        // Wait for connection with timeout
                        long startTime = System.currentTimeMillis();
                        long timeout = 5000; // 5 seconds timeout

                        while (!gameClient.isConnected() &&
                            System.currentTimeMillis() - startTime < timeout) {
                            Thread.sleep(100);
                        }

                        if (!gameClient.isConnected()) {
                            throw new IOException("Connection timed out after 5 seconds");
                        }

                        // Send login request
                        GameLogger.info("Connected, sending login request for: " + username);
                        gameClient.sendLoginRequest(username, password);

                    } catch (Exception e) {
                        GameLogger.error("Login failed: " + e.getMessage());
                        Gdx.app.postRunnable(() -> {
                            feedbackLabel.setColor(Color.RED);
                            feedbackLabel.setText("Connection failed: " + e.getMessage());
                            setUIEnabled(true);
                        });

                        // Clean up on error
                        if (gameClient != null) {
                            gameClient.disconnect();
                            gameClient = null;
                        }
                    }
                }).start();
            }
        });


        registerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String username = usernameField.getText().trim();
                String password = passwordField.getText().trim();

                GameLogger.info("Registration attempt starting for user: " + username);

                // Basic input validation
                if (!validateRegistrationInput(username, password)) {
                    return;
                }

                // Get selected server config
                ServerConnectionConfig selectedConfig = serverSelect.getSelected();
                if (selectedConfig == null) {
                    feedbackLabel.setColor(Color.RED);
                    feedbackLabel.setText("Please select a server.");
                    return;
                }

                // Disable buttons during registration attempt
                setUIEnabled(false);
                feedbackLabel.setColor(Color.WHITE);
                feedbackLabel.setText("Checking username availability...");

                new Thread(() -> {
                    try {
                        // Cleanup existing client if any
                        if (gameClient != null) {
                            gameClient.disconnect();
                        }

                        // Initialize GameClient
                        gameClient = GameClientSingleton.getInstance(selectedConfig);
                        if (gameClient == null) {
                            throw new RuntimeException("Failed to initialize game client");
                        }

                        // Connect to server
                        gameClient.connectToServer(selectedConfig);

                        // Wait for connection with timeout
                        if (!waitForConnection()) {
                            throw new IOException("Could not connect to server");
                        }

                        // Set up username check listener
                        gameClient.setUsernameCheckListener(response -> {
                            Gdx.app.postRunnable(() -> {
                                if (response.available) {
                                    // Username is available, proceed with registration
                                    proceedWithRegistration(username, password);
                                } else {
                                    feedbackLabel.setColor(Color.RED);
                                    feedbackLabel.setText("Username is already taken. Please choose another.");
                                    setUIEnabled(true);

                                    // Cleanup after failed check
                                    gameClient.disconnect();
                                    gameClient = null;
                                }
                            });
                        });

                        // Send username check request
                        gameClient.checkUsernameAvailability(username);

                    } catch (Exception e) {
                        GameLogger.error("Username check error: " + e.getMessage());
                        Gdx.app.postRunnable(() -> {
                            feedbackLabel.setColor(Color.RED);
                            feedbackLabel.setText("Error checking username: " + e.getMessage());
                            setUIEnabled(true);
                        });

                        // Cleanup on error
                        if (gameClient != null) {
                            gameClient.disconnect();
                            gameClient = null;
                        }
                    }
                }).start();
            }
        });
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                fadeToScreen(new ModeSelectionScreen(game));
            }
        });
    }

    private boolean validateRegistrationInput(String username, String password) {
        // Username validation
        if (username.isEmpty() || password.isEmpty()) {
            feedbackLabel.setColor(Color.RED);
            feedbackLabel.setText("Username and password cannot be empty.");
            return false;
        }

        if (username.length() < 3 || username.length() > 20) {
            feedbackLabel.setColor(Color.RED);
            feedbackLabel.setText("Username must be between 3 and 20 characters.");
            return false;
        }

        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            feedbackLabel.setColor(Color.RED);
            feedbackLabel.setText("Username can only contain letters, numbers, and underscores.");
            return false;
        }

        // Password validation
        String passwordError = validatePassword(password);
        if (passwordError != null) {
            feedbackLabel.setColor(Color.RED);
            feedbackLabel.setText(passwordError);
            return false;
        }

        return true;
    }

    private boolean waitForConnection() {
        long startTime = System.currentTimeMillis();
        while (!gameClient.isConnected() &&
            System.currentTimeMillis() - startTime < 5000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return gameClient.isConnected();
    }

    private void proceedWithRegistration(String username, String password) {
        feedbackLabel.setColor(Color.WHITE);
        feedbackLabel.setText("Creating account...");

        // Set up registration response listener
        gameClient.setRegistrationResponseListener(response -> {
            Gdx.app.postRunnable(() -> {
                if (response.success) {
                    feedbackLabel.setColor(Color.GREEN);
                    feedbackLabel.setText("Registration successful! Please log in.");
                    passwordField.setText(""); // Clear password for security
                } else {
                    feedbackLabel.setColor(Color.RED);
                    feedbackLabel.setText(response.message != null ?
                        response.message : "Registration failed. Please try again.");
                }
                setUIEnabled(true);
            });

            // Cleanup after registration attempt
            gameClient.disconnect();
            gameClient = null;
        });

        // Send registration request
        try {
            gameClient.sendRegisterRequest(username, password);
        } catch (Exception e) {
            GameLogger.error("Registration error: " + e.getMessage());
            Gdx.app.postRunnable(() -> {
                feedbackLabel.setColor(Color.RED);
                feedbackLabel.setText("Registration failed: " + e.getMessage());
                setUIEnabled(true);
            });

            // Cleanup on error
            gameClient.disconnect();
            gameClient = null;
        }
    }

    private void handleRegister(String username, String password, Label feedbackLabel) throws Exception {
        // Access DatabaseManager for local mode
        if (!isMultiplayerMode()) {
            DatabaseManager dbManager = game.getDatabaseManager();

            // Register the player using the DatabaseManager
            if (dbManager.registerPlayer(username, password)) {
                feedbackLabel.setColor(Color.GREEN);
                feedbackLabel.setText("Registration successful! Please log in.");
            } else {
                feedbackLabel.setColor(Color.RED);
                feedbackLabel.setText("Registration failed. Username might already exist.");
            }
        } else {
            // Handle multiplayer registration via NetworkProtocol and GameClient
            gameClient.sendRegisterRequest(username, password);
        }
    }

    private boolean isValidUsername(String username) {
        return username != null &&
            username.length() >= 3 &&
            username.length() <= 20 &&
            username.matches("^[a-zA-Z0-9_]+$");
    }

    // Update the password validation method to be more detailed
    private String validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return "Password cannot be empty.";
        }
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
        if (password.contains(" ")) {
            return "Password cannot contain spaces.";
        }
        return null; // Password is valid
    }

    private boolean isMultiplayerMode() {
        return game.isMultiplayerMode();  // This will now check multiplayer mode flag
    }


    private void handleLogin(Label feedbackLabel, ServerConnectionConfig selectedConfig, Button loginButton, Button registerButton, TextField usernameField, TextField passwordField) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (!validateInput(username, password)) {
            return;
        }

        setUIEnabled(false);
        feedbackLabel.setColor(Color.WHITE);
        feedbackLabel.setText("Connecting...");

        // Execute login in background
        new Thread(() -> {
            try {
                if (gameClient == null) {
                    gameClient = GameClientSingleton.getInstance(selectedConfig);
                }
                boolean connected = connectToServer(selectedConfig, feedbackLabel, loginButton, registerButton, usernameField, passwordField);
                if (!connected) return;

                gameClient.setLoginResponseListener(response -> {
                    Gdx.app.postRunnable(() -> {
                        handleLoginResponse(response, username, password,true);
                    });
                });

                gameClient.sendLoginRequest(username, password);

            } catch (Exception e) {
                Gdx.app.postRunnable(() -> {
                    feedbackLabel.setColor(Color.RED);
                    feedbackLabel.setText("Connection error: " + "e.getMessage()");
                    setUIEnabled(true);
                });
            }
        }).start();
    }

    private boolean connectToServer(ServerConnectionConfig config, Label feedbackLabel, Button loginButton, Button registerButton, TextField usernameField, TextField passwordField) {
        try {
            synchronized (this) {
                if (gameClient != null && gameClient.isConnected()) {
                    return true;
                }

                gameClient = GameClientSingleton.getInstance(config);

                // Wait for connection
                long startTime = System.currentTimeMillis();
                while (!gameClient.isConnected() &&
                    System.currentTimeMillis() - startTime < 5000) {
                    Thread.sleep(100);
                }

                if (!gameClient.isConnected()) {
                    Gdx.app.postRunnable(() -> {
                        feedbackLabel.setColor(Color.RED);
                        feedbackLabel.setText("Could not connect to server. Please try again.");
                    });
                    return false;
                }

                return true;
            }
        } catch (Exception e) {
            //            GameLogger.info(STR."Connection error: {}\{e.getMessage()}");
            Gdx.app.postRunnable(() -> {
                feedbackLabel.setColor(Color.RED);
                feedbackLabel.setText("Connection error: " + "e.getMessage()}");
            });
            return false;
        }
    }


    private void handleLoginResponse(NetworkProtocol.LoginResponse response,
                                     String username, String password, boolean remember) {
        try {
            if (response.success) {
                // Save credentials if requested
                if (remember) {
                    saveCredentials(username, password, true);
                    GameLogger.info("Credentials saved. Remember me: " + remember);
                }

                // Initialize world
                game.setMultiplayerMode(true);
                game.initializeMultiplayerWorld(
                    response.worldName,
                    response.worldSeed,
                    response.worldData
                );

                // Transition to game screen
                fadeToScreen(new GameScreen(game, username, gameClient, response.worldName));
            } else {
                feedbackLabel.setColor(Color.RED);
                feedbackLabel.setText(response.message);
                passwordField.setText("");
                setUIEnabled(true);

                // Clean up on failed login
                gameClient.disconnect();
                gameClient = null;
            }
        } catch (Exception e) {
            GameLogger.error("Error handling login response: " + e.getMessage());
            feedbackLabel.setColor(Color.RED);
            feedbackLabel.setText("Error: " + e.getMessage());
            setUIEnabled(true);

            // Clean up on error
            if (gameClient != null) {
                gameClient.disconnect();
                gameClient = null;
            }
        }
    }

    private void setUIEnabled(boolean enabled) {
        usernameField.setDisabled(!enabled);
        passwordField.setDisabled(!enabled);
        rememberMeBox.setDisabled(!enabled);
        serverSelect.setDisabled(!enabled);
    }

    private boolean validateInput(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            feedbackLabel.setText("Username and password are required");
            return false;
        }
        return true;
    }

    private void fadeToScreen(Screen next) {
        if (isTransitioning) return;

        isTransitioning = true;
        nextScreen = next;

        // Create fade out action
        fadeAction = Actions.sequence(
            Actions.alpha(1), // Ensure we start fully visible
            Actions.fadeOut(FADE_DURATION), // Fade out over duration
            Actions.run(() -> {
                game.setScreen(nextScreen);
                dispose();
            })
        );

        // Add the fade action to the stage
        stage.addAction(fadeAction);
    }

    @Override
    public void render(float delta) {
        // Clear screen
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();

        if (isTransitioning) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            ShapeRenderer renderer = new ShapeRenderer();
            renderer.begin(ShapeRenderer.ShapeType.Filled);
            renderer.setColor(0, 0, 0, fadeAlpha);
            renderer.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            renderer.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    @Override
    public void dispose() {
        if (!isDisposed) {
            stage.dispose();
            isDisposed = true;
        }
    }

    @Override
    public void show() {
    }


    @Override
    public void resize(int width, int height) {
        // Update the viewport on resize
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
        // Implement if needed
    }

    @Override
    public void resume() {
        // Implement if needed
    }

    @Override
    public void hide() {
        // Implement if needed
    }


}
