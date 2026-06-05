package com.github.singularityme.tile;

import net.minecraft.inventory.ISidedInventory;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.helpers.IInterfaceHost;
import appeng.util.InventoryAdaptor;
import appeng.util.inv.AdaptorDualityInterface;
import appeng.util.inv.WrapperMCISidedInventory;

/** 奇点总线目标适配器，补齐 AE2 原版 InventoryAdaptor 未覆盖的奇点 Tile 类型。 */
final class SingularityBusTargetAdapters {

    private SingularityBusTargetAdapters() {}

    /**
     * 为相邻目标创建 AE2 库存适配器。
     *
     * <p>AE2 原版只对 {@code TileInterface} 和线缆上的接口 part 特判
     * {@link AdaptorDualityInterface}。奇点接口是独立 Tile，也实现
     * {@link IInterfaceHost}，因此需要在这里显式走同一条注入路径。</p>
     */
    static InventoryAdaptor getAdaptor(final Object target, final ForgeDirection side, final int flags) {
        if ((flags & InventoryAdaptor.ALLOW_ITEMS) != 0
            && target instanceof IInterfaceHost host
            && target instanceof ISidedInventory inventory) {
            return new AdaptorDualityInterface(new WrapperMCISidedInventory(inventory, side), host);
        }
        return InventoryAdaptor.getAdaptor(target, side, flags);
    }
}
