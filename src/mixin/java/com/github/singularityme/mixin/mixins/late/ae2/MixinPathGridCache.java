package com.github.singularityme.mixin.mixins.late.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.singularityme.core.SingularityNetworkManager;

import appeng.api.networking.IGrid;
import appeng.me.cache.PathGridCache;

/**
 * Bypasses the ad-hoc channel limit for SingularityGrid networks.
 *
 * <p>
 * In a normal AE2 ad-hoc network, {@code calculateAdHocChannels()} counts every
 * REQUIRE_CHANNEL node and returns 0 (= all nodes go dark) if the count exceeds 8.
 * For a SingularityGrid we want unlimited devices, so we short-circuit the method
 * and return 1 — enough for every node's {@code meetsChannelRequirements()} check
 * ({@code usedChannels > 0}) to pass, without triggering the "over 8" cutoff.
 */
@Mixin(value = PathGridCache.class, remap = false)
public class MixinPathGridCache {

    @Shadow
    private IGrid myGrid;

    /**
     * If this PathGridCache belongs to a SingularityGrid's internal grid, skip the
     * normal channel-counting logic and return 1 so all nodes stay active.
     */
    @Inject(method = "calculateAdHocChannels", at = @At("HEAD"), cancellable = true, remap = false)
    private void singme$skipChannelLimitForSingularityGrid(CallbackInfoReturnable<Integer> cir) {
        if (SingularityNetworkManager.INSTANCE.isSingularityGrid(myGrid)) {
            // Return 1: every node gets usedChannels=1, satisfying meetsChannelRequirements()
            // without hitting the >8 cutoff that would return 0 and disable the network.
            cir.setReturnValue(1);
        }
    }
}
