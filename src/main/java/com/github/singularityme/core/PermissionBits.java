package com.github.singularityme.core;

import static appeng.api.config.SecurityPermissions.BUILD;
import static appeng.api.config.SecurityPermissions.CRAFT;
import static appeng.api.config.SecurityPermissions.EXTRACT;
import static appeng.api.config.SecurityPermissions.INJECT;

import java.util.EnumSet;

import appeng.api.config.SecurityPermissions;

/** 权限位工具，统一处理 AE2 SecurityPermissions 与网络包/NBT 中的 bit 表示。 */
public final class PermissionBits {

    /** 新授权玩家的默认权限：可建造、注入、取出和发起合成，不含 SECURITY。 */
    public static final int DEFAULT_MEMBER_BITS = toBits(EnumSet.of(BUILD, INJECT, EXTRACT, CRAFT));

    private PermissionBits() {}

    /** 将权限集合转换为按 ordinal 排列的 bit 集。 */
    public static int toBits(final EnumSet<SecurityPermissions> perms) {
        if (perms == null || perms.isEmpty()) return 0;
        int bits = 0;
        for (final SecurityPermissions permission : perms) {
            bits |= 1 << permission.ordinal();
        }
        return bits;
    }

    /** 从按 ordinal 排列的 bit 集还原权限集合，忽略未知高位。 */
    public static EnumSet<SecurityPermissions> fromBits(final int bits) {
        final EnumSet<SecurityPermissions> result = EnumSet.noneOf(SecurityPermissions.class);
        for (final SecurityPermissions permission : SecurityPermissions.values()) {
            if ((bits & (1 << permission.ordinal())) != 0) {
                result.add(permission);
            }
        }
        return result;
    }
}
