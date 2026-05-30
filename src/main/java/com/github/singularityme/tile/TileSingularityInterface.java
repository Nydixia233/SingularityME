package com.github.singularityme.tile;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.github.singularityme.capability.SingularityMEItemIO;
import com.github.singularityme.core.SingularityNetworkManager;
import com.github.singularityme.proxy.CommonProxy;
import com.google.common.collect.ImmutableSet;
import com.gtnewhorizon.gtnhlib.capability.item.ItemIO;
import com.gtnewhorizon.gtnhlib.capability.item.ItemSink;
import com.gtnewhorizon.gtnhlib.capability.item.ItemSource;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.implementations.tiles.ITileStorageMonitorable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.IPrimaryGuiIconProvider;
import appeng.helpers.IPriorityHost;
import appeng.me.GridAccessException;
import appeng.me.GridNode;
import appeng.me.cache.NetworkMonitor;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.InvOperation;
import appeng.util.inv.IInventoryDestination;
import io.netty.buffer.ByteBuf;

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
    IStorageMonitorable, IInventoryDestination, IInterfaceHost, IPriorityHost, IPowerChannelState,
    IPrimaryGuiIconProvider, ISingularityContributionHost, ISingularityNetworkDevice {

    private final DualityInterface duality = new DualityInterface(this.getProxy(), this);
    private static final int POWERED_FLAG = 1;
    private static final int CHANNEL_FLAG = 2;
    private static final int BOOTING_FLAG = 4;
    private int clientFlags = 0;
    private boolean contributionRetired = true;
    private boolean chunkAccessAvailable = true;
    /** Per-player network index. 0 = unassigned. */
    private int networkID = 0;
    private int gridOwnerPlayerID = -1;
    private boolean defaultNetworkApplied = false;

    public TileSingularityInterface() {
        // DualityInterface mirrors AE2 interfaces and requests a channel. Singularity
        // interfaces live on the player's virtual grid and must not require one.
        this.getProxy()
            .setFlags();
        this.getProxy()
            .setValidSides(EnumSet.noneOf(ForgeDirection.class));
        this.getProxy()
            .setIdlePowerUsage(1.0);
    }

    // ---- AE2 lifecycle ----

    @Override
    public void onReady() {
        this.contributionRetired = false;
        this.chunkAccessAvailable = SingularityChunkAccess.isHostChunkNetworkAccessible(this);
        this.getProxy()
            .setFlags();
        this.getProxy()
            .setValidSides(EnumSet.noneOf(ForgeDirection.class));
        super.onReady();
        this.duality.initialize();

        if (!worldObj.isRemote) {
            GridNode node = (GridNode) getProxy().getNode();
            if (node != null && node.getPlayerID() >= 0) {
                this.applyDefaultNetwork(node.getPlayerID());
                if (this.networkID != 0) {
                    this.gridOwnerPlayerID = SingularityNetworkManager.INSTANCE
                        .registerNode(node.getPlayerID(), this.networkID, node);
                }
            }
            this.duality.notifyNeighbors();
            this.markForUpdate();
        }
    }

    @Override
    public void onChunkUnload() {
        retireSingularityContribution();
        unregister(false);
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        retireSingularityContribution();
        unregister(true);
        super.invalidate();
    }

    @Override
    public void retireSingularityContribution() {
        if (this.contributionRetired) return;
        this.contributionRetired = true;
        this.chunkAccessAvailable = false;
        if (this.worldObj == null || this.worldObj.isRemote) return;

        removeStorageInterceptors();
        postCraftingPatternChange();
        this.duality.notifyNeighbors();
        this.markForUpdate();
    }

    private void removeStorageInterceptors() {
        try {
            if (this.getProxy()
                .getStorage()
                .getItemInventory() instanceof NetworkMonitor<?>itemMonitor) {
                itemMonitor.removeStorageInterceptor(this.duality);
            }
            if (this.getProxy()
                .getStorage()
                .getFluidInventory() instanceof NetworkMonitor<?>fluidMonitor) {
                fluidMonitor.removeStorageInterceptor(this.duality);
            }
        } catch (GridAccessException ignored) {}
    }

    private void postCraftingPatternChange() {
        final IGridNode node = this.getProxy()
            .getNode();
        if (node != null && node.getGrid() != null) {
            node.getGrid()
                .postEvent(new MENetworkCraftingPatternChange(this, node));
        }
    }

    private void unregister(final boolean permanent) {
        if (worldObj == null || worldObj.isRemote) return;
        GridNode node = (GridNode) getProxy().getNode();
        if (node != null) {
            final int ownerID = this.gridOwnerPlayerID >= 0 ? this.gridOwnerPlayerID : node.getPlayerID();
            SingularityNetworkManager.INSTANCE.unregisterNodeForOwner(ownerID, this.networkID, node, permanent);
            this.gridOwnerPlayerID = -1;
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

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkChannelsChanged c) {
        this.duality.notifyNeighbors();
        this.markForUpdate();
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkPowerStatusChange c) {
        this.duality.notifyNeighbors();
        this.markForUpdate();
    }

    public void onFacingChanged() {
        this.getProxy()
            .setValidSides(EnumSet.noneOf(ForgeDirection.class));
        this.duality.notifyNeighbors();
        this.markDirty();
        this.markForUpdate();
    }

    @Override
    public void getDrops(final World w, final int x, final int y, final int z, final List<ItemStack> drops) {
        this.duality.addDrops(drops);
    }

    // ---- NBT ----

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_Interface(final NBTTagCompound data) {
        this.duality.writeToNBT(data);
        data.setInteger("singularityNetworkID", this.networkID);
        data.setInteger("singularityGridOwner", this.gridOwnerPlayerID);
        data.setBoolean(SingularityNetworkDefaults.NBT_KEY, this.defaultNetworkApplied);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_Interface(final NBTTagCompound data) {
        this.duality.readFromNBT(data);
        this.networkID = data.hasKey("singularityNetworkID") ? data.getInteger("singularityNetworkID") : 0;
        this.gridOwnerPlayerID = data.hasKey("singularityGridOwner") ? data.getInteger("singularityGridOwner") : -1;
        this.defaultNetworkApplied = data.hasKey(SingularityNetworkDefaults.NBT_KEY)
            ? data.getBoolean(SingularityNetworkDefaults.NBT_KEY)
            : true;
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream_TileSingularityInterface(final ByteBuf data) {
        this.clientFlags = 0;
        try {
            if (this.getProxy()
                .getEnergy()
                .isNetworkPowered()) {
                this.clientFlags |= POWERED_FLAG;
            }
            if (this.getProxy()
                .getNode()
                .meetsChannelRequirements()) {
                this.clientFlags |= CHANNEL_FLAG;
            }
            if (this.getProxy()
                .getPath()
                .isNetworkBooting()) {
                this.clientFlags |= BOOTING_FLAG;
            }
        } catch (final GridAccessException ignored) {}
        data.writeByte(this.clientFlags);
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream_TileSingularityInterface(final ByteBuf data) {
        final int oldFlags = this.clientFlags;
        this.clientFlags = data.readByte();
        return oldFlags != this.clientFlags;
    }

    // ---- IGridTickable (delegated) ----

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return this.duality.getTickingRequest(node);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (this.contributionRetired) return TickRateModulation.SLEEP;
        this.syncChunkAccessState();
        if (!this.isContributionAvailable()) return TickRateModulation.SLOWER;
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
        if (this.contributionRetired) return;
        this.duality.onChangeInventory(inv, slot, mc, removed, added);
    }

    @Override
    public int[] getAccessibleSlotsBySide(final ForgeDirection side) {
        if (this.contributionRetired) return new int[0];
        return this.duality.getAccessibleSlotsFromSide(side.ordinal());
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        return this.duality.getInventoryByName(name);
    }

    @Override
    public @Nullable IMEMonitor<?> getMEMonitor(@NotNull final IAEStackType<?> type) {
        if (!this.isContributionAvailable()) return null;
        return this.duality.getMEMonitor(type);
    }

    // ---- IInterfaceHost ----

    @Override
    public DualityInterface getInterfaceDuality() {
        return this.duality;
    }

    @Override
    public EnumSet<ForgeDirection> getTargets() {
        if (!this.isContributionAvailable()) return EnumSet.noneOf(ForgeDirection.class);
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
    public ItemStack getSelfRep() {
        return new ItemStack(CommonProxy.blockInterface);
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return AEApi.instance()
            .definitions()
            .blocks()
            .iface()
            .maybeStack(1)
            .orNull();
    }

    @Override
    public void saveChanges() {
        this.markDirty();
    }

    // ---- IStorageMonitorable (delegated) ----

    @Override
    public IMEMonitor<IAEItemStack> getItemInventory() {
        if (!this.isContributionAvailable()) return null;
        return this.duality.getItemInventory();
    }

    @Override
    public IMEMonitor<IAEFluidStack> getFluidInventory() {
        if (!this.isContributionAvailable()) return null;
        return this.duality.getFluidInventory();
    }

    @Override
    public IStorageMonitorable getMonitorable(final ForgeDirection side, final BaseActionSource src) {
        if (!this.isContributionAvailable()) return null;
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
        if (!this.isContributionAvailable()) return;
        this.duality.provideCrafting(craftingTracker);
    }

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        if (!this.isContributionAvailable()) return false;
        return this.duality.pushPattern(patternDetails, table);
    }

    @Override
    public boolean isBusy() {
        if (!this.isContributionAvailable()) return false;
        return this.duality.isBusy();
    }

    // ---- ICraftingRequester (via IInterfaceHost) ----

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        if (!this.isContributionAvailable()) return ImmutableSet.of();
        return this.duality.getRequestedJobs();
    }

    @Override
    public IAEStack<?> injectCraftedItems(final ICraftingLink link, final IAEStack<?> items, final Actionable mode) {
        if (!this.isContributionAvailable()) return items;
        return this.duality.injectCraftedItems(link, items, mode);
    }

    @Override
    public void jobStateChange(final ICraftingLink link) {
        if (!this.isContributionAvailable()) return;
        this.duality.jobStateChange(link);
    }

    private SingularityMEItemIO getItemIO() {
        try {
            return new SingularityMEItemIO(this);
        } catch (final GridAccessException e) {
            return null;
        }
    }

    @Override
    public <T> @Nullable T getCapability(@Nonnull final Class<T> capability, @Nonnull final ForgeDirection side) {
        if (capability == ItemSource.class || capability == ItemSink.class || capability == ItemIO.class) {
            if (!this.isContributionAvailable()) return null;
            return capability.cast(getItemIO());
        }

        return super.getCapability(capability, side);
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
        if (this.contributionRetired) return;
        this.duality.setPriority(newValue);
    }

    // ---- IInventoryDestination ----

    @Override
    public boolean canInsert(final ItemStack stack) {
        if (!this.isContributionAvailable()) return false;
        return this.duality.canInsert(stack);
    }

    // ---- IPowerChannelState (for WAILA / GUI) ----

    @Override
    public boolean isPowered() {
        if (this.worldObj != null && this.worldObj.isRemote) {
            return (this.clientFlags & POWERED_FLAG) == POWERED_FLAG;
        }
        if (!this.isContributionAvailable()) return false;
        return this.getProxy()
            .isPowered();
    }

    @Override
    public boolean isActive() {
        if (this.worldObj != null && this.worldObj.isRemote) {
            return (this.clientFlags & CHANNEL_FLAG) == CHANNEL_FLAG;
        }
        if (!this.isContributionAvailable()) return false;
        return this.getProxy()
            .isActive();
    }

    @Override
    public boolean isBooting() {
        if (this.worldObj != null && this.worldObj.isRemote) {
            return (this.clientFlags & BOOTING_FLAG) == BOOTING_FLAG;
        }
        try {
            return this.getProxy()
                .getPath()
                .isNetworkBooting();
        } catch (final GridAccessException ignored) {
            return false;
        }
    }

    // ---- Cable connection ----

    @Override
    public IGridNode getGridNode(final ForgeDirection dir) {
        return SingularityPhysicalIsolation.getGridNode(this, dir);
    }

    @Override
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.NONE;
    }

    @Override
    public DimensionalCoord getLocation() {
        return this.duality.getLocation();
    }

    @Override
    public boolean isContributionLoaded() {
        return !this.contributionRetired;
    }

    public int getNetworkID() {
        return this.networkID;
    }

    @Override
    public int getGridOwnerPlayerID() {
        return this.gridOwnerPlayerID;
    }

    public void setNetworkID(final int newNetworkID) {
        if (this.networkID == newNetworkID) return;
        this.unregister(true);
        this.networkID = newNetworkID;
        this.gridOwnerPlayerID = -1;
        this.defaultNetworkApplied = true;
        this.markDirty();
        if (this.worldObj != null && !this.worldObj.isRemote) {
            final GridNode node = (GridNode) getProxy().getNode();
            if (node != null && node.getPlayerID() >= 0) {
                this.gridOwnerPlayerID = SingularityNetworkManager.INSTANCE
                    .registerNode(node.getPlayerID(), this.networkID, node);
            }
        }
    }

    private boolean isContributionAvailable() {
        return !this.contributionRetired && this.chunkAccessAvailable
            && this.getProxy()
                .isActive()
            && SingularityChunkAccess.isHostChunkNetworkAccessible(this);
    }

    private void applyDefaultNetwork(final int playerID) {
        if (this.defaultNetworkApplied) return;
        if (this.networkID == 0) {
            this.networkID = SingularityNetworkDefaults.resolveDefaultNetworkID(this, playerID);
        }
        this.defaultNetworkApplied = true;
        this.markDirty();
    }

    private void syncChunkAccessState() {
        if (this.worldObj == null || this.worldObj.isRemote || this.contributionRetired) return;

        final boolean available = SingularityChunkAccess.isHostChunkNetworkAccessible(this);
        if (this.chunkAccessAvailable == available) return;

        this.chunkAccessAvailable = available;
        removeStorageInterceptors();
        postCraftingPatternChange();
        this.duality.notifyNeighbors();
        this.markForUpdate();
    }
}
