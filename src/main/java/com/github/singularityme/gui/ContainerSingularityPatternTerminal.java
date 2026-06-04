package com.github.singularityme.gui;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

import com.github.singularityme.block.BlockSingularityPatternTerminal;
import com.github.singularityme.tile.TileSingularityPatternTerminal;

import appeng.container.PrimaryGui;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.helpers.InventoryAction;
import appeng.helpers.MonitorableAction;

/** 奇点样板终端容器，保留 AE2 样板终端行为并补齐自动合成权限检查。 */
public class ContainerSingularityPatternTerminal extends ContainerPatternTerm {

    public ContainerSingularityPatternTerminal(final InventoryPlayer ip,
        final TileSingularityPatternTerminal terminal) {
        super(ip, terminal);
    }

    @Override
    public PrimaryGui createPrimaryGui() {
        return SingularityPrimaryGui.create(BlockSingularityPatternTerminal.GUI_ID, this);
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
