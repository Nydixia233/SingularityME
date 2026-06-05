package com.github.singularityme.tile;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.core.SingularityNetworkManager;
import com.github.singularityme.proxy.CommonProxy;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.ActionItems;
import appeng.api.config.Actionable;
import appeng.api.config.ExtractionMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IStorageBus;
import appeng.api.storage.IExternalStorageHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMENetworkInventory;
import appeng.api.storage.IStorageBusMonitor;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.helpers.IInterfaceHost;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.GridNode;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.MEMonitorIInventory;
import appeng.me.storage.MEMonitorPassThrough;
import appeng.me.storage.StorageBusInventoryHandler;
import appeng.parts.automation.BlockUpgradeInventory;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.InventoryAdaptor;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.item.PrioritizedNetworkItemList;
import appeng.util.prioitylist.FuzzyPriorityList;
import appeng.util.prioitylist.OreFilteredList;
import appeng.util.prioitylist.PrecisePriorityList;
import buildcraft.api.transport.IPipeConnection.ConnectOverride;
import buildcraft.api.transport.IPipeTile.PipeType;

/**
 * Singularity Storage Bus exposes the faced external storage into the owner's Singularity grid.
 *
 * <p>
 * The networking model stays Singularity-specific, but handler construction follows GTNH AE2's
 * Storage Bus: external storage registry first, StorageBusInventoryHandler wrapping, then
 * item-inventory fallback.
 */
