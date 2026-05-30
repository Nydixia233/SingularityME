package com.github.singularityme.tile;

import static appeng.util.Platform.readStackByte;
import static appeng.util.Platform.writeStackByte;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.singularityme.core.AEReflection;
import com.github.singularityme.core.SingularityNetworkManager;
import com.github.singularityme.proxy.CommonProxy;

import appeng.api.AEApi;
import appeng.api.definitions.IBlocks;
import appeng.api.definitions.IItemDefinition;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AECableType;
import appeng.api.util.WorldCoord;
import appeng.me.GridNode;
import appeng.me.cluster.IAECluster;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.TileEvent;
import appeng.tile.crafting.TileCraftingStorageTile;
import appeng.tile.events.TileEventType;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import io.netty.buffer.ByteBuf;

/**
 * Single-block Singularity Crafting CPU.
 *
 * <p>
 * This intentionally does not form an AE2 multiblock and does not connect to
 * physical AE networks. It subclasses AE2's crafting storage tile only so the
 * native CraftingGridCache can discover its synthetic CraftingCPUCluster through
 * the existing TileCraftingStorageTile path.
 */
public class TileSingularityCraftingCore extends TileCraftingStorageTile
    implements IAEAppEngInventory, IGridTickable, ISingularityContributionHost, ISingularityNetworkDevice {

    private static final Logger LOG = LogManager.getLogger("SingularityME");
    private static final int COMPONENT_SLOTS = 18;

    private final AppEngInternalInventory components = new AppEngInternalInventory(this, COMPONENT_SLOTS);
    private CraftingCPUCluster syntheticCluster;
    private IAEStack<?> lastServerDisplayStack;
    private IAEStack<?> clientDisplayStack;
    private int clientMonitorCount;
    private boolean contributionRetired = true;
    private boolean chunkAccessAvailable = true;
    /** Per-player network index. 0 = unassigned. */
    private int networkID = 0;
    private int gridOwnerPlayerID = -1;
    private boolean defaultNetworkApplied = false;

    public TileSingularityCraftingCore() {
        this.components.setMaxStackSize(1);
        this.getProxy()
            .setFlags();
        this.getProxy()
            .setValidSides(EnumSet.noneOf(ForgeDirection.class));
        this.getProxy()
            .setIdlePowerUsage(0.0);
    }

    public AppEngInternalInventory getComponentInventory() {
        return this.components;
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

    public boolean isComponentLocked() {
        return this.isContributionAvailable() && this.syntheticCluster != null && this.syntheticCluster.isBusy();
    }

    public int getConfiguredCoProcessors() {
        int total = 0;
        for (final ItemStack stack : this.components) {
            total += getCoProcessorValue(stack);
        }
        return Math.max(0, total);
    }

    public int getMonitorCount() {
        int total = 0;
        for (final ItemStack stack : this.components) {
            if (isMonitorComponent(stack)) {
                total++;
            }
        }
        return total;
    }

    public boolean hasMonitorPanel() {
        if (this.worldObj != null && this.worldObj.isRemote) {
            return this.clientMonitorCount > 0;
        }
        return this.getMonitorCount() > 0;
    }

    public IAEStack<?> getMonitorDisplayStack() {
        if (this.worldObj != null && this.worldObj.isRemote) {
            return this.clientDisplayStack;
        }
        return this.getServerDisplayStack();
    }

    public boolean isValidComponent(final ItemStack stack) {
        return getStorageBytes(stack) > 0 || getCoProcessorValue(stack) > 0 || isMonitorComponent(stack);
    }

    public boolean isCoreBusy() {
        return this.isContributionAvailable() && this.syntheticCluster != null && this.syntheticCluster.isBusy();
    }

    @Override
    protected ItemStack getItemFromTile(final Object obj) {
        return CommonProxy.blockCraftingCore == null ? null : new ItemStack(CommonProxy.blockCraftingCore, 1);
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
        this.updateIdlePowerUsage();
        final GridNode node = (GridNode) this.getProxy()
            .getNode();
        if (node != null && node.getPlayerID() >= 0) {
            this.applyDefaultNetwork(node.getPlayerID());
            if (this.networkID != 0) {
                this.gridOwnerPlayerID = SingularityNetworkManager.INSTANCE
                    .registerNode(node.getPlayerID(), this.networkID, node);
            }
        }
        this.rebuildSyntheticCluster();
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

    @Override
    public void retireSingularityContribution() {
        if (this.contributionRetired) return;
        this.contributionRetired = true;
        this.chunkAccessAvailable = false;
        this.destroySyntheticCluster();
        this.getProxy()
            .setIdlePowerUsage(0.0);
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

    @Override
    public void updateMultiBlock() {
        // Singularity crafting cores are intentionally single-block CPUs.
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public void updateStatus(final CraftingCPUCluster cluster) {
        if (cluster == null || cluster == this.syntheticCluster) {
            super.updateStatus(cluster);
        }
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
    public long getStorageBytes() {
        long total = 0;
        for (final ItemStack stack : this.components) {
            final long value = getStorageBytes(stack);
            if (value == Long.MAX_VALUE) return Long.MAX_VALUE;
            if (Long.MAX_VALUE - total < value) return Long.MAX_VALUE;
            total += value;
        }
        return total;
    }

    @Override
    public IAECluster getCluster() {
        if (!this.isContributionAvailable()) return null;
        return this.syntheticCluster;
    }

    @Override
    public boolean isFormed() {
        if (this.worldObj != null && this.worldObj.isRemote) {
            return super.isFormed();
        }
        return this.isContributionAvailable() && this.syntheticCluster != null;
    }

    @Override
    public void updateCPUClusters(final appeng.api.networking.events.MENetworkCraftingPatternChange event) {
        if (this.isContributionAvailable() && this.syntheticCluster != null) {
            this.syntheticCluster.onPatternChange();
        }
    }

    @Override
    public void disconnect(final boolean affectWorld) {
        this.destroySyntheticCluster();
    }

    @Override
    public void saveChanges() {
        this.markDirty();
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
        final ItemStack removedStack, final ItemStack newStack) {
        this.markDirty();
        if (this.worldObj == null || this.worldObj.isRemote) return;
        this.updateIdlePowerUsage();
        if (!this.isComponentLocked()) {
            this.rebuildSyntheticCluster();
        }
        this.syncMonitorDisplayStack();
        this.markForUpdate();
    }

    public void addCoreDrops(final List<ItemStack> drops) {
        for (final ItemStack stack : this.components) {
            if (stack != null) {
                drops.add(stack);
            }
        }
    }

    public void destroyForBlockBreak() {
        this.destroySyntheticCluster();
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(5, 20, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (this.contributionRetired) return TickRateModulation.SLEEP;
        this.syncChunkAccessState();
        if (!this.isContributionAvailable()) return TickRateModulation.SLOWER;
        this.syncMonitorDisplayStack();
        return this.hasMonitorPanel() || this.isCoreBusy() ? TickRateModulation.SAME : TickRateModulation.SLOWER;
    }

    @Override
    public boolean requiresTESR() {
        return this.hasMonitorPanel() && this.getMonitorDisplayStack() != null;
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeSingularityCraftingCoreNBT(final NBTTagCompound data) {
        this.components.writeToNBT(data, "components");
        data.setInteger("coProcessors", this.getConfiguredCoProcessors());
        data.setInteger("singularityNetworkID", this.networkID);
        data.setInteger("singularityGridOwner", this.gridOwnerPlayerID);
        data.setBoolean(SingularityNetworkDefaults.NBT_KEY, this.defaultNetworkApplied);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readSingularityCraftingCoreNBT(final NBTTagCompound data) {
        this.components.readFromNBT(data, "components");
        this.networkID = data.hasKey("singularityNetworkID") ? data.getInteger("singularityNetworkID") : 0;
        this.gridOwnerPlayerID = data.hasKey("singularityGridOwner") ? data.getInteger("singularityGridOwner") : -1;
        this.defaultNetworkApplied = data.hasKey(SingularityNetworkDefaults.NBT_KEY)
            ? data.getBoolean(SingularityNetworkDefaults.NBT_KEY)
            : true;
        this.updateIdlePowerUsage();
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
    public void writeSingularityCraftingCoreStream(final ByteBuf data) throws IOException {
        data.writeInt(this.getMonitorCount());
        final IAEStack<?> displayStack = this.getServerDisplayStack();
        if (displayStack == null) {
            data.writeBoolean(false);
        } else {
            data.writeBoolean(true);
            writeStackByte(displayStack, data);
        }
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readSingularityCraftingCoreStream(final ByteBuf data) throws IOException {
        final int oldMonitorCount = this.clientMonitorCount;
        final IAEStack<?> oldDisplayStack = this.clientDisplayStack;
        this.clientMonitorCount = data.readInt();
        this.clientDisplayStack = data.readBoolean() ? readStackByte(data) : null;
        return oldMonitorCount != this.clientMonitorCount
            || !sameDisplayStack(oldDisplayStack, this.clientDisplayStack);
    }

    private void rebuildSyntheticCluster() {
        if (this.worldObj == null || this.worldObj.isRemote) return;
        if (!this.isContributionAvailable()) return;
        if (this.syntheticCluster != null && this.syntheticCluster.isBusy()) return;

        this.destroySyntheticCluster();

        final WorldCoord here = new WorldCoord(this);
        final CraftingCPUCluster cluster = new CraftingCPUCluster(here, here);
        if (!AEReflection.addCraftingTile(cluster, this)) {
            return;
        }

        this.syntheticCluster = cluster;
        this.setCoreBlock(true);
        this.updateStatus(cluster);
        AEReflection.setCraftingAccelerators(cluster, this.getConfiguredCoProcessors());

        if (this.getPreviousState() != null) {
            this.restorePreviousState(cluster);
            this.setPreviousState(null);
        }

        this.postCpuChange();
        this.syncMonitorDisplayStack();
        this.markDirty();
        this.markForUpdate();
    }

    private void destroySyntheticCluster() {
        if (this.syntheticCluster == null) return;
        final CraftingCPUCluster old = this.syntheticCluster;
        this.syntheticCluster = null;
        old.destroy();
        super.updateStatus(null);
        this.setCoreBlock(false);
        this.syncMonitorDisplayStack();
        this.postCpuChange();
    }

    private void restorePreviousState(final CraftingCPUCluster cluster) {
        final NBTTagCompound previousState = this.getPreviousState();
        if (previousState == null) return;
        if (!previousState.hasKey("finalOutput", 10)) {
            LOG.warn(
                "[SingularityME] Dropping invalid crafting core previousState at ({},{},{}) because finalOutput is missing",
                this.xCoord,
                this.yCoord,
                this.zCoord);
            return;
        }
        try {
            cluster.readFromNBT(previousState);
        } catch (final RuntimeException e) {
            LOG.warn(
                "[SingularityME] Dropping invalid crafting core previousState at ({},{},{})",
                this.xCoord,
                this.yCoord,
                this.zCoord,
                e);
        }
    }

    private void updateIdlePowerUsage() {
        int componentsInUse = 0;
        for (final ItemStack stack : this.components) {
            if (this.isValidComponent(stack)) {
                componentsInUse++;
            }
        }
        this.getProxy()
            .setIdlePowerUsage(this.isContributionAvailable() ? componentsInUse : 0.0);
    }

    private void syncMonitorDisplayStack() {
        if (this.worldObj == null || this.worldObj.isRemote) return;
        final IAEStack<?> displayStack = this.getServerDisplayStack();
        if (sameDisplayStack(this.lastServerDisplayStack, displayStack)) return;
        this.lastServerDisplayStack = displayStack == null ? null : displayStack.copy();
        this.markForUpdate();
    }

    private IAEStack<?> getServerDisplayStack() {
        if (!this.isContributionAvailable() || this.syntheticCluster == null
            || !this.syntheticCluster.isBusy()
            || this.getMonitorCount() <= 0) {
            return null;
        }
        final IAEStack<?> displayStack = this.syntheticCluster.getFinalMultiOutput();
        return displayStack == null ? null : displayStack.copy();
    }

    private static boolean sameDisplayStack(@Nullable final IAEStack<?> left, @Nullable final IAEStack<?> right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        return left.getStackSize() == right.getStackSize() && left.isSameType(right);
    }

    private void postCpuChange() {
        final IGridNode node = this.getProxy()
            .getNode();
        if (node != null && node.getGrid() != null) {
            node.getGrid()
                .postEvent(new MENetworkCraftingCpuChange(node));
        }
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
        if (available) {
            this.updateIdlePowerUsage();
            this.rebuildSyntheticCluster();
        } else {
            this.destroySyntheticCluster();
            this.getProxy()
                .setIdlePowerUsage(0.0);
        }
        this.syncMonitorDisplayStack();
        this.markForUpdate();
    }

    private static long getStorageBytes(@Nullable final ItemStack stack) {
        if (stack == null) return 0;
        final IBlocks blocks = AEApi.instance()
            .definitions()
            .blocks();
        if (matches(blocks.craftingStorage1k(), stack)) return 1024L;
        if (matches(blocks.craftingStorage4k(), stack)) return 4096L;
        if (matches(blocks.craftingStorage16k(), stack)) return 16_384L;
        if (matches(blocks.craftingStorage64k(), stack)) return 65_536L;
        if (matches(blocks.craftingStorage256k(), stack)) return 262_144L;
        if (matches(blocks.craftingStorage1024k(), stack)) return 1_048_576L;
        if (matches(blocks.craftingStorage4096k(), stack)) return 4_194_304L;
        if (matches(blocks.craftingStorage16384k(), stack)) return 16_777_216L;
        if (matches(blocks.craftingStorageSingularity(), stack)) return Long.MAX_VALUE;
        return 0;
    }

    private static int getCoProcessorValue(@Nullable final ItemStack stack) {
        if (stack == null) return 0;
        final IBlocks blocks = AEApi.instance()
            .definitions()
            .blocks();
        if (matches(blocks.craftingAccelerator(), stack)) return 1;
        if (matches(blocks.craftingAccelerator4x(), stack)) return 4;
        if (matches(blocks.craftingAccelerator16x(), stack)) return 16;
        if (matches(blocks.craftingAccelerator64x(), stack)) return 64;
        if (matches(blocks.craftingAccelerator256x(), stack)) return 256;
        if (matches(blocks.craftingAccelerator1024x(), stack)) return 1024;
        if (matches(blocks.craftingAccelerator4096x(), stack)) return 4096;
        return 0;
    }

    private static boolean isMonitorComponent(@Nullable final ItemStack stack) {
        return stack != null && matches(
            AEApi.instance()
                .definitions()
                .blocks()
                .craftingMonitor(),
            stack);
    }

    private static boolean matches(final IItemDefinition definition, final ItemStack stack) {
        if (stack == null || definition == null || !definition.isEnabled()) return false;
        final ItemStack expected = definition.maybeStack(1)
            .orNull();
        return expected != null && stack.getItem() == expected.getItem()
            && stack.getItemDamage() == expected.getItemDamage();
    }
}
