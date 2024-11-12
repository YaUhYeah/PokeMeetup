package io.github.pokemeetup.utils;

public class ScalingUtils {


    public static float scale(float value) {
        float scale = 1f;
        return Math.max(value * scale, 1f);
    }


    public static float getPadding() {
        return Math.max(scale(20f), 1f);
    }

}
