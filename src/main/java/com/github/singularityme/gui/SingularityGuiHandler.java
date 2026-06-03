package com.github.singularityme.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.block.BlockSingularityCraftingCore;
import com.github.singularityme.block.BlockSingularityCraftingTerminal;
import com.github.singularityme.block.BlockSingularityDrive;
import com.github.singularityme.block.BlockSingularityExportBus;
import com.github.singularityme.block.BlockSingularityImportBus;
import com.github.singularityme.block.BlockSingularityInterface;
import com.github.singularityme.block.BlockSingularityNetworkTerminal;
import com.github.singularityme.block.BlockSingularityPatternTerminal;
import com.github.singularityme.block.BlockSingularityPowerCore;
import com.github.singularityme.block.BlockSingularityStorageBus;
import com.github.singularityme.block.BlockSingularityTerminal;
import com.github.singularityme.client.ui.NetworkTabUI;
import com.github.singularityme.client.ui.NetworkTerminalUI;
import com.github.singularityme.tile.TileSingularityCraftingCore;
import com.github.singularityme.tile.TileSingularityCraftingTerminal;
import com.github.singularityme.tile.TileSingularityDrive;
import com.github.singularityme.tile.TileSingularityExportBus;
import com.github.singularityme.tile.TileSingularityImportBus;
import com.github.singularityme.tile.TileSingularityInterface;
import com.github.singularityme.tile.TileSingularityNetworkTerminal;
import com.github.singularityme.tile.TileSingularityPatternTerminal;
import com.github.singularityme.tile.TileSingularityPowerCore;
import com.github.singularityme.tile.TileSingularityStorageBus;
import com.github.singularityme.tile.TileSingularityTerminal;

import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerInterface;
import cpw.mods.fml.common.network.IGuiHandler;

/**
 * Registers GUI IDs for Singularity ME blocks.
 *
 * <p>
 * GUI ID assignments:
 * <ul>
 * <li>1 — Singularity Terminal (reuses AE2 ContainerMEMonitorable / GuiMEMonitorable)</li>
 * <li>2 — Singularity Storage Bus (custom ContainerSingularityStorageBus / GuiSingularityStorageBus)</li>
 * <li>3 — Singularity ME Interface (reuses AE2 ContainerInterface / GuiInterface)</li>
 * <li>4 — Singularity Export Bus (custom ContainerSingularityExportBus / GuiSingularityExportBus)</li>
 * <li>5 — Singularity Drive (custom ContainerSingularityDrive / GuiSingularityDrive)</li>
 * <li>6 — Singularity Import Bus (custom ContainerSingularityImportBus / GuiSingularityImportBus)</li>
 * <li>10 — Network Tab (ContainerSingularityNetworkTab / NetworkTabUI)</li>
 * <li>11 — Network Terminal (ContainerSingularityNetworkTerminal / NetworkTerminalUI)</li>
 * </ul>
 */
public class SingularityGuiHandler implements IGuiHandler {

    public static final SingularityGuiHandler INSTANCE = new SingularityGuiHandler();

    private SingularityGuiHandler() {}

    @Override
    public Object getServerGuiElement(final int id, final EntityPlayer player, final World world, final int x,
        final int y, final int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) return null;

