package com.github.singularityme.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.block.BlockSingularityDrive;
import com.github.singularityme.block.BlockSingularityExportBus;
import com.github.singularityme.block.BlockSingularityImportBus;
import com.github.singularityme.block.BlockSingularityInterface;
import com.github.singularityme.block.BlockSingularityStorageBus;
import com.github.singularityme.block.BlockSingularityTerminal;
import com.github.singularityme.tile.TileSingularityDrive;
import com.github.singularityme.tile.TileSingularityExportBus;
import com.github.singularityme.tile.TileSingularityImportBus;
import com.github.singularityme.tile.TileSingularityInterface;
import com.github.singularityme.tile.TileSingularityStorageBus;
import com.github.singularityme.tile.TileSingularityTerminal;

import appeng.client.gui.implementations.GuiInterface;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerInterface;
import appeng.container.implementations.ContainerMEMonitorable;
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
            return new ContainerMEMonitorable(player.inventory, terminal);
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
            return new ContainerInterface(player.inventory, iface);
        }
        if (id == BlockSingularityExportBus.GUI_ID && te instanceof TileSingularityExportBus exportBus) {
            final ContainerSingularityExportBus c = new ContainerSingularityExportBus(player.inventory, exportBus);
            setOpenContext(c, te, world, x, y, z);
            return c;
        }
        if (id == BlockSingularityDrive.GUI_ID && te instanceof TileSingularityDrive drive) {
            return new ContainerSingularityDrive(player.inventory, drive);
        }
        if (id == BlockSingularityImportBus.GUI_ID && te instanceof TileSingularityImportBus importBus) {
            final ContainerSingularityImportBus c = new ContainerSingularityImportBus(player.inventory, importBus);
            setOpenContext(c, te, world, x, y, z);
            return c;
        }
        return null;
    }

    private static void setOpenContext(final appeng.container.AEBaseContainer container, final TileEntity te,
        final World world, final int x, final int y, final int z) {
        final ContainerOpenContext ctx = new ContainerOpenContext(te);
        ctx.setWorld(world);
        ctx.setX(x);
        ctx.setY(y);
        ctx.setZ(z);
        ctx.setSide(ForgeDirection.UNKNOWN);
        container.setOpenContext(ctx);
    }

    @Override
    public Object getClientGuiElement(final int id, final EntityPlayer player, final World world, final int x,
        final int y, final int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) return null;

        if (id == BlockSingularityTerminal.GUI_ID && te instanceof TileSingularityTerminal terminal) {
            return new GuiMEMonitorable(player.inventory, terminal);
        }
        if (id == BlockSingularityStorageBus.GUI_ID && te instanceof TileSingularityStorageBus bus) {
            return new GuiSingularityStorageBus(player.inventory, bus);
        }
        if (id == BlockSingularityInterface.GUI_ID && te instanceof TileSingularityInterface iface) {
            return new GuiInterface(player.inventory, iface);
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
        return null;
    }
}
