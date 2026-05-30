package com.github.singularityme.gui;

import net.minecraft.tileentity.TileEntity;

import com.github.singularityme.network.SingularityChannel;
import com.github.singularityme.network.packet.PacketOpenNetworkTab;

final class NetworkTabClientActions {

    private NetworkTabClientActions() {}

    static void open(final TileEntity te) {
        if (te == null || te.getWorldObj() == null || te.getWorldObj().provider == null) return;

        SingularityChannel.CHANNEL.sendToServer(
            new PacketOpenNetworkTab(te.xCoord, te.yCoord, te.zCoord, te.getWorldObj().provider.dimensionId));
    }
}
