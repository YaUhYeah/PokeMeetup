package io.github.pokemeetup.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.client.GameClientSingleton;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class LoginScreen implements Screen {
    private final Stage stage;
    private Preferences prefs; // Add preferences
    private final Skin skin;
    private GameClient gameClient; // Single instance of GameClient
    private final CreatureCaptureGame game; // Reference to the main game class

    public LoginScreen(CreatureCaptureGame game) {
        this.game = game;
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        this.prefs = Gdx.app.getPreferences("LoginPrefs");

        skin = new Skin(Gdx.files.internal("Skins/uiskin.json")); // Ensure this file exists
        setupUI();
    }

    private void setupUI() {
        // Create UI elements
        Label titleLabel = new Label("PokeMeetup", skin);
        titleLabel.setFontScale(2);
        titleLabel.setAlignment(Align.center);

        boolean rememberMe = prefs.getBoolean("rememberMe", false);
        final TextField usernameField = new TextField("", skin);
        usernameField.setMessageText("Username");
        usernameField.setAlignment(Align.center);

        final TextField passwordField = new TextField("", skin);
        passwordField.setMessageText("Password");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('*');
        passwordField.setAlignment(Align.center);
        String savedUsername = prefs.getString("username", "");
        String savedPassword = prefs.getString("password", "");

        final CheckBox rememberMeBox = new CheckBox(" Remember Me", skin);
        rememberMeBox.setChecked(rememberMe);
        autoFillCredentials(usernameField,rememberMeBox);
        TextButton loginButton = new TextButton("Login", skin);
        TextButton registerButton = new TextButton("Register", skin);
        TextButton backButton = new TextButton("Back", skin); // Add back button

        Label feedbackLabel = new Label("", skin);
        feedbackLabel.setColor(1, 0, 0, 1); // Set feedback label color to red for errors

        // Layout using a Table
        Table table = new Table();
        table.setFillParent(true);
        table.center();

        table.add(titleLabel).colspan(2).padBottom(20);
        table.row();
        table.add(usernameField).width(300).colspan(2).padBottom(10);
        table.row();
        table.add(passwordField).width(300).colspan(2).padBottom(10);
        table.row();
        table.add(rememberMeBox).colspan(2).padBottom(10);
        table.row();
        table.add(loginButton).width(140).padRight(20);
        table.add(registerButton).width(140);
        table.row();
        table.add(backButton).colspan(2).width(300).padTop(10);

        table.add(feedbackLabel).colspan(2).padTop(10);

        stage.addActor(table);

        // Initialize GameClient
        try {
            gameClient = GameClientSingleton.getInstance();

        } catch (IOException e) {
            Gdx.app.error("LoginScreen", "Failed to connect to the server: " + e.getMessage());
            feedbackLabel.setText("Failed to connect to the server.");
            loginButton.setDisabled(true);
            registerButton.setDisabled(true);
            return;
        }


        // Set up RegistrationResponseListener
        gameClient.setRegistrationResponseListener(response -> Gdx.app.postRunnable(() -> {
            if (response.success) {
                feedbackLabel.setText("Registration successful! Please log in.");
            } else {
                feedbackLabel.setText(response.message);
            }
            loginButton.setDisabled(false);
            registerButton.setDisabled(false);
        }));

        // Add listeners to buttons
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                // Return to the mode selection screen
                game.setScreen(new ModeSelectionScreen(game));
                dispose();
            }
        });
        loginButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AtomicReference<String> username = new AtomicReference<>(usernameField.getText().trim());
                String password = passwordField.getText().trim();
                boolean remember = rememberMeBox.isChecked();

                // Validate input
                if (username.get().isEmpty() || password.isEmpty()) {
                    feedbackLabel.setText("Username and password are required");
                    return;
                }

                // Disable buttons during login attempt
                loginButton.setDisabled(true);
                registerButton.setDisabled(true);
                feedbackLabel.setText("Logging in...");

                // Set login response listener with proper feedback
                gameClient.setLoginResponseListener(response -> Gdx.app.postRunnable(() -> {
                    if (response.success) {
                        System.out.println("Login successful! Username: " + response.username);
                        game.initializeWorld(CreatureCaptureGame.MULTIPLAYER_WORLD_NAME, true);
                        game.setScreen(new GameScreen(
                            game,  // Pass game instance
                            response.username,
                            gameClient,
                            response.x,
                            response.y,
                            CreatureCaptureGame.MULTIPLAYER_WORLD_NAME
                        ));
                        dispose();
                    } else {
                        feedbackLabel.setText(response.message);
                        loginButton.setDisabled(false);
                        registerButton.setDisabled(false);
                    }
                }));

                try {
                    gameClient.sendLoginRequest(username.get(), password);
                } catch (Exception e) {
                    feedbackLabel.setText("Connection error. Please try again.");
                    loginButton.setDisabled(false);
                    registerButton.setDisabled(false);
                }
            }
        });

        registerButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                final String username = usernameField.getText().trim();
                final String password = passwordField.getText().trim();

                // Basic input validation
                if (username.isEmpty() || password.isEmpty()) {
                    feedbackLabel.setText("Username and password cannot be empty.");
                    return;
                }

                // Perform client-side password strength check
                String passwordError = validatePassword(password);
                if (passwordError != null) {
                    feedbackLabel.setText(passwordError);
                    return;
                }

                // Disable input to prevent multiple clicks
                loginButton.setDisabled(true);
                registerButton.setDisabled(true);
                feedbackLabel.setText("Registering...");

                // Send registration request to the server
                gameClient.sendRegisterRequest(username, password);
            }
        });
    }private void attemptLogin(String username, String password, boolean remember) {
        if (username.isEmpty() || password.isEmpty()) {
            return;
        }

        // Save credentials if remember me is checked
        if (remember) {
            prefs.putString("username", username);
            prefs.putString("password", password);
            prefs.putBoolean("rememberMe", true);
            prefs.flush();
        } else {
            prefs.clear();
            prefs.flush();
        }

        // Initialize GameClient if needed
        try {
            gameClient = GameClientSingleton.getInstance();
            gameClient.setLoginResponseListener(response -> {
                Gdx.app.postRunnable(() -> {
                    if (response.success) {
                        game.setScreen(new GameScreen(game,response.username, gameClient, response.x, response.y,CreatureCaptureGame.MULTIPLAYER_WORLD_NAME));
                        dispose();
                    }
                });
            });

            gameClient.sendLoginRequest(username, password);

        } catch (IOException e) {
            Gdx.app.error("LoginScreen", "Failed to connect to server: " + e.getMessage());
        }
    }
    private void tryAutoLogin() {
        boolean rememberMe = prefs.getBoolean("rememberMe", false);
        if (rememberMe) {
            String savedUsername = prefs.getString("username", "");
            String savedPassword = prefs.getString("password", "");
            if (!savedUsername.isEmpty() && !savedPassword.isEmpty()) {
                attemptLogin(savedUsername, savedPassword, true);
            }
        }
    }
    private void autoFillCredentials(TextField usernameField, CheckBox rememberMeBox) {
        if (prefs.getBoolean("rememberMe", false)) {
            usernameField.setText(prefs.getString("username", ""));
            rememberMeBox.setChecked(true);
        }
    }
    private void saveCredentials(String username, String password, boolean remember) {
        prefs.putBoolean("rememberMe", remember);
        if (remember) {
            prefs.putString("username", username);
            prefs.putString("password", password); // Consider encrypting
        } else {
            prefs.remove("username");
            prefs.remove("password");
        }
        prefs.flush();
    }

    /**
     * Validates the password based on predefined criteria.
     *
     * @param password The password to validate.
     * @return An error message if invalid; otherwise, null.
     */
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

    @Override
    public void show() {
        // Called when this screen becomes the current screen
    }

    @Override
    public void render(float delta) {
        // Clear screen
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Update and draw the stage
        stage.act(delta);
        stage.draw();
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

    @Override
    public void dispose() {
        // Dispose of assets
        stage.dispose();
        skin.dispose();

    }
}
