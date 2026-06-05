package com.github.singularityme.gui;

import static appeng.util.Platform.isServer;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

import org.apache.commons.lang3.tuple.Pair;

import com.github.singularityme.block.BlockSingularityStorageBus;
import com.github.singularityme.tile.TileSingularityStorageBus;

import appeng.api.config.AccessRestriction;
import appeng.api.config.ActionItems;
import appeng.api.config.ExtractionMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.PrimaryGui;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.slot.OptionalSlotFake;
import appeng.me.storage.MEInventoryHandler;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.IterationCounter;
import appeng.util.prioitylist.PrecisePriorityList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * Container for the Singularity Storage Bus GUI.
 *
 * <p>
 * Provides up to 63 virtual ghost filter slots (CAPACITY-gated), access restriction, extraction
 * mode, storage filter, fuzzy mode, and priority display.
 *
 * <p>
 * Implements {@link IConfigurableObject} so that {@code PacketValueConfig("ACTIONS", ...)} routes
 * to this container's config manager, triggering clear/partition through the local packet path.
 */
public class ContainerSingularityStorageBus extends ContainerUpgradeable
    implements IVirtualSlotHolder, IConfigurableObject, IConfigManagerHost {

    @GuiSync(3)
    public AccessRestriction accessMode = AccessRestriction.READ_WRITE;

    @GuiSync(4)
    public StorageFilter storageFilter = StorageFilter.EXTRACTABLE_ONLY;

    @GuiSync(7)
    public YesNo stickyMode = YesNo.NO;

    @GuiSync(8)
    public ActionItems partitionMode = ActionItems.WRENCH;

    @GuiSync(9)
    public ExtractionMode extractionMode = ExtractionMode.LOOSE;

    @GuiSync(10)
    public long priorityValue = 0;

    public final IAEStack<?>[] virtualSlotsClient = new IAEStack<?>[63];
    final TileSingularityStorageBus storageBus;
    private static final ConcurrentHashMap<EntityPlayer, Pair<IAEStackType<?>, IteratorState>> PARTITION_ITERATORS = new ConcurrentHashMap<>();

    /** Own config manager for routing PacketValueConfig ACTIONS to clear/partition. */
    private final IConfigManager containerCm = new ConfigManager(this);

    public ContainerSingularityStorageBus(final InventoryPlayer ip, final TileSingularityStorageBus te) {
        super(ip, te);
        this.storageBus = te;
        containerCm.registerSetting(Settings.ACTIONS, ActionItems.CLOSE);
        final Pair<IAEStackType<?>, IteratorState> pair = PARTITION_ITERATORS.get(ip.player);
        this.partitionMode = pair != null && pair.getKey() == this.storageBus.getStackType()
            ? ActionItems.NEXT_PARTITION
            : ActionItems.WRENCH;
    }

    @Override
    public PrimaryGui createPrimaryGui() {
        return SingularityPrimaryGui.create(BlockSingularityStorageBus.GUI_ID, this);
    }

    // ---- IConfigurableObject ----

    @Override
    public IConfigManager getConfigManager() {
        return containerCm;
    }

    // ---- IConfigManagerHost ----

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (settingName == Settings.ACTIONS) {
            if (newValue == ActionItems.CLOSE) {
                clear();
            } else if (newValue == ActionItems.WRENCH) {
                partition();
            } else if (newValue == ActionItems.NEXT_PARTITION) {
                clearPartitionIterator(this.getInventoryPlayer().player);
                this.detectAndSendChanges();
            }
        }
    }

    // ---- partition / clear ----

    public void clear() {
        storageBus.clearFilter();
        this.detectAndSendChanges();
    }

    public void partition() {
        if (storageBus.getInstalledUpgrades(Upgrades.ORE_FILTER) > 0) {
            clearPartitionIterator(this.getInventoryPlayer().player);
            this.detectAndSendChanges();
            return;
        }

        // Delegate to tile — tile has access to adjacent inventory
        final EntityPlayer player = this.getInventoryPlayer().player;
        final IAEStackInventory inv = storageBus.getAEInventoryByName(StorageName.CONFIG);
        final MEInventoryHandler cellInv = storageBus.getInternalHandler();

        if (cellInv == null) {
            clearPartitionIterator(player);
            return;
        }

        final Pair<IAEStackType<?>, IteratorState> pair = PARTITION_ITERATORS.get(player);
        final IteratorState it;
        if (pair == null || pair.getKey() != this.storageBus.getStackType()) {
            cellInv.setPartitionList(new PrecisePriorityList<>(this.storageBus.getItemList()));
            final IItemList list = cellInv
                .getAvailableItems(this.storageBus.getItemList(), IterationCounter.fetchNewId());
            it = new IteratorState(list.iterator());
            PARTITION_ITERATORS.put(player, Pair.of(this.storageBus.getStackType(), it));
            this.partitionMode = ActionItems.NEXT_PARTITION;
        } else {
            it = pair.getValue();
        }

        boolean skip = false;
        final int maxSlots = storageBus.getAvailableFilterSlots();
        for (int x = 0; x < inv.getSizeInventory(); x++) {
            if (skip || x >= maxSlots) {
                inv.putAEStackInSlot(x, null);
                continue;
            }

            final IAEStack<?> stack = it.next();
            if (stack != null) {
                final IAEStack<?> copy = stack.copy();
                copy.setStackSize(1);
                inv.putAEStackInSlot(x, copy);
            } else {
                clearPartitionIterator(player);
                skip = true;
                inv.putAEStackInSlot(x, null);
            }
        }

        if (!it.hasNext) {
            clearPartitionIterator(player);
        }
        storageBus.onFilterChanged();
        this.detectAndSendChanges();
    }

    private void clearPartitionIterator(final EntityPlayer player) {
        PARTITION_ITERATORS.remove(player);
        this.partitionMode = ActionItems.WRENCH;
    }

    // ---- ContainerUpgradeable overrides ----

    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);
        clearPartitionIterator(player);
    }

    @Override
    public int availableUpgrades() {
        return 5;
    }

    @Override
    protected int getHeight() {
        return 251;
    }

    @Override
    protected void loadSettingsFromHost(final IConfigManager cm) {
        this.accessMode = (AccessRestriction) cm.getSetting(Settings.ACCESS);
        this.storageFilter = (StorageFilter) cm.getSetting(Settings.STORAGE_FILTER);
        this.stickyMode = (YesNo) cm.getSetting(Settings.STICKY_MODE);
        this.extractionMode = (ExtractionMode) cm.getSetting(Settings.EXTRACTION_MODE);
        this.fzMode = (appeng.api.config.FuzzyMode) cm.getSetting(Settings.FUZZY_MODE);
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (isServer()) {
            this.loadSettingsFromHost(storageBus.getConfigManager());
            this.priorityValue = storageBus.getPriorityValue();
            this.updateVirtualSlots(
                StorageName.CONFIG,
                storageBus.getAEInventoryByName(StorageName.CONFIG),
                virtualSlotsClient);
        }
        this.clearDisabledFakeSlots();
        this.standardDetectAndSendChanges();
    }

    private void clearDisabledFakeSlots() {
        for (final Object slot : this.inventorySlots) {
            if (slot instanceof OptionalSlotFake fakeSlot && !fakeSlot.isEnabled()
                && fakeSlot.getDisplayStack() != null) {
                fakeSlot.clearStack();
            }
        }
    }

    // ---- IVirtualSlotHolder ----

    @Override
    public void receiveSlotStacks(final StorageName invName, final Int2ObjectMap<IAEStack<?>> slotStacks) {
        final IAEStackInventory config = storageBus.getAEInventoryByName(StorageName.CONFIG);
        for (var entry : slotStacks.int2ObjectEntrySet()) {
            config.putAEStackInSlot(entry.getIntKey(), entry.getValue());
        }
        storageBus.onFilterChanged();
        if (isServer()) {
            this.updateVirtualSlots(StorageName.CONFIG, config, virtualSlotsClient);
        }
    }

    public int getPriorityValue() {
        return (int) priorityValue;
    }

    private static class IteratorState {

        private final Iterator<IAEStack<?>> it;
        private boolean hasNext;

        private IteratorState(final Iterator<IAEStack<?>> it) {
            this.it = it;
            this.hasNext = it.hasNext();
        }

        private IAEStack<?> next() {
            if (this.hasNext) {
                final IAEStack<?> stack = this.it.next();
                this.hasNext = this.it.hasNext();
                return stack;
            }
            return null;
        }
    }
}
