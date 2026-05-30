package com.github.singularityme.tile;

import java.util.EnumSet;
import java.util.function.Predicate;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.core.SingularityNetworkManager;
import com.github.singularityme.grid.SingularityGrid;
import com.github.singularityme.proxy.CommonProxy;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.helpers.IOreFilterable;
import appeng.me.GridAccessException;
import appeng.me.GridNode;
import appeng.parts.automation.BlockUpgradeInventory;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.IInventoryDestination;
import appeng.util.inv.ItemSlot;
import appeng.util.item.AEItemStack;
import appeng.util.prioitylist.OreFilteredList;

/**
 * Singularity Import Bus — each tick pulls items from the adjacent container and
 * injects them into the player's global SingularityGrid.
 *
 * <p>
 * Supports CAPACITY (up to 2 cards → 1/5/9 filter slots), SPEED (0-4 cards → 1/8/32/64/96
 * items/tick), FUZZY (ignore NBT/damage), and REDSTONE control.
 */
public class TileSingularityImportBus extends AENetworkInvTile
    implements IGridTickable, IConfigurableObject, IConfigManagerHost, IUpgradeableHost, IIAEStackInventory,
    IOreFilterable, IInventoryDestination, ISingularityContributionHost, ISingularityNetworkDevice {

    private final BaseActionSource mySrc = new MachineSource(this);

    /** Ghost filter inventory — 9 slots. Empty = import all items. */
    private final IAEStackInventory filterInv = new IAEStackInventory(this, 9, StorageName.CONFIG);

    /** Upgrade card inventory — 4 slots. */
    private final BlockUpgradeInventory upgradesInv = new BlockUpgradeInventory(CommonProxy.blockImportBus, this, 4);

    /** Config manager for redstone mode and fuzzy mode. */
    private final IConfigManager cm = new ConfigManager(this);

    /** Tracks previous redstone state for SIGNAL_PULSE detection. */
    private boolean lastRedstone = false;

    private String oreFilterString = "";
    private Predicate<IAEItemStack> filterPredicate = null;
    private IMEMonitor<IAEItemStack> destination = null;
    private IAEItemStack lastItemChecked = null;
    private int itemToSend = 0;
    private boolean worked = false;
    private boolean contributionRetired = true;
    /** Per-player network index. 0 = unassigned. */
    private int networkID = 0;
    private int gridOwnerPlayerID = -1;
    private boolean defaultNetworkApplied = false;

    public TileSingularityImportBus() {
        this.getProxy()
            .setFlags();
        this.getProxy()
            .setValidSides(EnumSet.noneOf(ForgeDirection.class));
        this.getProxy()
            .setIdlePowerUsage(1.0);
        cm.registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        cm.registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    // ---- AENetworkInvTile requirements ----

    @Override
    public IInventory getInternalInventory() {
        return upgradesInv;
    }

    @Override
    public int[] getAccessibleSlotsBySide(final ForgeDirection side) {
        return new int[0];
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
        final ItemStack removedStack, final ItemStack newStack) {
        if (inv == upgradesInv) {
            if (getInstalledUpgrades(Upgrades.ORE_FILTER) == 0) {
                setFilter("");
            }
            markDirty();
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
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    // ---- IConfigurableObject / IConfigManagerHost ----

    @Override
    public IConfigManager getConfigManager() {
        return cm;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        // settings take effect on next tick automatically
    }

    // ---- IUpgradeableHost ----

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        return upgradesInv.getInstalledUpgrades(u);
    }

    @Override
    public TileEntity getTile() {
        return this;
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        return "upgrades".equals(name) ? upgradesInv : null;
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

    // ---- IIAEStackInventory ----

    @Override
    public void saveAEStackInv() {
        markDirty();
    }

    // ---- filter access (for GUI container) ----

    public IAEStackInventory getAEInventoryByName(final StorageName name) {
        return name == StorageName.CONFIG ? filterInv : null;
    }

    // ---- AE2 lifecycle ----

    @Override
    public void onReady() {
        this.contributionRetired = false;
        this.getProxy()
            .setFlags();
        this.getProxy()
            .setValidSides(EnumSet.noneOf(ForgeDirection.class));
        super.onReady();
        if (worldObj.isRemote) return;
        GridNode node = (GridNode) getProxy().getNode();
        if (node != null && node.getPlayerID() >= 0) {
            this.applyDefaultNetwork(node.getPlayerID());
            if (this.networkID != 0) {
                this.gridOwnerPlayerID = SingularityNetworkManager.INSTANCE
                    .registerNode(node.getPlayerID(), this.networkID, node);
            }
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

    private void unregister(final boolean permanent) {
        if (worldObj == null || worldObj.isRemote) return;
        GridNode node = (GridNode) getProxy().getNode();
        if (node != null) {
            final int ownerID = this.gridOwnerPlayerID >= 0 ? this.gridOwnerPlayerID : node.getPlayerID();
            SingularityNetworkManager.INSTANCE.unregisterNodeForOwner(ownerID, this.networkID, node, permanent);
            this.gridOwnerPlayerID = -1;
        }
    }

    // ---- NBT ----

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeExtraNBT(final NBTTagCompound tag) {
        filterInv.writeToNBT(tag, "filterInv");
        upgradesInv.writeToNBT(tag, "upgradesInv");
        cm.writeToNBT(tag);
        tag.setBoolean("lastRedstone", lastRedstone);
        tag.setString("filter", oreFilterString);
        tag.setInteger("singularityNetworkID", this.networkID);
        tag.setInteger("singularityGridOwner", this.gridOwnerPlayerID);
        tag.setBoolean(SingularityNetworkDefaults.NBT_KEY, this.defaultNetworkApplied);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readExtraNBT(final NBTTagCompound tag) {
        filterInv.readFromNBT(tag, "filterInv");
        upgradesInv.readFromNBT(tag, "upgradesInv");
        cm.readFromNBT(tag);
        lastRedstone = tag.getBoolean("lastRedstone");
        setFilter(tag.getString("filter"));
        this.networkID = tag.hasKey("singularityNetworkID") ? tag.getInteger("singularityNetworkID") : 0;
        this.gridOwnerPlayerID = tag.hasKey("singularityGridOwner") ? tag.getInteger("singularityGridOwner") : -1;
        this.defaultNetworkApplied = tag.hasKey(SingularityNetworkDefaults.NBT_KEY)
            ? tag.getBoolean(SingularityNetworkDefaults.NBT_KEY)
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

    // ---- IGridTickable ----

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(1, 20, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (this.contributionRetired) return TickRateModulation.SLEEP;
        if (!getProxy().isActive()) return TickRateModulation.IDLE;
        if (!this.isBusAccessible()) {
            this.destination = null;
            this.lastItemChecked = null;
            this.itemToSend = 0;
            this.worked = false;
            return TickRateModulation.SLOWER;
        }
        if (isBlockedByRedstone()) return TickRateModulation.SLOWER;
        return doBusWork();
    }

    /**
     * Reads the configured redstone mode and the block's current redstone signal.
     * Updates {@link #lastRedstone} as a side-effect (needed for SIGNAL_PULSE detection).
     *
     * @return true if the bus should NOT operate this tick (blocked by redstone configuration).
     */
    private boolean isBlockedByRedstone() {
        final RedstoneMode rm = (RedstoneMode) cm.getSetting(Settings.REDSTONE_CONTROLLED);
        final boolean powered = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
        if (rm == RedstoneMode.LOW_SIGNAL && powered) {
            lastRedstone = powered;
            return true;
        }
        if (rm == RedstoneMode.HIGH_SIGNAL && !powered) {
            lastRedstone = powered;
            return true;
        }
        if (rm == RedstoneMode.SIGNAL_PULSE) {
            if (!powered || lastRedstone) {
                lastRedstone = powered;
                return true;
            }
            lastRedstone = true;
        } else {
            lastRedstone = powered;
        }
        return false;
    }

    /**
     * Main bus work — analogous to AE2's {@code PartBaseImportBus.doBusWork()}.
     * Pulls items from the adjacent inventory into the SingularityGrid, respecting
     * filter, fuzzy, ore-filter and energy budget.
     */
    private TickRateModulation doBusWork() {
        final InventoryAdaptor adaptor = getAdjacentAdaptor();
        if (adaptor == null) return TickRateModulation.SLEEP;

        this.worked = false;
        final FuzzyMode fuzzyMode = (FuzzyMode) cm.getSetting(Settings.FUZZY_MODE);
        try {
            this.itemToSend = calculateMaxItems();
            final double availablePower = getProxy().getEnergy()
                .extractAEPower(
                    Platform.ceilDiv(this.itemToSend, getPowerMultiplier()),
                    Actionable.SIMULATE,
                    PowerMultiplier.CONFIG);
            this.itemToSend = Math.min(this.itemToSend, (int) (availablePower * getPowerMultiplier() + 0.01));

            final IMEMonitor<IAEItemStack> gridInv = getGridItemInventory();
            if (gridInv == null) return TickRateModulation.IDLE;
            final IEnergyGrid energy = getProxy().getEnergy();

            boolean configured = false;
            if (getInstalledUpgrades(Upgrades.ORE_FILTER) == 0) {
                for (int x = 0; x < availableSlots(); x++) {
                    final IAEStack<?> stack = filterInv.getAEStackInSlot(x);
                    if (stack instanceof IAEItemStack ais && this.itemToSend > 0) {
                        configured = true;
                        while (this.itemToSend > 0) {
                            if (importStuff(adaptor, ais, gridInv, energy, fuzzyMode)) {
                                break;
                            }
                        }
                    }
                }
            } else {
                configured = doOreDict(adaptor, gridInv, energy, fuzzyMode);
            }

            if (!configured) {
                while (this.itemToSend > 0) {
                    if (importStuff(adaptor, null, gridInv, energy, fuzzyMode)) {
                        break;
                    }
                }
            }
        } catch (final GridAccessException ignored) {
            return TickRateModulation.IDLE;
        }

        return this.worked ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    private boolean importStuff(final InventoryAdaptor adaptor, final IAEItemStack whatToImport,
        final IMEMonitor<IAEItemStack> gridInv, final IEnergyGrid energy, final FuzzyMode fuzzyMode) {
        final int toSend = calculateMaximumAmountToImport(adaptor, whatToImport, gridInv, fuzzyMode);
        if (toSend <= 0) return true;
        final ItemStack pulled;
        if (getInstalledUpgrades(Upgrades.FUZZY) > 0) {
            pulled = adaptor.removeSimilarItems(
                toSend,
                whatToImport == null ? null : whatToImport.getItemStack(),
                fuzzyMode,
                configDestination(gridInv));
        } else {
            pulled = adaptor.removeItems(
                toSend,
                whatToImport == null ? null : whatToImport.getItemStack(),
                configDestination(gridInv));
        }
        if (pulled == null || pulled.stackSize <= 0) return true;

        this.itemToSend -= pulled.stackSize;
        if (this.lastItemChecked == null || !this.lastItemChecked.isSameType(pulled)) {
            this.lastItemChecked = AEApi.instance()
                .storage()
                .createItemStack(pulled);
        } else {
            this.lastItemChecked.setStackSize(pulled.stackSize);
        }

        final IAEItemStack failed = Platform.poweredInsert(energy, this.destination, this.lastItemChecked, this.mySrc);
        if (failed != null) {
            adaptor.addItems(failed.getItemStack());
            return true;
        }

        this.worked = true;
        return false;
    }

    @Override
    public boolean canInsert(final ItemStack stack) {
        if (!this.isBusAccessible() || stack == null || stack.getItem() == null || this.destination == null) {
            return false;
        }

        final IAEItemStack out = this.destination.injectItems(
            this.lastItemChecked = AEApi.instance()
                .storage()
                .createItemStack(stack),
            Actionable.SIMULATE,
            this.mySrc);
        if (out == null) {
            return true;
        }
        return out.getStackSize() != stack.stackSize;
    }

    private int calculateMaximumAmountToImport(final InventoryAdaptor adaptor, final IAEItemStack whatToImport,
        final IMEMonitor<IAEItemStack> gridInv, final FuzzyMode fuzzyMode) {
        final int toSend = Math.min(this.itemToSend, 64);
        final ItemStack itemStackToImport = whatToImport == null ? null : whatToImport.getItemStack();
        final ItemStack simResult;
        if (getInstalledUpgrades(Upgrades.FUZZY) > 0) {
            simResult = adaptor.simulateSimilarRemove(toSend, itemStackToImport, fuzzyMode, configDestination(gridInv));
        } else {
            simResult = adaptor.simulateRemove(toSend, itemStackToImport, configDestination(gridInv));
        }
        if (simResult == null || simResult.stackSize <= 0) return 0;

        final IAEItemStack notStorable = this.destination
            .injectItems(AEItemStack.create(simResult), Actionable.SIMULATE, this.mySrc);
        if (notStorable != null) {
            return (int) Math.min(simResult.stackSize - notStorable.getStackSize(), toSend);
        }

        return toSend;
    }

    private IInventoryDestination configDestination(final IMEMonitor<IAEItemStack> itemInventory) {
        this.destination = itemInventory;
        return this;
    }

    private boolean doOreDict(final InventoryAdaptor adaptor, final IMEMonitor<IAEItemStack> gridInv,
        final IEnergyGrid energy, final FuzzyMode fuzzyMode) {
        if (oreFilterString.isEmpty()) return false;
        final Predicate<IAEItemStack> predicate = getOreFilterPredicate();
        for (final ItemSlot slot : adaptor) {
            if (this.itemToSend <= 0) break;
            final IAEItemStack stack = slot.getAEItemStack();
            if (slot.isExtractable() && stack != null && predicate != null && predicate.test(stack)) {
                while (this.itemToSend > 0) {
                    if (importStuff(adaptor, stack, gridInv, energy, fuzzyMode)) {
                        break;
                    }
                }
            }
        }
        return true;
    }

    // ---- helpers ----

    private int getPowerMultiplier() {
        return 1;
    }

    private int availableSlots() {
        return Math.min(1 + getInstalledUpgrades(Upgrades.CAPACITY) * 4, filterInv.getSizeInventory());
    }

    private int calculateMaxItems() {
        long amount = switch (getInstalledUpgrades(Upgrades.SPEED)) {
            case 1:
                yield 8L;
            case 2:
                yield 32L;
            case 3:
                yield 64L;
            case 4:
                yield 96L;
            default:
                yield 1L;
        };

        amount += switch (getInstalledUpgrades(Upgrades.SUPERSPEED)) {
            case 1 -> 16L;
            case 2 -> 16L * 8L;
            case 3 -> 16L * 8L * 8L;
            case 4 -> 16L * 8L * 8L * 8L;
            default -> 0L;
        };

        amount += switch (getInstalledUpgrades(Upgrades.SUPERLUMINALSPEED)) {
            case 1 -> 131_072L;
            case 2 -> 131_072L * 8L;
            case 3 -> 131_072L * 8L * 8L;
            case 4 -> 131_072L * 8L * 8L * 8L;
            default -> 0L;
        };

        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
    }

    private Predicate<IAEItemStack> getOreFilterPredicate() {
        if (filterPredicate == null) {
            filterPredicate = OreFilteredList.makeFilter(oreFilterString);
        }
        return filterPredicate;
    }

    @Override
    public String getFilter() {
        return oreFilterString;
    }

    @Override
    public void setFilter(final String filter) {
        oreFilterString = filter == null ? "" : filter;
        filterPredicate = null;
        markDirty();
    }

    private IMEMonitor<IAEItemStack> getGridItemInventory() {
        if (!SingularityChunkAccess.isHostChunkNetworkAccessible(this)) return null;
        GridNode node = (GridNode) getProxy().getNode();
        if (node == null) return null;
        final int ownerID = this.gridOwnerPlayerID >= 0 ? this.gridOwnerPlayerID : node.getPlayerID();
        SingularityGrid sg = SingularityNetworkManager.INSTANCE.getGridForPlayer(ownerID, this.networkID);
        if (sg == null) return null;
        IStorageGrid storage = sg.getCache(IStorageGrid.class);
        return storage == null ? null : storage.getItemInventory();
    }

    private InventoryAdaptor getAdjacentAdaptor() {
        final ForgeDirection facing = this.getTargetSide();
        final TileEntity te = SingularityChunkAccess.getLoadedAdjacentTileIfAccessible(this, facing);
        if (te == null) return null;
        return InventoryAdaptor
            .getAdaptor(te, facing.getOpposite(), InventoryAdaptor.ALLOW_ITEMS | InventoryAdaptor.FOR_EXTRACTS);
    }

    private boolean isBusAccessible() {
        if (this.worldObj == null || this.worldObj.isRemote) return false;
        final ForgeDirection facing = this.getTargetSide();
        return !this.contributionRetired && SingularityChunkAccess.isHostChunkNetworkAccessible(this)
            && SingularityChunkAccess.isAdjacentTargetChunkNetworkAccessible(this, facing);
    }

    private ForgeDirection getTargetSide() {
        final int meta = this.worldObj.getBlockMetadata(this.xCoord, this.yCoord, this.zCoord);
        return ForgeDirection.getOrientation(meta);
    }

    @Override
    public void retireSingularityContribution() {
        this.contributionRetired = true;
        this.destination = null;
        this.lastItemChecked = null;
        this.itemToSend = 0;
        this.worked = false;
    }

    @Override
    public boolean isContributionLoaded() {
        return !this.contributionRetired;
    }
}
