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
import io.github.pokemeetup.managers.DatabaseManager;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.multiplayer.server.config.ServerConfigManager;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;
import io.github.pokemeetup.utils.AssetManagerSingleton;

import java.io.IOException;
import java.util.ArrayList;

public class LoginScreen implements Screen {
    private static final float FADE_DURATION = 0.3f;

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
            System.err.println("Error loading servers: "+e.getMessage());
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

    private void saveCredentials(String username, String password, boolean remember) {
        prefs.putBoolean("rememberMe", remember);
        if (remember) {
            prefs.putString("username", username);
            // In production, use encryption for password storage
            prefs.putString("password", password);
        } else {
            prefs.remove("username");
            prefs.remove("password");
        }
        prefs.flush();
    }private void debugLogin(String username, String password) {
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
                            game.enableMultiplayerMode();
                            game.initializeWorld(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME, true);
                            fadeToScreen(new GameScreen(game, username, gameClient,
                                response.x, response.y,
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
        }}

    private void setupUI() {
        Table mainTable = new Table();
        mainTable.setFillParent(true);

        // Title
        Label titleLabel = new Label("PokeMeetup", skin, "title");
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

        stage.addActor(mainTable);  if (DEBUG_MODE) {
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
        final Dialog dialog = new Dialog("Add Custom Server", skin, "dialog") {
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
    }private static final boolean DEBUG_MODE = true; // Toggle for debug mode
    private static final String[] DEBUG_USERS = {
        "allidoisgame1:Ferfer4455$",
        "yauhyeah:Derder44$",
        "etienne:Derder44$"
    };

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

                // Get the selected server configuration
                ServerConnectionConfig selectedConfig = serverSelect.getSelected();
                if (selectedConfig == null) {
                    feedbackLabel.setColor(Color.RED);
                    feedbackLabel.setText("Please select a server.");
                    return;
                }

                // Disable buttons during login attempt
                loginButton.setDisabled(true);
                registerButton.setDisabled(true);
                feedbackLabel.setColor(Color.WHITE);
                feedbackLabel.setText("Connecting...");

                // Save credentials if 'Remember Me' is checked
                saveCredentials(username, password, remember);

                // Connect to the selected server and send login request
                new Thread(() -> {
                    try {
                        // Initialize GameClient first
                        gameClient = GameClientSingleton.getInstance(selectedConfig);

                        if (gameClient == null) {
                            Gdx.app.postRunnable(() -> {
                                feedbackLabel.setColor(Color.RED);
                                feedbackLabel.setText("Failed to initialize game client");
                                loginButton.setDisabled(false);
                                registerButton.setDisabled(false);
                            });
                            return;
                        }

                        // Set username for the client
                        gameClient.setLocalUsername(username);

                        // Try to connect
                        gameClient.connectToServer(selectedConfig);

                        // Wait for connection
                        long startTime = System.currentTimeMillis();
                        while (!gameClient.isConnected() &&
                            System.currentTimeMillis() - startTime < 5000) {
                            Thread.sleep(100);
                        }

                        if (gameClient.isConnected()) {
                            // Set up login response listener
                            gameClient.setLoginResponseListener(response -> {
                                Gdx.app.postRunnable(() -> {
                                    if (response.success) {
                                        game.enableMultiplayerMode();
                                        game.initializeWorld(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME, true);
                                        fadeToScreen(new GameScreen(game, username, gameClient,
                                            response.x, response.y,
                                            CreatureCaptureGame.MULTIPLAYER_WORLD_NAME));
                                    } else {
                                        feedbackLabel.setColor(Color.RED);
                                        feedbackLabel.setText(response.message);
                                        loginButton.setDisabled(false);
                                        registerButton.setDisabled(false);
                                    }
                                });
                            });

                            // Send login request
                            gameClient.sendLoginRequest(username, password);
                            Gdx.app.postRunnable(() ->
                                feedbackLabel.setText("Login request sent..."));
                        } else {
                            Gdx.app.postRunnable(() -> {
                                feedbackLabel.setColor(Color.RED);
                                feedbackLabel.setText("Could not connect to server. Please try again.");
                                loginButton.setDisabled(false);
                                registerButton.setDisabled(false);
                            });
                        }
                    } catch (Exception e) {
                        Gdx.app.postRunnable(() -> {
                            feedbackLabel.setColor(Color.RED);
                            feedbackLabel.setText("Error: " + e.getMessage());
                            loginButton.setDisabled(false);
                            registerButton.setDisabled(false);
                        });
                        e.printStackTrace();
                    }
                }).start();
            }
        });

        registerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String username = usernameField.getText().trim();
                String password = passwordField.getText().trim();

                // Basic input validation
                if (username.isEmpty() || password.isEmpty()) {
                    feedbackLabel.setColor(Color.RED);
                    feedbackLabel.setText("Username and password cannot be empty.");
                    return;
                }

                // Validate password
                String passwordError = validatePassword(password);
                if (passwordError != null) {
                    feedbackLabel.setColor(Color.RED);
                    feedbackLabel.setText(passwordError);
                    return;
                }

                // Disable buttons during registration attempt
                loginButton.setDisabled(true);
                registerButton.setDisabled(true);
                feedbackLabel.setColor(Color.WHITE);
                feedbackLabel.setText("Registering...");

                // Get selected server config
                ServerConnectionConfig selectedConfig = serverSelect.getSelected();
                if (selectedConfig == null) {
                    feedbackLabel.setColor(Color.RED);
                    feedbackLabel.setText("Please select a server.");
                    loginButton.setDisabled(false);
                    registerButton.setDisabled(false);
                    return;
                }

                // Initialize client and register in background
                new Thread(() -> {
                    try {
                        // Initialize GameClient first
                        gameClient = GameClientSingleton.getInstance(selectedConfig);

                        if (gameClient == null) {
                            Gdx.app.postRunnable(() -> {
                                feedbackLabel.setColor(Color.RED);
                                feedbackLabel.setText("Failed to initialize game client");
                                loginButton.setDisabled(false);
                                registerButton.setDisabled(false);
                            });
                            return;
                        }

                        // Connect to server
                        gameClient.connectToServer(selectedConfig);

                        // Wait for connection
                        long startTime = System.currentTimeMillis();
                        while (!gameClient.isConnected() &&
                            System.currentTimeMillis() - startTime < 5000) {
                            Thread.sleep(100);
                        }

                        if (gameClient.isConnected()) {
                            // Set registration response listener
                            gameClient.setRegistrationResponseListener(response -> {
                                Gdx.app.postRunnable(() -> {
                                    if (response.success) {
                                        feedbackLabel.setColor(Color.GREEN);
                                        feedbackLabel.setText("Registration successful! Please log in.");
                                    } else {
                                        feedbackLabel.setColor(Color.RED);
                                        feedbackLabel.setText(response.message);
                                    }
                                    loginButton.setDisabled(false);
                                    registerButton.setDisabled(false);
                                });
                            });

                            // Send registration request
                            gameClient.sendRegisterRequest(username, password);
                        } else {
                            Gdx.app.postRunnable(() -> {
                                feedbackLabel.setColor(Color.RED);
                                feedbackLabel.setText("Could not connect to server");
                                loginButton.setDisabled(false);
                                registerButton.setDisabled(false);
                            });
                        }

                    } catch (Exception e) {
                        Gdx.app.postRunnable(() -> {
                            feedbackLabel.setColor(Color.RED);
                            feedbackLabel.setText("Registration error: " + e.getMessage());
                            loginButton.setDisabled(false);
                            registerButton.setDisabled(false);
                        });
                        e.printStackTrace();
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

    private boolean isMultiplayerMode() {
        return game.isMultiplayerMode();  // This will now check multiplayer mode flag
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
            return "Password must contain at least one digit.";
        }
        if (!password.matches(".*[!@#$%^&*()].*")) {
            return "Password must contain at least one special character.";
        }
        return null; // Password is valid
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
                    Gdx.app.postRunnable(() -> handleLoginResponse(response, username, password));
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
//            System.out.println(STR."Connection error: {}\{e.getMessage()}");
            Gdx.app.postRunnable(() -> {
                feedbackLabel.setColor(Color.RED);
                feedbackLabel.setText("Connection error: " + "e.getMessage()}");
            });
            return false;
        }
    }

    private void handleLoginResponse(NetworkProtocol.LoginResponse response, String username, String password) {
        if (response.success) {
            saveCredentials(username, password, rememberMeBox.isChecked());
            game.initializeWorld(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME, true);
            fadeToScreen(new GameScreen(game, username, gameClient, response.x, response.y,
                CreatureCaptureGame.MULTIPLAYER_WORLD_NAME));
        } else {
            feedbackLabel.setColor(Color.RED);
            feedbackLabel.setText(response.message);
            passwordField.setText("");
            setUIEnabled(true);
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
        // Called when this screen becomes the current screen
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
