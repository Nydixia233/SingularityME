package com.github.singularityme.network.packet;

/** 网络操作反馈结果，携带 UI 文案 key 与成功/失败语义。 */
public enum NetworkActionResult {

    SUCCESS("gui.singularityme.network_action.success", true),
    JOINED("gui.singularityme.network_action.joined", true),
    ALREADY_MEMBER("gui.singularityme.network_action.already_member", true),
    DEVICE_ASSIGNED("gui.singularityme.network_action.device_assigned", true),
    NETWORK_NOT_FOUND("gui.singularityme.network_action.network_not_found", false),
    PRIVATE_NETWORK("gui.singularityme.network_action.private_network", false),
    PASSWORD_REQUIRED("gui.singularityme.network_action.password_required", false),
    BAD_PASSWORD("gui.singularityme.network_action.bad_password", false),
    BLOCKED("gui.singularityme.network_action.blocked", false),
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
