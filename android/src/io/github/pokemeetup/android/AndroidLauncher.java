package io.github.pokemeetup.android;
import android.os.Bundle;
import android.util.Log;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import io.github.pokemeetup.CreatureCaptureGame;

public class AndroidLauncher extends AndroidApplication {
    private static final String TAG = "AndroidLauncher";
    private CreatureCaptureGame game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);

            AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
            // Configure OpenGL settings
            config.useImmersiveMode = true;
            config.useAccelerometer = false;
            config.useCompass = false;

            // Configure buffer settings
            config.r = 8;
            config.g = 8;
            config.b = 8;
            config.a = 8;
            config.depth = 16;

            // Initialize game
            game = new CreatureCaptureGame();
            initialize(game, config);

            Log.i(TAG, "Game initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing game", e);
            throw new RuntimeException("Failed to initialize game", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (game != null) {
                // Handle resume state
                Log.d(TAG, "Game resumed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resuming game", e);
        }
    }

    @Override
    protected void onPause() {
        try {
            if (game != null) {
                // Handle pause state
                Log.d(TAG, "Game paused");
            }
            super.onPause();
        } catch (Exception e) {
            Log.e(TAG, "Error pausing game", e);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (game != null) {
                // Clean up resources
                game.dispose();
                Log.d(TAG, "Game destroyed and resources cleaned up");
            }
            super.onDestroy();
        } catch (Exception e) {
            Log.e(TAG, "Error destroying game", e);
        }
    }
}
