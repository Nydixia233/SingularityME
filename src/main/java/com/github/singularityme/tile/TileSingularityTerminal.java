package com.github.singularityme.tile;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.github.singularityme.core.SingularityNetworkManager;
import com.github.singularityme.grid.SingularityGrid;

import appeng.api.AEApi;
import appeng.api.config.PinsRows;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.ITerminalPins;
import appeng.api.storage.ITerminalTypeFilterProvider;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.helpers.IPrimaryGuiIconProvider;
import appeng.items.contents.PinsHandler;
import appeng.items.contents.PinsHolder;
import appeng.me.GridAccessException;
import appeng.me.GridNode;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.MonitorableTypeFilter;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;

/**
 * Singularity Terminal exposes the player's global SingularityGrid storage through
 * AE2's standard ME terminal GUI.
 */
public class TileSingularityTerminal extends AENetworkTile
    implements ITerminalHost, IConfigManagerHost, IViewCellStorage, IAEAppEngInventory, ITerminalPins,
    ITerminalTypeFilterProvider, IPrimaryGuiIconProvider, ISingularityNetworkDevice {

    private static final int POWERED_FLAG = 4;
    private static final int BOOTING_FLAG = 8;
    private static final int CHANNEL_FLAG = 16;

    private final IConfigManager cm = new ConfigManager(this);
    private final AppEngInternalInventory viewCell = new AppEngInternalInventory(this, 5);
    private final PinsHolder pinsInv = new PinsHolder(this);
    private final MonitorableTypeFilter typeFilters = new MonitorableTypeFilter();
    private byte spin = 0;
    private int clientFlags = 0;
    /** Per-player network index. 0 = unassigned. */
    private int networkID = 0;
    private int gridOwnerPlayerID = -1;
    private boolean defaultNetworkApplied = false;

    public TileSingularityTerminal() {
        this.getProxy()
            .setFlags();
        this.getProxy()
            .setValidSides(EnumSet.noneOf(ForgeDirection.class));
        this.getProxy()
            .setIdlePowerUsage(0.5);
        this.cm.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.cm.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        this.cm.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
    }

    @Override
    public void onReady() {
        this.getProxy()
            .setFlags();
        this.getProxy()
            .setValidSides(EnumSet.noneOf(ForgeDirection.class));
        super.onReady();
        if (this.worldObj.isRemote) return;
        final GridNode node = (GridNode) this.getProxy()
            .getNode();
        if (node != null && node.getPlayerID() >= 0) {
            this.applyDefaultNetwork(node.getPlayerID());
            if (this.networkID != 0) {
                this.gridOwnerPlayerID = SingularityNetworkManager.INSTANCE
                    .registerNode(node.getPlayerID(), this.networkID, node);
            }
        }
    }

    @MENetworkEventSubscribe
    public void bootingRender(final MENetworkBootingStatusChange event) {
        this.markForUpdate();
    }

    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange event) {
        this.markForUpdate();
    }

    @Override
    public void onChunkUnload() {
        this.unregister(false);
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        this.unregister(true);
        super.invalidate();
    }

    private void unregister(final boolean permanent) {
        if (this.worldObj == null || this.worldObj.isRemote) return;
        final GridNode node = (GridNode) this.getProxy()
            .getNode();
        if (node != null && this.networkID != 0) {
            final int ownerID = this.gridOwnerPlayerID >= 0 ? this.gridOwnerPlayerID : node.getPlayerID();
            SingularityNetworkManager.INSTANCE.unregisterNodeForOwner(ownerID, this.networkID, node, permanent);
            this.gridOwnerPlayerID = -1;
        }
    }

    @Override
    public IMEMonitor<IAEItemStack> getItemInventory() {
        if (!this.isTerminalAccessible()) return null;
        final IStorageGrid storage = this.getStorageGrid();
        return storage == null ? null : storage.getItemInventory();
    }

    @Override
    public IMEMonitor<IAEFluidStack> getFluidInventory() {
        if (!this.isTerminalAccessible()) return null;
        final IStorageGrid storage = this.getStorageGrid();
        return storage == null ? null : storage.getFluidInventory();
    }

    @Override
    public IMEMonitor<?> getMEMonitor(final IAEStackType<?> type) {
        if (!this.isTerminalAccessible()) return null;
        final IStorageGrid storage = this.getStorageGrid();
        return storage == null ? null : storage.getMEMonitor(type);
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.cm;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        this.markDirty();
    }

    @Override
    public IGridNode getActionableNode() {
        return this.getProxy()
            .getNode();
    }

    @Override
    public IGridNode getGridNode(final ForgeDirection dir) {
        return SingularityPhysicalIsolation.getGridNode(this, dir);
    }

    @Override
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.NONE;
    }

    @Override
    public IInventory getViewCellStorage() {
        return this.viewCell;
    }

    @Override
    public PinsHandler getPinsHandler(final EntityPlayer player) {
        return new TerminalPinsHandler(player);
    }

    @Override
    public IGrid getGrid() {
        final SingularityGrid sg = this.getSingularityGrid();
        if (sg != null) return sg;
        try {
            return this.getProxy()
                .getGrid();
        } catch (final GridAccessException ignored) {
            return null;
        }
    }

    @Override
    @NotNull
    public Reference2BooleanMap<IAEStackType<?>> getTypeFilter(final EntityPlayer player) {
        return this.typeFilters.getFilters(player);
    }

    @Override
    public void saveTypeFilter() {
        this.markDirty();
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return AEApi.instance()
            .definitions()
            .parts()
            .terminal()
            .maybeStack(1)
            .orNull();
    }

    @Override
    public void saveChanges() {
        this.markDirty();
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
        final ItemStack removedStack, final ItemStack newStack) {
        this.markDirty();
    }

    public void addTerminalDrops(final List<ItemStack> drops) {
        for (final ItemStack stack : this.viewCell) {
            if (stack != null) {
                drops.add(stack);
            }
        }
    }

    public void setPlacementSpin(final EntityLivingBase placer, final ForgeDirection side) {
        if (side == ForgeDirection.UP || side == ForgeDirection.DOWN) {
            this.spin = (byte) (MathHelper.floor_double((placer.rotationYaw * 4F) / 360F + 2.5D) & 3);
            this.markDirty();
            this.markForUpdate();
        }
    }

    public void rotateSpin() {
        if (this.spin > 3) {
            this.spin = 0;
        }

        this.spin = switch (this.spin) {
            case 0 -> 1;
            case 1 -> 3;
            case 2 -> 0;
            case 3 -> 2;
            default -> 0;
        };

        this.markDirty();
        this.markForUpdate();
    }

    public byte getSpin() {
        return this.spin;
    }

    public int getNetworkID() {
        return this.networkID;
    }

    @Override
    public int getGridOwnerPlayerID() {
        return this.gridOwnerPlayerID;
    }

    /**
     * Reassigns this terminal to a different network. Called server-side when the
     * player changes the network via the in-device network tab.
     */
    public void setNetworkID(final int newNetworkID) {
        if (this.networkID == newNetworkID) return;
        // Unregister from old network (permanent=true so the persisted record is removed)
        this.unregister(true);
        this.networkID = newNetworkID;
        this.gridOwnerPlayerID = -1;
        this.defaultNetworkApplied = true;
        this.markDirty();
        // Re-register into the new network
        if (this.worldObj != null && !this.worldObj.isRemote && this.networkID != 0) {
            final GridNode node = (GridNode) this.getProxy()
                .getNode();
            if (node != null && node.getPlayerID() >= 0) {
                this.gridOwnerPlayerID = SingularityNetworkManager.INSTANCE
                    .registerNode(node.getPlayerID(), this.networkID, node);
            }
        }
    }

    public int getClientFlags() {
        return this.clientFlags;
    }

    public boolean isTerminalActive() {
        return (this.clientFlags & (POWERED_FLAG | CHANNEL_FLAG)) == (POWERED_FLAG | CHANNEL_FLAG);
    }

    public boolean isTerminalPowered() {
        return (this.clientFlags & POWERED_FLAG) == POWERED_FLAG;
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeTerminalNBT(final NBTTagCompound data) {
        this.cm.writeToNBT(data);
        this.viewCell.writeToNBT(data, "viewCell");
        this.pinsInv.writeToNBT(data, "pins");
        this.typeFilters.writeToNBT(data);
        data.setByte("spin", this.spin);
        data.setInteger("singularityNetworkID", this.networkID);
        data.setInteger("singularityGridOwner", this.gridOwnerPlayerID);
        data.setBoolean(SingularityNetworkDefaults.NBT_KEY, this.defaultNetworkApplied);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readTerminalNBT(final NBTTagCompound data) {
        this.cm.readFromNBT(data);
        this.viewCell.readFromNBT(data, "viewCell");
        this.pinsInv.readFromNBT(data, "pins");
        this.typeFilters.readFromNBT(data);
        this.spin = data.getByte("spin");
        this.networkID = data.hasKey("singularityNetworkID") ? data.getInteger("singularityNetworkID") : 0;
        this.gridOwnerPlayerID = data.hasKey("singularityGridOwner") ? data.getInteger("singularityGridOwner") : -1;
        this.defaultNetworkApplied = data.hasKey(SingularityNetworkDefaults.NBT_KEY)
            ? data.getBoolean(SingularityNetworkDefaults.NBT_KEY)
            : true;
    }

    private void applyDefaultNetwork(final int playerID) {
        if (this.defaultNetworkApplied) return;
        if (this.networkID == 0) {
            this.networkID = SingularityNetworkDefaults.resolveDefaultNetworkID(this, playerID);
        }
        this.defaultNetworkApplied = true;
        this.markDirty();
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeTerminalStream(final ByteBuf data) {
        this.clientFlags = this.spin & 3;
        try {
            if (this.isTerminalAccessible() && this.getProxy()
                .getEnergy()
                .isNetworkPowered()) {
                this.clientFlags |= POWERED_FLAG;
            }
            if (this.isTerminalAccessible() && this.isJoinedToSingularityGrid()) {
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
    public boolean readTerminalStream(final ByteBuf data) {
        final int oldFlags = this.clientFlags;
        this.clientFlags = data.readByte();
        this.spin = (byte) (this.clientFlags & 3);
        return oldFlags != this.clientFlags;
    }

    private IStorageGrid getStorageGrid() {
        final SingularityGrid sg = this.getSingularityGrid();
        if (sg == null) return null;
        return sg.getCache(IStorageGrid.class);
    }

    private SingularityGrid getSingularityGrid() {
        final GridNode node = (GridNode) this.getProxy()
            .getNode();
        if (node == null || this.networkID == 0) return null;
        final int ownerID = this.gridOwnerPlayerID >= 0 ? this.gridOwnerPlayerID : node.getPlayerID();
        if (ownerID < 0) return null;
        return SingularityNetworkManager.INSTANCE.getGridForPlayer(ownerID, this.networkID);
    }

    private boolean isJoinedToSingularityGrid() {
        final GridNode node = (GridNode) this.getProxy()
            .getNode();
        if (node == null || this.networkID == 0) return false;
        final int ownerID = this.gridOwnerPlayerID >= 0 ? this.gridOwnerPlayerID : node.getPlayerID();
        return ownerID >= 0 && SingularityNetworkManager.INSTANCE.hasGrid(ownerID, this.networkID);
    }

    private boolean isTerminalAccessible() {
        return this.getProxy()
            .isActive() && SingularityChunkAccess.isHostChunkNetworkAccessible(this);
    }

    private final class TerminalPinsHandler extends PinsHandler {

        private TerminalPinsHandler(final EntityPlayer player) {
            super(TileSingularityTerminal.this.pinsInv, player);
        }

        @Override
        public void setPin(final int idx, final IAEStack<?> stack) {
            super.setPin(idx, stack);
            TileSingularityTerminal.this.markDirty();
        }

        @Override
        public void addItemsToPins(final Iterable<IAEStack<?>> pinsList) {
            super.addItemsToPins(pinsList);
            TileSingularityTerminal.this.markDirty();
        }

        @Override
        public void setPinsRows(final PinsRows craftingRows, final PinsRows playerRows) {
            super.setPinsRows(craftingRows, playerRows);
            TileSingularityTerminal.this.markDirty();
        }
    }
}
