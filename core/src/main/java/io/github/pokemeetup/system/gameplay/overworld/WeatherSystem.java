package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.managers.BiomeTransitionResult;
import io.github.pokemeetup.system.gameplay.overworld.World;
import io.github.pokemeetup.utils.GameLogger;
import io.github.pokemeetup.utils.textures.TextureManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static io.github.pokemeetup.system.gameplay.overworld.WeatherSystem.WeatherType.HEAVY_RAIN;
import static io.github.pokemeetup.system.gameplay.overworld.WeatherSystem.WeatherType.RAIN;


public class WeatherSystem {
    private static final int MAX_PARTICLES = 1000;
    private static final float RAIN_SPEED = 200f;
    private static final float SNOW_SPEED = 50f;
    private static final float SAND_SPEED = 150f;

    private final List<WeatherParticle> particles;
    private WeatherType currentWeather;
    private float intensity;
    private float accumulation;
    private final TextureRegion rainDrop;
    private final TextureRegion snowflake;
    private final TextureRegion sandParticle;

    public enum WeatherType {
        CLEAR,
        RAIN,
        HEAVY_RAIN,
        SNOW,
        BLIZZARD,
        SANDSTORM,
        FOG,
        THUNDERSTORM
    }

    public WeatherSystem() {
        this.particles = new ArrayList<>();
        this.currentWeather = WeatherType.CLEAR;
        this.intensity = 0f;
        this.accumulation = 0f;
        this.rainDrop = TextureManager.effects.findRegion("rain_drop");
        this.snowflake = TextureManager.effects.findRegion("snowflake");
        this.sandParticle = TextureManager.effects.findRegion("sand_particle");
    }


    private void updateWeatherType(BiomeTransitionResult biomeTransition, float temperature, float timeOfDay) {
        // Increase base moisture and reduce thresholds
        float moisture = new Random().nextFloat() * 1.2f; // Increased moisture multiplier
        float mountainInfluence = new Random().nextFloat() * 1.1f; // Increased mountain influence

        // Increased base weather chance
        float weatherChance = moisture * 0.6f + (mountainInfluence * 0.4f); // Increased weights

        if (temperature < 0) {
            // Snow conditions - lower thresholds
            if (weatherChance > 0.6f) { // Was 0.7f
                setWeather(WeatherType.BLIZZARD, 0.8f);
            } else if (weatherChance > 0.3f) { // Was 0.4f
                setWeather(WeatherType.SNOW, 0.5f);
            }
        } else if (temperature > 30) {
            // Hot weather conditions - more frequent sandstorms
            if (moisture < 0.3f) { // Was 0.2f
                setWeather(WeatherType.SANDSTORM, 0.6f);
            }
        } else {
            // Rain conditions - lower thresholds
            if (weatherChance > 0.7f) { // Was 0.8f
                setWeather(WeatherType.THUNDERSTORM, 0.9f);
            } else if (weatherChance > 0.4f) { // Was 0.5f
                setWeather(WeatherType.HEAVY_RAIN, 0.7f);
            } else if (weatherChance > 0.2f) { // Was 0.3f
                setWeather(WeatherType.RAIN, 0.4f);
            }
        }

        // More frequent morning fog
        if (timeOfDay > 5 && timeOfDay < 8 && moisture > 0.5f) { // Was 0.6f
            setWeather(WeatherType.FOG, 0.5f);
        }

        // Add weather persistence
        if (currentWeather != WeatherType.CLEAR && new Random().nextFloat() < 0.7f) {
            // 70% chance to maintain current weather
            return;
        }
    }
    private float weatherStateTimer = 0;
    private static final float MIN_WEATHER_DURATION = 60.0f; // Minimum weather duration in seconds

    public void update(float delta, BiomeTransitionResult biomeTransition, float temperature, float timeOfDay) {
        weatherStateTimer += delta;

        // Only update weather type if enough time has passed
        if (weatherStateTimer >= MIN_WEATHER_DURATION) {
            updateWeatherType(biomeTransition, temperature, timeOfDay);
            weatherStateTimer = 0;
        }

        // Rest of the update method remains the same
        updateParticles(delta);
        generateParticles();
        updateAccumulation(delta);
    }

    private void generateParticles() {
        if (currentWeather == WeatherType.CLEAR || currentWeather == WeatherType.FOG) {
            return;
        }

        int particlesToGenerate = (int)(MAX_PARTICLES * intensity) - particles.size();
        for (int i = 0; i < particlesToGenerate; i++) {
            WeatherParticle particle = createParticle();
            if (particle != null) {  // Only add non-null particles
                particles.add(particle);
            }
        }
    }


