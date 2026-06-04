package com.github.singularityme.gui;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.SingularityME;
import com.github.singularityme.block.BlockSingularityCraftingTerminal;
import com.github.singularityme.block.BlockSingularityDrive;
import com.github.singularityme.block.BlockSingularityExportBus;
import com.github.singularityme.block.BlockSingularityImportBus;
import com.github.singularityme.block.BlockSingularityInterface;
import com.github.singularityme.block.BlockSingularityPatternTerminal;
import com.github.singularityme.block.BlockSingularityStorageBus;
import com.github.singularityme.block.BlockSingularityTerminal;
import com.github.singularityme.proxy.CommonProxy;

import appeng.container.AEBaseContainer;
import appeng.container.ContainerOpenContext;
import appeng.container.PrimaryGui;
import appeng.helpers.IPrimaryGuiIconProvider;

/**
 * 奇点设备专用的 AE2 原始 GUI 指针。
 *
 * <p>
 * AE2 的 {@link PrimaryGui} 默认只会通过 {@code GuiBridge} 返回原 GUI；奇点设备走 Forge
 * GUI ID，因此需要直接重新打开本模组的 GUI。
 */
class SingularityPrimaryGui extends PrimaryGui {

    private static final int[] AE2_STYLE_DEVICE_GUI_IDS = {
        BlockSingularityTerminal.GUI_ID,
        BlockSingularityCraftingTerminal.GUI_ID,
        BlockSingularityPatternTerminal.GUI_ID,
        BlockSingularityStorageBus.GUI_ID,
        BlockSingularityInterface.GUI_ID,
        BlockSingularityExportBus.GUI_ID,
        BlockSingularityDrive.GUI_ID,
        BlockSingularityImportBus.GUI_ID };

    private final int guiID;

    private SingularityPrimaryGui(final int guiID, final ItemStack icon, final TileEntity te,
        final ForgeDirection side) {
        super(null, icon, te, side == null ? ForgeDirection.UNKNOWN : side);
        this.guiID = guiID;
    }

    /** 根据容器当前打开上下文创建可返回奇点原 GUI 的指针。 */
    static SingularityPrimaryGui create(final int guiID, final AEBaseContainer container) {
        final ContainerOpenContext context = container.getOpenContext();
        TileEntity tile = container.getTileEntity();
        ForgeDirection side = ForgeDirection.UNKNOWN;
        if (context != null) {
            if (context.getTile() != null) {
                tile = context.getTile();
            }
            side = context.getSide();
        }

        final SingularityPrimaryGui primaryGui =
            create(guiID, tile, side, iconFor(guiID, container.getTarget()));
        primaryGui.setSlotIndex(container.getTargetSlotIndex());
        return primaryGui;
    }

    /** 创建指定奇点 GUI ID 的原始 GUI 指针。 */
    static SingularityPrimaryGui create(final int guiID, final TileEntity te, final ForgeDirection side,
        final ItemStack icon) {
        return new SingularityPrimaryGui(guiID, icon, te, side);
    }

    /** 需要覆盖 AE2 子页面返回路径的奇点设备 GUI ID。 */
    static int[] ae2StyleDeviceGuiIDs() {
        return AE2_STYLE_DEVICE_GUI_IDS.clone();
    }

    int getGuiID() {
        return this.guiID;
    }

    @Override
    public void open(final EntityPlayer player) {
        if (player == null || this.te == null) return;
        final World world = this.te.getWorldObj();
        if (world == null) return;
        player.openGui(SingularityME.instance, this.guiID, world, this.te.xCoord, this.te.yCoord, this.te.zCoord);
    }

    private static ItemStack iconFor(final int guiID, final Object target) {
        if (target instanceof IPrimaryGuiIconProvider provider) {
            final ItemStack icon = provider.getPrimaryGuiIcon();
            if (icon != null) return icon;
        }

        return switch (guiID) {
            case BlockSingularityTerminal.GUI_ID -> stack(CommonProxy.blockTerminal);
            case BlockSingularityCraftingTerminal.GUI_ID -> stack(CommonProxy.blockCraftingTerminal);
            case BlockSingularityPatternTerminal.GUI_ID -> stack(CommonProxy.blockPatternTerminal);
            case BlockSingularityStorageBus.GUI_ID -> stack(CommonProxy.blockStorageBus);
            case BlockSingularityInterface.GUI_ID -> stack(CommonProxy.blockInterface);
            case BlockSingularityExportBus.GUI_ID -> stack(CommonProxy.blockExportBus);
            case BlockSingularityDrive.GUI_ID -> stack(CommonProxy.blockDrive);
            case BlockSingularityImportBus.GUI_ID -> stack(CommonProxy.blockImportBus);
            default -> null;
        };
    }

    private static ItemStack stack(final Block block) {
        return block == null ? null : new ItemStack(block);
    }
}