public class TileSingularityStorageBus extends AENetworkInvTile implements IStorageBus, IConfigurableObject,
    IConfigManagerHost, ISingularityContributionHost, ISingularityNetworkDevice {

    private static final String FLUID_PACKET_CLASS = "com.glodblock.github.common.item.ItemFluidPacket";

    private final BaseActionSource mySrc = new MachineSource(this);
    private final Map<IAEStackType<?>, MEInventoryHandler<?>> handlers = new HashMap<>();
    private final Map<IStorageBusMonitor<?>, IAEStackType<?>> storageBusMonitors = new IdentityHashMap<>();
    private final Map<IMEMonitor<?>, MEInventoryHandler<?>> monitorHandlers = new IdentityHashMap<>();
    private final Map<IMEMonitor<?>, IAEStackType<?>> monitorTypes = new IdentityHashMap<>();
    private final IAEStackInventory filterInv = new IAEStackInventory(this, 63, StorageName.CONFIG);
    private final BlockUpgradeInventory upgradesInv = new BlockUpgradeInventory(CommonProxy.blockStorageBus, this, 5);
    private final IConfigManager cm = new ConfigManager(this);

    private int handlerHash = 0;
    private int priority = 0;
    private boolean needSyncGUI = false;
    private String oreFilterString = "";
    private String previousOreFilterString = "";
    private boolean contributionRetired = true;
    private boolean bypassStorageAccessGuard = false;
    /** Per-player network index. 0 = unassigned. */
    private int networkID = 0;
    private int gridOwnerPlayerID = -1;
    private boolean defaultNetworkApplied = false;

    public TileSingularityStorageBus() {
        this.getProxy()
            .setFlags();
        this.getProxy()
            .setValidSides(EnumSet.noneOf(ForgeDirection.class));
        this.getProxy()
            .setIdlePowerUsage(1.0);
        cm.registerSetting(Settings.ACCESS, AccessRestriction.READ_WRITE);
        cm.registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        cm.registerSetting(Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY);
        cm.registerSetting(Settings.EXTRACTION_MODE, ExtractionMode.LOOSE);
        cm.registerSetting(Settings.STICKY_MODE, YesNo.NO);
        cm.registerSetting(Settings.ACTIONS, ActionItems.CLOSE);
    }

    @Override
    public IConfigManager getConfigManager() {
        return cm;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (settingName == Settings.ACTIONS) {
            if (newValue == ActionItems.CLOSE) {
                clearFilter();
            } else if (newValue == ActionItems.WRENCH) {
                partitionFilterFromAdjacentInventory();
            }
            return;
        }
        invalidateStorageHandler();
    }

    public void clearFilter() {
        for (int i = 0; i < filterInv.getSizeInventory(); i++) {
            filterInv.putAEStackInSlot(i, null);
        }
        invalidateStorageHandler();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void partitionFilterFromAdjacentInventory() {
        if (getInstalledUpgrades(Upgrades.ORE_FILTER) > 0) return;

        final MEInventoryHandler cellInv = getInternalHandler();
        if (cellInv == null) return;

        cellInv.setPartitionList(new PrecisePriorityList<>(getItemList()));
        final IItemList list = cellInv.getAvailableItems(getItemList(), IterationCounter.fetchNewId());

        int slot = 0;
        final int maxSlots = getAvailableFilterSlots();
        for (final Object obj : list) {
            if (slot >= maxSlots) break;
            if (obj instanceof IAEStack<?>stack) {
                final IAEStack<?> copy = stack.copy();
                copy.setStackSize(1);
                filterInv.putAEStackInSlot(slot++, copy);
            }
        }

        for (int i = slot; i < filterInv.getSizeInventory(); i++) {
            filterInv.putAEStackInSlot(i, null);
        }
        invalidateStorageHandler();
    }

    public void onFilterChanged() {
        invalidateStorageHandler();
    }

    public void onAdjacentStorageChanged() {
        if (worldObj == null || worldObj.isRemote) return;
        invalidateStorageHandler();
    }

    private boolean isStorageAccessible() {
        return !this.contributionRetired && this.getProxy()
            .isActive()
            && this.isHostStillLoaded()
            && this.isTargetStillLoaded()
            && this.isHostChunkNetworkAccessible()
            && this.isTargetChunkNetworkAccessible();
    }

    public boolean isHostStillLoaded() {
        return SingularityChunkAccess.isTileStillLoaded(this);
    }

    public boolean isTargetChunkLoaded() {
        if (this.worldObj == null || !this.isHostStillLoaded()) return false;
        final ForgeDirection facing = getTargetSide();
        return this.worldObj
            .blockExists(this.xCoord + facing.offsetX, this.yCoord + facing.offsetY, this.zCoord + facing.offsetZ);
    }

    public boolean isTargetStillLoaded() {
        return getLoadedTargetTile() != null;
    }

    public boolean isHostChunkNetworkAccessible() {
        return SingularityChunkAccess.isHostChunkNetworkAccessible(this);
    }

    public boolean isTargetChunkNetworkAccessible() {
        final ForgeDirection facing = getTargetSide();
        return SingularityChunkAccess.isAdjacentTargetChunkNetworkAccessible(this, facing);
    }

    private void retireAndUnregisterIfHostUnavailable() {
        if (this.contributionRetired || this.isHostStillLoaded()) return;
        retireSingularityContribution();
        unregister(false);
    }

    private void invalidateStorageHandler() {
        retireAndUnregisterIfHostUnavailable();
        if (this.contributionRetired) {
            clearStorageHandlerCaches(!storageBusMonitors.isEmpty());
            return;
        }
        final boolean hadMonitor = !storageBusMonitors.isEmpty();
        final List<IAEStackType<?>> affectedTypes = new ArrayList<>(handlers.keySet());
        final Map<IAEStackType<?>, IItemList> beforeByType = captureAvailableItemsFromHandlers(handlers);

        clearStorageHandlerCaches(hadMonitor);

        final Map<IAEStackType<?>, IItemList> afterByType = captureAvailableItemsFromCurrentTarget(affectedTypes);
        postAvailableItemDiffs(beforeByType, afterByType);

        markDirty();
        postCellArrayUpdate();
    }

    private void clearStorageHandlerCaches(final boolean hadMonitor) {
        for (IMEMonitor<?> monitor : monitorHandlers.keySet()) {
            monitor.removeListener(this);
        }
        handlers.clear();
        storageBusMonitors.clear();
        monitorHandlers.clear();
        monitorTypes.clear();
        handlerHash = 0;
        syncTickSleepState(hadMonitor, false);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Map<IAEStackType<?>, IItemList> captureAvailableItemsFromHandlers(
        final Map<IAEStackType<?>, MEInventoryHandler<?>> sourceHandlers) {
        final Map<IAEStackType<?>, IItemList> out = new HashMap<>();
        if (worldObj == null || worldObj.isRemote || !getProxy().isActive() || !isHostStillLoaded()) return out;

        for (final Map.Entry<IAEStackType<?>, MEInventoryHandler<?>> entry : sourceHandlers.entrySet()) {
            final IAEStackType stackType = entry.getKey();
            final IItemList list = stackType.createList();
            final MEInventoryHandler handler = entry.getValue();
            if (handler != null) {
                this.bypassStorageAccessGuard = this.isTargetStillLoaded();
                try {
                    handler.getAvailableItems(list, IterationCounter.fetchNewId());
                } finally {
                    this.bypassStorageAccessGuard = false;
                }
            }
            out.put(entry.getKey(), list);
        }
        return out;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Map<IAEStackType<?>, IItemList> captureAvailableItemsFromCurrentTarget(
        final List<IAEStackType<?>> stackTypes) {
        final Map<IAEStackType<?>, IItemList> out = new HashMap<>();
        if (worldObj == null || worldObj.isRemote || !isStorageAccessible()) return out;

        for (final IAEStackType<?> stackTypeKey : stackTypes) {
            final IAEStackType stackType = stackTypeKey;
            final IItemList list = stackType.createList();
            final MEInventoryHandler handler = getInternalHandler(stackTypeKey);
            if (handler != null) {
                handler.getAvailableItems(list, IterationCounter.fetchNewId());
            }
            out.put(stackTypeKey, list);
        }
        return out;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void postAvailableItemDiffs(final Map<IAEStackType<?>, IItemList> beforeByType,
        final Map<IAEStackType<?>, IItemList> afterByType) {
        if (beforeByType.isEmpty() || worldObj == null || worldObj.isRemote) return;

        try {
            for (final Map.Entry<IAEStackType<?>, IItemList> entry : beforeByType.entrySet()) {
                final IAEStackType stackType = entry.getKey();
                final IItemList before = entry.getValue();
                IItemList after = afterByType.get(entry.getKey());
                if (after == null) {
                    after = stackType.createList();
                }

                final List changes = buildAvailableItemDiff(before, after);
                if (!changes.isEmpty()) {
                    getProxy().getStorage()
                        .postAlterationOfStoredItems(stackType, changes, mySrc);
                }
            }
        } catch (GridAccessException ignored) {}
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<IAEStack<?>> buildAvailableItemDiff(final IItemList before, final IItemList after) {
        final List<IAEStack<?>> changes = new ArrayList<>();

        for (final Object obj : before) {
            final IAEStack stack = (IAEStack) obj;
            stack.setStackSize(-stack.getStackSize());
        }

        for (final Object obj : after) {
            before.add((IAEStack) obj);
        }

        for (final Object obj : before) {
            final IAEStack stack = (IAEStack) obj;
            if (stack.getStackSize() != 0) {
                changes.add(stack);
            }
        }

        return changes;
    }

    private void syncTickSleepState(final boolean hadMonitor) {
        syncTickSleepState(hadMonitor, !storageBusMonitors.isEmpty());
    }

    private void syncTickSleepState(final boolean hadMonitor, final boolean hasMonitor) {
        if (worldObj == null || worldObj.isRemote || hadMonitor == hasMonitor) return;
        try {
            final ITickManager tickManager = getProxy().getTick();
            final IGridNode node = getProxy().getNode();
            if (node == null) return;
            if (hasMonitor) {
                tickManager.wakeDevice(node);
            } else {
                tickManager.sleepDevice(node);
            }
        } catch (GridAccessException ignored) {}
    }

    private void postCellArrayUpdate() {
        if (worldObj == null || worldObj.isRemote) return;
        try {
            getProxy().getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (GridAccessException ignored) {}
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(final int newValue) {
        priority = newValue;
        invalidateStorageHandler();
    }

    public int getPriorityValue() {
        return getPriority();
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

    public void setPriorityValue(final int p) {
        setPriority(p);
    }

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

    @Override
    public void saveAEStackInv() {
        invalidateStorageHandler();
    }

    @Override
    public IAEStackInventory getAEInventoryByName(final StorageName name) {
        return name == StorageName.CONFIG ? filterInv : null;
    }

    public int getAvailableFilterSlots() {
        if (getInstalledUpgrades(Upgrades.ORE_FILTER) > 0) return 0;
        return Math.min(18 + getInstalledUpgrades(Upgrades.CAPACITY) * 9, 63);
    }

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
            syncOreFilterUpgradeState();
            needSyncGUI = true;
        }
        invalidateStorageHandler();
    }

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

    @Override
    public void retireSingularityContribution() {
        if (this.contributionRetired) return;
        if (worldObj == null || worldObj.isRemote) {
            this.contributionRetired = true;
            clearStorageHandlerCaches(!storageBusMonitors.isEmpty());
            return;
        }

        final boolean hadMonitor = !storageBusMonitors.isEmpty();
        final Map<IAEStackType<?>, IItemList> beforeByType = captureAvailableItemsFromHandlers(handlers);
        this.contributionRetired = true;
        clearStorageHandlerCaches(hadMonitor);
        postAvailableItemDiffs(beforeByType, Collections.emptyMap());
        markDirty();
        postCellArrayUpdate();
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

    /**
     * AE2 networks reshuffle cell arrays whenever channel availability or power state changes.
     * Mirror that here so the Storage Bus rebuilds its handler — this matches AE2's
     * {@code PartStorageBus.updateChannels()} / {@code updatePowerStatus()} hooks.
     */
    @MENetworkEventSubscribe
    public void onChannelsChanged(final MENetworkChannelsChanged ev) {
        if (worldObj == null || worldObj.isRemote) return;
        invalidateStorageHandler();
    }

    @MENetworkEventSubscribe
    public void onPowerStatusChanged(final MENetworkPowerStatusChange ev) {
        if (worldObj == null || worldObj.isRemote) return;
        invalidateStorageHandler();
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeExtraNBT(final NBTTagCompound tag) {
        filterInv.writeToNBT(tag, "filterInv");
        upgradesInv.writeToNBT(tag, "upgradesInv");
        cm.writeToNBT(tag);
        tag.setInteger("priority", priority);
        tag.setString("filter", oreFilterString);
        tag.setString("previousFilter", previousOreFilterString);
        tag.setInteger("singularityNetworkID", this.networkID);
        tag.setInteger("singularityGridOwner", this.gridOwnerPlayerID);
        tag.setBoolean(SingularityNetworkDefaults.NBT_KEY, this.defaultNetworkApplied);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readExtraNBT(final NBTTagCompound tag) {
        filterInv.readFromNBT(tag, "filterInv");
        upgradesInv.readFromNBT(tag, "upgradesInv");
        cm.readFromNBT(tag);
        priority = tag.getInteger("priority");
        oreFilterString = tag.getString("filter");
        previousOreFilterString = tag.getString("previousFilter");
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

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(
            TickRates.StorageBus.getMin(),
            TickRates.StorageBus.getMax(),
            storageBusMonitors.isEmpty(),
            true);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        retireAndUnregisterIfHostUnavailable();
        if (this.contributionRetired) return TickRateModulation.SLEEP;

        if (!isHostChunkNetworkAccessible() || !isTargetChunkNetworkAccessible()) {
            if (!handlers.isEmpty() || handlerHash != 0) {
                invalidateStorageHandler();
            }
            return TickRateModulation.SLOWER;
        }

        if (!isTargetStillLoaded()) {
            if (!handlers.isEmpty() || handlerHash != 0) {
                invalidateStorageHandler();
            }
            return TickRateModulation.SLOWER;
        }

        TileEntity target = getLoadedTargetTile();
        int newHash = Platform.generateTileHash(target);
        if (newHash != handlerHash) {
            invalidateStorageHandler();
            return TickRateModulation.FASTER;
        }

        TickRateModulation modulation = TickRateModulation.SLOWER;
        for (IStorageBusMonitor<?> monitor : storageBusMonitors.keySet()) {
            TickRateModulation next = monitor.onTick();
            if (next.ordinal() > modulation.ordinal()) {
                modulation = next;
            }
        }
        return storageBusMonitors.isEmpty() ? TickRateModulation.SLEEP : modulation;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<IMEInventoryHandler> getCellArray(final IAEStackType<?> type) {
        retireAndUnregisterIfHostUnavailable();
        if (!isStorageAccessible()) {
            return Collections.emptyList();
        }
        MEInventoryHandler<?> out = getInternalHandler(type);
        return out == null ? Collections.emptyList() : Collections.singletonList(out);
    }

    @Override
    public void saveChanges(final IMEInventory cellInventory) {
        // Adjacent storage persists itself.
    }

    @Override
    public boolean needSyncGUI() {
        return needSyncGUI;
    }

    @Override
    public void setNeedSyncGUI(final boolean needSyncGUI) {
        this.needSyncGUI = needSyncGUI;
    }

    @Override
    public MEInventoryHandler<?> getInternalHandler() {
        retireAndUnregisterIfHostUnavailable();
        if (!isStorageAccessible()) return null;
        return getInternalHandler(getStackType());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private MEInventoryHandler<?> getInternalHandler(final IAEStackType<?> type) {
        retireAndUnregisterIfHostUnavailable();
        if (!isStorageAccessible()) return null;
        boolean hadMonitor = !storageBusMonitors.isEmpty();
        TileEntity target = getLoadedTargetTile();
        int newHandlerHash = Platform.generateTileHash(target);
        boolean canReuseHandler = handlerHash != 0 && handlerHash == newHandlerHash;
        if (!handlers.isEmpty() && !canReuseHandler) {
            invalidateStorageHandler();
            hadMonitor = !storageBusMonitors.isEmpty();
        }
        handlerHash = newHandlerHash;

        if (canReuseHandler && handlers.containsKey(type)) {
            return handlers.get(type);
        }
        if (target == null || type == null) {
            return null;
        }

        ForgeDirection side = getTargetSide();
        ForgeDirection targetSide = side.getOpposite();
        IMEInventory inv = null;

        IExternalStorageHandler esh = AEApi.instance()
            .registries()
            .externalStorage()
            .getHandler(target, targetSide, type, mySrc);
        if (esh != null) {
            inv = esh.getInventory(target, targetSide, type, mySrc);
        }

        if (inv == null && type == ITEM_STACK_TYPE) {
            InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(target, targetSide);
            if (adaptor != null) {
                MEMonitorIInventory monitor = new MEMonitorIInventory(adaptor);
                monitor.setMode((StorageFilter) cm.getSetting(Settings.STORAGE_FILTER));
                monitor.setActionSource(mySrc);
                inv = monitor;
            }
        }

        if (inv == null) {
            return null;
        }

        if (inv instanceof IStorageBusMonitor<?>monitor) {
            monitor.setMode((StorageFilter) cm.getSetting(Settings.STORAGE_FILTER));
            monitor.setActionSource(mySrc);
            storageBusMonitors.put(monitor, type);
        }
        if (inv instanceof MEMonitorPassThrough<?>passThrough) {
            passThrough.setMode((StorageFilter) cm.getSetting(Settings.STORAGE_FILTER));
        }

        checkInterfaceVsStorageBus(target, targetSide);

        MEInventoryHandler handler = new GuardedStorageBusInventoryHandler(inv, type);
        configureHandler(handler, type);
        handlers.put(type, handler);

        if (inv instanceof IMEMonitor<?>monitor) {
            monitor.addListener(this, handler);
            monitorHandlers.put(monitor, handler);
            monitorTypes.put(monitor, type);
        }

        syncTickSleepState(hadMonitor);
        return handler;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void configureHandler(final MEInventoryHandler handler, final IAEStackType<?> type) {
        AccessRestriction access = (AccessRestriction) cm.getSetting(Settings.ACCESS);
        handler.setBaseAccess(access);
        handler.setWhitelist(
            getInstalledUpgrades(Upgrades.INVERTER) > 0 ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
        handler.setPriority(priority);

        ExtractionMode extractionMode = (ExtractionMode) cm.getSetting(Settings.EXTRACTION_MODE);
        boolean extractRights = access == AccessRestriction.READ
            || (extractionMode == ExtractionMode.STRICT && access == AccessRestriction.READ_WRITE);
        handler.setIsExtractFilterActive(extractRights);
        handler.setSticky(getInstalledUpgrades(Upgrades.STICKY) > 0);

        if (type == ITEM_STACK_TYPE && !oreFilterString.isEmpty()) {
            OreFilteredList partitionList = new OreFilteredList(oreFilterString);
            handler.setPartitionList(partitionList);
            handler.setExtractPartitionList(partitionList);
            return;
        }

        IItemList priorityList = type.createList();
        int availSlots = getAvailableFilterSlots();
        for (int i = 0; i < filterInv.getSizeInventory() && i < availSlots; i++) {
            IAEStack<?> raw = filterInv.getAEStackInSlot(i);
            IAEStack<?> converted = convertFilterStackForType(raw, type);
            if (converted != null) {
                priorityList.add(converted.copy());
            }
        }

        if (type == ITEM_STACK_TYPE && getInstalledUpgrades(Upgrades.FUZZY) > 0) {
            FuzzyPriorityList<IAEItemStack> partitionList = new FuzzyPriorityList<>(
                priorityList,
                (FuzzyMode) cm.getSetting(Settings.FUZZY_MODE));
            handler.setPartitionList(partitionList);
            handler.setExtractPartitionList(partitionList);
        } else {
            PrecisePriorityList partitionList = new PrecisePriorityList(priorityList);
            handler.setPartitionList(partitionList);
            handler.setExtractPartitionList(partitionList);
        }
    }

    private IAEStack<?> convertFilterStackForType(final IAEStack<?> stack, final IAEStackType<?> type) {
        if (stack == null || type == null) return null;
        if (stack.getStackType() == type) return stack;
        if (type == FLUID_STACK_TYPE && Platform.isAE2FCLoaded && stack instanceof IAEItemStack itemStack) {
            return getFluidStackFromPacket(itemStack);
        }
        return null;
    }

    private IAEStack<?> getFluidStackFromPacket(final IAEItemStack itemStack) {
        try {
            Class<?> packetClass = Class.forName(FLUID_PACKET_CLASS);
            if (!packetClass.isInstance(itemStack.getItem())) return null;
            Method method = packetClass.getMethod("getFluidAEStack", IAEItemStack.class);
            Object converted = method.invoke(null, itemStack);
            return converted instanceof IAEStack<?>aeStack ? aeStack : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private void syncOreFilterUpgradeState() {
        if (getInstalledUpgrades(Upgrades.ORE_FILTER) == 0) {
            oreFilterString = "";
        } else if (oreFilterString.isEmpty()) {
            oreFilterString = previousOreFilterString;
        }
    }

    @Override
    public String getFilter() {
        return oreFilterString;
    }

    @Override
    public void setFilter(final String filter) {
        final String normalizedFilter = filter == null ? "" : filter;
        oreFilterString = normalizedFilter;
        previousOreFilterString = normalizedFilter;
        invalidateStorageHandler();
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return new ItemStack(CommonProxy.blockStorageBus);
    }

    @Override
    public IAEStackType<?> getStackType() {
        return ITEM_STACK_TYPE;
    }

    public IItemList<IAEItemStack> getItemList() {
        return ITEM_STACK_TYPE.createList();
    }

    @Override
    public boolean isValid(final Object verificationToken) {
        return handlers.containsValue(verificationToken);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void postChange(final IBaseMonitor<IAEStack<?>> monitor, final Iterable<IAEStack<?>> change,
        final BaseActionSource source) {
        retireAndUnregisterIfHostUnavailable();
        if (!isStorageAccessible()) return;
        MEInventoryHandler handler = monitorHandlers.get(monitor);
        IAEStackType type = monitorTypes.get(monitor);
        if (handler == null || type == null) return;

        Iterable<IAEStack<?>> filteredChanges = filterChanges(change, handler);
        if (filteredChanges == null) return;

        try {
            getProxy().getStorage()
                .postAlterationOfStoredItems(type, filteredChanges, mySrc);
        } catch (GridAccessException ignored) {}
    }

    private Iterable<IAEStack<?>> filterChanges(final Iterable<IAEStack<?>> change,
        final MEInventoryHandler<?> handler) {
        if (handler.isExtractFilterActive() && !handler.getExtractPartitionList()
            .isEmpty()) {
            Predicate filterCondition = handler.getExtractFilterCondition();
            java.util.ArrayList<IAEStack<?>> filtered = new java.util.ArrayList<>();
            for (IAEStack<?> changedItem : change) {
                if (filterCondition.test(changedItem)) {
                    filtered.add(changedItem);
                }
            }
            return filtered.isEmpty() ? null : Collections.unmodifiableList(filtered);
        }
        return change;
    }

    @Override
    public void onListUpdate() {
        // AE2's Storage Bus does not use this callback directly.
    }

    @Override
    public ConnectOverride overridePipeConnection(final PipeType type, final ForgeDirection with) {
        return type == PipeType.ITEM && with == getTargetSide() ? ConnectOverride.CONNECT : ConnectOverride.DISCONNECT;
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

    private TileEntity getLoadedTargetTile() {
        if (this.contributionRetired || this.worldObj == null || !isHostStillLoaded()) return null;
        final ForgeDirection facing = getTargetSide();
        return SingularityChunkAccess.getLoadedAdjacentTile(this, facing);
    }

    private TileEntity getAdjacentTile() {
        return getLoadedTargetTile();
    }

    @Override
    public boolean isContributionLoaded() {
        return !this.contributionRetired;
    }

    private ForgeDirection getTargetSide() {
        return ForgeDirection.getOrientation(worldObj.getBlockMetadata(xCoord, yCoord, zCoord));
    }

    private void checkInterfaceVsStorageBus(final TileEntity target, final ForgeDirection side) {
        IInterfaceHost interfaceHost = null;
        if (target instanceof IInterfaceHost host) {
            interfaceHost = host;
        }
        if (target instanceof appeng.api.parts.IPartHost partHost) {
            Object part = partHost.getPart(side);
            if (part instanceof IInterfaceHost host) {
                interfaceHost = host;
            }
        }
        if (interfaceHost != null && interfaceHost.getActionableNode() != null) {
            Platform.addStat(
                interfaceHost.getActionableNode()
                    .getPlayerID(),
                appeng.core.stats.Achievements.Recursive.getAchievement());
        }
    }

    private final class GuardedStorageBusInventoryHandler<T extends IAEStack<T>> extends StorageBusInventoryHandler<T> {

        private GuardedStorageBusInventoryHandler(final IMEInventory<T> inventory, final IAEStackType<T> type) {
            super(inventory, type);
        }

        private boolean canUseStorage() {
            return TileSingularityStorageBus.this.bypassStorageAccessGuard
                || TileSingularityStorageBus.this.isStorageAccessible();
        }

        @Override
        public T injectItems(final T input, final Actionable type, final BaseActionSource src) {
            return canUseStorage() ? super.injectItems(input, type, src) : input;
        }

        @Override
        public T extractItems(final T request, final Actionable type, final BaseActionSource src) {
            return canUseStorage() ? super.extractItems(request, type, src) : null;
        }

        @Override
        public IItemList<T> getAvailableItems(final IItemList<T> out, final int iteration) {
            return canUseStorage() ? super.getAvailableItems(out, iteration) : out;
        }

        @Override
        public IItemList<T> getAvailableItems(final IItemList<T> out, final int iteration,
            final Optional<Predicate<T>> preFilter) {
            return canUseStorage() ? super.getAvailableItems(out, iteration, preFilter) : out;
        }

        @Override
        public T getAvailableItem(final T request, final int iteration) {
            return canUseStorage() ? super.getAvailableItem(request, iteration) : null;
        }

        @Override
        public boolean canAccept(final T input) {
            return canUseStorage() && super.canAccept(input);
        }

        @Override
        public boolean isPrioritized(final T input) {
            return canUseStorage() && super.isPrioritized(input);
        }

        @Override
        public AccessRestriction getAccess() {
            return canUseStorage() ? super.getAccess() : AccessRestriction.NO_ACCESS;
        }

        @Override
        public boolean validForPass(final int pass) {
            return canUseStorage() && super.validForPass(pass);
        }

        @Override
        public IMEInventory<T> getInternal() {
            return canUseStorage() ? super.getInternal() : null;
        }

        @Override
        public IMENetworkInventory<T> getExternalNetworkInventory() {
            return canUseStorage() ? super.getExternalNetworkInventory() : null;
        }

        @Override
        public PrioritizedNetworkItemList<T> getAvailableItemsWithPriority(final int iteration) {
            if (canUseStorage()) return super.getAvailableItemsWithPriority(iteration);
            final IMENetworkInventory<T> external = super.getExternalNetworkInventory();
            return external == null ? null : new PrioritizedNetworkItemList<>(external);
        }
    }
}
