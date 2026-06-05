package com.github.singularityme.mixin.mixins.late.ae2;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.singularityme.gui.SingularityTerminalPermissionGuards;

import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerCraftConfirm;

/** AE2 合成确认页提交任务前，补上奇点网络 CRAFT 权限检查。 */
@Mixin(value = ContainerCraftConfirm.class, remap = false)
public class MixinContainerCraftConfirm {

    /**
     * 阻止无 CRAFT 权限的玩家在确认页最终提交自动合成任务。
     */
    @Inject(method = "startJob(Z)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void singme$denySingularityCraftSubmit(final boolean follow, final CallbackInfo ci) {
        final ContainerCraftConfirm self = (ContainerCraftConfirm) (Object) this;
        final EntityPlayer player = self.getInventoryPlayer() == null ? null : self.getInventoryPlayer().player;
        if (!SingularityTerminalPermissionGuards.canRequestCrafting((AEBaseContainer) (Object) this, player)) {
            self.setAutoStart(false);
            self.setAutoStartAndFollow(false);
            ci.cancel();
        }
    }
}
