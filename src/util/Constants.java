package util;

import core.Settings;

public class Constants {

    public static String getStringAny(Settings primary, Settings fallback,
                                       String[] names, String defaultValue) {
        for (String name : names) {
            if (primary.contains(name)) {
                return primary.getSetting(name);
            }
        }
        for (String name : names) {
            if (fallback.contains(name)) {
                return fallback.getSetting(name);
            }
        }
        return defaultValue;
    }

    public static int getIntAny(Settings primary, Settings fallback,
                                 String[] names, int defaultValue) {
        for (String name : names) {
            if (primary.contains(name)) {
                return primary.getInt(name);
            }
        }
        for (String name : names) {
            if (fallback.contains(name)) {
                return fallback.getInt(name);
            }
        }
        return defaultValue;
    }

    public static double getDoubleAny(Settings primary, Settings fallback,
                                       String[] names, double defaultValue) {
        for (String name : names) {
            if (primary.contains(name)) {
                return primary.getDouble(name);
            }
        }
        for (String name : names) {
            if (fallback.contains(name)) {
                return fallback.getDouble(name);
            }
        }
        return defaultValue;
    }

    public static boolean getBooleanAny(Settings primary,
                                         Settings fallback, String[] names, boolean defaultValue) {
        for (String name : names) {
            if (primary.contains(name)) {
                return primary.getBoolean(name);
            }
        }
        for (String name : names) {
            if (fallback.contains(name)) {
                return fallback.getBoolean(name);
            }
        }
        return defaultValue;
    }

    public static int[] getCsvIntsAny(Settings primary, Settings fallback,
                                       String[] names, int[] defaultValue, int expectedLength) {
        for (String name : names) {
            if (primary.contains(name)) {
                return primary.getCsvInts(name, expectedLength);
            }
        }
        for (String name : names) {
            if (fallback.contains(name)) {
                return fallback.getCsvInts(name, expectedLength);
            }
        }
        return defaultValue.clone();
    }
}
