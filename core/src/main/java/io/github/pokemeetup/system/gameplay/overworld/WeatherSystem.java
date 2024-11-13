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

    public void update(float delta, BiomeTransitionResult biomeTransition, float temperature, float timeOfDay) {
        // Update weather based on biome and conditions
        updateWeatherType(biomeTransition, temperature, timeOfDay);

        // Update existing particles
        updateParticles(delta);

        // Generate new particles based on weather type and intensity
        generateParticles();

        // Update accumulation
        updateAccumulation(delta);
    }

    private void updateWeatherType(BiomeTransitionResult biomeTransition, float temperature, float timeOfDay) {
        float moisture = biomeTransition.getMoisture();
        float mountainInfluence = biomeTransition.getMountainInfluence();

        // Base weather chance calculation
        float weatherChance = moisture * 0.5f + (mountainInfluence * 0.3f);

        if (temperature < 0) {
            // Snow conditions
            if (weatherChance > 0.7f) {
                setWeather(WeatherType.BLIZZARD, 0.8f);
            } else if (weatherChance > 0.4f) {
                setWeather(WeatherType.SNOW, 0.5f);
            }
        } else if (temperature > 30) {
            // Hot weather conditions
            if (moisture < 0.2f) {
                setWeather(WeatherType.SANDSTORM, 0.6f);
            }
        } else {
            // Rain conditions
            if (weatherChance > 0.8f) {
                setWeather(WeatherType.THUNDERSTORM, 0.9f);
            } else if (weatherChance > 0.5f) {
                setWeather(HEAVY_RAIN, 0.7f);
            } else if (weatherChance > 0.3f) {
                setWeather(RAIN, 0.4f);
            }
        }

        // Early morning fog
        if (timeOfDay > 5 && timeOfDay < 8 && moisture > 0.6f) {
            setWeather(WeatherType.FOG, 0.5f);
        }
    }

    private void generateParticles() {
        if (currentWeather == WeatherType.CLEAR) return;

        int particlesToGenerate = (int)(MAX_PARTICLES * intensity) - particles.size();
        for (int i = 0; i < particlesToGenerate; i++) {
            particles.add(createParticle());
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
        // Don't render if clear weather
        if (currentWeather == WeatherType.CLEAR) return;

        // Calculate visible area
        float left = cameraPosition.x - viewportWidth/2;
        float right = cameraPosition.x + viewportWidth/2;
        float bottom = cameraPosition.y - viewportHeight/2;
        float top = cameraPosition.y + viewportHeight/2;

        // Render weather effects
        for (WeatherParticle particle : particles) {
            if (particle.isInView(left, right, bottom, top)) {
                particle.render(batch);
            }
        }

        // Render fog if active
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
