package com.won.autoinvestor.common.util;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MapUtils {

    private MapUtils() {
    }

    public static Map<String, Object> map(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    public static String string(Map<String, Object> map, String key) {
        Object value = value(map, key);
        return value == null ? null : value.toString();
    }

    public static boolean bool(Map<String, Object> map, String key) {
        Object value = value(map, key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    public static int integer(Map<String, Object> map, String key) {
        Object value = value(map, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || value.toString().isBlank()) {
            return 0;
        }
        return new BigDecimal(value.toString()).intValue();
    }

    public static long longValue(Map<String, Object> map, String key) {
        Object value = value(map, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || value.toString().isBlank()) {
            return 0L;
        }
        return new BigDecimal(value.toString()).longValue();
    }

    public static BigDecimal decimal(Map<String, Object> map, String key) {
        Object value = value(map, key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value == null || value.toString().isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }

    public static OffsetDateTime offsetDateTime(Map<String, Object> map, String key) {
        Object value = value(map, key);
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime;
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(value.toString());
    }

    public static Object value(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        if (map.containsKey(key)) {
            return map.get(key);
        }
        String upperSnakeKey = upperSnake(key);
        if (map.containsKey(upperSnakeKey)) {
            return map.get(upperSnakeKey);
        }
        String upperKey = key.toUpperCase();
        if (map.containsKey(upperKey)) {
            return map.get(upperKey);
        }
        return null;
    }

    private static String upperSnake(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isUpperCase(current) && i > 0) {
                builder.append('_');
            }
            builder.append(Character.toUpperCase(current));
        }
        return builder.toString();
    }
}
