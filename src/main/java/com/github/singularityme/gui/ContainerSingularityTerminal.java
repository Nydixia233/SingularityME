package com.github.singularityme.gui;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

import com.github.singularityme.tile.TileSingularityTerminal;

import appeng.container.implementations.ContainerMEMonitorable;
import appeng.helpers.InventoryAction;
import appeng.helpers.MonitorableAction;

/** 奇点 ME 终端容器，保留 AE2 行为并补齐自动合成权限检查。 */
public class ContainerSingularityTerminal extends ContainerMEMonitorable {

    public ContainerSingularityTerminal(final InventoryPlayer ip, final TileSingularityTerminal terminal) {
        super(ip, terminal);
    }

    @Override
    public void doMonitorableAction(final MonitorableAction action, final int slot,
        final EntityPlayerMP player) {
        if (SingularityTerminalPermissionGuards.isAutoCraftAction(action)
            && !SingularityTerminalPermissionGuards.canRequestCrafting(this, player)) {
            return;
        }
        super.doMonitorableAction(action, slot, player);
    }

    @Override
    public void doAction(final EntityPlayerMP player, final InventoryAction action, final int slot,
        final long id) {
        if (SingularityTerminalPermissionGuards.isAutoCraftAction(action)
            && !SingularityTerminalPermissionGuards.canRequestCrafting(this, player)) {
            return;
        }
        super.doAction(player, action, slot, id);
    }
}
