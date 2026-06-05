package com.github.singularityme.tile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

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
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;

import appeng.api.config.FuzzyMode;
import appeng.api.config.SchedulingMode;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAETagCompound;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.core.settings.TickRates;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.InventoryAdaptor;
import appeng.util.inv.AdaptorDualityInterface;
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

    /** 输出总线 tick 频率必须使用 AE2 原版 ExportBus 配置，不能用 1 tick 硬编码。 */
    @Test
    public void exportBusTickRequestUsesAe2TickRates() {
        final int oldMin = TickRates.ExportBus.getMin();
        final int oldMax = TickRates.ExportBus.getMax();
        try {
            TickRates.ExportBus.setMin(11);
            TickRates.ExportBus.setMax(47);
            final TickingRequest request = new TileSingularityExportBus().getTickingRequest(null);

            assertEquals(11, request.minTickRate);
            assertEquals(47, request.maxTickRate);
        } finally {
            TickRates.ExportBus.setMin(oldMin);
            TickRates.ExportBus.setMax(oldMax);
        }
    }

    /** 输入总线 tick 频率必须使用 AE2 原版 ImportBus 配置，避免比原版更快抽取。 */
    @Test
    public void importBusTickRequestUsesAe2TickRates() {
        final int oldMin = TickRates.ImportBus.getMin();
        final int oldMax = TickRates.ImportBus.getMax();
        try {
            TickRates.ImportBus.setMin(13);
            TickRates.ImportBus.setMax(53);
            final TickingRequest request = new TileSingularityImportBus().getTickingRequest(null);

            assertEquals(13, request.minTickRate);
            assertEquals(53, request.maxTickRate);
        } finally {
            TickRates.ImportBus.setMin(oldMin);
            TickRates.ImportBus.setMax(oldMax);
        }
    }

    /** 存储总线 tick 频率也必须跟随 AE2 StorageBus 配置，避免配置包下再次漂移。 */
    @Test
    public void storageBusTickRequestUsesAe2TickRates() throws Exception {
        final int oldMin = TickRates.StorageBus.getMin();
        final int oldMax = TickRates.StorageBus.getMax();
        try {
            TickRates.StorageBus.setMin(17);
            TickRates.StorageBus.setMax(61);
            final TickingRequest request = newRetiredStorageBus().getTickingRequest(null);

            assertEquals(17, request.minTickRate);
            assertEquals(61, request.maxTickRate);
        } finally {
            TickRates.StorageBus.setMin(oldMin);
            TickRates.StorageBus.setMax(oldMax);
        }
    }

    /** 直连奇点接口时，总线必须像 AE2 原版接口一样走 DualityInterface adaptor。 */
    @Test
    public void interfaceHostTargetUsesDualityAdaptorForItems() {
        final InventoryAdaptor adaptor = SingularityBusTargetAdapters.getAdaptor(
            new FakeInterfaceInventory(),
            ForgeDirection.NORTH,
            InventoryAdaptor.ALLOW_ITEMS | InventoryAdaptor.FOR_INSERTS);

        assertNotNull(adaptor);
        assertTrue(adaptor instanceof AdaptorDualityInterface);
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

    private static final class FakeInterfaceInventory extends TileEntity implements IInterfaceHost, ISidedInventory {

        @Override
        public DualityInterface getInterfaceDuality() {
            return null;
        }

        @Override
        public java.util.EnumSet<ForgeDirection> getTargets() {
            return java.util.EnumSet.of(ForgeDirection.NORTH);
        }

        @Override
        public TileEntity getTileEntity() {
            return this;
        }

        @Override
        public void saveChanges() {}

        @Override
        public IGridNode getActionableNode() {
            return null;
        }

        @Override
        public IGridNode getGridNode(final ForgeDirection dir) {
            return null;
        }

        @Override
        public AECableType getCableConnectionType(final ForgeDirection dir) {
            return AECableType.NONE;
        }

        @Override
        public void securityBreak() {}

        @Override
        public DimensionalCoord getLocation() {
            return new DimensionalCoord(0, 0, 0, 0);
        }

        @Override
        public int rows() {
            return 1;
        }

        @Override
        public int rowSize() {
            return 1;
        }

        @Override
        public IInventory getPatterns() {
            return this;
        }

        @Override
        public String getName() {
            return "fake";
        }

        @Override
        public boolean shouldDisplay() {
            return true;
        }

        @Override
        public appeng.api.util.IConfigManager getConfigManager() {
            return null;
        }

        @Override
        public int getInstalledUpgrades(final Upgrades u) {
            return 0;
        }

        @Override
        public TileEntity getTile() {
            return this;
        }

        @Override
        public net.minecraft.inventory.IInventory getInventoryByName(final String name) {
            return null;
        }

        @Override
        public void provideCrafting(final ICraftingProviderHelper craftingTracker) {}

        @Override
        public boolean pushPattern(final ICraftingPatternDetails patternDetails,
            final net.minecraft.inventory.InventoryCrafting table) {
            return false;
        }

        @Override
        public boolean isBusy() {
            return false;
        }

        @Override
        public ImmutableSet<ICraftingLink> getRequestedJobs() {
            return ImmutableSet.of();
        }

        @Override
        public IAEStack<?> injectCraftedItems(final ICraftingLink link, final IAEStack<?> items, final Actionable mode) {
            return items;
        }

        @Override
        public void jobStateChange(final ICraftingLink link) {}

        @Override
        public int[] getAccessibleSlotsFromSide(final int side) {
            return new int[] { 0 };
        }

        @Override
        public boolean canInsertItem(final int slot, final ItemStack stack, final int side) {
            return true;
        }

        @Override
        public boolean canExtractItem(final int slot, final ItemStack stack, final int side) {
            return true;
        }

        @Override
        public int getSizeInventory() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(final int slot) {
            return null;
        }

        @Override
        public ItemStack decrStackSize(final int slot, final int amount) {
            return null;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(final int slot) {
            return null;
        }

        @Override
        public void setInventorySlotContents(final int slot, final ItemStack stack) {}

        @Override
        public String getInventoryName() {
            return "fake";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return false;
        }

        @Override
        public int getInventoryStackLimit() {
            return 64;
        }

        @Override
        public boolean isUseableByPlayer(final net.minecraft.entity.player.EntityPlayer player) {
            return true;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(final int slot, final ItemStack stack) {
            return true;
        }
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
