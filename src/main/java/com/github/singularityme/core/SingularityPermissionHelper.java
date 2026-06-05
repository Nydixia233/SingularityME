package com.github.singularityme.core;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.singularityme.tile.ISingularityNetworkDevice;

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGridNode;
import appeng.me.GridNode;
import appeng.me.helpers.IGridProxyable;

/** 服务端权限辅助方法，统一奇点网络对 AE2 SecurityPermissions 的检查入口。 */
public final class SingularityPermissionHelper {

    private SingularityPermissionHelper() {}

    /** 检查玩家是否可建造、旋转、拆除或重新配置该网络设备。 */
    public static boolean checkBuild(final World world, final TileEntity te, final EntityPlayer player) {
        if (!(te instanceof ISingularityNetworkDevice device)) return true;
        final int networkID = device.getNetworkID();
        if (networkID == 0) return true;
        final int playerID = getPlayerID(player);
        return hasPermission(world, networkID, playerID, SecurityPermissions.BUILD);
    }

    /** 检查玩家是否可打开普通网络设备 GUI。未分配设备优先限制为放置者可配置。 */
    public static boolean checkUse(final World world, final TileEntity te, final EntityPlayer player) {
        if (!(te instanceof ISingularityNetworkDevice device)) return true;
        final int playerID = getPlayerID(player);
        if (playerID < 0) return false;

        final int networkID = device.getNetworkID();
        if (networkID == 0) {
            final int placerID = getPlacedPlayerID(te);
            return placerID < 0 || placerID == playerID;
        }
        if (world == null || world.isRemote) return false;
        final World registryWorld = getRegistryWorld(world);
        return registryWorld != null
            && SingularityNetworkRegistry.get(registryWorld)
                .canUseNetwork(networkID, playerID);
    }

    /** 按 AE2 内部玩家 ID 检查指定网络权限，供自动设备按放置者身份执行。 */
    public static boolean hasPermission(final World world, final int networkID, final int playerID,
        final SecurityPermissions permission) {
        if (networkID == 0 || playerID < 0 || permission == null) return false;
        if (world == null || world.isRemote) return false;
        final World registryWorld = getRegistryWorld(world);
        return registryWorld != null
            && SingularityNetworkRegistry.get(registryWorld)
                .hasPermission(networkID, playerID, permission);
    }

    /** 按当前玩家检查指定网络权限。 */
    public static boolean hasPlayerPermission(final World world, final int networkID, final EntityPlayer player,
        final SecurityPermissions permission) {
        return hasPermission(world, networkID, getPlayerID(player), permission);
    }

    /** 按 GridNode 的放置者玩家 ID 检查指定网络权限。 */
    public static boolean hasNodePermission(final World world, final int networkID, final IGridNode node,
        final SecurityPermissions permission) {
        return hasPermission(world, networkID, getNodePlayerID(node), permission);
    }

    /** 从当前玩家解析 AE2 内部玩家 ID。 */
    public static int getPlayerID(final EntityPlayer player) {
        if (player == null) return -1;
        try {
            return AEApi.instance()
                .registries()
                .players()
                .getID(player);
        } catch (final RuntimeException ignored) {
            return -1;
        }
    }

    /** 从 GridNode 读取放置者玩家 ID。 */
    public static int getNodePlayerID(final IGridNode node) {
        return node instanceof GridNode gridNode ? gridNode.getPlayerID() : -1;
    }

    /** 从 TileEntity 的 AE2 proxy 读取放置者玩家 ID。 */
    public static int getPlacedPlayerID(final TileEntity te) {
        if (!(te instanceof IGridProxyable proxyable)) return -1;
        try {
            return getNodePlayerID(proxyable.getProxy()
                .getNode());
        } catch (final RuntimeException ignored) {
            return -1;
        }
    }

    /** 奇点网络元数据固定写入主世界；主世界不可用时回退到当前世界。 */
    private static World getRegistryWorld(final World fallback) {
        try {
            final MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                final World overworld = server.worldServerForDimension(0);
                if (overworld != null) return overworld;
            }
        } catch (final RuntimeException ignored) {}
        return fallback;
    }
}