        if (id == BlockSingularityTerminal.GUI_ID && te instanceof TileSingularityTerminal terminal) {
            final ContainerSingularityTerminal c = new ContainerSingularityTerminal(player.inventory, terminal);
            setOpenContext(c, te, world, x, y, z, ForgeDirection.UNKNOWN);
            return c;
        }
        if (id == BlockSingularityCraftingTerminal.GUI_ID && te instanceof TileSingularityCraftingTerminal terminal) {
            final ContainerSingularityCraftingTerminal c =
                new ContainerSingularityCraftingTerminal(player.inventory, terminal);
            setOpenContext(c, te, world, x, y, z, ForgeDirection.UNKNOWN);
            return c;
        }
        if (id == BlockSingularityPatternTerminal.GUI_ID && te instanceof TileSingularityPatternTerminal terminal) {
            final ContainerSingularityPatternTerminal c =
                new ContainerSingularityPatternTerminal(player.inventory, terminal);
            setOpenContext(c, te, world, x, y, z, ForgeDirection.UNKNOWN);
            return c;
        }
        if (id == BlockSingularityCraftingCore.GUI_ID && te instanceof TileSingularityCraftingCore core) {
            final ContainerSingularityCraftingCore c = new ContainerSingularityCraftingCore(player.inventory, core);
            setOpenContext(c, te, world, x, y, z, ForgeDirection.UNKNOWN);
            return c;
        }
        if (id == BlockSingularityStorageBus.GUI_ID && te instanceof TileSingularityStorageBus bus) {
            final ContainerSingularityStorageBus c = new ContainerSingularityStorageBus(player.inventory, bus);
            // Set ContainerOpenContext so PacketSwitchGuis(GUI_PRIORITY) can find the tile
            final ContainerOpenContext ctx = new ContainerOpenContext(te);
            ctx.setWorld(world);
            ctx.setX(x);
            ctx.setY(y);
            ctx.setZ(z);
            ctx.setSide(ForgeDirection.UNKNOWN);
            c.setOpenContext(ctx);
            return c;
        }
        if (id == BlockSingularityInterface.GUI_ID && te instanceof TileSingularityInterface iface) {
            final ContainerInterface c = new ContainerInterface(player.inventory, iface);
            setOpenContext(c, te, world, x, y, z, ForgeDirection.UNKNOWN);
            return c;
        }
        if (id == BlockSingularityExportBus.GUI_ID && te instanceof TileSingularityExportBus exportBus) {
            final ContainerSingularityExportBus c = new ContainerSingularityExportBus(player.inventory, exportBus);
            setOpenContext(c, te, world, x, y, z, ForgeDirection.UNKNOWN);
            return c;
        }
        if (id == BlockSingularityDrive.GUI_ID && te instanceof TileSingularityDrive drive) {
            final ContainerSingularityDrive c = new ContainerSingularityDrive(player.inventory, drive);
            setOpenContext(c, te, world, x, y, z, ForgeDirection.UNKNOWN);
            return c;
        }
        if (id == BlockSingularityImportBus.GUI_ID && te instanceof TileSingularityImportBus importBus) {
            final ContainerSingularityImportBus c = new ContainerSingularityImportBus(player.inventory, importBus);
            setOpenContext(c, te, world, x, y, z, ForgeDirection.UNKNOWN);
            return c;
        }
        if (id == BlockSingularityPowerCore.GUI_ID && te instanceof TileSingularityPowerCore powerCore) {
            final ContainerSingularityPowerCore c = new ContainerSingularityPowerCore(player.inventory, powerCore);
            setOpenContext(c, te, world, x, y, z, ForgeDirection.UNKNOWN);
            return c;
        }
        if (id == BlockSingularityNetworkTerminal.GUI_ID && te instanceof TileSingularityNetworkTerminal terminal) {
            return new ContainerSingularityNetworkTerminal(player.inventory, terminal);
        }
        if (id == ContainerSingularityNetworkTab.GUI_ID) {
            return new ContainerSingularityNetworkTab(player.inventory, te);
        }
        return null;
    }

    private static void setOpenContext(final appeng.container.AEBaseContainer container, final TileEntity te,
        final World world, final int x, final int y, final int z, final ForgeDirection side) {
        final ContainerOpenContext ctx = new ContainerOpenContext(te);
        ctx.setWorld(world);
        ctx.setX(x);
        ctx.setY(y);
        ctx.setZ(z);
        ctx.setSide(side);
        container.setOpenContext(ctx);
    }

    @Override
    public Object getClientGuiElement(final int id, final EntityPlayer player, final World world, final int x,
        final int y, final int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) return null;

        if (id == BlockSingularityTerminal.GUI_ID && te instanceof TileSingularityTerminal terminal) {
            return new GuiSingularityTerminal(player.inventory, terminal);
        }
        if (id == BlockSingularityCraftingTerminal.GUI_ID && te instanceof TileSingularityCraftingTerminal terminal) {
            return new GuiSingularityCraftingTerminal(player.inventory, terminal);
        }
        if (id == BlockSingularityPatternTerminal.GUI_ID && te instanceof TileSingularityPatternTerminal terminal) {
            return new GuiSingularityPatternTerminal(player.inventory, terminal);
        }
        if (id == BlockSingularityCraftingCore.GUI_ID && te instanceof TileSingularityCraftingCore core) {
            return new GuiSingularityCraftingCore(player.inventory, core);
        }
        if (id == BlockSingularityStorageBus.GUI_ID && te instanceof TileSingularityStorageBus bus) {
            return new GuiSingularityStorageBus(player.inventory, bus);
        }
        if (id == BlockSingularityInterface.GUI_ID && te instanceof TileSingularityInterface iface) {
            return new GuiSingularityInterface(player.inventory, iface);
        }
        if (id == BlockSingularityExportBus.GUI_ID && te instanceof TileSingularityExportBus exportBus) {
            return new GuiSingularityExportBus(player.inventory, exportBus);
        }
        if (id == BlockSingularityDrive.GUI_ID && te instanceof TileSingularityDrive drive) {
            return new GuiSingularityDrive(player.inventory, drive);
        }
        if (id == BlockSingularityImportBus.GUI_ID && te instanceof TileSingularityImportBus importBus) {
            return new GuiSingularityImportBus(player.inventory, importBus);
        }
        if (id == BlockSingularityPowerCore.GUI_ID && te instanceof TileSingularityPowerCore powerCore) {
            return new GuiSingularityPowerCore(player.inventory, powerCore);
        }
        if (id == BlockSingularityNetworkTerminal.GUI_ID && te instanceof TileSingularityNetworkTerminal) {
            return NetworkTerminalUI.create(te);
        }
        if (id == ContainerSingularityNetworkTab.GUI_ID) {
            return NetworkTabUI.create(te);
        }
        return null;
    }
}
