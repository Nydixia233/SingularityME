package com.github.singularityme.capability;

import java.util.Collection;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.singularityme.tile.TileSingularityInterface;
import com.gtnewhorizon.gtnhlib.capability.item.ItemIO;
import com.gtnewhorizon.gtnhlib.item.AbstractInventoryIterator;
import com.gtnewhorizon.gtnhlib.item.ImmutableItemStack;
import com.gtnewhorizon.gtnhlib.item.InventoryIterator;
import com.gtnewhorizon.gtnhlib.item.ItemStack2IntFunction;
import com.gtnewhorizon.gtnhlib.item.ItemStackPredicate;
import com.gtnewhorizon.gtnhlib.util.ItemUtil;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.DualityInterface;
import appeng.me.GridAccessException;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.ImmutableAEItemStackWrapper;

public class SingularityMEItemIO implements ItemIO {

    private static final int[] SLOTS = IntStream.range(0, 9)
        .toArray();

    private final DualityInterface duality;
    private final IEnergyGrid energyGrid;
    private final IMEMonitor<IAEItemStack> storage;

    private int[] allowedSourceSlots;
    private int[] allowedSinkSlots;

    public SingularityMEItemIO(final TileSingularityInterface iface) throws GridAccessException {
        this.energyGrid = iface.getProxy()
            .getGrid()
            .getCache(IEnergyGrid.class);
        this.storage = iface.getProxy()
            .getGrid()
            .<IStorageGrid>getCache(IStorageGrid.class)
            .getItemInventory();
        this.duality = iface.getInterfaceDuality();
    }

    @Override
    public @NotNull InventoryIterator sourceIterator() {
        return getInventoryIterator(allowedSourceSlots);
    }

    @Override
    public @NotNull InventoryIterator sinkIterator() {
        return getInventoryIterator(allowedSinkSlots);
    }

    @Override
    public void setAllowedSourceSlots(final int[] allowedSourceSlots) {
        this.allowedSourceSlots = allowedSourceSlots;
    }

    @Override
    public void setAllowedSinkSlots(final int @Nullable [] slots) {
        this.allowedSinkSlots = slots;
    }

    @Override
    public @Nullable ItemStack pull(final @Nullable ItemStackPredicate filter,
        final @Nullable ItemStack2IntFunction amount) {
        final InventoryIterator iter = sourceIterator();

        while (iter.hasNext()) {
            final ImmutableItemStack stack = iter.next();

            if (stack == null || stack.isEmpty()) continue;

            if (filter == null || filter.test(stack)) {
                final int toExtract = amount == null ? stack.getStackSize() : amount.apply(stack);

                return iter.extract(toExtract, false);
            }
        }

        return null;
    }

    @Override
    public int store(final ImmutableItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;

        if (!duality.getProxy()
            .isActive()) {
            return stack.getStackSize();
        }

        final IAEItemStack rejected = storage
            .injectItems(AEItemStack.create(stack.toStack()), Actionable.MODULATE, duality.getActionSource());

        return rejected == null ? 0 : Platform.longToInt(rejected.getStackSize());
    }

    @Override
    public OptionalInt getStoredItemsInSink(final @Nullable ItemStackPredicate filter) {
        if (!duality.getProxy()
            .isActive()) {
            return OptionalInt.empty();
        }

        if (filter == null) {
            long sum = 0;

            for (final IAEItemStack stack : storage.getStorageList()) {
                sum += stack.getStackSize();
            }

            return sum == 0 ? ZERO : OptionalInt.of(Platform.longToInt(sum));
        }

        final Collection<ItemStack> stacks = filter.getStacks();
        long sum = 0;
        final ImmutableAEItemStackWrapper wrapper = new ImmutableAEItemStackWrapper();

        if (stacks != null) {
            for (final ItemStack stack : stacks) {
                final IAEItemStack available = storage
                    .getAvailableItem(AEItemStack.create(stack), IterationCounter.fetchNewId());

                if (filter.test(wrapper.set(available))) {
                    sum += available.getStackSize();
                }
            }
        } else {
            for (final IAEItemStack stack : storage.getStorageList()) {
                if (filter.test(wrapper.set(stack))) {
                    sum += stack.getStackSize();
                }
            }
        }

        return sum == 0 ? ZERO : OptionalInt.of(Platform.longToInt(sum));
    }

    private @NotNull InventoryIterator getInventoryIterator(final int[] allowedSlots) {
        if (!duality.getProxy()
            .isActive()) {
            return InventoryIterator.EMPTY;
        }

        return new MEInventoryIterator(allowedSlots);
    }

    private class MEInventoryIterator extends AbstractInventoryIterator {

        private MEInventoryIterator(final int[] allowedSlots) {
            super(SLOTS, allowedSlots);
        }

        @Override
        protected ItemStack getStackInSlot(final int slot) {
            final IAEItemStack config = duality.getConfig()
                .getAEStackInSlot(slot);
            final ItemStack stored = duality.getStorage()
                .getStackInSlot(slot);

            if (config == null) return stored;

            final IAEItemStack blindCheck = config.copy()
                .setStackSize(Integer.MAX_VALUE);
            final IAEItemStack inMESystem = storage
                .extractItems(blindCheck, Actionable.SIMULATE, duality.getActionSource());

            ItemStack output = null;
            long totalAmount = 0;
            final boolean isStorageEmpty = ItemUtil.isStackEmpty(stored);

            if (!isStorageEmpty) {
                output = stored.splitStack(0);
                totalAmount = stored.stackSize;
            }

            if (inMESystem != null) {
                final ItemStack inSystem = inMESystem.getItemStack();
                if (isStorageEmpty) {
                    output = inSystem;
                    totalAmount = inMESystem.getStackSize();
                } else if (ItemUtil.areStacksEqual(output, inSystem)) {
                    totalAmount += inMESystem.getStackSize();
                }
            }

            if (output != null) {
                output.stackSize = Platform.longToInt(totalAmount);
            }

            return output;
        }

        @Override
        public ItemStack extract(int amount, final boolean force) {
            final int slot = getCurrentSlot();
            final IAEItemStack config = duality.getConfig()
                .getAEStackInSlot(slot);
            final ItemStack stored = duality.getStorage()
                .getStackInSlot(slot);

            if (config == null) return stored;

            if (!ItemUtil.areStacksEqual(stored, config.getItemStack())) {
                return stored;
            }

            final ItemStack result = config.getItemStack();
            result.stackSize = 0;

            final IAEItemStack extracted = Platform.poweredExtraction(
                energyGrid,
                storage,
                config.empty()
                    .setStackSize(amount),
                duality.getActionSource());

            if (extracted != null) {
                result.stackSize += Platform.longToInt(extracted.getStackSize());
                amount -= Platform.longToInt(extracted.getStackSize());
            }

            if (amount > 0 && !ItemUtil.isStackEmpty(stored)) {
                final ItemStack fromSlot = duality.getStorage()
                    .decrStackSize(getCurrentSlot(), Math.min(amount, stored.stackSize));

                result.stackSize += fromSlot.stackSize;
            }

            return result;
        }

        @Override
        public int insert(final ImmutableItemStack stack, final boolean force) {
            final IAEItemStack rejected = storage
                .injectItems(AEItemStack.create(stack.toStack()), Actionable.MODULATE, duality.getActionSource());

            return rejected == null ? 0 : Platform.longToInt(rejected.getStackSize());
        }
    }
}
