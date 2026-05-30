package com.github.singularityme.init;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.singularityme.init.SingularityPrototypeRegistry.SyncStatus;
import com.github.singularityme.proxy.CommonProxy;
import com.google.common.base.Optional;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.api.definitions.IBlocks;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IItemDefinition;
import appeng.api.definitions.IParts;
import appeng.util.Platform;

public final class SingularityUpgradeMirror {

    private static final Logger LOG = LogManager.getLogger("SingularityME");

    private static final EnumSet<Upgrades> INTERFACE_UPGRADES = EnumSet.of(
        Upgrades.CRAFTING,
        Upgrades.PATTERN_CAPACITY,
        Upgrades.ADVANCED_BLOCKING,
        Upgrades.FAKE_CRAFTING,
        Upgrades.LOCK_CRAFTING,
        Upgrades.FUZZY);

    private static final EnumSet<Upgrades> STORAGE_BUS_UPGRADES = EnumSet
        .of(Upgrades.CAPACITY, Upgrades.FUZZY, Upgrades.INVERTER, Upgrades.ORE_FILTER, Upgrades.STICKY);

    private static final EnumSet<Upgrades> IMPORT_BUS_UPGRADES = EnumSet.of(
        Upgrades.CAPACITY,
        Upgrades.SPEED,
        Upgrades.SUPERSPEED,
        Upgrades.SUPERLUMINALSPEED,
        Upgrades.ORE_FILTER,
        Upgrades.FUZZY,
        Upgrades.REDSTONE);

    private static final EnumSet<Upgrades> EXPORT_BUS_UPGRADES = EnumSet.of(
        Upgrades.CAPACITY,
        Upgrades.SPEED,
        Upgrades.SUPERSPEED,
        Upgrades.SUPERLUMINALSPEED,
        Upgrades.ORE_FILTER,
        Upgrades.FUZZY,
        Upgrades.REDSTONE,
        Upgrades.CRAFTING);

    private SingularityUpgradeMirror() {}

    public static void mirrorAll() {
        final IDefinitions definitions = AEApi.instance()
            .definitions();
        final IParts parts = definitions.parts();
        final IBlocks blocks = definitions.blocks();

        final List<DeviceMapping> mappings = Arrays.asList(
            new DeviceMapping(
                "ME Interface",
                new ItemStack(CommonProxy.blockInterface),
                INTERFACE_UPGRADES,
                SyncStatus.AUTO_MIRROR,
                parts.iface(),
                blocks.iface()),
            new DeviceMapping(
                "ME Import Bus",
                new ItemStack(CommonProxy.blockImportBus),
                IMPORT_BUS_UPGRADES,
                SyncStatus.MIRROR_SAFE,
                parts.importBus()),
            new DeviceMapping(
                "ME Export Bus",
                new ItemStack(CommonProxy.blockExportBus),
                EXPORT_BUS_UPGRADES,
                SyncStatus.MIRROR_SAFE,
                parts.exportBus()),
            new DeviceMapping(
                "ME Storage Bus",
                new ItemStack(CommonProxy.blockStorageBus),
                STORAGE_BUS_UPGRADES,
                SyncStatus.MIRROR_SAFE,
                parts.storageBus()));

        int mirrored = 0;
        int denied = 0;
        for (final DeviceMapping mapping : mappings) {
            final MirrorResult result = mirror(mapping);
            mirrored += result.mirrored;
            denied += result.denied;
        }

        LOG.info(
            "[SingularityME] Mirrored {} upgrade registrations; denied {} unsupported prototype registrations.",
            mirrored,
            denied);
    }

    private static MirrorResult mirror(final DeviceMapping mapping) {
        int mirrored = 0;
        int denied = 0;

        for (final Upgrades upgrade : Upgrades.values()) {
            final int sourceMax = getMaxSupported(upgrade, mapping.sourceStacks);
            final int existingTargetMax = getMaxSupported(upgrade, mapping.targetStack);
            final boolean allowed = mapping.allowedUpgrades.contains(upgrade);

            removeSupported(upgrade, mapping.targetStack);

            if (allowed && Math.max(sourceMax, existingTargetMax) > 0) {
                final int max = Math.max(sourceMax, existingTargetMax);
                upgrade.registerItem(mapping.targetStack.copy(), max);
                mirrored++;
                SingularityPrototypeRegistry.recordUpgradeResult(
                    mapping.name,
                    upgrade,
                    mapping.allowedStatus,
                    sourceMax,
                    max,
                    "prototype mirror");
                LOG.info("[SingularityME] {} accepts {} x{} via AE2 prototype mirror.", mapping.name, upgrade, max);
            } else if (!allowed && (sourceMax > 0 || existingTargetMax > 0)) {
                denied++;
                SingularityPrototypeRegistry.recordUpgradeResult(
                    mapping.name,
                    upgrade,
                    SyncStatus.NOT_SUPPORTED,
                    sourceMax,
                    existingTargetMax,
                    "behavior not implemented");
                LOG.warn(
                    "[SingularityME] {} rejected unsupported upgrade {} (prototypeMax={}, directTargetMax={}).",
                    mapping.name,
                    upgrade,
                    sourceMax,
                    existingTargetMax);
            }
        }

        return new MirrorResult(mirrored, denied);
    }

    private static int getMaxSupported(final Upgrades upgrade, final List<ItemStack> candidates) {
        int max = 0;
        for (final ItemStack candidate : candidates) {
            max = Math.max(max, getMaxSupported(upgrade, candidate));
        }
        return max;
    }

    private static int getMaxSupported(final Upgrades upgrade, final ItemStack candidate) {
        int max = 0;
        for (final Map.Entry<ItemStack, Integer> entry : upgrade.getSupported()
            .entrySet()) {
            if (Platform.isSameItem(entry.getKey(), candidate)) {
                max = Math.max(max, entry.getValue());
            }
        }
        return max;
    }

    private static void removeSupported(final Upgrades upgrade, final ItemStack target) {
        final List<ItemStack> removals = new ArrayList<>();
        for (final ItemStack supported : upgrade.getSupported()
            .keySet()) {
            if (Platform.isSameItem(supported, target)) {
                removals.add(supported);
            }
        }
        for (final ItemStack supported : removals) {
            upgrade.getSupported()
                .remove(supported);
        }
    }

    private static List<ItemStack> stacks(final IItemDefinition... definitions) {
        final List<ItemStack> out = new ArrayList<>();
        for (final IItemDefinition definition : definitions) {
            if (definition == null) continue;
            final Optional<ItemStack> stack = definition.maybeStack(1);
            for (final ItemStack itemStack : stack.asSet()) {
                out.add(itemStack);
            }
        }
        return out;
    }

    private static final class DeviceMapping {

        private final String name;
        private final ItemStack targetStack;
        private final EnumSet<Upgrades> allowedUpgrades;
        private final SyncStatus allowedStatus;
        private final List<ItemStack> sourceStacks;

        private DeviceMapping(final String name, final ItemStack targetStack, final EnumSet<Upgrades> allowedUpgrades,
            final SyncStatus allowedStatus, final IItemDefinition... sources) {
            this.name = name;
            this.targetStack = targetStack;
            this.allowedUpgrades = allowedUpgrades;
            this.allowedStatus = allowedStatus;
            this.sourceStacks = stacks(sources);
        }
    }

    private static final class MirrorResult {

        private final int mirrored;
        private final int denied;

        private MirrorResult(final int mirrored, final int denied) {
            this.mirrored = mirrored;
            this.denied = denied;
        }
    }
}
