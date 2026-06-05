package com.github.singularityme.gui;

import net.minecraft.entity.player.EntityPlayer;

import com.github.singularityme.core.SingularityPermissionHelper;
import com.github.singularityme.tile.ISingularityNetworkDevice;

import appeng.api.config.SecurityPermissions;
import appeng.container.AEBaseContainer;
import appeng.helpers.InventoryAction;
import appeng.helpers.MonitorableAction;

/** 奇点终端与 AE2 合成子容器权限守卫，补齐 AE2 原生容器无法识别的虚拟网络权限。 */
public final class SingularityTerminalPermissionGuards {

    private SingularityTerminalPermissionGuards() {}

    /** 判断是否为 AE2 终端自动合成请求动作。 */
    public static boolean isAutoCraftAction(final MonitorableAction action) {
        return action == MonitorableAction.AUTO_CRAFT;
    }

    /** 判断是否为 AE2 容器自动合成请求动作。 */
    public static boolean isAutoCraftAction(final InventoryAction action) {
        return action == InventoryAction.AUTO_CRAFT;
    }

    /** 自动合成请求需要玩家拥有当前奇点网络的 CRAFT 权限。 */
    public static boolean canRequestCrafting(final AEBaseContainer container, final EntityPlayer player) {
        if (container == null) return true;
        return canRequestCrafting(
            container.getTarget(),
            (networkID, permission) -> player != null && SingularityPermissionHelper
                .hasPlayerPermission(player.worldObj, networkID, player, permission));
    }

    /** 自动合成请求核心判断；非奇点目标继续交由 AE2 自身权限系统处理。 */
    static boolean canRequestCrafting(final Object target, final CraftPermissionLookup lookup) {
        if (!(target instanceof ISingularityNetworkDevice device)) return true;
        return lookup != null && lookup.hasPermission(device.getNetworkID(), SecurityPermissions.CRAFT);
    }

    /** 为测试与 mixin 共享的最小权限查询接口。 */
    @FunctionalInterface
    interface CraftPermissionLookup {

        boolean hasPermission(int networkID, SecurityPermissions permission);
    }
}
