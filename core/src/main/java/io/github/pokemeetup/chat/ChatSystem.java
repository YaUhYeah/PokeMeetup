package io.github.pokemeetup.chat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.utils.TimeUtils;

import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatSystem {
    // ... existing fields ...
    public static final float CHAT_PADDING = 10f;
    public static final float MIN_CHAT_WIDTH = 300f;
    public static final float MIN_CHAT_HEIGHT = 200f;
    private static final int MAX_MESSAGES = 50;
    private static final float MESSAGE_FADE_TIME = 10f;
    private static final Color WINDOW_BACKGROUND = new Color(0, 0, 0, 0.8f);
    private static final Color[] CHAT_COLORS = {
        new Color(0.8f, 0.3f, 0.3f, 1), // Red
        new Color(0.3f, 0.8f, 0.3f, 1), // Green
        new Color(0.3f, 0.3f, 0.8f, 1), // Blue
        new Color(0.8f, 0.8f, 0.3f, 1), // Yellow
        new Color(0.8f, 0.3f, 0.8f, 1), // Purple
        new Color(0.3f, 0.8f, 0.8f, 1), // Cyan
        new Color(0.8f, 0.5f, 0.3f, 1), // Orange
        new Color(0.5f, 0.8f, 0.3f, 1)  // Lime
    };
    private static float DEFAULT_CHAT_WIDTH = 400f;
    private static float DEFAULT_CHAT_HEIGHT = 200f;
    private final Stage stage;
    private final Skin skin;
    private final GameClient gameClient;
    private final String username;
    private final Queue<ChatMessage> messages;
    private float currentScale = 1f;
    private Table chatWindow;
    private ScrollPane messageScroll;
    private Table messageTable;
    private boolean isActive;
    private float inactiveTimer;
    private boolean isInitialized = false;
    private float chatWidth = DEFAULT_CHAT_WIDTH;
    private float chatHeight = DEFAULT_CHAT_HEIGHT;
    private boolean ignoreNextCharacter = false;
    private TextField inputField;

    public ChatSystem(Stage stage, Skin skin, GameClient gameClient, String username) {
        this.stage = stage;
        this.skin = skin;
        this.gameClient = gameClient;
        this.username = username;
        this.messages = new LinkedList<>();

        createChatUI();
        setupChatHandler();
    }

    public void setSize(float width, float height) {
        this.chatWidth = width;
        this.chatHeight = height;

        if (chatWindow != null) {
            chatWindow.setSize(width, height);

            // Update message scroll size
            if (messageScroll != null) {
                messageScroll.setSize(width, height - 40); // Leave room for input
            }

            // Force layout update
            chatWindow.invalidateHierarchy();
        }
    }

    public void setPosition(float x, float y) {
        if (chatWindow != null) {
            chatWindow.setPosition(x, y);
        }
    }

    public void resize(int width, int height) {
        float chatWidth = Math.max(MIN_CHAT_WIDTH, width * 0.25f);
        float chatHeight = Math.max(MIN_CHAT_HEIGHT, height * 0.3f);

        chatWindow.setSize(chatWidth, chatHeight);
        chatWindow.setPosition(
            CHAT_PADDING,
            height - chatHeight - CHAT_PADDING
        );
    }
    private static final Pattern TEAM_CHAT_PATTERN = Pattern.compile("^#(.*)");




    public boolean isActive() {
        return isActive;
    }

    private void setupChatHandler() {
        gameClient.setChatMessageHandler(this::handleIncomingMessage);
    }

    private void sendMessage(String content) {
        if (content.isEmpty()) return;

        NetworkProtocol.ChatMessage chatMessage = new NetworkProtocol.ChatMessage();
        chatMessage.sender = username;
        chatMessage.content = content;
        chatMessage.timestamp = System.currentTimeMillis();
        chatMessage.type = NetworkProtocol.ChatType.NORMAL;

        gameClient.sendMessage(chatMessage);
        addMessageToChat(chatMessage);
    }

    public void handleIncomingMessage(NetworkProtocol.ChatMessage message) {
        Gdx.app.postRunnable(() -> addMessageToChat(message));
    }

    // Remove the InputListener from setupInputHandling()
    private void setupInputHandling() {
        // Remove the stage capture listener and replace with a normal listener
        // that only activates when specific conditions are met
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                // Only handle T and SLASH when chat is not active
                if (!isActive) {
                    if (keycode == Input.Keys.T || keycode == Input.Keys.SLASH) {
                        activateChat();
                        return true;
                    }
                    return false;
                }

                // When chat is active, handle ESCAPE
                if (keycode == Input.Keys.ESCAPE) {
                    deactivateChat();
                    event.cancel(); // Cancel the event so it doesn't trigger game pause
                    return true;
                }

                return false;
            }
        });

        // Modify the input field listener
        inputField.setTextFieldListener((textField, c) -> {
            if (c == '\n' || c == '\r') {
                String message = textField.getText().trim();
                if (!message.isEmpty()) {
                    sendMessage(message);
                }
                textField.setText("");
                deactivateChat();
            }
        });
    }


    public void activateChat() {
        isActive = true;
        inputField.setVisible(true);
        inputField.setText("");
        inactiveTimer = 0;
        chatWindow.getColor().a = 1f;

        // Delay setting keyboard focus to the next frame
        Gdx.app.postRunnable(() -> {
            stage.setKeyboardFocus(inputField);
        });
    }


    public void deactivateChat() {
        isActive = false;
        inputField.setVisible(false);
        stage.setKeyboardFocus(null);
    }

    public void update(float delta) {
        if (!isActive) {
            inactiveTimer += delta;
            if (inactiveTimer > MESSAGE_FADE_TIME) {
                chatWindow.getColor().a = Math.max(0.3f, 1 - (inactiveTimer - MESSAGE_FADE_TIME) / 2f);
            }
        }

        while (messages.size() > MAX_MESSAGES) {
            ((LinkedList<ChatMessage>) messages).removeFirst();
            messageTable.getChildren().first().remove();
        }
    }

    private void createChatUI() {
        if (isInitialized) {
            // Chat UI has already been initialized
            return;
        }
        chatWindow = new Table();

        // Create background
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(WINDOW_BACKGROUND);
        pixmap.fill();
        TextureRegion bgTexture = new TextureRegion(new Texture(pixmap));
        pixmap.dispose();

        chatWindow.setBackground(new TextureRegionDrawable(bgTexture));

        // Create content table with padding
        Table contentTable = new Table();
        contentTable.pad(10);

        // Create message area
        messageTable = new Table();
        messageScroll = new ScrollPane(messageTable, skin);
        messageScroll.setFadeScrollBars(false);
        messageScroll.setScrollingDisabled(true, false);
        contentTable.add(messageScroll).expand().fill().padBottom(5).row();

        // Create input field
        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle(skin.get(TextField.TextFieldStyle.class));
        textFieldStyle.background = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 0.8f));
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.cursor = skin.newDrawable("white", Color.WHITE);

        inputField = new TextField("", textFieldStyle);
        inputField.setMessageText("Press T to chat...");
        contentTable.add(inputField).expandX().fillX().height(30);

        chatWindow.add(contentTable).expand().fill();
        stage.addActor(chatWindow);

        // Initialize in hidden state
        inputField.setVisible(false);
        setupInputHandling();
        isInitialized = true;
    }

    private Color getSenderColor(String sender) {
        // Get consistent color based on username hash
        int colorIndex = Math.abs(sender.hashCode()) % CHAT_COLORS.length;
        return CHAT_COLORS[colorIndex];
    }

    private void addMessageToChat(NetworkProtocol.ChatMessage message) {
        Table messageEntry = new Table();
        messageEntry.pad(5);

        // Style labels
        Label.LabelStyle timeStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
        timeStyle.fontColor = Color.GRAY;

        Label.LabelStyle nameStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
        nameStyle.fontColor = getSenderColor(message.sender);

        Label.LabelStyle contentStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
        contentStyle.fontColor = Color.WHITE;

        Label timeLabel = new Label(TimeUtils.formatTime(message.timestamp), timeStyle);
        Label nameLabel = new Label(message.sender + ": ", nameStyle);
        Label contentLabel = new Label(message.content, contentStyle);
        contentLabel.setWrap(true);

        messageEntry.add(timeLabel).padRight(5);
        messageEntry.add(nameLabel).padRight(5);
        messageEntry.add(contentLabel).expandX().fillX();

        messages.add(new ChatMessage(message));
        messageTable.add(messageEntry).expandX().fillX().padBottom(2).row();

        messageScroll.scrollTo(0, 0, 0, 0);
        chatWindow.getColor().a = 1f;
        inactiveTimer = 0;
    }

    // Add method to set chat window transparency
    public void setChatAlpha(float alpha) {
        chatWindow.getColor().a = alpha;
    }

    private static class ChatMessage {
        public final String sender;
        public final String content;
        public final long timestamp;

        public ChatMessage(NetworkProtocol.ChatMessage message) {
            this.sender = message.sender;
            this.content = message.content;
            this.timestamp = message.timestamp;
        }
    }
}
