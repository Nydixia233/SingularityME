package com.github.singularityme.gui;

import static appeng.util.Platform.isServer;

import net.minecraft.entity.player.InventoryPlayer;

import com.github.singularityme.tile.TileSingularityStorageBus;

import appeng.api.config.AccessRestriction;
import appeng.api.config.ActionItems;
import appeng.api.config.ExtractionMode;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.YesNo;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * Container for the Singularity Storage Bus GUI.
 *
 * <p>
 * Provides up to 63 virtual ghost filter slots (CAPACITY-gated), access restriction, extraction
 * mode, storage filter, fuzzy mode, and priority display.
 *
 * <p>
 * Implements {@link IConfigurableObject} so that {@code PacketValueConfig("ACTIONS", "CLOSE"/"WRENCH")}
 * routes to this container's config manager, triggering clear/partition via {@link #updateSetting}.
 */
public class ContainerSingularityStorageBus extends ContainerUpgradeable
    implements IVirtualSlotHolder, IConfigurableObject, IConfigManagerHost {

    @GuiSync(3)
    public AccessRestriction accessMode = AccessRestriction.READ_WRITE;

    @GuiSync(4)
    public StorageFilter storageFilter = StorageFilter.EXTRACTABLE_ONLY;

    @GuiSync(7)
    public YesNo stickyMode = YesNo.NO;

    @GuiSync(9)
    public ExtractionMode extractionMode = ExtractionMode.LOOSE;

    @GuiSync(10)
    public long priorityValue = 0;

    public final IAEStack<?>[] virtualSlotsClient = new IAEStack<?>[63];
    final TileSingularityStorageBus storageBus;

    /** Own config manager for routing PacketValueConfig ACTIONS to clear/partition. */
    private final IConfigManager containerCm = new ConfigManager(this);

    public ContainerSingularityStorageBus(final InventoryPlayer ip, final TileSingularityStorageBus te) {
        super(ip, te);
        this.storageBus = te;
        containerCm.registerSetting(Settings.ACTIONS, ActionItems.CLOSE);
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
            }
        }
    }

    // ---- partition / clear ----

    public void clear() {
        storageBus.clearFilter();
        this.detectAndSendChanges();
    }

    public void partition() {
        // Delegate to tile — tile has access to adjacent inventory
        storageBus.partitionFilterFromAdjacentInventory();
        this.detectAndSendChanges();
    }

    // ---- ContainerUpgradeable overrides ----

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
        if (storageBus.getInstalledUpgrades(appeng.api.config.Upgrades.FUZZY) > 0) {
            this.fzMode = (appeng.api.config.FuzzyMode) cm.getSetting(Settings.FUZZY_MODE);
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (isServer()) {
            this.loadSettingsFromHost(storageBus.getConfigManager());
            this.priorityValue = storageBus.getPriorityValue();
            this.updateVirtualSlots(
                StorageName.CONFIG,
                storageBus.getAEInventoryByName(StorageName.CONFIG),
                virtualSlotsClient);
        }
        this.standardDetectAndSendChanges();
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
}
