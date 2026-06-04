package com.github.singularityme.tile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import org.junit.Test;

import appeng.api.config.FuzzyMode;
import appeng.api.config.SchedulingMode;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAETagCompound;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.MEInventoryHandler;
import io.netty.buffer.ByteBuf;

/** 验证奇点输出/存储总线与 AE2 原版细节保持一致。 */
public class TileSingularityExportBusParityTest {

    /** ROUNDROBIN 必须像 AE2 一样持续尝试后续槽位，直到本 tick 预算耗尽。 */
    @Test
    public void roundRobinSchedulingContinuesUntilItemBudgetIsSpent() {
        final List<Integer> slots = new ArrayList<>();

        final TileSingularityExportBus.ScheduledExportResult result = TileSingularityExportBus.runScheduledExports(
            SchedulingMode.ROUNDROBIN,
            2,
            9,
            5,
            new SequenceRandom(),
            (slot, remainingItems) -> {
                slots.add(slot);
                return Math.min(2, remainingItems);
            });

        assertEquals(Arrays.asList(2, 3, 4), slots);
        assertEquals(5, result.itemsMoved);
        assertEquals(5, result.nextSlot);
    }

    /** RANDOM 必须像 AE2 一样每次尝试重新抽槽，而不是每 tick 只抽一次。 */
    @Test
    public void randomSchedulingDrawsANewSlotForEachAttempt() {
        final SequenceRandom random = new SequenceRandom(7, 1, 4);
        final List<Integer> slots = new ArrayList<>();

        final TileSingularityExportBus.ScheduledExportResult result = TileSingularityExportBus.runScheduledExports(
            SchedulingMode.RANDOM,
            0,
            9,
            3,
            random,
            (slot, remainingItems) -> {
                slots.add(slot);
                return 1;
            });

        assertEquals(Arrays.asList(7, 1, 4), slots);
        assertEquals(3, random.calls());
        assertEquals(3, result.itemsMoved);
        assertEquals(0, result.nextSlot);
    }

    /** Fuzzy 导出候选必须保留全部 fuzzy 匹配项，后续逻辑才能逐个尝试。 */
    @Test
    public void fuzzyExportCandidatesIncludeEveryMatch() {
        final IAEItemStack wanted = new FakeItemStack("wanted");
        final IAEItemStack first = new FakeItemStack("first");
        final IAEItemStack second = new FakeItemStack("second");
        final IAEItemStack third = new FakeItemStack("third");
        final List<IAEItemStack> matches = Arrays.asList(first, second, third);

        final Iterable<IAEItemStack> candidates = TileSingularityExportBus
            .findExportCandidates(itemListProxy(null, matches), wanted, true, FuzzyMode.IGNORE_ALL);

        final Iterator<IAEItemStack> iterator = candidates.iterator();
        assertSame(first, iterator.next());
        assertSame(second, iterator.next());
        assertSame(third, iterator.next());
    }

    /** 存储总线 setFilter 必须像 AE2 一样同步更新 previous，拆装矿辞卡后才能恢复当前文本。 */
    @Test
    public void storageBusSetFilterStoresCurrentTextAsPreviousText() throws Exception {
        final TileSingularityStorageBus storageBus = newRetiredStorageBus();

        storageBus.setFilter("oreCopper");

        assertEquals("oreCopper", stringField(storageBus, "oreFilterString"));
        assertEquals("oreCopper", stringField(storageBus, "previousOreFilterString"));
    }

    @SuppressWarnings("unchecked")
    private static IItemList<IAEItemStack> itemListProxy(final IAEItemStack precise,
        final Collection<IAEItemStack> fuzzy) {
        return (IItemList<IAEItemStack>) Proxy.newProxyInstance(
            TileSingularityExportBusParityTest.class.getClassLoader(),
            new Class<?>[] { IItemList.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "findPrecise" -> precise;
                case "findFuzzy" -> fuzzy;
                case "iterator" -> fuzzy.iterator();
                case "size" -> fuzzy.size();
                case "isEmpty" -> fuzzy.isEmpty();
                case "isSorted", "hasWriteAccess" -> false;
                default -> null;
            });
    }

    private static TileSingularityStorageBus newRetiredStorageBus() throws Exception {
        final TileSingularityStorageBus storageBus = allocate(TileSingularityStorageBus.class);
        setField(storageBus, "handlers", new HashMap<IAEStackType<?>, MEInventoryHandler<?>>());
        setField(storageBus, "storageBusMonitors", new IdentityHashMap<>());
        setField(storageBus, "monitorHandlers", new IdentityHashMap<IMEMonitor<?>, MEInventoryHandler<?>>());
        setField(storageBus, "monitorTypes", new IdentityHashMap<>());
        setField(storageBus, "contributionRetired", true);
        return storageBus;
    }

