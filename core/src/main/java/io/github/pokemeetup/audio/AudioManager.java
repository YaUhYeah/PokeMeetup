package io.github.pokemeetup.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AudioManager {
    private static AudioManager instance;
    private final Map<SoundEffect, Sound> sounds;
    private final Map<BiomeType, Music> biomeMusic;
    private final Map<String, Sound> customSounds;
    private final float MUSIC_FADE_DURATION = 2.0f;
    private Music currentMusic;
    private BiomeType currentBiome;
    private float masterVolume = 1.0f;
    private float musicVolume = 0.7f;
    private float soundVolume = 1.0f;
    private boolean musicEnabled = true;
    private boolean soundEnabled = true;
    private float musicFadeTimer = 0;
    private Music nextMusic;

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
            music.setLooping(true);
            music.setVolume(musicVolume * masterVolume);
            biomeMusic.put(biome, music);
        } catch (Exception e) {
            Gdx.app.error("AudioManager", "Failed to load music: " + path + ", error: " + e.getMessage(), e);
        }
    }


    public void playSound(SoundEffect effect) {
        if (!soundEnabled) return;

        Sound sound = sounds.get(effect);
        if (sound != null) {
            sound.play(soundVolume * masterVolume);
        }
    }

    private void loadSettings() {
        Preferences prefs = Gdx.app.getPreferences("audio_settings");
        setMusicVolume(prefs.getFloat("music_volume", 0.7f));
        setSoundVolume(prefs.getFloat("sound_volume", 1.0f));
        setMusicEnabled(prefs.getBoolean("music_enabled", true));
        setSoundEnabled(prefs.getBoolean("sound_enabled", true));
    }

    // Add getters for the UI
    public float getMusicVolume() {
        return musicVolume;
    }

    public void setMusicVolume(float volume) {
        this.musicVolume = Math.max(0, Math.min(1.0f, volume));
        updateVolumes();
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public void setSoundVolume(float volume) {
        this.soundVolume = Math.max(0, Math.min(1.0f, volume));
    }

    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    // Enable/Disable methods
    public void setMusicEnabled(boolean enabled) {
        this.musicEnabled = enabled;
        if (!enabled && currentMusic != null) {
            currentMusic.pause();
        } else if (enabled && currentMusic != null) {
            currentMusic.play();
        }
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
    }

    public void playSoundWithPitch(SoundEffect effect, float pitch) {
        if (!soundEnabled) return;

        Sound sound = sounds.get(effect);
        if (sound != null) {
            sound.play(soundVolume * masterVolume, pitch, 0);
        }
    }
    // Volume control methods

    public void updateBiomeMusic(BiomeType newBiome) {
        if (!musicEnabled || newBiome == currentBiome) return;

        Music targetMusic = biomeMusic.get(newBiome);
        if (targetMusic != null && targetMusic != currentMusic) {
            if (currentMusic != null) {
                // Start fade transition with safe initial volumes
                nextMusic = targetMusic;
                musicFadeTimer = MUSIC_FADE_DURATION;
                currentMusic.setVolume(Math.max(0, musicVolume * masterVolume));
                nextMusic.setVolume(0); // Start at 0 and fade in
            } else {
                // Direct start if no music is playing
                targetMusic.setVolume(Math.max(0, musicVolume * masterVolume));
                targetMusic.play();
                currentMusic = targetMusic;
            }
            currentBiome = newBiome;
        }
    }

    private void updateVolumes() {
        if (currentMusic != null) {
            currentMusic.setVolume(Math.max(0, musicVolume * masterVolume));
        }
        if (nextMusic != null) {
            nextMusic.setVolume(Math.max(0, musicVolume * masterVolume));
        }
    }

    public void update(float delta) {
        if (musicFadeTimer > 0 && nextMusic != null) {
            musicFadeTimer -= delta;
            // Clamp fadeAlpha between 0 and 1
            float fadeAlpha = Math.max(0, Math.min(1, musicFadeTimer / MUSIC_FADE_DURATION));

            if (currentMusic != null) {
                float currentVolume = Math.max(0, (musicVolume * masterVolume) * fadeAlpha);
                currentMusic.setVolume(currentVolume);
            }

            // Ensure next music volume is never negative
            float nextVolume = Math.max(0, (musicVolume * masterVolume) * (1 - fadeAlpha));
            nextMusic.setVolume(nextVolume);

            if (!nextMusic.isPlaying()) {
                nextMusic.play();
            }

            if (musicFadeTimer <= 0) {
                if (currentMusic != null) {
                    currentMusic.stop();
                }
                // Ensure final volume is set correctly
                nextMusic.setVolume(Math.max(0, musicVolume * masterVolume));
                currentMusic = nextMusic;
                nextMusic = null;
                musicFadeTimer = 0;
            }
        }
    }

    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0, Math.min(1.0f, volume));
        updateVolumes();
    }

    // Custom sound management
    public void addCustomSound(String identifier, String path) {
        try {
            Sound sound = Gdx.audio.newSound(Gdx.files.internal(path));
            customSounds.put(identifier, sound);
        } catch (Exception e) {
            Gdx.app.error("AudioManager", "Failed to load custom sound: " + path);
        }
    }

    public void playCustomSound(String identifier) {
        if (!soundEnabled) return;

        Sound sound = customSounds.get(identifier);
        if (sound != null) {
            sound.play(soundVolume * masterVolume);
        }
    }

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

    public enum SoundEffect {
        ITEM_PICKUP("sounds/pickup.ogg"),
        MENU_SELECT("sounds/select.ogg"),
        MENU_BACK("sounds/back.ogg");

        private final String path;

        SoundEffect(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }
}
