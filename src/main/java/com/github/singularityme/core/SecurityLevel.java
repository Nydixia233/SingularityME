package com.github.singularityme.core;

public enum SecurityLevel {

    PUBLIC,
    ENCRYPTED,
    PRIVATE;

    public static SecurityLevel fromOrdinal(final int ordinal) {
        final SecurityLevel[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PRIVATE;
    }
}
