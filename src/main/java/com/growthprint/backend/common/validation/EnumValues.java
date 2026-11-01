package com.growthprint.backend.common.validation;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Parses enum values that arrive as plain strings in request DTOs.
 * <p>
 * {@code Enum.valueOf} throws "No enum constant com.growthprint...", which leaked internal class names
 * into API errors. This produces a client-friendly message listing the allowed values instead
 * (mapped to 400 by {@code GlobalExceptionHandler}).
 */
public final class EnumValues {

    private EnumValues() {
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required. Allowed values: " + allowed(type));
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(normalized)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(
                "Invalid " + fieldName + " '" + truncate(value) + "'. Allowed values: " + allowed(type));
    }

    private static <E extends Enum<E>> String allowed(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
    }

    private static String truncate(String value) {
        String trimmed = value.trim();
        return trimmed.length() > 40 ? trimmed.substring(0, 40) + "..." : trimmed;
    }
}
