package com.github.singularityme.network.packet;

/** 网络 UI 操作类型，用于客户端区分反馈来源。 */
public enum NetworkActionType {

    JOIN,
    ASSIGN_DEVICE,
    SET_DEFAULT;

    /** 从网络包中的 ordinal 安全还原操作类型。 */
    public static NetworkActionType fromOrdinal(final int ordinal) {
        final NetworkActionType[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : JOIN;
    }
}
