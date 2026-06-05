package com.github.singularityme.gui;

import net.minecraft.entity.player.InventoryPlayer;

import com.github.singularityme.block.BlockSingularityInterface;
import com.github.singularityme.tile.TileSingularityInterface;

import appeng.container.PrimaryGui;
import appeng.container.implementations.ContainerInterface;

/** 奇点接口容器，仅覆盖 AE2 子页面返回目标，主体行为沿用 AE2 接口容器。 */
public class ContainerSingularityInterface extends ContainerInterface {

    public ContainerSingularityInterface(final InventoryPlayer ip, final TileSingularityInterface iface) {
        super(ip, iface);
    }

    @Override
    public PrimaryGui createPrimaryGui() {
        return SingularityPrimaryGui.create(BlockSingularityInterface.GUI_ID, this);
    }
}
