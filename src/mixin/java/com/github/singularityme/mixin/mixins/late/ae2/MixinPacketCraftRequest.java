package com.github.singularityme.mixin.mixins.late.ae2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.singularityme.gui.SingularityTerminalPermissionGuards;

import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.core.sync.packets.PacketCraftRequest;

/** AE2 合成数量页发起计划计算前，补上奇点网络 CRAFT 权限检查。 */
@Mixin(value = PacketCraftRequest.class, remap = false)
public class MixinPacketCraftRequest {

    /**
     * 阻止无 CRAFT 权限的玩家通过 AE2 原生子容器绕过奇点终端权限。
     */
    @Inject(method = "serverPacketData", at = @At("HEAD"), cancellable = true, remap = false)
    private void singme$denySingularityCraftPlanning(final INetworkInfo manager, final AppEngPacket packet,
        final EntityPlayer player, final CallbackInfo ci) {
        final Container container = player == null ? null : player.openContainer;
        if (container instanceof ContainerCraftAmount && container instanceof AEBaseContainer aeContainer
            && !SingularityTerminalPermissionGuards.canRequestCrafting(aeContainer, player)) {
            ci.cancel();
        }
    }
}
