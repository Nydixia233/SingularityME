package com.github.singularityme.tile;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
import appeng.api.config.ExtractionMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IStorageBus;
import appeng.api.storage.IExternalStorageHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
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
import appeng.util.Platform;
import appeng.util.inv.ItemSlot;
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
public class TileSingularityStorageBus extends AENetworkInvTile
    implements IStorageBus, IConfigurableObject, IConfigManagerHost {

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

    public TileSingularityStorageBus() {
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

    public void partitionFilterFromAdjacentInventory() {
        InventoryAdaptor adj = getAdjacentAdaptor();
        if (adj == null) return;

        int slot = 0;
        final int maxSlots = getAvailableFilterSlots();
        for (ItemSlot itemSlot : adj) {
            if (slot >= maxSlots) break;

            ItemStack stack = itemSlot.getItemStack();
            if (stack != null) {
                IAEItemStack ae = appeng.util.item.AEItemStack.create(stack);
                if (ae != null) {
                    ae.setStackSize(1);
                    filterInv.putAEStackInSlot(slot++, ae);
                }
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

    private void invalidateStorageHandler() {
        for (IMEMonitor<?> monitor : monitorHandlers.keySet()) {
            monitor.removeListener(this);
        }
        handlers.clear();
        storageBusMonitors.clear();
        monitorHandlers.clear();
        monitorTypes.clear();
        handlerHash = 0;
        markDirty();
        postCellArrayUpdate();
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

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeExtraNBT(final NBTTagCompound tag) {
        filterInv.writeToNBT(tag, "filterInv");
        upgradesInv.writeToNBT(tag, "upgradesInv");
        cm.writeToNBT(tag);
        tag.setInteger("priority", priority);
        tag.setString("filter", oreFilterString);
        tag.setString("previousFilter", previousOreFilterString);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readExtraNBT(final NBTTagCompound tag) {
        filterInv.readFromNBT(tag, "filterInv");
        upgradesInv.readFromNBT(tag, "upgradesInv");
        cm.readFromNBT(tag);
        priority = tag.getInteger("priority");
        oreFilterString = tag.getString("filter");
        previousOreFilterString = tag.getString("previousFilter");
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(5, 40, true, true);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        TileEntity target = getAdjacentTile();
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
        return storageBusMonitors.isEmpty() ? TickRateModulation.SLOWER : modulation;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<IMEInventoryHandler> getCellArray(final IAEStackType<?> type) {
        if (!getProxy().isActive()) {
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
        return getInternalHandler(getStackType());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private MEInventoryHandler<?> getInternalHandler(final IAEStackType<?> type) {
        TileEntity target = getAdjacentTile();
        int newHandlerHash = Platform.generateTileHash(target);
        boolean canReuseHandler = handlerHash != 0 && handlerHash == newHandlerHash;
        if (!handlers.isEmpty() && !canReuseHandler) {
            invalidateStorageHandler();
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

        MEInventoryHandler handler = new StorageBusInventoryHandler(inv, type);
        configureHandler(handler, type);
        handlers.put(type, handler);

        if (inv instanceof IMEMonitor<?>monitor) {
            monitor.addListener(this, handler);
            monitorHandlers.put(monitor, handler);
            monitorTypes.put(monitor, type);
        }

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
        oreFilterString = filter == null ? "" : filter;
        previousOreFilterString = oreFilterString;
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

    @Override
    public boolean isValid(final Object verificationToken) {
        return handlers.containsValue(verificationToken);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void postChange(final IBaseMonitor<IAEStack<?>> monitor, final Iterable<IAEStack<?>> change,
        final BaseActionSource source) {
        if (!getProxy().isActive()) return;
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
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.SMART;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    private TileEntity getAdjacentTile() {
        ForgeDirection facing = getTargetSide();
        return worldObj.getTileEntity(xCoord + facing.offsetX, yCoord + facing.offsetY, zCoord + facing.offsetZ);
    }

    private InventoryAdaptor getAdjacentAdaptor() {
        TileEntity te = getAdjacentTile();
        if (te == null) return null;
        return InventoryAdaptor.getAdaptor(te, getTargetSide().getOpposite());
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
}
