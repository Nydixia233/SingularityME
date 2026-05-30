package com.github.singularityme.tile;

import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGridNode;
import appeng.me.helpers.IGridProxyable;

final class SingularityPhysicalIsolation {

    private SingularityPhysicalIsolation() {}

    static IGridNode getGridNode(final IGridProxyable host, final ForgeDirection dir) {
        return dir == ForgeDirection.UNKNOWN ? host.getProxy()
            .getNode() : null;
    }
}
