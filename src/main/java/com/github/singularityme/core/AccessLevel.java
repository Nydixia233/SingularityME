package com.github.singularityme.core;

public enum AccessLevel {

    OWNER,
    ADMIN,
    MEMBER,
    BLOCKED,
    NONE;

    public static AccessLevel fromOrdinal(final int ordinal) {
        final AccessLevel[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
    }
}
