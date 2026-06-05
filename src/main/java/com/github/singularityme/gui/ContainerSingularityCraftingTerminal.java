package com.github.singularityme.gui;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

import com.github.singularityme.block.BlockSingularityCraftingTerminal;
import com.github.singularityme.tile.TileSingularityCraftingTerminal;

import appeng.container.PrimaryGui;
import appeng.container.implementations.ContainerCraftingTerm;
import appeng.helpers.InventoryAction;
import appeng.helpers.MonitorableAction;

/** 奇点合成终端容器，保留 AE2 合成终端行为并补齐自动合成权限检查。 */
public class ContainerSingularityCraftingTerminal extends ContainerCraftingTerm {

    public ContainerSingularityCraftingTerminal(final InventoryPlayer ip,
        final TileSingularityCraftingTerminal terminal) {
        super(ip, terminal);
    }

    @Override
    public PrimaryGui createPrimaryGui() {
        return SingularityPrimaryGui.create(BlockSingularityCraftingTerminal.GUI_ID, this);
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
