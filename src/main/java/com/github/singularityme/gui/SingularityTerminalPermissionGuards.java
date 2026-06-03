package com.github.singularityme.gui;

import net.minecraft.entity.player.EntityPlayerMP;

import com.github.singularityme.core.SingularityPermissionHelper;
import com.github.singularityme.tile.ISingularityNetworkDevice;

import appeng.api.config.SecurityPermissions;
import appeng.container.AEBaseContainer;
import appeng.helpers.InventoryAction;
import appeng.helpers.MonitorableAction;

/** 奇点终端容器权限守卫，补齐 AE2 原生容器无法识别的虚拟网络权限。 */
final class SingularityTerminalPermissionGuards {

    private SingularityTerminalPermissionGuards() {}

    /** 判断是否为 AE2 终端自动合成请求动作。 */
    static boolean isAutoCraftAction(final MonitorableAction action) {
        return action == MonitorableAction.AUTO_CRAFT;
    }

    /** 判断是否为 AE2 容器自动合成请求动作。 */
    static boolean isAutoCraftAction(final InventoryAction action) {
        return action == InventoryAction.AUTO_CRAFT;
    }

    /** 自动合成请求需要玩家拥有当前奇点网络的 CRAFT 权限。 */
    static boolean canRequestCrafting(final AEBaseContainer container, final EntityPlayerMP player) {
        final Object target = container.getTarget();
        if (!(target instanceof ISingularityNetworkDevice device)) return true;
        return SingularityPermissionHelper.hasPlayerPermission(
            player.worldObj,
            device.getNetworkID(),
            player,
            SecurityPermissions.CRAFT);
    }
}
