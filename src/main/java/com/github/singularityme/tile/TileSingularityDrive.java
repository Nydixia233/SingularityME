package com.github.singularityme.tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.core.SingularityNetworkManager;

import appeng.api.AEApi;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.GridNode;
import appeng.me.storage.MEInventoryHandler;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.InvOperation;

/**
 * Singularity Drive — accepts up to 10 AE2 storage cells and contributes their
 * capacity to the player's global SingularityGrid.
 *
 * <p>
 * Analogous to {@code TileDrive} but registered with the SingularityGrid instead
 * of a local ME network.
 */
public class TileSingularityDrive extends AENetworkInvTile implements ICellContainer, IGridTickable, ISaveProvider {

    private static final int CELL_COUNT = 10;

    /** The 10 cell slots. */
    private final AppEngInternalInventory cells = new AppEngInternalInventory(this, CELL_COUNT);

    /** Storage priority — higher values are preferred over lower ones. */
    private int priority = 0;

    // ---- AENetworkInvTile requirements ----

    @Override
    public IInventory getInternalInventory() {
        return cells;
    }

    @Override
    public int[] getAccessibleSlotsBySide(final ForgeDirection side) {
        return new int[0]; // cells are not accessible via pipes
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
        final ItemStack removedStack, final ItemStack newStack) {
        try {
            getProxy().getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (GridAccessException ignored) {}
        markDirty();
    }

    // ---- AE2 lifecycle ----

    @Override
    public void onReady() {
        super.onReady();
        if (worldObj.isRemote) return;
        GridNode node = (GridNode) getProxy().getNode();
        if (node != null && node.getPlayerID() >= 0) {
            SingularityNetworkManager.INSTANCE.registerNode(node.getPlayerID(), node);
        }
    }

    @Override
    public void onChunkUnload() {
        unregister();
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        unregister();
        super.invalidate();
    }

    private void unregister() {
        if (worldObj == null || worldObj.isRemote) return;
        GridNode node = (GridNode) getProxy().getNode();
        if (node != null) {
            SingularityNetworkManager.INSTANCE.unregisterNode(node.getPlayerID(), node);
        }
    }

    // ---- ICellContainer ----

    @Override
    @SuppressWarnings("unchecked")
    public List<IMEInventoryHandler> getCellArray(final IAEStackType<?> type) {
        if (!getProxy().isActive()) return Collections.emptyList();

        List<IMEInventoryHandler> result = new ArrayList<>();
        for (int i = 0; i < CELL_COUNT; i++) {
            ItemStack is = cells.getStackInSlot(i);
            if (is == null) continue;
            ICellHandler handler = AEApi.instance()
                .registries()
                .cell()
                .getHandler(is);
            if (handler == null) continue;
            IMEInventoryHandler inv = handler.getCellInventory(is, this, type);
            if (inv != null) {
                MEInventoryHandler wrapper = new MEInventoryHandler<>(inv, type);
                wrapper.setPriority(priority);
                result.add(wrapper);
            }
        }
        return result;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public void setPriorityValue(final int p) {
        priority = p;
        markDirty();
        try {
            getProxy().getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (GridAccessException ignored) {}
    }

    public int getPriorityValue() {
        return priority;
    }

    @Override
    public void saveChanges(final IMEInventory cellInventory) {
        worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
    }

    // ---- IGridTickable ----

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(20, 40, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        return TickRateModulation.SLOWER;
    }

    // ---- cell inventory accessor (for GUI) ----

    public AppEngInternalInventory getCellInventory() {
        return cells;
    }

    // ---- NBT ----

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeExtraNBT(final NBTTagCompound tag) {
        tag.setInteger("priority", priority);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readExtraNBT(final NBTTagCompound tag) {
        priority = tag.getInteger("priority");
    }

    // ---- Cable connection ----

    @Override
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.SMART;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }
}