    private static String stringField(final Object target, final String fieldName) throws Exception {
        final Field field = target.getClass()
            .getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(target);
    }

    private static void setField(final Object target, final String fieldName, final Object value) throws Exception {
        final Field field = target.getClass()
            .getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T allocate(final Class<T> cls) throws Exception {
        // 仅用于跳过 AE2 Tile 构造器，测试纯字段状态；不要用于依赖 world/proxy/tick 的行为。
        final Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        final sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        return cls.cast(unsafe.allocateInstance(cls));
    }

    private static final class FakeItemStack implements IAEItemStack {

        private final String name;
        private long stackSize = 1;

        private FakeItemStack(final String name) {
            this.name = name;
        }

        @Override
        public ItemStack getItemStack() {
            return null;
        }

        @Override
        public void add(final IAEItemStack option) {}

        @Override
        public long getStackSize() {
            return stackSize;
        }

        @Override
        public IAEItemStack setStackSize(final long stackSize) {
            this.stackSize = stackSize;
            return this;
        }

        @Override
        public long getCountRequestable() {
            return 0;
        }

        @Override
        public IAEItemStack setCountRequestable(final long countRequestable) {
            return this;
        }

        @Override
        public boolean isCraftable() {
            return false;
        }

        @Override
        public IAEItemStack setCraftable(final boolean craftable) {
            return this;
        }

        @Override
        public IAEItemStack reset() {
            return this;
        }

        @Override
        public boolean isMeaningful() {
            return true;
        }

        @Override
        public void incStackSize(final long amount) {
            stackSize += amount;
        }

        @Override
        public void decStackSize(final long amount) {
            stackSize -= amount;
        }

        @Override
        public void incCountRequestable(final long amount) {}

        @Override
        public void decCountRequestable(final long amount) {}

        @Override
        public void writeToNBT(final NBTTagCompound data) {}

        @Override
        public void writeToPacket(final ByteBuf data) throws IOException {}

        @Override
        public boolean fuzzyComparison(final Object stack, final FuzzyMode mode) {
            return false;
        }

        @Override
        public IAEItemStack copy() {
            final FakeItemStack copy = new FakeItemStack(name);
            copy.stackSize = stackSize;
            return copy;
        }

        @Override
        public IAEItemStack empty() {
            return new FakeItemStack(name);
        }

        @Override
        public IAETagCompound getTagCompound() {
            return null;
        }

        @Override
        public boolean isItem() {
            return true;
        }

        @Override
        public boolean isFluid() {
            return false;
        }

        @Override
        public StorageChannel getChannel() {
            return StorageChannel.ITEMS;
        }

        @Override
        public String getLocalizedName() {
            return name;
        }

        @Override
        public boolean isSameType(final IAEItemStack otherStack) {
            return this == otherStack;
        }

        @Override
        public boolean isSameType(final Object otherStack) {
            return this == otherStack;
        }

        @Override
        public String getUnlocalizedName() {
            return name;
        }

        @Override
        public String getDisplayName() {
            return name;
        }

        @Override
        public String getModId() {
            return "singularityme-test";
        }

        @Override
        public void setTagCompound(final NBTTagCompound tagCompound) {}

        @Override
        public boolean hasTagCompound() {
            return false;
        }

        @Override
        public ItemStack getItemStackForNEI() {
            return null;
        }

        @Override
        public void drawInGui(final Minecraft minecraft, final int x, final int y) {}

        @Override
        public void drawOverlayInGui(final Minecraft minecraft, final int x, final int y, final boolean displayAmount,
            final boolean displayCraftable, final boolean displayRequestable, final boolean displayRequestableCrafts) {}

        @Override
        public void drawOnBlockFace(final World world) {}

        @Override
        public int getAmountPerUnit() {
            return 1;
        }

        @Override
        public IAEStackType<IAEItemStack> getStackType() {
            return null;
        }

        @Override
        public Item getItem() {
            return null;
        }

        @Override
        public int getItemDamage() {
            return 0;
        }

        @Override
        public boolean sameOre(final IAEItemStack otherStack) {
            return false;
        }

        @Override
        public boolean isSameType(final ItemStack otherStack) {
            return false;
        }

        @Override
        public IChatComponent getChatComponent() {
            return null;
        }
    }

    private static final class SequenceRandom extends Random {

        private final int[] values;
        private int index = 0;

        private SequenceRandom(final int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(final int bound) {
            final int value = values[index++];
            if (value >= bound) {
                throw new AssertionError("random value " + value + " outside bound " + bound);
            }
            return value;
        }

        private int calls() {
            return index;
        }
    }
}
