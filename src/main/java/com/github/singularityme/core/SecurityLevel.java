package com.github.singularityme.core;

public enum SecurityLevel {

    PUBLIC,
    PRIVATE;

    /** 从网络包/NBT 中安全还原安全级别；旧 ENCRYPTED ordinal=1 会迁移为 PRIVATE。 */
    public static SecurityLevel fromOrdinal(final int ordinal) {
        return ordinal == PUBLIC.ordinal() ? PUBLIC : PRIVATE;
    }
}
