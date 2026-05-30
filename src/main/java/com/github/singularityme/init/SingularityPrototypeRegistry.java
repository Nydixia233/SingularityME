package com.github.singularityme.init;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;

import com.github.singularityme.tile.TileSingularityCraftingCore;
import com.github.singularityme.tile.TileSingularityCraftingTerminal;
import com.github.singularityme.tile.TileSingularityDrive;
import com.github.singularityme.tile.TileSingularityExportBus;
import com.github.singularityme.tile.TileSingularityImportBus;
import com.github.singularityme.tile.TileSingularityInterface;
import com.github.singularityme.tile.TileSingularityPatternTerminal;
import com.github.singularityme.tile.TileSingularityPowerCore;
import com.github.singularityme.tile.TileSingularityProbe;
import com.github.singularityme.tile.TileSingularityStorageBus;
import com.github.singularityme.tile.TileSingularityTerminal;

import appeng.api.config.Upgrades;

public final class SingularityPrototypeRegistry {

    public enum SyncStatus {
        AUTO_MIRROR,
        DELEGATE,
        ADAPT_REQUIRED,
        FEATURE_EXEMPT,
        FORBIDDEN,
        NOT_SUPPORTED,
        VERIFY,
        MIRROR_SAFE,
        CUSTOM_MIRROR,
        SINGULARITY_ONLY
    }

    private static final Map<Class<? extends TileEntity>, DeviceInfo> DEVICES = new HashMap<>();

    static {
        register(
            TileSingularityInterface.class,
            "ME Interface",
            line("Core", SyncStatus.DELEGATE, "DualityInterface"),
            line("GUI", SyncStatus.DELEGATE, "GuiInterface/ContainerInterface"),
            line("Upgrades", SyncStatus.AUTO_MIRROR, "interface prototype registrations"),
            line("Channel", SyncStatus.FEATURE_EXEMPT, "no physical channel required"),
            line("Grid", SyncStatus.FEATURE_EXEMPT, "player private SingularityGrid"));

        register(
            TileSingularityStorageBus.class,
            "ME Storage Bus",
            line("Core", SyncStatus.ADAPT_REQUIRED, "block-hosted storage bus handler"),
            line("Upgrades", SyncStatus.MIRROR_SAFE, "implemented upgrade whitelist"),
            line("ExternalStorage", SyncStatus.VERIFY, "registry/fluid path needs in-game checks"),
            line("GUI", SyncStatus.CUSTOM_MIRROR, "AE2-like custom GUI"),
            line("Grid", SyncStatus.FEATURE_EXEMPT, "wireless global grid"));

        register(
            TileSingularityImportBus.class,
            "ME Import Bus",
            line("Core", SyncStatus.ADAPT_REQUIRED, "tick import loop adapted"),
            line("Upgrades", SyncStatus.MIRROR_SAFE, "implemented upgrade whitelist"),
            line("Energy/Redstone", SyncStatus.VERIFY, "AE2 helper behavior needs comparison"),
            line("Grid", SyncStatus.FEATURE_EXEMPT, "wireless global grid"));

        register(
            TileSingularityExportBus.class,
            "ME Export Bus",
            line("Core", SyncStatus.ADAPT_REQUIRED, "tick export loop adapted"),
            line("CraftOnly", SyncStatus.VERIFY, "crafting card scheduling needs comparison"),
            line("Upgrades", SyncStatus.MIRROR_SAFE, "implemented upgrade whitelist"),
            line("Grid", SyncStatus.FEATURE_EXEMPT, "wireless global grid"));

        register(
            TileSingularityDrive.class,
            "ME Drive",
            line("CellProvider", SyncStatus.ADAPT_REQUIRED, "block cell provider cache"),
            line("Texture", SyncStatus.AUTO_MIRROR, "AE2 drive icons"),
            line("Model", SyncStatus.ADAPT_REQUIRED, "custom block renderer"),
            line("GUI", SyncStatus.CUSTOM_MIRROR, "AE2-like custom GUI"),
            line("Grid", SyncStatus.FEATURE_EXEMPT, "player private SingularityGrid"));

        register(
            TileSingularityTerminal.class,
            "ME Terminal",
            line("MonitorGUI", SyncStatus.DELEGATE, "GuiMEMonitorable/ContainerMEMonitorable"),
            line("Container", SyncStatus.DELEGATE, "ContainerMEMonitorable"),
            line("ViewCells", SyncStatus.ADAPT_REQUIRED, "block-hosted AbstractPartTerminal inventory"),
            line("Pins", SyncStatus.ADAPT_REQUIRED, "block-hosted terminal pins"),
            line("TypeFilter", SyncStatus.ADAPT_REQUIRED, "per-player monitor type filters"),
            line("StackTypes", SyncStatus.DELEGATE, "IStorageGrid#getMEMonitor(type)"),
            line("Texture/Model", SyncStatus.ADAPT_REQUIRED, "AE2 reporting part renderer"),
            line("Grid/Channel", SyncStatus.FEATURE_EXEMPT, "global player network without physical channels"),
            line("Security", SyncStatus.VERIFY, "permission smoke required"));

        register(
            TileSingularityCraftingTerminal.class,
            "ME Crafting Terminal",
            line("MonitorGUI", SyncStatus.DELEGATE, "GuiCraftingTerm/ContainerCraftingTerm"),
            line("CraftRequest", SyncStatus.DELEGATE, "AE2 terminal crafting request flow"),
            line("CraftingGrid", SyncStatus.ADAPT_REQUIRED, "block-hosted 3x3 matrix"),
            line("Grid/Channel", SyncStatus.FEATURE_EXEMPT, "global player network without physical channels"));

        register(
            TileSingularityPatternTerminal.class,
            "ME Pattern Terminal",
            line("MonitorGUI", SyncStatus.DELEGATE, "GuiPatternTerm/ContainerPatternTerm"),
            line("PatternEncode", SyncStatus.ADAPT_REQUIRED, "block-hosted pattern inventories"),
            line("PatternLoad", SyncStatus.DELEGATE, "IPatternTerminal default loader"),
            line("Grid/Channel", SyncStatus.FEATURE_EXEMPT, "global player network without physical channels"));

        register(
            TileSingularityCraftingCore.class,
            "Crafting CPU",
            line("CPU", SyncStatus.ADAPT_REQUIRED, "single-block synthetic CraftingCPUCluster"),
            line("Components", SyncStatus.AUTO_MIRROR, "AE2 crafting storage/accelerator/monitor items"),
            line("Grid", SyncStatus.FEATURE_EXEMPT, "player private SingularityGrid"),
            line("PhysicalNetwork", SyncStatus.FORBIDDEN, "no cable/controller bridge"));

        register(
            TileSingularityProbe.class,
            "Singularity Probe",
            line("Device", SyncStatus.SINGULARITY_ONLY, "debug grid diagnostics"));

        register(
            TileSingularityPowerCore.class,
            "Singularity Power Core",
            line("Device", SyncStatus.SINGULARITY_ONLY, "GT-to-AE power bridge"),
            line("Energy", SyncStatus.FEATURE_EXEMPT, "future replacement for virtual power"));
    }

