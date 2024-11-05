package io.github.pokemeetup.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AudioManager {
    private static AudioManager instance;
    private final Map<SoundEffect, Sound> sounds;
    private final Map<BiomeType, Music> biomeMusic;
    private final Map<String, Sound> customSounds;
    private final float MUSIC_FADE_DURATION = 2.0f;
    private final float FADE_OUT_DURATION = 2f; // 1.5 seconds for fade-out
    private Music menuMusic; // New field for menu music
    private Music currentMusic;
    private BiomeType currentBiome;
    private float masterVolume = 1.0f;
    private float musicVolume = 0.7f;
    private float soundVolume = 1.0f;
    private boolean musicEnabled = true;
    private float fadeOutTimer = 0f;
    private boolean soundEnabled = true;
    private float musicFadeTimer = 0;
    private Music nextMusic;

    private boolean isFadingOut = false;
    private BiomeType pendingBiome; // New field to track the latest biome


    private AudioManager() {
        sounds = new EnumMap<>(SoundEffect.class);
        biomeMusic = new EnumMap<>(BiomeType.class);
        customSounds = new ConcurrentHashMap<>();
        initializeAudio();
    }

    public static AudioManager getInstance() {
        if (instance == null) {
            instance = new AudioManager();
        }
        return instance;
    }

    public static void setInstance(AudioManager instance) {
        AudioManager.instance = instance;
    }

    public void playSound(AudioManager.SoundEffect effect) {
        if (!soundEnabled) return;

        Sound sound = sounds.get(effect);
        if (sound != null) {
            sound.play(soundVolume * masterVolume);
        }
    }

    private void initializeAudio() {
        // Load sound effects
        for (SoundEffect effect : SoundEffect.values()) {
            try {
                Sound sound = Gdx.audio.newSound(Gdx.files.internal(effect.getPath()));
                sounds.put(effect, sound);
            } catch (Exception e) {
                Gdx.app.error("AudioManager", "Failed to load sound: " + effect.getPath());
            }
        }

        // Load menu music and set it to loop
        menuMusic = Gdx.audio.newMusic(Gdx.files.internal("music/menu_music.mp3"));
        menuMusic.setLooping(true);
        menuMusic.setVolume(musicVolume * masterVolume);

        // Load biome music
        loadBiomeMusic(BiomeType.FOREST, "music/forest_theme.mp3");
        loadBiomeMusic(BiomeType.SNOW, "music/snow_theme.mp3");
        loadBiomeMusic(BiomeType.HAUNTED, "music/haunted_theme.mp3");
        loadBiomeMusic(BiomeType.PLAINS, "music/plains_theme.mp3");
    }

    private void loadBiomeMusic(BiomeType biome, String path) {
        if (biomeMusic.containsKey(biome) && biomeMusic.get(biome) != null) {
            biomeMusic.get(biome).dispose(); // Dispose of any existing music for this biome
        }

        try {
            Music music = Gdx.audio.newMusic(Gdx.files.internal(path));
            music.setVolume(musicVolume * masterVolume);
            // Do not set looping here; we'll set it when playing
            biomeMusic.put(biome, music);
        } catch (Exception e) {
            Gdx.app.error("AudioManager", "Failed to load music: " + path + ", error: " + e.getMessage(), e);
        }
    }

    public void playMenuMusic() {
        if (musicEnabled && menuMusic != null && !menuMusic.isPlaying()) {
            menuMusic.setLooping(true); // Ensure it loops
            menuMusic.play();
        }
    }

    public void stopMenuMusic() {
        if (menuMusic != null) {
            menuMusic.stop();
        }
    }

    public Map<SoundEffect, Sound> getSounds() {
        return sounds;
    }

    public Map<BiomeType, Music> getBiomeMusic() {
        return biomeMusic;
    }

    public Map<String, Sound> getCustomSounds() {
        return customSounds;
    }

    public float getMUSIC_FADE_DURATION() {
        return MUSIC_FADE_DURATION;
    }

    public float getFADE_OUT_DURATION() {
        return FADE_OUT_DURATION;
    }

    public Music getMenuMusic() {
        return menuMusic;
    }

    public void setMenuMusic(Music menuMusic) {
        this.menuMusic = menuMusic;
    }

    public Music getCurrentMusic() {
        return currentMusic;
    }

    public void setCurrentMusic(Music currentMusic) {
        this.currentMusic = currentMusic;
    }

    public BiomeType getCurrentBiome() {
        return currentBiome;
    }

    public void setCurrentBiome(BiomeType currentBiome) {
        this.currentBiome = currentBiome;
    }

    public float getMasterVolume() {
        return masterVolume;
    }

    public void setMasterVolume(float masterVolume) {
        this.masterVolume = masterVolume;
    }

    public float getMusicVolume() {
        return musicVolume;
    }

    public void setMusicVolume(float musicVolume) {
        this.musicVolume = musicVolume;
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public void setSoundVolume(float soundVolume) {
        this.soundVolume = soundVolume;
    }

    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    public void setMusicEnabled(boolean musicEnabled) {
        this.musicEnabled = musicEnabled;
    }

    public float getFadeOutTimer() {
        return fadeOutTimer;
    }

    public void setFadeOutTimer(float fadeOutTimer) {
        this.fadeOutTimer = fadeOutTimer;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
    }

    public float getMusicFadeTimer() {
        return musicFadeTimer;
    }

    public void setMusicFadeTimer(float musicFadeTimer) {
        this.musicFadeTimer = musicFadeTimer;
    }

    public Music getNextMusic() {
        return nextMusic;
    }

    public void setNextMusic(Music nextMusic) {
        this.nextMusic = nextMusic;
    }

    public boolean isFadingOut() {
        return isFadingOut;
    }

    public void setFadingOut(boolean fadingOut) {
        isFadingOut = fadingOut;
    }

    public void fadeOutMenuMusic() {
        isFadingOut = true;
        fadeOutTimer = FADE_OUT_DURATION;
    }

    public void updateBiomeMusic(BiomeType newBiome) {
        if (!musicEnabled || (pendingBiome != null && newBiome == pendingBiome)) return;

        pendingBiome = newBiome;

        if (currentMusic == null || !currentMusic.isPlaying()) {
            // No current music playing, start the music for pendingBiome
            startMusicForPendingBiome();
        }
        // Else, current music is playing; will handle transition when it ends
    }

    private void startMusicForPendingBiome() {
        if (pendingBiome != null) {
            Music targetMusic = biomeMusic.get(pendingBiome);
            if (targetMusic != null) {
                currentMusic = targetMusic;
                currentBiome = pendingBiome;
                pendingBiome = null;
                currentMusic.setVolume(Math.max(0, musicVolume * masterVolume));
                currentMusic.setLooping(false); // Don't loop so it can end naturally
                currentMusic.play();
                setMusicCompletionListener();
            } else {
                // No music for the pending biome
                currentMusic = null;
                currentBiome = null;
                pendingBiome = null;
            }
        } else {
            // No pending biome, stop current music
            currentMusic = null;
            currentBiome = null;
        }
    }

    private void setMusicCompletionListener() {
        if (currentMusic != null) {
            currentMusic.setOnCompletionListener(new Music.OnCompletionListener() {
                @Override
                public void onCompletion(Music music) {
                    // When current music completes
                    startMusicForPendingBiome();
                }
            });
        }
    }

    private void updateVolumes() {
        if (currentMusic != null) {
            currentMusic.setVolume(Math.max(0, musicVolume * masterVolume));
        }
    }

    public void update(float delta) {
        if (isFadingOut && menuMusic.isPlaying()) {
            fadeOutTimer -= delta;
            float fadeAlpha = Math.max(0, fadeOutTimer / FADE_OUT_DURATION);
            menuMusic.setVolume(fadeAlpha * 0.7f);

            if (fadeOutTimer <= 0) {
                menuMusic.stop();
                isFadingOut = false;
            }
        }
        // No need to handle music fade transitions since we play the entire song
    }

    // Rest of the class remains the same...

    // Sound effects, volume controls, and other methods...

    // Dispose method
    public void dispose() {
        for (Sound sound : sounds.values()) {
            sound.dispose();
        }
        for (Music music : biomeMusic.values()) {
            music.dispose();
        }
        for (Sound sound : customSounds.values()) {
            sound.dispose();
        }
        sounds.clear();
        biomeMusic.clear();
        customSounds.clear();
    }

    // Enum for SoundEffect remains the same...
    public enum SoundEffect {
        ITEM_PICKUP("sounds/pickup.ogg"),
        MENU_SELECT("sounds/select.ogg"),
        MENU_BACK("sounds/back.ogg"),
        BATTLE_WIN("sounds/battle_win.ogg"),
        CRITICAL_HIT("sounds/critical_hit.ogg"),
        CURSOR_MOVE("sounds/cursor_move.ogg"),
        DAMAGE("sounds/damage.ogg"),
        MOVE_SELECT("sounds/move_select.ogg"),
        NOT_EFFECTIVE("sounds/not_effective.ogg"),
        SUPER_EFFECTIVE("sounds/super_effective.ogg");

        private final String path;

        SoundEffect(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }
}
