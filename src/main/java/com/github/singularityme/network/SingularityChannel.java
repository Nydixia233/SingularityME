package com.github.singularityme.network;

import com.github.singularityme.network.packet.PacketAddMemberByName;
import com.github.singularityme.network.packet.PacketCreateNetwork;
import com.github.singularityme.network.packet.PacketDeleteNetwork;
import com.github.singularityme.network.packet.PacketJoinEncryptedNetwork;
import com.github.singularityme.network.packet.PacketNetworkTabData;
import com.github.singularityme.network.packet.PacketOpenNetworkTab;
import com.github.singularityme.network.packet.PacketRenameNetwork;
import com.github.singularityme.network.packet.PacketRequestNetworkTabData;
import com.github.singularityme.network.packet.PacketSetDefaultNetwork;
import com.github.singularityme.network.packet.PacketSetDeviceNetwork;
import com.github.singularityme.network.packet.PacketSetMemberRole;
import com.github.singularityme.network.packet.PacketSetNetworkSettings;

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
        CHANNEL.registerMessage(
            PacketJoinEncryptedNetwork.Handler.class,
            PacketJoinEncryptedNetwork.class,
            id++,
            Side.SERVER);
        CHANNEL
            .registerMessage(PacketSetNetworkSettings.Handler.class, PacketSetNetworkSettings.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketRenameNetwork.Handler.class, PacketRenameNetwork.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketDeleteNetwork.Handler.class, PacketDeleteNetwork.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketSetMemberRole.Handler.class, PacketSetMemberRole.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PacketAddMemberByName.Handler.class, PacketAddMemberByName.class, id++, Side.SERVER);
    }
}