    private SingularityPrototypeRegistry() {}

    public static DeviceInfo getDeviceInfo(final TileEntity tile) {
        if (tile == null) return null;
        return DEVICES.get(tile.getClass());
    }

    public static Stats getStats() {
        final Stats stats = new Stats();
        for (final DeviceInfo info : DEVICES.values()) {
            for (final SyncLine line : info.lines) {
                stats.add(line.status);
            }
            for (final UpgradeMirrorState state : info.upgradeStates.values()) {
                stats.add(state.status);
            }
        }
        return stats;
    }

    public static void recordUpgradeResult(final String deviceName, final Upgrades upgrade, final SyncStatus status,
        final int sourceMax, final int targetMax, final String reason) {
        for (final DeviceInfo info : DEVICES.values()) {
            if (info.name.equals(deviceName)) {
                info.upgradeStates.put(upgrade, new UpgradeMirrorState(upgrade, status, sourceMax, targetMax, reason));
                return;
            }
        }
    }

    private static void register(final Class<? extends TileEntity> tileClass, final String prototype,
        final SyncLine... lines) {
        DEVICES.put(tileClass, new DeviceInfo(tileClass, prototype, lines));
    }

    private static SyncLine line(final String key, final SyncStatus status, final String note) {
        return new SyncLine(key, status, note);
    }

    public static final class DeviceInfo {

        private final Class<? extends TileEntity> tileClass;
        private final String name;
        private final List<SyncLine> lines;
        private final Map<Upgrades, UpgradeMirrorState> upgradeStates = new EnumMap<>(Upgrades.class);

        private DeviceInfo(final Class<? extends TileEntity> tileClass, final String name, final SyncLine... lines) {
            this.tileClass = tileClass;
            this.name = name;
            final List<SyncLine> out = new ArrayList<>();
            Collections.addAll(out, lines);
            this.lines = Collections.unmodifiableList(out);
        }

        public String getName() {
            return this.name;
        }

        public List<SyncLine> getLines() {
            return this.lines;
        }

        public List<UpgradeMirrorState> getUpgradeStates() {
            return new ArrayList<>(this.upgradeStates.values());
        }

        public Class<? extends TileEntity> getTileClass() {
            return this.tileClass;
        }
    }

    public static final class SyncLine {

        private final String key;
        private final SyncStatus status;
        private final String note;

        private SyncLine(final String key, final SyncStatus status, final String note) {
            this.key = key;
            this.status = status;
            this.note = note;
        }

        public String format() {
            if (this.note.isEmpty()) return this.key + ": " + this.status;
            return this.key + ": " + this.status + " (" + this.note + ")";
        }

        public SyncStatus getStatus() {
            return this.status;
        }
    }

    public static final class UpgradeMirrorState {

        private final Upgrades upgrade;
        private final SyncStatus status;
        private final int sourceMax;
        private final int targetMax;
        private final String reason;

        private UpgradeMirrorState(final Upgrades upgrade, final SyncStatus status, final int sourceMax,
            final int targetMax, final String reason) {
            this.upgrade = upgrade;
            this.status = status;
            this.sourceMax = sourceMax;
            this.targetMax = targetMax;
            this.reason = reason;
        }

        public String format() {
            return this.upgrade + ": "
                + this.status
                + " (source="
                + this.sourceMax
                + ", target="
                + this.targetMax
                + ", "
                + this.reason
                + ")";
        }

        public SyncStatus getStatus() {
            return this.status;
        }
    }

    public static final class Stats {

        private final Map<SyncStatus, Integer> counts = new EnumMap<>(SyncStatus.class);
        private int total;

        private void add(final SyncStatus status) {
            this.total++;
            this.counts.put(status, this.counts.getOrDefault(status, 0) + 1);
        }

        public int getTotal() {
            return this.total;
        }

        public int get(final SyncStatus status) {
            return this.counts.getOrDefault(status, 0);
        }
    }
}