    private WeatherParticle createParticle() {
        float x = MathUtils.random(-100, World.WORLD_SIZE * World.TILE_SIZE + 100);
        float y = World.WORLD_SIZE * World.TILE_SIZE + 100;

        switch (currentWeather) {
            case RAIN:
            case HEAVY_RAIN:
            case THUNDERSTORM:
                return new WeatherParticle(x, y, 0, -RAIN_SPEED, rainDrop);
            case SNOW:
            case BLIZZARD:
                return new WeatherParticle(
                    x, y,
                    MathUtils.random(-20, 20),
                    -SNOW_SPEED,
                    snowflake
                );
            case SANDSTORM:
                return new WeatherParticle(
                    x, y,
                    -SAND_SPEED,
                    MathUtils.random(-20, 20),
                    sandParticle
                );
            default:
                return null;
        }
    }

    private void updateParticles(float delta) {
        particles.removeIf(particle -> {
            particle.update(delta);
            return particle.isOutOfBounds();
        });
    }



    public void render(SpriteBatch batch, Vector2 cameraPosition, float viewportWidth, float viewportHeight) {
        if (currentWeather == WeatherType.CLEAR) return;

        float left = cameraPosition.x - viewportWidth/2;
        float right = cameraPosition.x + viewportWidth/2;
        float bottom = cameraPosition.y - viewportHeight/2;
        float top = cameraPosition.y + viewportHeight/2;

        // Create a temporary list to avoid concurrent modification
        List<WeatherParticle> visibleParticles = new ArrayList<>();

        for (WeatherParticle particle : particles) {
            if (particle != null && particle.isInView(left, right, bottom, top)) {
                visibleParticles.add(particle);
            }
        }

        for (WeatherParticle particle : visibleParticles) {
            particle.render(batch);
        }

        if (currentWeather == WeatherType.FOG) {
            renderFog(batch, left, bottom, viewportWidth, viewportHeight);
        }
    }
    private void renderFog(SpriteBatch batch, float x, float y, float width, float height) {
        // Apply fog overlay
        batch.setColor(1, 1, 1, 0.3f * intensity);
        batch.draw(TextureManager.effects.findRegion("fog"), x, y, width, height);
        batch.setColor(1, 1, 1, 1);
    }

    private void updateAccumulation(float delta) {
        float accumulationRate;

        if (currentWeather == WeatherType.SNOW || currentWeather == WeatherType.BLIZZARD) {
            accumulationRate = 0.1f * intensity;
        } else if (currentWeather.equals(RAIN)) {
            accumulationRate = 0.05f * intensity;
        } else if (currentWeather == HEAVY_RAIN || currentWeather == WeatherType.THUNDERSTORM) {
            accumulationRate = 0.15f * intensity;
        } else {
            accumulationRate = -0.05f; // Evaporation/melting
        }

        accumulation = MathUtils.clamp(accumulation + accumulationRate * delta, 0, 1);
    }


    public void setWeather(WeatherType type, float intensity) {
        this.currentWeather = type;
        this.intensity = intensity;
    }

    // Getters
    public WeatherType getCurrentWeather() { return currentWeather; }
    public float getIntensity() { return intensity; }
    public float getAccumulation() { return accumulation; }
}

class WeatherParticle {
    private float x, y;
    private float velocityX, velocityY;
    private final TextureRegion texture;
    private float rotation;

    public WeatherParticle(float x, float y, float velocityX, float velocityY, TextureRegion texture) {
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.texture = texture;
        this.rotation = MathUtils.random(360);
    }

    public void update(float delta) {
        x += velocityX * delta;
        y += velocityY * delta;
        rotation += velocityX * delta * 0.1f;
    }

    public void render(SpriteBatch batch) {
        batch.draw(
            texture,
            x, y,
            texture.getRegionWidth()/2f, texture.getRegionHeight()/2f,
            texture.getRegionWidth(), texture.getRegionHeight(),
            1, 1, rotation
        );
    }

    public boolean isOutOfBounds() {
        return y < -100 || y > World.WORLD_SIZE * World.TILE_SIZE + 100 ||
            x < -100 || x > World.WORLD_SIZE * World.TILE_SIZE + 100;
    }

    public boolean isInView(float left, float right, float bottom, float top) {
        return x >= left && x <= right && y >= bottom && y <= top;
    }
}
