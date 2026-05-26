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
public class TileSingularityImportBus extends AENetworkInvTile implements IGridTickable, IConfigurableObject,
    IConfigManagerHost, IUpgradeableHost, IIAEStackInventory, IOreFilterable {

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

    public TileSingularityImportBus() {
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
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.SMART;
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
        tag.setBoolean("lastRedstone", lastRedstone);
        tag.setString("filter", oreFilterString);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readExtraNBT(final NBTTagCompound tag) {
        filterInv.readFromNBT(tag, "filterInv");
        upgradesInv.readFromNBT(tag, "upgradesInv");
        cm.readFromNBT(tag);
        lastRedstone = tag.getBoolean("lastRedstone");
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

        IMEMonitor<IAEItemStack> gridInv = getGridItemInventory();
        if (gridInv == null) return TickRateModulation.IDLE;

        InventoryAdaptor adaptor = getAdjacentAdaptor();
        if (adaptor == null) return TickRateModulation.SLOWER;

        // Upgrade card effects
        int capacityCards = getInstalledUpgrades(Upgrades.CAPACITY);
        int availSlots = Math.min(1 + capacityCards * 4, 9);
        int maxItems = calculateMaxItems();
        boolean fuzzy = getInstalledUpgrades(Upgrades.FUZZY) > 0;
        FuzzyMode fuzzyMode = fuzzy ? (FuzzyMode) cm.getSetting(Settings.FUZZY_MODE) : FuzzyMode.IGNORE_ALL;
        boolean oreFilter = getInstalledUpgrades(Upgrades.ORE_FILTER) > 0 && !oreFilterString.isEmpty();
        IEnergyGrid energy;
        try {
            energy = getProxy().getEnergy();
            double availablePower = energy.extractAEPower(maxItems, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            maxItems = Math.min(maxItems, (int) (availablePower + 0.01));
        } catch (GridAccessException ignored) {
            return TickRateModulation.IDLE;
        }
        if (maxItems <= 0) return TickRateModulation.SLOWER;

        boolean hasFilter = !filterInv.isEmpty();
        boolean moved = false;
        int itemsMoved = 0;

        for (ItemSlot slot : adaptor) {
            if (itemsMoved >= maxItems) break;
            ItemStack stack = slot.getItemStack();
            if (stack == null || stack.stackSize <= 0) continue;

            if (oreFilter) {
                IAEItemStack aeStack = AEItemStack.create(stack);
                if (aeStack == null || !getOreFilterPredicate().test(aeStack)) continue;
            } else if (hasFilter && !matchesFilter(stack, fuzzy, availSlots)) {
                continue;
            }

            IAEItemStack toInject = AEApi.instance()
                .storage()
                .createItemStack(stack);
            if (toInject == null) continue;

            IAEItemStack notAccepted = gridInv.injectItems(toInject.copy(), Actionable.SIMULATE, mySrc);
            long canAccept = toInject.getStackSize() - (notAccepted == null ? 0 : notAccepted.getStackSize());
            if (canAccept <= 0) continue;

            int toRemove = (int) Math.min(canAccept, maxItems - itemsMoved);

            ItemStack pulled;
            if (fuzzy) {
                pulled = adaptor.removeSimilarItems(toRemove, stack, fuzzyMode, null);
            } else {
                pulled = adaptor.removeItems(toRemove, stack, null);
            }
            if (pulled == null || pulled.stackSize <= 0) continue;

            IAEItemStack toStore = AEApi.instance()
                .storage()
                .createItemStack(pulled);
            IAEItemStack leftover = Platform.poweredInsert(energy, gridInv, toStore, mySrc);

            if (leftover != null && leftover.getStackSize() > 0) {
                ItemStack returnStack = leftover.getItemStack();
                ItemStack excess = adaptor.addItems(returnStack);
                if (excess != null && excess.stackSize > 0) {
                    worldObj.spawnEntityInWorld(
                        new net.minecraft.entity.item.EntityItem(
                            worldObj,
                            xCoord + 0.5,
                            yCoord + 1.0,
                            zCoord + 0.5,
                            excess));
                }
            }
            itemsMoved += pulled.stackSize;
            moved = true;
        }

        return moved ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
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

    private boolean matchesFilter(final ItemStack stack, final boolean fuzzy, final int availSlots) {
        for (int i = 0; i < availSlots; i++) {
            IAEStack<?> filter = filterInv.getAEStackInSlot(i);
            if (!(filter instanceof IAEItemStack aeFilter)) continue;
            if (fuzzy) {
                if (aeFilter.getItem() == stack.getItem()) return true;
            } else {
                IAEItemStack aeStack = AEItemStack.create(stack);
                if (aeStack != null && aeFilter.isSameType(aeStack)) return true;
            }
        }
        return false;
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
