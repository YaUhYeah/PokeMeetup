package io.github.pokemeetup.android;

import android.os.Bundle;
import android.util.Log;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import io.github.pokemeetup.CreatureCaptureGame;
import io.github.pokemeetup.utils.FilePathManager;
import io.github.pokemeetup.utils.GameInitializer;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);

            // Just initialize the path - don't create directories yet
            String internalPath = getFilesDir().getAbsolutePath() + "/";
            GameInitializer.init(internalPath, true);

            AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
            configureAndroidSettings(config);

            CreatureCaptureGame game = new CreatureCaptureGame();
            initialize(game, config);

        } catch (Exception e) {
            Log.e("AndroidLauncher", "Failed to initialize game", e);
            throw new RuntimeException("Game initialization failed", e);
        }
    }

    private void configureAndroidSettings(AndroidApplicationConfiguration config) {
        config.useGL30 = false;
        config.useImmersiveMode = true;
        config.useAccelerometer = false;
        config.useCompass = false;
        config.r = 8;
        config.g = 8;
        config.b = 8;
        config.a = 8;
        config.depth = 16;
        config.maxSimultaneousSounds = 8;
        config.useWakelock = true;
    }
}
