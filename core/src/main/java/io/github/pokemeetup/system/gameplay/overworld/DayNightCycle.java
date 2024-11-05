package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.graphics.Color;

public class DayNightCycle {
    private static final float DAWN_START = 5.0f;    // 5:00 AM
    private static final float DAY_START = 6.0f;     // 6:00 AM
    private static final float DUSK_START = 18.0f;   // 6:00 PM
    private static final float NIGHT_START = 19.0f;  // 7:00 PM

    // Base colors for different times of day
    private static final Color DAY_COLOR = new Color(1, 1, 1, 1);
    private static final Color NIGHT_COLOR = new Color(0.2f, 0.2f, 0.4f, 1);
    private static final Color DAWN_DUSK_COLOR = new Color(0.8f, 0.6f, 0.6f, 1);

    /**
     * Converts world time in minutes to the current hour of the day.
     *
     * @param worldTimeInMinutes The current world time in minutes.
     * @return The current hour of the day (0-23).
     */
    public static float getHourOfDay(double worldTimeInMinutes) {
        return (float)((worldTimeInMinutes % (24 * 60)) / 60.0);
    }

    /**
     * Determines the world color based on the current hour of the day.
     *
     * @param hourOfDay The current hour of the day.
     * @return The corresponding Color.
     */
    public static Color getWorldColor(float hourOfDay) {
        Color result = new Color();

        if (hourOfDay >= DAY_START && hourOfDay < DUSK_START) {
            // Daytime
            return DAY_COLOR;
        } else if (hourOfDay >= NIGHT_START || hourOfDay < DAWN_START) {
            // Nighttime
            return NIGHT_COLOR;
        } else if (hourOfDay >= DAWN_START && hourOfDay < DAY_START) {
            // Dawn transition
            float progress = (hourOfDay - DAWN_START);
            return result.set(DAWN_DUSK_COLOR).lerp(DAY_COLOR, progress);
        } else {
            // Dusk transition
            float progress = (hourOfDay - DUSK_START);
            return result.set(DAY_COLOR).lerp(NIGHT_COLOR, progress);
        }
    }

    /**
     * Generates a formatted time string based on world time.
     *
     * @param worldTimeInMinutes The current world time in minutes.
     * @return A formatted time string (e.g., "8:00 AM").
     */
    public static String getTimeString(double worldTimeInMinutes) {
        int hour = (int)(worldTimeInMinutes / 60) % 24;
        int minute = (int)(worldTimeInMinutes % 60);
        String amPm = hour >= 12 ? "PM" : "AM";
        hour = hour % 12;
        if (hour == 0) hour = 12;
        return String.format("%d:%02d %s", hour, minute, amPm);
    }
}
