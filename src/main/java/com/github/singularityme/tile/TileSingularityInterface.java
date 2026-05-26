package com.github.singularityme.tile;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.core.SingularityNetworkManager;
import com.google.common.collect.ImmutableSet;

import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.implementations.tiles.ITileStorageMonitorable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.IPriorityHost;
import appeng.me.GridNode;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.InvOperation;
import appeng.util.inv.IInventoryDestination;

/**
 * Singularity ME Interface — connects the player's global SingularityGrid to an
 * adjacent machine for autocrafting pattern push/pull.
 *
 * <p>
 * All logic is delegated to {@link DualityInterface}, exactly as AE2's own
 * {@code TileInterface} does. The only difference is that this tile self-registers
 * into the SingularityGrid in {@link #onReady()} instead of relying on local cables.
 */
public class TileSingularityInterface extends AENetworkInvTile implements IGridTickable, ITileStorageMonitorable,
    IStorageMonitorable, IInventoryDestination, IInterfaceHost, IPriorityHost {

    private final DualityInterface duality = new DualityInterface(this.getProxy(), this);

    // ---- AE2 lifecycle ----

    @Override
    public void onReady() {
        this.getProxy()
            .setValidSides(EnumSet.complementOf(EnumSet.of(getTargetSide(), ForgeDirection.UNKNOWN)));
        super.onReady();
        this.duality.initialize();

        if (!worldObj.isRemote) {
            GridNode node = (GridNode) getProxy().getNode();
            if (node != null && node.getPlayerID() >= 0) {
                SingularityNetworkManager.INSTANCE.registerNode(node.getPlayerID(), node);
            }
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

    @Override
    public void markDirty() {
        this.duality.markDirty();
    }

    @Override
    public void gridChanged() {
        this.duality.gridChanged();
    }

    @Override
    public void getDrops(final World w, final int x, final int y, final int z, final List<ItemStack> drops) {
        this.duality.addDrops(drops);
    }

    // ---- NBT ----

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_Interface(final NBTTagCompound data) {
        this.duality.writeToNBT(data);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_Interface(final NBTTagCompound data) {
        this.duality.readFromNBT(data);
    }

    // ---- IGridTickable (delegated) ----

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return this.duality.getTickingRequest(node);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        return this.duality.tickingRequest(node, ticksSinceLastCall);
    }

    // ---- AENetworkInvTile (delegated) ----

    @Override
    public IInventory getInternalInventory() {
        return this.duality.getInternalInventory();
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc, final ItemStack removed,
        final ItemStack added) {
        this.duality.onChangeInventory(inv, slot, mc, removed, added);
    }

    @Override
    public int[] getAccessibleSlotsBySide(final ForgeDirection side) {
        return this.duality.getAccessibleSlotsFromSide(side.ordinal());
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        return this.duality.getInventoryByName(name);
    }

    // ---- IInterfaceHost ----

    @Override
    public DualityInterface getInterfaceDuality() {
        return this.duality;
    }

    @Override
    public EnumSet<ForgeDirection> getTargets() {
        return EnumSet.of(getTargetSide());
    }

    private ForgeDirection getTargetSide() {
        ForgeDirection side = ForgeDirection.getOrientation(worldObj.getBlockMetadata(xCoord, yCoord, zCoord));
        return side == ForgeDirection.UNKNOWN ? ForgeDirection.NORTH : side;
    }

    @Override
    public TileEntity getTileEntity() {
        return this;
    }

    @Override
    public void saveChanges() {
        this.markDirty();
    }

    // ---- IStorageMonitorable (delegated) ----

    @Override
    public IMEMonitor<IAEItemStack> getItemInventory() {
        return this.duality.getItemInventory();
    }

    @Override
    public IMEMonitor<IAEFluidStack> getFluidInventory() {
        return this.duality.getFluidInventory();
    }

    @Override
    public IStorageMonitorable getMonitorable(final ForgeDirection side, final BaseActionSource src) {
        return this.duality.getMonitorable(side, src, this);
    }

    // ---- IConfigurableObject (via IInterfaceHost -> IUpgradeableHost) ----

    @Override
    public IConfigManager getConfigManager() {
        return this.duality.getConfigManager();
    }

    // ---- ICraftingProvider (via IInterfaceHost) ----

    @Override
    public void provideCrafting(final ICraftingProviderHelper craftingTracker) {
        this.duality.provideCrafting(craftingTracker);
    }

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        return this.duality.pushPattern(patternDetails, table);
    }

    @Override
    public boolean isBusy() {
        return this.duality.isBusy();
    }

    // ---- ICraftingRequester (via IInterfaceHost) ----

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return this.duality.getRequestedJobs();
    }

    @Override
    public IAEStack<?> injectCraftedItems(final ICraftingLink link, final IAEStack<?> items, final Actionable mode) {
        return this.duality.injectCraftedItems(link, items, mode);
    }

    @Override
    public void jobStateChange(final ICraftingLink link) {
        this.duality.jobStateChange(link);
    }

    // ---- IUpgradeableHost (via IInterfaceHost) ----

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        return this.duality.getInstalledUpgrades(u);
    }

    @Override
    public TileEntity getTile() {
        return this;
    }

    // ---- IPriorityHost ----

    @Override
    public int getPriority() {
        return this.duality.getPriority();
    }

    @Override
    public void setPriority(final int newValue) {
        this.duality.setPriority(newValue);
    }

    // ---- IInventoryDestination ----

    @Override
    public boolean canInsert(final ItemStack stack) {
        return this.duality.canInsert(stack);
    }

    // ---- Cable connection ----

    @Override
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return this.duality.getCableConnectionType(dir);
    }

    @Override
    public DimensionalCoord getLocation() {
        return this.duality.getLocation();
    }
}
