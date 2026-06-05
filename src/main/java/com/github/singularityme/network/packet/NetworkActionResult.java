package com.github.singularityme.network.packet;

/** 网络操作反馈结果，携带 UI 文案 key 与成功/失败语义。 */
public enum NetworkActionResult {

    SUCCESS("gui.singularityme.network_action.success", true),
    DEVICE_ASSIGNED("gui.singularityme.network_action.device_assigned", true),
    NETWORK_NOT_FOUND("gui.singularityme.network_action.network_not_found", false),
    DEVICE_UNAVAILABLE("gui.singularityme.network_action.device_unavailable", false),
    NO_ACCESS("gui.singularityme.network_action.no_access", false);

    public final String translationKey;
    public final boolean success;

    NetworkActionResult(final String translationKey, final boolean success) {
        this.translationKey = translationKey;
        this.success = success;
    }

    /** 从网络包中的 ordinal 安全还原结果枚举。 */
    public static NetworkActionResult fromOrdinal(final int ordinal) {
        final NetworkActionResult[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NETWORK_NOT_FOUND;
    }
}
