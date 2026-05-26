package com.github.singularityme.tile;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.Collections;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.core.SingularityNetworkManager;
import com.github.singularityme.proxy.CommonProxy;

import appeng.api.config.AccessRestriction;
import appeng.api.config.ActionItems;
import appeng.api.config.ExtractionMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.helpers.IPriorityHost;
import appeng.me.GridAccessException;
import appeng.me.GridNode;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.MEMonitorIInventory;
import appeng.parts.automation.BlockUpgradeInventory;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.inv.AdaptorIInventory;
import appeng.util.prioitylist.FuzzyPriorityList;
import appeng.util.prioitylist.PrecisePriorityList;

/**
 * Singularity Storage Bus — exposes an adjacent container's inventory into the player's global
 * SingularityGrid without requiring any local ME cables.
 *
 * <p>
 * Supports CAPACITY (up to 5 cards → 18/27/36/45/54/63 filter slots), FUZZY (fuzzy filter
 * matching), INVERTER (blacklist mode), and access restriction / priority.
 */
public class TileSingularityStorageBus extends AENetworkInvTile implements ICellContainer, IGridTickable,
    IConfigurableObject, IConfigManagerHost, IUpgradeableHost, IIAEStackInventory, IPriorityHost {

    /** Simple hash of slot count * slot contents — detects any container change. */
    private int lastHash = -1;

    /** Lazily built handler; reset whenever the adjacent container, filter, or upgrades change. */
    private MEInventoryHandler<IAEItemStack> handler = null;

    /** Ghost filter inventory — 63 slots (max with 5 CAPACITY cards). Empty = expose all items. */
    private final IAEStackInventory filterInv = new IAEStackInventory(this, 63, StorageName.CONFIG);

    /** Upgrade card inventory — 5 slots. */
    private final BlockUpgradeInventory upgradesInv = new BlockUpgradeInventory(CommonProxy.blockStorageBus, this, 5);

    /** Config manager for access restriction setting. */
    private final IConfigManager cm = new ConfigManager(this);

    /** Storage priority — higher values are preferred over lower ones. */
    private int priority = 0;

    public TileSingularityStorageBus() {
        cm.registerSetting(Settings.ACCESS, AccessRestriction.READ_WRITE);
        cm.registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        cm.registerSetting(Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY);
        cm.registerSetting(Settings.EXTRACTION_MODE, ExtractionMode.LOOSE);
        cm.registerSetting(Settings.STICKY_MODE, YesNo.NO);
        cm.registerSetting(Settings.ACTIONS, ActionItems.CLOSE);
    }

    // ---- IConfigurableObject / IConfigManagerHost ----

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
        IInventory adj = getAdjacentInventory();
        if (adj == null) return;
        int slot = 0;
        final int maxSlots = getAvailableFilterSlots();
        for (int i = 0; i < adj.getSizeInventory() && slot < maxSlots; i++) {
            ItemStack stack = adj.getStackInSlot(i);
            if (stack != null) {
                IAEItemStack ae = appeng.util.item.AEItemStack.create(stack);
                if (ae != null) {
                    ae.setStackSize(1);
                    filterInv.putAEStackInSlot(slot++, ae);
                }
            }
        }
        // clear remaining slots
        for (int i = slot; i < filterInv.getSizeInventory(); i++) {
            filterInv.putAEStackInSlot(i, null);
        }
        invalidateStorageHandler();
    }

    public void onFilterChanged() {
        invalidateStorageHandler();
    }

    private void invalidateStorageHandler() {
        handler = null;
        markDirty();
        try {
            getProxy().getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (GridAccessException ignored) {}
    }

    // ---- IPriorityHost ----

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(final int newValue) {
        priority = newValue;
        invalidateStorageHandler();
    }

    /** Alias for container use. */
    public int getPriorityValue() {
        return getPriority();
    }

    public void setPriorityValue(final int p) {
        setPriority(p);
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

    // ---- IIAEStackInventory ----

    @Override
    public void saveAEStackInv() {
        markDirty();
    }

    // ---- filter access (for GUI container) ----

    public IAEStackInventory getAEInventoryByName(final StorageName name) {
        return name == StorageName.CONFIG ? filterInv : null;
    }

    /** Returns the number of filter slots currently active based on installed CAPACITY cards. */
    public int getAvailableFilterSlots() {
        return Math.min(18 + getInstalledUpgrades(Upgrades.CAPACITY) * 9, 63);
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
        handler = null;
        try {
            getProxy().getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (GridAccessException ignored) {}
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

    // ---- NBT ----

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeExtraNBT(final NBTTagCompound tag) {
        filterInv.writeToNBT(tag, "filterInv");
        upgradesInv.writeToNBT(tag, "upgradesInv");
        cm.writeToNBT(tag);
        tag.setInteger("priority", priority);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readExtraNBT(final NBTTagCompound tag) {
        filterInv.readFromNBT(tag, "filterInv");
        upgradesInv.readFromNBT(tag, "upgradesInv");
        cm.readFromNBT(tag);
        priority = tag.getInteger("priority");
    }

    // ---- IGridTickable — detect adjacent inventory changes ----

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(5, 40, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        IInventory inv = getAdjacentInventory();
        int newHash = computeHash(inv);
        if (newHash != lastHash) {
            lastHash = newHash;
            handler = null;
            try {
                getProxy().getGrid()
                    .postEvent(new MENetworkCellArrayUpdate());
            } catch (GridAccessException ignored) {}
            return TickRateModulation.FASTER;
        }
        return TickRateModulation.SLOWER;
    }

    // ---- ICellContainer ----

    @Override
    @SuppressWarnings("unchecked")
    public List<IMEInventoryHandler> getCellArray(final IAEStackType<?> type) {
        if (type != ITEM_STACK_TYPE || !getProxy().isActive()) {
            return Collections.emptyList();
        }
        if (handler == null) {
            IInventory inv = getAdjacentInventory();
            if (inv == null) return Collections.emptyList();

            MEMonitorIInventory monitor = new MEMonitorIInventory(new AdaptorIInventory(inv));
            handler = new MEInventoryHandler<>(monitor, ITEM_STACK_TYPE);
            handler.setPriority(priority);

            // Access restriction
            AccessRestriction access = (AccessRestriction) cm.getSetting(Settings.ACCESS);
            handler.setBaseAccess(access);

            // Extraction mode
            ExtractionMode extractionMode = (ExtractionMode) cm.getSetting(Settings.EXTRACTION_MODE);
            boolean extractRights = (access == AccessRestriction.READ)
                || (extractionMode == ExtractionMode.STRICT && access == AccessRestriction.READ_WRITE);
            handler.setIsExtractFilterActive(extractRights);

            if (getInstalledUpgrades(Upgrades.STICKY) > 0) {
                handler.setSticky(true);
            }

            // Filter (CAPACITY + FUZZY + INVERTER)
            applyFilter();
        }
        return Collections.singletonList(handler);
    }

    private void applyFilter() {
        if (handler == null) return;

        int availSlots = getAvailableFilterSlots();
        boolean fuzzy = getInstalledUpgrades(Upgrades.FUZZY) > 0;
        boolean inverter = getInstalledUpgrades(Upgrades.INVERTER) > 0;
        FuzzyMode fuzzyMode = fuzzy ? (FuzzyMode) cm.getSetting(Settings.FUZZY_MODE) : FuzzyMode.IGNORE_ALL;

        // Build the item list for the partition
        IItemList<IAEItemStack> filterList = ITEM_STACK_TYPE.createList();
        boolean hasAnyFilter = false;
        for (int i = 0; i < availSlots; i++) {
            IAEStack<?> f = filterInv.getAEStackInSlot(i);
            if (f instanceof IAEItemStack ae) {
                filterList.add(ae.copy());
                hasAnyFilter = true;
            }
        }

        if (!hasAnyFilter) {
            // Empty filter: expose all items (whitelist with no entries = pass-through)
            handler.setWhitelist(IncludeExclude.WHITELIST);
            handler.setPartitionList(new PrecisePriorityList<>(ITEM_STACK_TYPE.createList()));
        } else {
            handler.setWhitelist(inverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
            if (fuzzy) {
                FuzzyPriorityList<IAEItemStack> partitionList = new FuzzyPriorityList<>(filterList, fuzzyMode);
                handler.setPartitionList(partitionList);
                handler.setExtractPartitionList(partitionList);
            } else {
                PrecisePriorityList<IAEItemStack> partitionList = new PrecisePriorityList<>(filterList);
                handler.setPartitionList(partitionList);
                handler.setExtractPartitionList(partitionList);
            }
        }
    }

    @Override
    public void saveChanges(final IMEInventory cellInventory) {
        // no-op: adjacent inventory persists itself
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

    // ---- helpers ----

    private IInventory getAdjacentInventory() {
        int meta = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
        ForgeDirection facing = ForgeDirection.getOrientation(meta);
        TileEntity te = worldObj
            .getTileEntity(xCoord + facing.offsetX, yCoord + facing.offsetY, zCoord + facing.offsetZ);
        if (te instanceof ISidedInventory) return (ISidedInventory) te;
        if (te instanceof IInventory) return (IInventory) te;
        return null;
    }

    private static int computeHash(final IInventory inv) {
        if (inv == null) return 0;
        int h = inv.getSizeInventory();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            var stack = inv.getStackInSlot(i);
            h = h * 31 + (stack == null ? 0
                : stack.getItem()
                    .hashCode() ^ stack.stackSize);
        }
        return h;
    }
}
