package io.github.pokemeetup.chat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import io.github.pokemeetup.multiplayer.client.GameClient;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;
import io.github.pokemeetup.utils.TimeUtils;

import java.util.LinkedList;
import java.util.Queue;

public class ChatSystem {   // ... existing fields ...
    private static final int MAX_MESSAGES = 50;
    private static final float MESSAGE_FADE_TIME = 10f;
    private static final float CHAT_WIDTH = 400f;
    private static final float CHAT_HEIGHT = 200f;
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
    }; // Add constants for positioning
    private static final float CHAT_PADDING = 10f;
    private static final float MIN_CHAT_WIDTH = 300f;
    private static final float MIN_CHAT_HEIGHT = 200f;

    // Add field to track window size
    private float windowWidth;
    private float windowHeight;

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
    private TextField inputField;
    private boolean isActive;
    private float inactiveTimer;

    public ChatSystem(Stage stage, Skin skin, GameClient gameClient, String username) {
        this.stage = stage;
        this.skin = skin;
        this.gameClient = gameClient;
        this.username = username;
        this.messages = new LinkedList<>();

        createChatUI();
        setupChatHandler();
    }

    // Add resize method
    public void resize(int width, int height) {
        // Scale chat window based on screen size
        float scaleX = width / 1920f; // Base resolution
        float scaleY = height / 1080f;
        currentScale = Math.min(scaleX, scaleY);

        // Update chat window size and position
        windowWidth = width;
        windowHeight = height;

        // Calculate chat window size based on screen size
        float chatWidth = Math.max(MIN_CHAT_WIDTH, width * 0.2f);
        float chatHeight = Math.max(MIN_CHAT_HEIGHT, height * 0.3f);

        // Position in top left corner with padding
        chatWindow.setBounds(CHAT_PADDING, height - chatHeight - CHAT_PADDING,
            chatWidth, chatHeight);

        // Update scroll pane size
        messageScroll.setSize(chatWidth, chatHeight - 40); // Leave room for input

        // Scale font if needed

        // Update input field height
        float inputHeight = 30 * currentScale;
        Cell<?> inputCell = chatWindow.getCell(inputField);
        if (inputCell != null) {
            inputCell.height(inputHeight);
        }

        // Force layout update
        chatWindow.invalidate();
        messageScroll.invalidate();
        messageTable.invalidate();
    }

    // Add these methods to support manual positioning if needed
    public void setPosition(float x, float y) {
        chatWindow.setPosition(x, y);
    }

    public void setSize(float width, float height) {
        DEFAULT_CHAT_WIDTH = width;
        DEFAULT_CHAT_HEIGHT = height;
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    // Add method to get current dimensions
    public float[] getDimensions() {
        return new float[]{chatWindow.getWidth(), chatWindow.getHeight()};
    }

    private void setupInputHandling() {
        stage.addListener(event -> {
            if (!(event instanceof InputEvent)) return false;

            if (Gdx.input.isKeyJustPressed(Input.Keys.SLASH)||Gdx.input.isKeyJustPressed(Input.Keys.T) && !isActive) {
                activateChat();
                return true;
            }

            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && isActive) {
                deactivateChat();
                return true;
            }

            return false;
        });

        inputField.setTextFieldListener((textField, c) -> {
            if (c == '\n') {
                String message = textField.getText().trim();
                if (!message.isEmpty()) {
                    sendMessage(message);
                }
                textField.setText("");
                deactivateChat();
            }
        });
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

    private void handleIncomingMessage(NetworkProtocol.ChatMessage message) {
        Gdx.app.postRunnable(() -> addMessageToChat(message));
    }

    private void activateChat() {
        isActive = true;
        inputField.setVisible(true);
        inputField.setText("");
        stage.setKeyboardFocus(inputField);
        inactiveTimer = 0;
        chatWindow.getColor().a = 1f;
    }

    private void deactivateChat() {
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
        // Create a drawable for the window background
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(WINDOW_BACKGROUND);
        pixmap.fill();
        TextureRegion bgTexture = new TextureRegion(new Texture(pixmap));
        pixmap.dispose();

        chatWindow = new Table();
        chatWindow.setBackground(new TextureRegionDrawable(bgTexture));
        chatWindow.setSize(CHAT_WIDTH, CHAT_HEIGHT);
        chatWindow.setPosition(10, 10);

        // Create inner table for content with padding
        Table innerTable = new Table();
        innerTable.pad(10);

        messageTable = new Table();
        messageScroll = new ScrollPane(messageTable, skin);
        messageScroll.setFadeScrollBars(false);
        messageScroll.setScrollingDisabled(true, false);

        // Style input field
        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle(skin.get(TextField.TextFieldStyle.class));
        textFieldStyle.background = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 0.8f));
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.cursor = skin.newDrawable("white", Color.WHITE);

        inputField = new TextField("", textFieldStyle);
        inputField.setMessageText("Press T to chat...");

        innerTable.add(messageScroll).expand().fill().padBottom(5);
        innerTable.row();
        innerTable.add(inputField).expandX().fillX().height(30);

        chatWindow.add(innerTable).expand().fill();

        inputField.setVisible(false);
        setupInputHandling();
        stage.addActor(chatWindow);
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
