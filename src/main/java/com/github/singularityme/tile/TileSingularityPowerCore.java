package com.github.singularityme.tile;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.core.SingularityNetworkManager;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;
import appeng.me.GridNode;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import gregtech.api.interfaces.tileentity.IEnergyConnected;

/**
 * Draws EU from GT cables and contributes that buffer to the owner's virtual
 * SingularityGrid energy store.
 */
public class TileSingularityPowerCore extends AENetworkTile implements IGridTickable, IEnergyConnected,
    ISingularityContributionHost, ISingularityNetworkDevice, IAEAppEngInventory {

    public static final int SLOT_ENERGY_CELL = 0;
    public static final int SLOT_DENSE_ENERGY_CELL = 1;
    public static final int SLOT_CREATIVE_ENERGY_CELL = 2;
    public static final int COMPONENT_SLOT_COUNT = 3;

    private static final double ENERGY_CELL_BASE_BUFFER = 200_000.0;
    private static final double DENSE_ENERGY_CELL_BASE_BUFFER = ENERGY_CELL_BASE_BUFFER * 8.0;
    private static final double CREATIVE_ENERGY_CELL_BUFFER = (double) (Long.MAX_VALUE / 10000L);

    private double buffer = 0.0;
    private final AppEngInternalInventory components = new AppEngInternalInventory(this, COMPONENT_SLOT_COUNT);
    private boolean normalizingComponentStacks = false;
    private boolean contributionRetired = true;
    private boolean chunkAccessAvailable = true;
    /** Per-player network index. 0 = unassigned. */
    private int networkID = 0;
    private int gridOwnerPlayerID = -1;
    private boolean defaultNetworkApplied = false;

    public TileSingularityPowerCore() {
        this.components.setMaxStackSize(64);
        this.getProxy()
            .setFlags();
        this.getProxy()
            .setValidSides(EnumSet.noneOf(ForgeDirection.class));
        this.getProxy()
            .setIdlePowerUsage(0.0);
    }

    @Override
    public void onReady() {
        this.contributionRetired = false;
        this.chunkAccessAvailable = SingularityChunkAccess.isHostChunkNetworkAccessible(this);
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
        this.notifyPowerContributionChanged();
    }

    @Override
    public void onChunkUnload() {
        this.retireSingularityContribution();
        this.unregister(false);
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        this.retireSingularityContribution();
        this.unregister(true);
        super.invalidate();
    }

    private void unregister(final boolean permanent) {
        if (this.worldObj == null || this.worldObj.isRemote) return;
        final GridNode node = (GridNode) this.getProxy()
            .getNode();
        if (node != null) {
            final int ownerID = this.gridOwnerPlayerID >= 0 ? this.gridOwnerPlayerID : node.getPlayerID();
            SingularityNetworkManager.INSTANCE.unregisterNodeForOwner(ownerID, this.networkID, node, permanent);
            this.gridOwnerPlayerID = -1;
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_PowerCore(final NBTTagCompound data) {
        data.setDouble("aeBuffer", this.buffer);
        data.setInteger("singularityNetworkID", this.networkID);
        data.setInteger("singularityGridOwner", this.gridOwnerPlayerID);
        data.setBoolean(SingularityNetworkDefaults.NBT_KEY, this.defaultNetworkApplied);
        for (int slot = 0; slot < COMPONENT_SLOT_COUNT; slot++) {
            final ItemStack stack = this.components.getStackInSlot(slot);
            if (stack != null) {
                final NBTTagCompound itemTag = new NBTTagCompound();
                stack.writeToNBT(itemTag);
                data.setTag("componentSlot" + slot, itemTag);
            }
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_PowerCore(final NBTTagCompound data) {
        for (int slot = 0; slot < COMPONENT_SLOT_COUNT; slot++) {
            final String key = "componentSlot" + slot;
            final ItemStack stack = data.hasKey(key) ? ItemStack.loadItemStackFromNBT(data.getCompoundTag(key)) : null;
            this.components.setInventorySlotContents(slot, this.isValidComponent(slot, stack) ? stack : null);
        }
        this.normalizeComponentStacks();
        this.buffer = Math.max(0.0, data.getDouble("aeBuffer"));
        this.clampBufferToCapacity();
        this.networkID = data.hasKey("singularityNetworkID") ? data.getInteger("singularityNetworkID") : 0;
        this.gridOwnerPlayerID = data.hasKey("singularityGridOwner") ? data.getInteger("singularityGridOwner") : -1;
        this.defaultNetworkApplied = data.hasKey(SingularityNetworkDefaults.NBT_KEY)
            ? data.getBoolean(SingularityNetworkDefaults.NBT_KEY)
            : true;
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
            final GridNode node = (GridNode) this.getProxy()
                .getNode();
            if (node != null && node.getPlayerID() >= 0) {
                this.gridOwnerPlayerID = SingularityNetworkManager.INSTANCE
                    .registerNode(node.getPlayerID(), this.networkID, node);
            }
        }
    }

    public double getStoredAEPower() {
        if (this.hasCreativePowerComponent()) return this.getMaxBuffer();
        return this.buffer;
    }

    private void applyDefaultNetwork(final int playerID) {
        if (this.defaultNetworkApplied) return;
        if (this.networkID == 0) {
            this.networkID = SingularityNetworkDefaults.resolveDefaultNetworkID(this, playerID);
        }
        this.defaultNetworkApplied = true;
        this.markDirty();
    }

    public double getMaxAEPower() {
        return this.getMaxBuffer();
    }

    public double getConfiguredPowerCapacity() {
        return this.getMaxBuffer();
    }

    public double getStoredPowerForVirtualStorage() {
        if (!this.isPowerCoreContributionAvailable()) return 0.0;
        return this.hasCreativePowerComponent() ? this.getMaxBuffer() : this.buffer;
    }

    public boolean isPowerCoreContributionAvailable() {
        return this.isContributionAvailable();
    }

    public IInventory getPowerComponentInventory() {
        return this.components;
    }

    public boolean isValidComponent(final int slot, final ItemStack stack) {
        return this.matchesComponent(slot, stack);
    }

    public boolean hasCreativePowerComponent() {
        final ItemStack creative = this.components.getStackInSlot(SLOT_CREATIVE_ENERGY_CELL);
        return creative != null && creative.stackSize > 0 && this.matchesComponent(SLOT_CREATIVE_ENERGY_CELL, creative);
    }

    private boolean matchesComponent(final int slot, final ItemStack stack) {
        if (stack == null) return false;
        final Block block = Block.getBlockFromItem(stack.getItem());
        if (block == null) return false;
        return switch (slot) {
            case SLOT_ENERGY_CELL -> block == AEApi.instance()
                .definitions()
                .blocks()
                .energyCell()
                .maybeBlock()
                .orNull();
            case SLOT_DENSE_ENERGY_CELL -> block == AEApi.instance()
                .definitions()
                .blocks()
                .energyCellDense()
                .maybeBlock()
                .orNull();
            case SLOT_CREATIVE_ENERGY_CELL -> block == AEApi.instance()
                .definitions()
                .blocks()
                .energyCellCreative()
                .maybeBlock()
                .orNull();
            default -> false;
        };
    }

    public int getComponentSlotLimit(final int slot) {
        return slot == SLOT_CREATIVE_ENERGY_CELL ? 1 : 64;
    }

    public void addPowerComponentDrops(final List<ItemStack> drops) {
        for (final ItemStack stack : this.components) {
            if (stack != null) {
                drops.add(stack);
            }
        }
    }

    @Override
    public void saveChanges() {
        this.clampBufferToCapacity();
        this.markDirty();
        this.markForUpdate();
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
        final ItemStack removedStack, final ItemStack newStack) {
        if (this.normalizingComponentStacks) return;
        this.normalizingComponentStacks = true;
        try {
            this.normalizeComponentStacks();
        } finally {
            this.normalizingComponentStacks = false;
        }
        this.clampBufferToCapacity();
        this.notifyPowerContributionChanged();
        this.markDirty();
        this.markForUpdate();
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(20, 20, this.buffer <= 0.001, false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        this.syncChunkAccessState();
        return this.buffer > 0.0 ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    public double extractPowerForVirtualStorage(final double amt, final Actionable mode) {
        if (!this.isPowerCoreContributionAvailable()) return 0.0;
        if (amt <= 0.0) return 0.0;
        if (this.hasCreativePowerComponent()) {
            if (mode == Actionable.MODULATE) {
                this.lockCreativeBuffer();
            }
            return amt;
        }
        final double extracted = Math.min(this.buffer, amt);
        if (mode == Actionable.MODULATE && extracted > 0.0) {
            this.buffer -= extracted;
            if (this.buffer < 0.001) {
                this.buffer = 0.0;
            }
            this.markDirty();
            this.markForUpdate();
        }
        return extracted;
    }

    @Override
    public long injectEnergyUnits(final ForgeDirection side, final long voltage, final long amperage) {
        if (!this.inputEnergyFrom(side)) return 0;
        if (voltage <= 0 || amperage <= 0) return 0;
        if (this.hasCreativePowerComponent()) {
            this.lockCreativeBuffer();
            return 0;
        }

        final double maxBuffer = this.getMaxBuffer();
        final double space = maxBuffer - this.buffer;
        if (space <= 0.0) return 0;

        final double aePerAmp = PowerUnits.EU.convertTo(PowerUnits.AE, (double) voltage);
        if (!Double.isFinite(aePerAmp) || aePerAmp <= 0.0) return 0;

        final long used = Math.min(amperage, (long) Math.floor(space / aePerAmp));
        if (used <= 0) return 0;

        final double before = this.buffer;
        this.buffer += Math.min(space, aePerAmp * (double) used);
        if (this.buffer > maxBuffer) {
            this.buffer = maxBuffer;
        }
        if (before <= 0.001 && this.buffer > 0.001) {
            this.notifyPowerContributionChanged();
        }
        this.markDirty();
        this.markForUpdate();
        return used;
    }

    @Override
    public boolean inputEnergyFrom(final ForgeDirection side) {
        return true;
    }

    @Override
    public boolean outputsEnergyTo(final ForgeDirection side) {
        return false;
    }

    @Override
    public byte getColorization() {
        return -1;
    }

    @Override
    public byte setColorization(final byte color) {
        return -1;
    }

    @Override
    public IGridNode getGridNode(final ForgeDirection dir) {
        return SingularityPhysicalIsolation.getGridNode(this, dir);
    }

    @Override
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.NONE;
    }

    private double getMaxBuffer() {
        if (this.hasCreativePowerComponent()) return CREATIVE_ENERGY_CELL_BUFFER;

        final double multiplier = PowerMultiplier.CONFIG.multiplier;
        double max = 0.0;

        final ItemStack normal = this.components.getStackInSlot(SLOT_ENERGY_CELL);
        if (normal != null) {
            max += normal.stackSize * ENERGY_CELL_BASE_BUFFER * multiplier;
        }

        final ItemStack dense = this.components.getStackInSlot(SLOT_DENSE_ENERGY_CELL);
        if (dense != null) {
            max += dense.stackSize * DENSE_ENERGY_CELL_BASE_BUFFER * multiplier;
        }

        return max;
    }

    private void normalizeComponentStacks() {
        for (int slot = 0; slot < COMPONENT_SLOT_COUNT; slot++) {
            ItemStack stack = this.components.getStackInSlot(slot);
            if (stack == null) {
                continue;
            }
            if (!this.isValidComponent(slot, stack)) {
                this.components.setInventorySlotContents(slot, null);
                continue;
            }
            final int limit = this.getComponentSlotLimit(slot);
            if (stack.stackSize > limit) {
                stack = stack.copy();
                stack.stackSize = limit;
                this.components.setInventorySlotContents(slot, stack);
            }
        }
    }

    private void clampBufferToCapacity() {
        final double maxBuffer = this.getMaxBuffer();
        if (this.hasCreativePowerComponent()) {
            this.buffer = maxBuffer;
            return;
        }
        if (this.buffer > maxBuffer) {
            this.buffer = maxBuffer;
        }
        if (this.buffer < 0.001) {
            this.buffer = 0.0;
        }
    }

    private void lockCreativeBuffer() {
        if (!this.hasCreativePowerComponent()) return;
        final double maxBuffer = this.getMaxBuffer();
        if (this.buffer == maxBuffer) return;
        this.buffer = maxBuffer;
        this.markDirty();
        this.markForUpdate();
    }

    private void notifyPowerContributionChanged() {
        if (this.worldObj == null || this.worldObj.isRemote) return;
        SingularityNetworkManager.INSTANCE.refreshPowerCoreContribution(this);
    }

    @Override
    public void retireSingularityContribution() {
        if (this.contributionRetired) return;
        this.contributionRetired = true;
        this.chunkAccessAvailable = false;
        this.notifyPowerContributionChanged();
    }

    @Override
    public boolean isContributionLoaded() {
        return !this.contributionRetired;
    }

    private boolean isContributionAvailable() {
        return !this.contributionRetired && this.chunkAccessAvailable
            && SingularityChunkAccess.isHostChunkNetworkAccessible(this);
    }

    private void syncChunkAccessState() {
        if (this.worldObj == null || this.worldObj.isRemote || this.contributionRetired) return;

        final boolean available = SingularityChunkAccess.isHostChunkNetworkAccessible(this);
        if (this.chunkAccessAvailable == available) return;

        this.chunkAccessAvailable = available;
        this.notifyPowerContributionChanged();
        this.markForUpdate();
    }
}
