package com.github.singularityme.tile;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.core.SingularityNetworkManager;
import com.google.common.base.Optional;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMENetworkInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.IPrimaryGuiIconProvider;
import appeng.helpers.IPriorityHost;
import appeng.items.AEBaseCell;
import appeng.items.materials.ItemMultiMaterial;
import appeng.items.storage.ItemBasicStorageCell;
import appeng.me.GridAccessException;
import appeng.me.GridNode;
import appeng.me.storage.MEInventoryHandler;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.item.PrioritizedNetworkItemList;
import io.netty.buffer.ByteBuf;

/**
 * Singularity Drive — accepts up to 10 AE2 storage cells and contributes their
 * capacity to the player's global SingularityGrid.
 *
 * <p>
 * Analogous to {@code TileDrive} but registered with the SingularityGrid instead
 * of a local ME network. Implements {@link IChestOrDrive} so the AE2 cell
 * status/type bitmask machinery (used by world-block renderers and APIs) works
 * uniformly with native AE2 drives.
 */
public class TileSingularityDrive extends AENetworkInvTile implements IChestOrDrive, IPriorityHost, IGridTickable,
    ISaveProvider, IPrimaryGuiIconProvider, ISingularityContributionHost, ISingularityNetworkDevice {

    private static final int CELL_COUNT = 10;
    /** Lower 30 bits = 3 bits per cell × 10 cells. Bit 30 = active flag. */
    private static final int STATE_MASK = 0b1111111111111111111111111111111;
    private static final int STATE_ACTIVE_MASK = 1 << 30;

    /** The 10 cell slots. */
    private final int[] sides = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
    private final AppEngInternalInventory cells = new AppEngInternalInventory(this, CELL_COUNT);
    private final BaseActionSource mySrc = new MachineSource(this);

    /** Cached per-slot cell handler / inventory handler, rebuilt on inventory change. */
    private final ICellHandler[] handlersBySlot = new ICellHandler[CELL_COUNT];
    @SuppressWarnings("rawtypes")
    private final MEInventoryHandler[] invBySlot = new MEInventoryHandler[CELL_COUNT];
    @SuppressWarnings("rawtypes")
    private final Map<IAEStackType<?>, List<IMEInventoryHandler>> cellsMap = new IdentityHashMap<>();
    private boolean isCached = false;

    /** Storage priority — higher values are preferred over lower ones. */
    private int priority = 0;
    /** Per-player network index. 0 = unassigned. */
    private int networkID = 0;
    private int gridOwnerPlayerID = -1;
    private boolean defaultNetworkApplied = false;

    /** Bitmask: 3 bits per cell for status (0..4) + bit 30 for active. Synced to client. */
    private int state = 0;
    /** Bitmask: 2 bits per cell for type (0=item, 1=fluid, 2=essentia). Synced to client. */
    private int type = 0;
    private boolean wasActive = false;
    private boolean contributionRetired = true;
    private boolean chunkAccessAvailable = true;

    public TileSingularityDrive() {
        this.getProxy()
            .setFlags();
        this.getProxy()
            .setValidSides(EnumSet.noneOf(ForgeDirection.class));
    }

    // ---- AENetworkInvTile requirements ----

    @Override
    public IInventory getInternalInventory() {
        return cells;
    }

    @Override
    public int[] getAccessibleSlotsBySide(final ForgeDirection side) {
        return this.sides;
    }

    @Override
    public boolean isItemValidForSlot(final int i, final ItemStack itemstack) {
        return itemstack != null && AEApi.instance()
            .registries()
            .cell()
            .isCellHandled(itemstack);
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
        final ItemStack removedStack, final ItemStack newStack) {
        if (this.contributionRetired) return;
        if (this.isCached) {
            this.isCached = false;
            this.updateState();
        }
        try {
            getProxy().getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
            final IStorageGrid storageGrid = getProxy().getStorage();
            Platform.postChanges(storageGrid, removedStack, newStack, this.mySrc);
        } catch (GridAccessException ignored) {}
        markDirty();
        this.markForUpdate();
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
        if (worldObj.isRemote) return;
        this.updateState();
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
        if (this.worldObj == null || this.worldObj.isRemote) {
            this.contributionRetired = true;
            this.chunkAccessAvailable = false;
            clearCellHandlerCaches();
            return;
        }

        this.updateState();
        final Map<IAEStackType<?>, IItemList> beforeByType = captureAvailableItems();
        this.contributionRetired = true;
        this.chunkAccessAvailable = false;
        clearCellHandlerCaches();
        this.getProxy()
            .setIdlePowerUsage(0.0);
        postAvailableItemRemoval(beforeByType);
        try {
            this.getProxy()
                .getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (GridAccessException ignored) {}
        this.markForUpdate();
    }

    private void clearCellHandlerCaches() {
        this.cellsMap.clear();
        for (int i = 0; i < CELL_COUNT; i++) {
            this.handlersBySlot[i] = null;
            this.invBySlot[i] = null;
        }
        this.isCached = false;
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

    // ---- IChestOrDrive ----

    @Override
    public int getCellCount() {
        return CELL_COUNT;
    }

    @Override
    public int getCellStatus(final int slot) {
        if (Platform.isClient()) {
            return (this.state >> (slot * 3)) & 0b111;
        }
        final ItemStack cell = this.cells.getStackInSlot(slot);
        final ICellHandler ch = this.handlersBySlot[slot];
        @SuppressWarnings("unchecked")
        final MEInventoryHandler<IAEItemStack> handler = this.invBySlot[slot];
        if (handler == null || ch == null) return 0;
        return ch.getStatusForCell(cell, handler.getInternal());
    }

    @Override
    public int getCellType(final int slot) {
        if (Platform.isClient()) {
            return (this.type >> (slot * 2)) & 0b11;
        }

        @SuppressWarnings("rawtypes")
        final MEInventoryHandler handler = this.invBySlot[slot];
        if (handler == null) return 0;
        if (handler.getInternal() instanceof ICellCacheRegistry iccr) {
            switch (iccr.getCellType()) {
                case ITEM:
                    return 0;
                case FLUID:
                    return 1;
                case ESSENTIA:
                    return 2;
            }
        }
        return 0;
    }

    @SuppressWarnings("rawtypes")
    public MEInventoryHandler getCellInvBySlot(final int slot) {
        return this.invBySlot[slot];
    }

    @Override
    public boolean isPowered() {
        if (Platform.isClient()) {
            return (this.state & STATE_ACTIVE_MASK) == STATE_ACTIVE_MASK;
        }
        return this.getProxy()
            .isActive();
    }

    // ---- ICellContainer / IPriorityHost ----

    @Override
    @Nonnull
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List<IMEInventoryHandler> getCellArray(final IAEStackType<?> stackType) {
        if (!this.isContributionAvailable()) return Collections.emptyList();
        this.updateState();
        return this.cellsMap.getOrDefault(stackType, Collections.emptyList());
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(final int newValue) {
        priority = newValue;
        markDirty();
        this.isCached = false;
        this.updateState();
        try {
            getProxy().getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (GridAccessException ignored) {}
    }

    public void setPriorityValue(final int p) {
        this.setPriority(p);
    }

    public int getPriorityValue() {
        return priority;
    }

    @Override
    public void saveChanges(final IMEInventory cellInventory) {
        worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
    }

    // ---- updateState / recalculateDisplay ----

    /**
     * Rebuild the per-slot cell handler and inventory handler caches.
     * Called when the cell inventory changes or priority changes.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void updateState() {
        if (this.isCached) return;
        if (this.worldObj == null || this.worldObj.isRemote) return;
        if (this.contributionRetired || !SingularityChunkAccess.isHostChunkNetworkAccessible(this)) {
            clearCellHandlerCaches();
            this.isCached = true;
            this.getProxy()
                .setIdlePowerUsage(0.0);
            return;
        }

        this.cellsMap.clear();
        for (final IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.cellsMap.put(type, new ArrayList<>());
        }

        double power = 2.0;
        for (int x = 0; x < CELL_COUNT; x++) {
            this.invBySlot[x] = null;
            this.handlersBySlot[x] = null;

            final ItemStack is = this.cells.getStackInSlot(x);
            if (is == null) continue;
            final ICellHandler ch = AEApi.instance()
                .registries()
                .cell()
                .getHandler(is);
            if (ch == null) continue;
            this.handlersBySlot[x] = ch;
            for (final IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                final IMEInventoryHandler cell = ch.getCellInventory(is, this, type);
                if (cell != null) {
                    power += ch.cellIdleDrain(is, cell);

                    final MEInventoryHandler ih = new DriveWatcher(cell, cell.getStackType());
                    ih.setPriority(this.priority);
                    this.invBySlot[x] = ih;
                    this.cellsMap.get(type)
                        .add(ih);
                    break;
                }
            }
        }
        this.getProxy()
            .setIdlePowerUsage(power);
        this.isCached = true;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Map<IAEStackType<?>, IItemList> captureAvailableItems() {
        final Map<IAEStackType<?>, IItemList> out = new IdentityHashMap<>();
        if (!this.getProxy()
            .isActive()) return out;

        for (final Map.Entry<IAEStackType<?>, List<IMEInventoryHandler>> entry : this.cellsMap.entrySet()) {
            final IAEStackType stackType = entry.getKey();
            final IItemList list = stackType.createList();
            for (final IMEInventoryHandler handler : entry.getValue()) {
                handler.getAvailableItems(list, IterationCounter.fetchNewId());
            }
            if (!list.isEmpty()) {
                out.put(entry.getKey(), list);
            }
        }
        return out;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void postAvailableItemRemoval(final Map<IAEStackType<?>, IItemList> beforeByType) {
        if (beforeByType.isEmpty()) return;

        try {
            final IStorageGrid storageGrid = this.getProxy()
                .getStorage();
            for (final Map.Entry<IAEStackType<?>, IItemList> entry : beforeByType.entrySet()) {
                final List<IAEStack<?>> changes = new ArrayList<>();
                for (final Object obj : entry.getValue()) {
                    final IAEStack stack = ((IAEStack) obj).copy();
                    stack.setStackSize(-stack.getStackSize());
                    changes.add(stack);
                }
                if (!changes.isEmpty()) {
                    storageGrid.postAlterationOfStoredItems(entry.getKey(), changes, this.mySrc);
                }
            }
        } catch (GridAccessException ignored) {}
    }

    @Override
    public boolean isContributionLoaded() {
        return !this.contributionRetired;
    }

    private final class DriveWatcher<T extends IAEStack<T>> extends MEInventoryHandler<T> {

        public DriveWatcher(final IMEInventory<T> i, final IAEStackType<T> type) {
            super(i, type);
        }

        private boolean canUseStorage() {
            return TileSingularityDrive.this.isContributionAvailable();
        }

        @Override
        public T injectItems(final T input, final Actionable type, final BaseActionSource src) {
            return this.canUseStorage() ? super.injectItems(input, type, src) : input;
        }

        @Override
        public T extractItems(final T request, final Actionable type, final BaseActionSource src) {
            return this.canUseStorage() ? super.extractItems(request, type, src) : null;
        }

        @Override
        public IItemList<T> getAvailableItems(final IItemList<T> out, final int iteration) {
            return this.canUseStorage() ? super.getAvailableItems(out, iteration) : out;
        }

        @Override
        public IItemList<T> getAvailableItems(final IItemList<T> out, final int iteration,
            final java.util.Optional<Predicate<T>> preFilter) {
            return this.canUseStorage() ? super.getAvailableItems(out, iteration, preFilter) : out;
        }

        @Override
        public T getAvailableItem(final T request, final int iteration) {
            return this.canUseStorage() ? super.getAvailableItem(request, iteration) : null;
        }

        @Override
        public boolean canAccept(final T input) {
            return this.canUseStorage() && super.canAccept(input);
        }

        @Override
        public boolean isPrioritized(final T input) {
            return this.canUseStorage() && super.isPrioritized(input);
        }

        @Override
        public AccessRestriction getAccess() {
            return this.canUseStorage() ? super.getAccess() : AccessRestriction.NO_ACCESS;
        }

        @Override
        public boolean validForPass(final int pass) {
            return this.canUseStorage() && super.validForPass(pass);
        }

        @Override
        public IMENetworkInventory<T> getExternalNetworkInventory() {
            return this.canUseStorage() ? super.getExternalNetworkInventory() : null;
        }

        @Override
        public PrioritizedNetworkItemList<T> getAvailableItemsWithPriority(final int iteration) {
            if (this.canUseStorage()) return super.getAvailableItemsWithPriority(iteration);
            final IMENetworkInventory<T> external = super.getExternalNetworkInventory();
            return external == null ? null : new PrioritizedNetworkItemList<>(external);
        }
    }

    public static void partitionStorageCellToItemsOnCell(final ICellInventoryHandler handler) {
        final ICellInventory cellInventory = handler.getCellInv();
        if (cellInventory != null && cellInventory.getStoredItemTypes() != 0) {
            int idx = 0;
            for (final Object partitionStack : handler.getAvailableItems(
                cellInventory.getStackType()
                    .createPrimitiveList(),
                IterationCounter.fetchNewId())) {
                final IAEStack<?> aes = ((IAEStack<?>) partitionStack).copy();
                aes.setStackSize(1);
                cellInventory.getConfigAEInventory()
                    .putAEStackInSlot(idx++, aes);
            }
            cellInventory.getConfigAEInventory()
                .markDirty();
        }
    }

    public static void unpartitionStorageCell(final ICellInventoryHandler handler) {
        final ICellInventory cellInventory = handler.getCellInv();
        if (cellInventory != null) {
            for (int i = 0; i < cellInventory.getConfigAEInventory()
                .getSizeInventory(); i++) {
                cellInventory.getConfigAEInventory()
                    .putAEStackInSlot(i, null);
            }
            cellInventory.getConfigAEInventory()
                .markDirty();
        }
    }

    public static boolean applyStickyCardToItemStorageCell(final ICellHandler cellHandler, final ItemStack cell,
        final ISaveProvider host, final ICellWorkbenchItem cellItem) {
        final IMEInventoryHandler<?> inv = cellHandler.getCellInventory(
            cell,
            host,
            cell.getItem() instanceof AEBaseCell abc ? abc.getStackType() : ITEM_STACK_TYPE);
        if (inv instanceof ICellInventoryHandler handler) {
            final ICellInventory cellInventory = handler.getCellInv();
            if (cellInventory != null && cellInventory.getStoredItemTypes() > 0) {
                final IInventory cellUpgrades = cellItem.getUpgradesInventory(cell);
                int freeSlot = -1;
                for (int i = 0; i < cellUpgrades.getSizeInventory(); i++) {
                    if (freeSlot == -1 && cellUpgrades.getStackInSlot(i) == null) {
                        freeSlot = i;
                        continue;
                    } else if (cellUpgrades.getStackInSlot(i) == null) {
                        continue;
                    }
                    if (ItemMultiMaterial.instance.getType(cellUpgrades.getStackInSlot(i)) == Upgrades.STICKY) {
                        freeSlot = -1;
                        break;
                    }
                }
                final Optional<ItemStack> stickyCard = AEApi.instance()
                    .definitions()
                    .materials()
                    .cardSticky()
                    .maybeStack(1);
                if (freeSlot != -1 && stickyCard.isPresent()) {
                    cellUpgrades.setInventorySlotContents(freeSlot, stickyCard.get());
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean toggleItemStorageCellLocking() {
        this.updateState();
        boolean res = false;
        for (int i = 0; i < this.handlersBySlot.length; i++) {
            final ICellHandler cellHandler = this.handlersBySlot[i];
            final ItemStack cell = this.cells.getStackInSlot(i);
            if (cellHandler == null || ItemBasicStorageCell.checkInvalidForLockingAndStickyCarding(cell, cellHandler)) {
                continue;
            }
            final IMEInventoryHandler<?> inv = cellHandler.getCellInventory(
                cell,
                this,
                cell.getItem() instanceof AEBaseCell abc ? abc.getStackType() : ITEM_STACK_TYPE);
            if (inv instanceof ICellInventoryHandler handler) {
                if (ItemBasicStorageCell.cellIsPartitioned(handler)) {
                    unpartitionStorageCell(handler);
                } else {
                    partitionStorageCellToItemsOnCell(handler);
                }
                res = true;
            }
        }
        if (this.isCached) {
            this.isCached = false;
            this.updateState();
        }
        try {
            this.getProxy()
                .getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (final GridAccessException ignored) {}
        return res;
    }

    @Override
    public int applyStickyToItemStorageCells(final ItemStack cards) {
        this.updateState();
        int res = 0;
        for (int i = 0; i < this.handlersBySlot.length; i++) {
            final ICellHandler cellHandler = this.handlersBySlot[i];
            final ItemStack cell = this.cells.getStackInSlot(i);
            if (cellHandler == null || ItemBasicStorageCell.checkInvalidForLockingAndStickyCarding(cell, cellHandler)) {
                continue;
            }
            if (cell.getItem() instanceof ICellWorkbenchItem cellItem && res + 1 <= cards.stackSize) {
                if (applyStickyCardToItemStorageCell(cellHandler, cell, this, cellItem)) {
                    res++;
                }
            }
        }
        if (this.isCached) {
            this.isCached = false;
            this.updateState();
        }
        try {
            this.getProxy()
                .getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (final GridAccessException ignored) {}
        return res;
    }

    /**
     * Recompute {@link #state} and {@link #type} from current cell statuses.
     * If the active-flag changed, post a {@link MENetworkCellArrayUpdate} so dependent
     * networks rebuild their cell array. If state or type changed, push to client.
     */
    private void recalculateDisplay() {
        int newState = 0;
        int newType = 0;
        final boolean currentActive = this.isContributionAvailable();
        if (currentActive) {
            newState |= STATE_ACTIVE_MASK;
        }
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            try {
                this.getProxy()
                    .getGrid()
                    .postEvent(new MENetworkCellArrayUpdate());
            } catch (GridAccessException ignored) {}
        }
        for (int x = 0; x < CELL_COUNT; x++) {
            newState |= ((this.getCellStatus(x) & 0b111) << (3 * x));
            newType |= ((this.getCellType(x) & 0b11) << (2 * x));
        }
        if (this.state != newState || this.type != newType) {
            this.state = newState;
            this.type = newType;
            this.markForUpdate();
        }
    }

    // ---- AE2 network event subscription (rebuild on power/channel changes) ----

    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.recalculateDisplay();
    }

    @MENetworkEventSubscribe
    public void channelRender(final MENetworkChannelsChanged c) {
        this.recalculateDisplay();
    }

    // ---- IGridTickable ----

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(15, 15, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        this.syncChunkAccessState();
        this.recalculateDisplay();
        return TickRateModulation.SAME;
    }

    private void syncChunkAccessState() {
        if (this.worldObj == null || this.worldObj.isRemote || this.contributionRetired) return;

        final boolean available = SingularityChunkAccess.isHostChunkNetworkAccessible(this);
        if (this.chunkAccessAvailable == available) return;

        final Map<IAEStackType<?>, IItemList> beforeByType = captureAvailableItems();
        this.chunkAccessAvailable = available;
        this.isCached = false;
        if (!available) {
            clearCellHandlerCaches();
            this.getProxy()
                .setIdlePowerUsage(0.0);
            postAvailableItemRemoval(beforeByType);
        } else {
            this.updateState();
        }

        try {
            this.getProxy()
                .getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (final GridAccessException ignored) {}
        this.markForUpdate();
    }

    private boolean isContributionAvailable() {
        return !this.contributionRetired && this.chunkAccessAvailable
            && this.getProxy()
                .isActive()
            && SingularityChunkAccess.isHostChunkNetworkAccessible(this);
    }

    // ---- cell inventory accessor (for GUI) ----

    public AppEngInternalInventory getCellInventory() {
        return cells;
    }

    public void addCellDrops(final List<ItemStack> drops) {
        for (final ItemStack stack : this.cells) {
            if (stack != null) {
                drops.add(stack);
            }
        }
    }

    public int getNetworkID() {
        return this.networkID;
    }

    @Override
    public int getGridOwnerPlayerID() {
        return this.gridOwnerPlayerID;
    }

    /**
     * Reassigns this drive to a different network. Called server-side when the
     * player changes the network via the in-device network tab.
     */
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

    // ---- Network sync (state/type to client for GUI rendering) ----

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream_TileSingularityDrive(final ByteBuf data) {
        data.writeInt(this.state);
        data.writeInt(this.type);
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream_TileSingularityDrive(final ByteBuf data) {
        final int oldState = this.state;
        final int oldType = this.type;
        this.state = data.readInt() & (STATE_MASK | STATE_ACTIVE_MASK);
        this.type = data.readInt();
        return oldState != this.state || oldType != this.type;
    }

    // ---- NBT ----

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeExtraNBT(final NBTTagCompound tag) {
        tag.setInteger("priority", priority);
        tag.setInteger("singularityNetworkID", this.networkID);
        tag.setInteger("singularityGridOwner", this.gridOwnerPlayerID);
        tag.setBoolean(SingularityNetworkDefaults.NBT_KEY, this.defaultNetworkApplied);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readExtraNBT(final NBTTagCompound tag) {
        priority = tag.getInteger("priority");
        this.networkID = tag.hasKey("singularityNetworkID") ? tag.getInteger("singularityNetworkID") : 0;
        this.gridOwnerPlayerID = tag.hasKey("singularityGridOwner") ? tag.getInteger("singularityGridOwner") : -1;
        this.defaultNetworkApplied = tag.hasKey(SingularityNetworkDefaults.NBT_KEY)
            ? tag.getBoolean(SingularityNetworkDefaults.NBT_KEY)
            : true;
        this.isCached = false;
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
    public ItemStack getPrimaryGuiIcon() {
        return AEApi.instance()
            .definitions()
            .blocks()
            .drive()
            .maybeStack(1)
            .orNull();
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
        return new DimensionalCoord(this);
    }
}
