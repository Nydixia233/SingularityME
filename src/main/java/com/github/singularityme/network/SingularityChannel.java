package com.github.singularityme.network;

import com.github.singularityme.network.packet.PacketCreateNetwork;
import com.github.singularityme.network.packet.PacketDeleteNetwork;
import com.github.singularityme.network.packet.PacketGrantPermissionByName;
import com.github.singularityme.network.packet.PacketNetworkActionResult;
import com.github.singularityme.network.packet.PacketNetworkStatus;
import com.github.singularityme.network.packet.PacketNetworkTabData;
import com.github.singularityme.network.packet.PacketOpenNetworkTab;
import com.github.singularityme.network.packet.PacketRenameNetwork;
import com.github.singularityme.network.packet.PacketRequestNetworkStatus;
import com.github.singularityme.network.packet.PacketRequestNetworkTabData;
import com.github.singularityme.network.packet.PacketSetDefaultNetwork;
import com.github.singularityme.network.packet.PacketSetDeviceNetwork;
import com.github.singularityme.network.packet.PacketSetNetworkSettings;
import com.github.singularityme.network.packet.PacketSetPermissions;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * Registers the Singularity ME FML network channel and all custom packets.
 */
public final class SingularityChannel {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("singularityme");

    private SingularityChannel() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(PacketNetworkTabData.Handler.class, PacketNetworkTabData.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PacketSetDeviceNetwork.Handler.class, PacketSetDeviceNetwork.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketCreateNetwork.Handler.class, PacketCreateNetwork.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketOpenNetworkTab.Handler.class, PacketOpenNetworkTab.class, id++, Side.SERVER);
        CHANNEL.registerMessage(
            PacketRequestNetworkTabData.Handler.class,
            PacketRequestNetworkTabData.class,
            id++,
            Side.SERVER);
        CHANNEL
            .registerMessage(PacketSetDefaultNetwork.Handler.class, PacketSetDefaultNetwork.class, id++, Side.SERVER);
        CHANNEL
            .registerMessage(PacketSetNetworkSettings.Handler.class, PacketSetNetworkSettings.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketRenameNetwork.Handler.class, PacketRenameNetwork.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketDeleteNetwork.Handler.class, PacketDeleteNetwork.class, id++, Side.SERVER);
        CHANNEL
            .registerMessage(PacketRequestNetworkStatus.Handler.class, PacketRequestNetworkStatus.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketNetworkStatus.Handler.class, PacketNetworkStatus.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PacketNetworkActionResult.Handler.class, PacketNetworkActionResult.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PacketSetPermissions.Handler.class, PacketSetPermissions.class, id++, Side.SERVER);
        CHANNEL.registerMessage(
            PacketGrantPermissionByName.Handler.class,
            PacketGrantPermissionByName.class,
            id++,
            Side.SERVER);
    }
}
