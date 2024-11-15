package io.github.pokemeetup.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import io.github.pokemeetup.system.gameplay.overworld.biomes.BiomeType;
import io.github.pokemeetup.utils.GameLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AudioManager {
    private static final float AMBIENT_FADE_DURATION = 2.0f;
    private static AudioManager instance;
    private final Map<WeatherSoundEffect, Sound> weatherSounds = new EnumMap<>(WeatherSoundEffect.class);
    private final Map<WeatherSoundEffect, Long> loopingSoundIds = new EnumMap<>(WeatherSoundEffect.class);
    private final Map<SoundEffect, Sound> sounds;
    private final Map<BiomeType, List<Music>> biomeMusic;

    private final Map<String, Sound> customSounds;
    private final float MUSIC_FADE_DURATION = 2.0f;
    private final float FADE_OUT_DURATION = 2f; // 1.5 seconds for fade-out
    private final Map<AmbientSoundType, Sound> ambientSounds;
    private final Map<AmbientSoundType, Long> activeAmbientLoops;
    private final Map<WeatherSoundEffect, Long> loopingStartTimes = new EnumMap<>(WeatherSoundEffect.class);
    private final Map<WeatherSoundEffect, Float> loopingDurations = new EnumMap<>(WeatherSoundEffect.class);
    private List<Music> menuMusicList;

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
    private AmbientSoundType currentAmbient;
    private float ambientVolume = 0.5f;
    private float ambientFadeTimer = 0f;
    private boolean isFadingOutMusic = false;
    private float fadeOutMusicTimer = 0f;
    private boolean isFadingInMusic = false;
    private float fadeInMusicTimer = 0f;

    private AudioManager() {
        sounds = new EnumMap<>(SoundEffect.class);
        biomeMusic = new EnumMap<>(BiomeType.class);
        customSounds = new ConcurrentHashMap<>();
        // Existing initialization...
        this.ambientSounds = new EnumMap<>(AmbientSoundType.class);
        this.activeAmbientLoops = new EnumMap<>(AmbientSoundType.class);
        initializeAmbientSounds();
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

    private void initializeWeatherSounds() {
        for (WeatherSoundEffect effect : WeatherSoundEffect.values()) {
            try {
                Sound sound = Gdx.audio.newSound(Gdx.files.internal(effect.getPath()));
                weatherSounds.put(effect, sound);
            } catch (Exception e) {
                GameLogger.error("Failed to load weather sound: " + effect.getPath());
            }
        }
    }

    private void initializeAmbientSounds() {
        for (AmbientSoundType type : AmbientSoundType.values()) {
            try {
                Sound sound = Gdx.audio.newSound(Gdx.files.internal(type.getPath()));
                ambientSounds.put(type, sound);
                GameLogger.info("Loaded ambient sound: " + type.name());
            } catch (Exception e) {
                GameLogger.error("Failed to load ambient sound: " + type.name() + " - " + e.getMessage());
            }
        }
    }

    public void updateAmbientSound(String ambientName, float intensity) {
        if (!soundEnabled || ambientName == null) {
            stopAllAmbientSounds();
            return;
        }

        try {
            AmbientSoundType newAmbient = AmbientSoundType.valueOf(ambientName);

            // If we're already playing this ambient, just update volume
            if (currentAmbient == newAmbient) {
                updateAmbientVolume(newAmbient, intensity);
                return;
            }

            // Start fading out current ambient if exists
            if (currentAmbient != null) {
                fadeOutAmbient(currentAmbient);
            }

            // Start new ambient sound
            startAmbientSound(newAmbient, intensity);
            currentAmbient = newAmbient;

        } catch (IllegalArgumentException e) {
            GameLogger.error("Invalid ambient sound name: " + ambientName);
        }
    }

    private void startAmbientSound(AmbientSoundType type, float intensity) {
        Sound sound = ambientSounds.get(type);
        if (sound != null && !activeAmbientLoops.containsKey(type)) {
            float volume = calculateAmbientVolume(intensity);
            long id = sound.loop(volume);
            activeAmbientLoops.put(type, id);
            GameLogger.error("Started ambient sound: " + type.name());
        }
    }

    private float calculateAmbientVolume(float intensity) {
        return ambientVolume * soundVolume * masterVolume * intensity;
    }

    public void stopAllAmbientSounds() {
        for (Map.Entry<AmbientSoundType, Long> entry : activeAmbientLoops.entrySet()) {
            Sound sound = ambientSounds.get(entry.getKey());
            if (sound != null) {
                sound.stop(entry.getValue());
            }
        }
        activeAmbientLoops.clear();
        currentAmbient = null;
    }

    private void updateAmbientVolume(AmbientSoundType type, float intensity) {
        Sound sound = ambientSounds.get(type);
        Long id = activeAmbientLoops.get(type);
        if (sound != null && id != null) {
            float volume = calculateAmbientVolume(intensity);
            sound.setVolume(id, volume);
        }
    }

    private void fadeOutAmbient(AmbientSoundType type) {
        Sound sound = ambientSounds.get(type);
        Long id = activeAmbientLoops.get(type);
        if (sound != null && id != null) {
            // Start fade out
            sound.setVolume(id, 0f); // Immediate volume reduction
            sound.stop(id);
            activeAmbientLoops.remove(type);
        }
    }

    public void playWeatherSound(WeatherSoundEffect effect, float volume, float pitch) {
        if (!soundEnabled) return;

        Sound sound = weatherSounds.get(effect);
        if (sound != null) {
            sound.play(volume * soundVolume * masterVolume, pitch, 0);
        }
    }

    public void updateWeatherLoop(WeatherSoundEffect effect, float volume) {
        if (!soundEnabled) {
            stopWeatherLoop(effect);
            return;
        }

        Sound sound = weatherSounds.get(effect);
        if (sound != null) {
            Long currentId = loopingSoundIds.get(effect);

            if (currentId == null || !isPlaying(effect)) {
                // Start new loop
                long id = sound.loop(volume * soundVolume * masterVolume);
                loopingSoundIds.put(effect, id);
                loopingStartTimes.put(effect, System.currentTimeMillis());

                // Store duration based on effect type
                float duration = getEffectDuration(effect);
                loopingDurations.put(effect, duration);
            } else {
                // Update existing loop volume
                sound.setVolume(currentId, volume * soundVolume * masterVolume);
            }
        }
    }

    private float getEffectDuration(WeatherSoundEffect effect) {
        // Define durations for each effect (in seconds)
        switch (effect) {
            case LIGHT_RAIN:
            case WIND:
            case SAND_WIND:
                return 10.0f; // 10-second loop for ambient sounds
            case THUNDER:
                return 3.0f; // 3-second duration for thunder
            default:
                return 5.0f; // Default duration
        }
    }

    public void stopWeatherLoop(WeatherSoundEffect effect) {
        Sound sound = weatherSounds.get(effect);
        Long id = loopingSoundIds.get(effect);
        if (sound != null && id != null) {
            sound.stop(id);
            loopingSoundIds.remove(effect);
            loopingStartTimes.remove(effect);
            loopingDurations.remove(effect);
        }
    }

    public void stopAllWeatherLoops() {
        for (WeatherSoundEffect effect : WeatherSoundEffect.values()) {
            stopWeatherLoop(effect);
        }
    }

    private boolean isPlaying(WeatherSoundEffect effect) {
        Long startTime = loopingStartTimes.get(effect);
        Float duration = loopingDurations.get(effect);
        Long soundId = loopingSoundIds.get(effect);

        if (startTime == null || duration == null || soundId == null) {
            return false;
        }

        // Check if the sound has exceeded its duration
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        // If the sound has played longer than its duration, consider it finished
        if (elapsedTime > duration * 1000) { // Convert duration to milliseconds
            loopingSoundIds.remove(effect);
            loopingStartTimes.remove(effect);
            return false;
        }

        return true;
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
        menuMusicList = new ArrayList<>();
        loadMenuMusic(Arrays.asList(
            "music/Menu-Music-1.mp3",
            "music/Menu-Music-2.mp3",
            "music/Menu-Music-0.mp3",
            "music/Menu-Music-3.mp3",
            "music/Menu-Music-4.mp3"
            // Add more paths as needed
        ));
        loadBiomeMusic(BiomeType.FOREST, (Arrays.asList("music/Forest-Biome-0.mp3", "music/Forest-Biome-1.mp3", "music/Forest-Biome-2.mp3", "music/Forest-Biome-3.mp3")));
        loadBiomeMusic(BiomeType.SNOW, (Arrays.asList("music/Snow-Biome-0.mp3", "music/Snow-Biome-1.mp3")));
        loadBiomeMusic(BiomeType.HAUNTED, (Arrays.asList("music/Haunted-Biome-0.mp3", "music/Haunted-Biome-1.mp3")));
        loadBiomeMusic(BiomeType.PLAINS, (Arrays.asList("music/Plains-Biome-0.mp3", "music/Plains-Biome-1.mp3", "music/Plains-Biome-2.mp3", "music/Plains-Biome-3.mp3", "music/Plains-Biome-4.mp3")));
        loadBiomeMusic(BiomeType.BIG_MOUNTAINS, (Arrays.asList("music/Mountain-Biome-1.mp3", "music/Mountain-Biome-0.mp3")));
        loadBiomeMusic(BiomeType.RAIN_FOREST, (Arrays.asList("music/RainForest-Biome-0.mp3", "music/RainForest-Biome-1.mp3")));
        loadBiomeMusic(BiomeType.DESERT, (Arrays.asList("music/Desert-Biome-0.mp3", "music/Desert-Biome-1.mp3")));

    }

    private void loadMenuMusic(List<String> paths) {
        for (String path : paths) {
            try {
                Music music = Gdx.audio.newMusic(Gdx.files.internal(path));
                music.setVolume(musicVolume * masterVolume);
                menuMusicList.add(music);
            } catch (Exception e) {
                Gdx.app.error("AudioManager", "Failed to load menu music: " + path + ", error: " + e.getMessage(), e);
            }
        }
    }

    private void loadBiomeMusic(BiomeType biome, List<String> paths) {
        List<Music> musicList = new ArrayList<>();
        for (String path : paths) {
            try {
                Music music = Gdx.audio.newMusic(Gdx.files.internal(path));
                music.setVolume(musicVolume * masterVolume);
                musicList.add(music);
            } catch (Exception e) {
                Gdx.app.error("AudioManager", "Failed to load music: " + path + ", error: " + e.getMessage(), e);
            }
        }
        List<Music> put = biomeMusic.put(biome, musicList);
    }

    public void playMenuMusic() {
        if (musicEnabled && (currentMusic == null || !currentMusic.isPlaying())) {
            stopCurrentMusic(); // Ensure no other music is playing
            // Randomly select a menu music track
            int index = MathUtils.random(menuMusicList.size() - 1);
            Music menuMusic = menuMusicList.get(index);
            currentMusic = menuMusic;
            currentBiome = null; // Indicate that we're not in a biome
            currentMusic.setVolume(0f); // Start from 0 volume for fade-in
            currentMusic.setLooping(false); // Don't loop so it can change tracks
            currentMusic.play();
            isFadingInMusic = true;
            fadeInMusicTimer = MUSIC_FADE_DURATION;
            setMusicCompletionListenerForMenu();
        }
    }

    private void setMusicCompletionListenerForMenu() {
        if (currentMusic != null) {
            currentMusic.setOnCompletionListener(new Music.OnCompletionListener() {
                @Override
                public void onCompletion(Music music) {
                    // Play next menu music track
                    playMenuMusic();
                }
            });
        }
    }

    public void stopMenuMusic() {
        if (currentMusic != null && menuMusicList.contains(currentMusic)) {
            isFadingOutMusic = true;
            fadeOutMusicTimer = MUSIC_FADE_DURATION;
        }
    }


    public Map<SoundEffect, Sound> getSounds() {
        return sounds;
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

        if (currentBiome != newBiome) {
            pendingBiome = newBiome;
            GameLogger.info("Pending biome set to: " + pendingBiome);

            if (currentMusic != null && menuMusicList.contains(currentMusic)) {
                // We're transitioning from menu to biome music
                isFadingOutMusic = true;
                fadeOutMusicTimer = MUSIC_FADE_DURATION;
            } else if (currentMusic == null || !currentMusic.isPlaying()) {
                // Start music immediately if no current music is playing
                startMusicForPendingBiome();
            }
        }
    }

    private void startMusicForPendingBiome() {
        if (pendingBiome != null) {
            List<Music> musicList = biomeMusic.get(pendingBiome);
            if (musicList != null && !musicList.isEmpty()) {
                int index = MathUtils.random(musicList.size() - 1);
                Music targetMusic = musicList.get(index);
                currentMusic = targetMusic;
                currentBiome = pendingBiome;
                pendingBiome = null;
                currentMusic.setVolume(0f); // Start from 0 volume for fade-in
                currentMusic.setLooping(false); // Don't loop so it can end naturally
                currentMusic.play();
                GameLogger.info("Started playing music for biome: " + currentBiome);
                setMusicCompletionListener();
                isFadingInMusic = true; // Flag to start fade-in
                fadeInMusicTimer = MUSIC_FADE_DURATION;
            } else {
                GameLogger.error("No music found for biome: " + pendingBiome);
                currentMusic = null;
                currentBiome = null;
                pendingBiome = null;
            }
        } else {
            currentMusic = null;
            currentBiome = null;
        }
    }


    private void updateVolumes() {
        if (currentMusic != null) {
            currentMusic.setVolume(Math.max(0, musicVolume * masterVolume));
        }
        // Update volumes for all biome music
        for (List<Music> musicList : biomeMusic.values()) {
            for (Music music : musicList) {
                music.setVolume(musicVolume * masterVolume);
            }
        }
    }


    public void update(float delta) {

        if (isFadingInMusic && currentMusic != null) {
            fadeInMusicTimer -= delta;
            float progress = 1 - Math.max(0, fadeInMusicTimer / MUSIC_FADE_DURATION);
            float volume = progress * musicVolume * masterVolume;
            currentMusic.setVolume(volume);

            if (fadeInMusicTimer <= 0) {
                isFadingInMusic = false;
                currentMusic.setVolume(musicVolume * masterVolume);
            }
        }
        if (isFadingOutMusic && currentMusic != null) {
            fadeOutMusicTimer -= delta;
            float volume = Math.max(0, (fadeOutMusicTimer / MUSIC_FADE_DURATION) * musicVolume * masterVolume);
            currentMusic.setVolume(volume);

            if (fadeOutMusicTimer <= 0) {
                currentMusic.stop();
                isFadingOutMusic = false;
                currentMusic = null;
                if (pendingBiome != null) {
                    startMusicForPendingBiome(); // Start new music after fade-out
                } else if (menuMusicList.contains(currentMusic)) {
                    playMenuMusic(); // Start menu music after fade-out
                }
            }
        }
    }

    private void stopCurrentMusic() {
        if (currentMusic != null) {
            isFadingOutMusic = true;
            fadeOutMusicTimer = MUSIC_FADE_DURATION;
        }
    }

    public boolean isValidAmbientSound(String soundName) {
        try {
            AmbientSoundType.valueOf(soundName);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void setMusicCompletionListener() {
        if (currentMusic != null) {
            currentMusic.setOnCompletionListener(new Music.OnCompletionListener() {
                @Override
                public void onCompletion(Music music) {
                    if (pendingBiome != null && pendingBiome != currentBiome) {
                        // Biome has changed, start music for new biome
                        startMusicForPendingBiome();
                    } else {
                        // Biome hasn't changed, pick another random song from currentBiome
                        pendingBiome = currentBiome; // Ensure pendingBiome is set
                        startMusicForPendingBiome();
                    }
                }
            });
        }
    }

    public float getAmbientVolume() {
        return ambientVolume;
    }

    public void setAmbientVolume(float volume) {
        this.ambientVolume = Math.max(0f, Math.min(1f, volume));
        // Update all active ambient sounds
        for (AmbientSoundType type : activeAmbientLoops.keySet()) {
            updateAmbientVolume(type, 1f);
        }
    }

    public void dispose() {
        for (Sound sound : sounds.values()) {
            sound.dispose();
        }
        loopingStartTimes.clear();
        loopingDurations.clear();

        for (List<Music> musicList : biomeMusic.values()) {
            for (Music music : musicList) {
                music.dispose();
            }
        }
        biomeMusic.clear();
        for (Sound sound : customSounds.values()) {
            sound.dispose();
        }
        sounds.clear();
        biomeMusic.clear();
        customSounds.clear();
        for (Sound sound : weatherSounds.values()) {
            sound.dispose();
        }
        stopAllAmbientSounds();
        for (Sound sound : ambientSounds.values()) {
            sound.dispose();
        }
        ambientSounds.clear();
        weatherSounds.clear();
        loopingSoundIds.clear();
    }

    public enum AmbientSoundType {
        ;
//        WINTER_WIND("sounds/ambient/winter_wind_loop.ogg");

        private final String path;

        AmbientSoundType(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }


    public enum WeatherSoundEffect {
        LIGHT_RAIN("sounds/weather/rain.ogg"),
        THUNDER("sounds/weather/thunder.ogg"),
        WIND("sounds/weather/wind.ogg"),
        SAND_WIND("sounds/weather/sandwind.ogg");

        private final String path;

        WeatherSoundEffect(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }

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
