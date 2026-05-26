package com.github.singularityme.tile;

import java.util.function.Predicate;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.core.SingularityNetworkManager;
import com.github.singularityme.grid.SingularityGrid;
import com.github.singularityme.proxy.CommonProxy;
import com.google.common.collect.ImmutableSet;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.RedstoneMode;
import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
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
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.helpers.IOreFilterable;
import appeng.helpers.MultiCraftingTracker;
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
import appeng.util.prioitylist.OreFilteredList;

/**
 * Singularity Export Bus — each tick pulls items from the player's global
 * SingularityGrid and pushes them into the adjacent container.
 *
 * <p>
 * Supports CAPACITY (up to 2 cards → 1/5/9 filter slots), SPEED (0-4 cards → 1/8/32/64/96
 * items/tick), FUZZY (ignore NBT/damage), CRAFTING (submit crafting job when item absent),
 * and REDSTONE/SCHEDULING control.
 */
public class TileSingularityExportBus extends AENetworkInvTile implements IGridTickable, IConfigurableObject,
    IConfigManagerHost, IUpgradeableHost, IIAEStackInventory, ICraftingRequester, IOreFilterable {

    private final BaseActionSource mySrc = new MachineSource(this);

    /** Ghost filter inventory — 9 slots, items are not consumed. */
    private final IAEStackInventory filterInv = new IAEStackInventory(this, 9, StorageName.CONFIG);

    /** Upgrade card inventory — 4 slots. */
    private final BlockUpgradeInventory upgradesInv = new BlockUpgradeInventory(CommonProxy.blockExportBus, this, 4);

    /** Config manager for redstone mode, scheduling mode, and fuzzy mode. */
    private final IConfigManager cm = new ConfigManager(this);

    /** Crafting tracker — one slot per filter slot. */
    private final MultiCraftingTracker craftingTracker = new MultiCraftingTracker(this, 9);

    /** Tracks previous redstone state for SIGNAL_PULSE detection. */
    private boolean lastRedstone = false;

    /** Next slot index for ROUNDROBIN scheduling. */
    private int nextSlot = 0;

    private String oreFilterString = "";
    private Predicate<IAEItemStack> filterPredicate = null;

    public TileSingularityExportBus() {
        cm.registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        cm.registerSetting(Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT);
        cm.registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        cm.registerSetting(Settings.CRAFT_ONLY, YesNo.NO);
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

    // ---- IIAEStackInventory ----

    @Override
    public void saveAEStackInv() {
        markDirty();
    }

    // ---- filter access (for GUI container) ----

    public IAEStackInventory getAEInventoryByName(final StorageName name) {
        return name == StorageName.CONFIG ? filterInv : null;
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

    // ---- ICraftingRequester ----

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return craftingTracker.getRequestedJobs();
    }

    @Override
    public IAEItemStack injectCraftedItems(final ICraftingLink link, final IAEItemStack items, final Actionable mode) {
        InventoryAdaptor adaptor = getAdjacentAdaptor();
        if (adaptor != null && getProxy().isActive()) {
            IEnergyGrid energy;
            try {
                energy = getProxy().getEnergy();
            } catch (GridAccessException ignored) {
                return items;
            }
            final double power = (double) items.getStackSize() / items.getAmountPerUnit();
            if (energy.extractAEPower(power, mode, PowerMultiplier.CONFIG) <= power - 0.01) {
                return items;
            }
            if (mode == Actionable.SIMULATE) {
                ItemStack notFit = adaptor.simulateAdd(items.getItemStack());
                if (notFit == null) return null;
                IAEItemStack leftover = items.copy();
                leftover.setStackSize(notFit.stackSize);
                return leftover;
            } else {
                ItemStack excess = adaptor.addItems(items.getItemStack());
                if (excess == null) return null;
                IAEItemStack leftover = items.copy();
                leftover.setStackSize(excess.stackSize);
                return leftover;
            }
        }
        return items;
    }

    @Override
    public void jobStateChange(final ICraftingLink link) {
        craftingTracker.jobStateChange(link);
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
        craftingTracker.writeToNBT(tag);
        tag.setBoolean("lastRedstone", lastRedstone);
        tag.setInteger("nextSlot", nextSlot);
        tag.setString("filter", oreFilterString);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readExtraNBT(final NBTTagCompound tag) {
        filterInv.readFromNBT(tag, "filterInv");
        upgradesInv.readFromNBT(tag, "upgradesInv");
        cm.readFromNBT(tag);
        craftingTracker.readFromNBT(tag);
        lastRedstone = tag.getBoolean("lastRedstone");
        nextSlot = tag.getInteger("nextSlot");
        setFilter(tag.getString("filter"));
    }

    // ---- IGridTickable ----

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(1, 20, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (!getProxy().isActive()) return TickRateModulation.IDLE;

        // Redstone control
        RedstoneMode rm = (RedstoneMode) cm.getSetting(Settings.REDSTONE_CONTROLLED);
        boolean powered = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
        if (rm == RedstoneMode.LOW_SIGNAL && powered) {
            lastRedstone = powered;
            return TickRateModulation.SLOWER;
        }
        if (rm == RedstoneMode.HIGH_SIGNAL && !powered) {
            lastRedstone = powered;
            return TickRateModulation.SLOWER;
        }
        if (rm == RedstoneMode.SIGNAL_PULSE) {
            if (!powered || lastRedstone) {
                lastRedstone = powered;
                return TickRateModulation.SLOWER;
            }
            lastRedstone = true;
        } else {
            lastRedstone = powered;
        }

        if (filterInv.isEmpty()) return TickRateModulation.SLOWER;

        IMEMonitor<IAEItemStack> gridInv = getGridItemInventory();
        if (gridInv == null) return TickRateModulation.IDLE;

        InventoryAdaptor adaptor = getAdjacentAdaptor();
        if (adaptor == null) return TickRateModulation.SLOWER;

        // Upgrade card effects
        int capacityCards = getInstalledUpgrades(Upgrades.CAPACITY);
        int availSlots = Math.min(1 + capacityCards * 4, 9);
        int maxItems = calculateMaxItems();
        boolean fuzzy = getInstalledUpgrades(Upgrades.FUZZY) > 0;
        boolean crafting = getInstalledUpgrades(Upgrades.CRAFTING) > 0;
        boolean craftOnly = crafting && cm.getSetting(Settings.CRAFT_ONLY) == YesNo.YES;
        FuzzyMode fuzzyMode = fuzzy ? (FuzzyMode) cm.getSetting(Settings.FUZZY_MODE) : FuzzyMode.IGNORE_ALL;
        boolean oreFilter = getInstalledUpgrades(Upgrades.ORE_FILTER) > 0 && !oreFilterString.isEmpty();

        SchedulingMode sched = (SchedulingMode) cm.getSetting(Settings.SCHEDULING_MODE);
        if (oreFilter) {
            return exportOreFiltered(gridInv, adaptor, maxItems);
        }
        return exportWithScheduling(
            gridInv,
            adaptor,
            sched,
            availSlots,
            maxItems,
            fuzzy,
            fuzzyMode,
            crafting,
            craftOnly);
    }

    private TickRateModulation exportOreFiltered(final IMEMonitor<IAEItemStack> gridInv, final InventoryAdaptor adaptor,
        final int maxItems) {
        boolean moved = false;
        int itemsMoved = 0;
        Predicate<IAEItemStack> predicate = getOreFilterPredicate();
        for (IAEItemStack stack : gridInv.getStorageList()) {
            if (itemsMoved >= maxItems) break;
            if (stack == null || !predicate.test(stack)) continue;
            int movedNow = exportStack(gridInv, adaptor, stack, maxItems - itemsMoved);
            if (movedNow > 0) {
                moved = true;
                itemsMoved += movedNow;
            }
        }
        return moved ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    private TickRateModulation exportWithScheduling(final IMEMonitor<IAEItemStack> gridInv,
        final InventoryAdaptor adaptor, final SchedulingMode sched, final int availSlots, final int maxItems,
        final boolean fuzzy, final FuzzyMode fuzzyMode, final boolean crafting, final boolean craftOnly) {

        IItemList<IAEItemStack> available = gridInv.getStorageList();

        if (sched == SchedulingMode.DEFAULT) {
            boolean moved = false;
            int itemsMoved = 0;
            for (int i = 0; i < availSlots; i++) {
                if (itemsMoved >= maxItems) break;
                int n = exportSlot(
                    i,
                    gridInv,
                    adaptor,
                    available,
                    maxItems - itemsMoved,
                    fuzzy,
                    fuzzyMode,
                    crafting,
                    craftOnly);
                if (n > 0) {
                    moved = true;
                    itemsMoved += n;
                }
            }
            return moved ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
        } else if (sched == SchedulingMode.ROUNDROBIN) {
            for (int attempt = 0; attempt < availSlots; attempt++) {
                int slot = (nextSlot + attempt) % availSlots;
                int n = exportSlot(slot, gridInv, adaptor, available, maxItems, fuzzy, fuzzyMode, crafting, craftOnly);
                if (n > 0) {
                    nextSlot = (slot + 1) % availSlots;
                    return TickRateModulation.FASTER;
                }
            }
            return TickRateModulation.SLOWER;
        } else {
            // RANDOM
            int slot = worldObj.rand.nextInt(availSlots);
            int n = exportSlot(slot, gridInv, adaptor, available, maxItems, fuzzy, fuzzyMode, crafting, craftOnly);
            return n > 0 ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
        }
    }

    /**
     * Attempts to export items from the given filter slot.
     * Returns the number of items actually moved (0 if nothing moved).
     */
    private int exportSlot(final int i, final IMEMonitor<IAEItemStack> gridInv, final InventoryAdaptor adaptor,
        final IItemList<IAEItemStack> available, final int maxItems, final boolean fuzzy, final FuzzyMode fuzzyMode,
        final boolean crafting, final boolean craftOnly) {

        IAEStack<?> filterStack = filterInv.getAEStackInSlot(i);
        if (!(filterStack instanceof IAEItemStack wanted)) return 0;

        if (craftOnly) {
            submitCraftingRequest(i, wanted);
            return 0;
        }

        IAEItemStack inGrid;
        if (fuzzy) {
            java.util.Collection<IAEItemStack> matches = available.findFuzzy(wanted, fuzzyMode);
            inGrid = matches.isEmpty() ? null
                : matches.iterator()
                    .next();
        } else {
            inGrid = available.findPrecise(wanted);
        }

        if (inGrid == null || inGrid.getStackSize() <= 0) {
            // Try crafting if card installed
            if (crafting) {
                submitCraftingRequest(i, wanted);
            }
            return 0;
        }

        ItemStack probe = inGrid.getItemStack();
        int canMove = (int) Math.min(inGrid.getStackSize(), Math.min(probe.getMaxStackSize(), maxItems));
        probe = probe.copy();
        probe.stackSize = canMove;
        ItemStack notFit = adaptor.simulateAdd(probe);
        int canFit = canMove - (notFit == null ? 0 : notFit.stackSize);
        if (canFit <= 0) return 0;

        IAEItemStack toExtract = inGrid.copy();
        toExtract.setStackSize(canFit);
        return exportStack(gridInv, adaptor, toExtract, canFit);
    }

    private int exportStack(final IMEMonitor<IAEItemStack> gridInv, final InventoryAdaptor adaptor,
        final IAEItemStack stack, final int maxItems) {
        ItemStack probe = stack.getItemStack();
        int canMove = (int) Math.min(stack.getStackSize(), Math.min(probe.getMaxStackSize(), maxItems));
        probe = probe.copy();
        probe.stackSize = canMove;
        ItemStack notFit = adaptor.simulateAdd(probe);
        int canFit = canMove - (notFit == null ? 0 : notFit.stackSize);
        if (canFit <= 0) return 0;

        IAEItemStack toExtract = stack.copy();
        toExtract.setStackSize(canFit);
        IAEItemStack extracted;
        try {
            extracted = Platform.poweredExtraction(getProxy().getEnergy(), gridInv, toExtract, mySrc);
        } catch (GridAccessException ignored) {
            return 0;
        }
        if (extracted == null || extracted.getStackSize() <= 0) return 0;

        ItemStack toAdd = extracted.getItemStack();
        ItemStack excess = adaptor.addItems(toAdd);

        if (excess != null && excess.stackSize > 0) {
            IAEItemStack returnStack = AEApi.instance()
                .storage()
                .createItemStack(excess);
            gridInv.injectItems(returnStack, Actionable.MODULATE, mySrc);
        }
        return (int) extracted.getStackSize();
    }

    private void submitCraftingRequest(final int slot, final IAEItemStack wanted) {
        try {
            IGrid grid = getProxy().getGrid();
            ICraftingGrid cg = getProxy().getCrafting();
            craftingTracker.handleCrafting(slot, wanted.getStackSize(), wanted, worldObj, grid, cg, mySrc);
        } catch (GridAccessException ignored) {}
    }

    // ---- helpers ----

    private int calculateMaxItems() {
        int amount = switch (getInstalledUpgrades(Upgrades.SPEED)) {
            case 1:
                yield 8;
            case 2:
                yield 32;
            case 3:
                yield 64;
            case 4:
                yield 96;
            default:
                yield 1;
        };

        amount += switch (getInstalledUpgrades(Upgrades.SUPERSPEED)) {
            case 1 -> 16;
            case 2 -> 16 * 8;
            case 3 -> 16 * 8 * 8;
            case 4 -> 16 * 8 * 8 * 8;
            default -> 0;
        };

        amount += switch (getInstalledUpgrades(Upgrades.SUPERLUMINALSPEED)) {
            case 1 -> 131_072;
            case 2 -> 131_072 * 8;
            case 3 -> 131_072 * 8 * 8;
            case 4 -> 131_072 * 8 * 8 * 8;
            default -> 0;
        };

        return amount;
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

    public IAEStackInventory getFilterInv() {
        return filterInv;
    }

    @Override
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.SMART;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    private IMEMonitor<IAEItemStack> getGridItemInventory() {
        GridNode node = (GridNode) getProxy().getNode();
        if (node == null) return null;
        SingularityGrid sg = SingularityNetworkManager.INSTANCE.getGridForPlayer(node.getPlayerID());
        if (sg == null) return null;
        IStorageGrid storage = sg.getCache(IStorageGrid.class);
        return storage == null ? null : storage.getItemInventory();
    }

    private InventoryAdaptor getAdjacentAdaptor() {
        int meta = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
        ForgeDirection facing = ForgeDirection.getOrientation(meta);
        TileEntity te = worldObj
            .getTileEntity(xCoord + facing.offsetX, yCoord + facing.offsetY, zCoord + facing.offsetZ);
        if (te == null) return null;
        return InventoryAdaptor.getAdaptor(te, facing.getOpposite());
    }
}
