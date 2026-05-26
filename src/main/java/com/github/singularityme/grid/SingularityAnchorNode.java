package com.github.singularityme.grid;

import java.util.EnumSet;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridNotification;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridNode;

/**
 * Virtual anchor node that acts as the physical root of a SingularityGrid.
 *
 * <p>
 * The anchor is not a real tile entity — it lives at a dummy coordinate in dim 0 and is
 * marked as NOT world-accessible so AE2 won't try to scan adjacent blocks. It carries
 * DENSE_CAPACITY so all connections through it get unlimited channels.
 *
 * <p>
 * Also implements {@link IAEPowerStorage} to provide infinite AE power so that all adopted
 * nodes see {@code isActive()=true} without physical ME cables. Phase 4 will replace this with
 * real GT EU consumption via a SingularityPowerCore tile.
 */
public class SingularityAnchorNode implements IGridBlock, IGridHost, IAEPowerStorage {

    private static final EnumSet<GridFlags> FLAGS = EnumSet.of(GridFlags.DENSE_CAPACITY // unlimited channel capacity;
                                                                                        // also implies PREFERRED
    );

    private final SingularityGrid owner;
    private GridNode node;

    // Dummy location — dim 0, far underground, will never collide with real blocks
    // Uses the (x, y, z, dim) constructor which doesn't require a World reference
    private static final DimensionalCoord DUMMY_COORD = new DimensionalCoord(0, -256, 0, 0);

    public SingularityAnchorNode(SingularityGrid owner) {
        this.owner = owner;
    }

    public GridNode getNode() {
        return node;
    }

    public void setNode(GridNode node) {
        this.node = node;
    }

    public void destroy() {
        if (node != null) {
            node.destroy();
            node = null;
        }
    }

    // ---- IGridBlock ----

    @Override
    public double getIdlePowerUsage() {
        return 0.0; // energy cost handled separately by SingularityPowerDrain
    }

    @Override
    public EnumSet<GridFlags> getFlags() {
        return FLAGS;
    }

    @Override
    public boolean isWorldAccessible() {
        // Critical: returning false prevents AE2 from scanning adjacent blocks
        return false;
    }

    @Override
    public DimensionalCoord getLocation() {
        return DUMMY_COORD;
    }

    @Override
    public AEColor getGridColor() {
        return AEColor.Transparent;
    }

    @Override
    public void onGridNotification(GridNotification notification) {}

    @Override
    public void setNetworkStatus(IGrid grid, int channelsInUse) {}

    @Override
    public EnumSet<ForgeDirection> getConnectableSides() {
        return EnumSet.noneOf(ForgeDirection.class);
    }

    @Override
    public IGridHost getMachine() {
        return this;
    }

    @Override
    public void gridChanged() {}

    @Override
    public ItemStack getMachineRepresentation() {
        return null;
    }

    // ---- IGridHost ----

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return node;
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return AECableType.DENSE;
    }

    @Override
    public void securityBreak() {}

    // ---- IAEPowerStorage — unlimited AE power (Phase 4 will replace with GT EU) ----

    @Override
    public double injectAEPower(final double amt, final Actionable mode) {
        // Accept all injected power (nothing left over)
        return 0.0;
    }

    /** IEnergySource overload with PowerMultiplier — always supply the full amount. */
    @Override
    public double extractAEPower(final double amt, final Actionable mode, final PowerMultiplier multiplier) {
        return multiplier.multiply(amt);
    }

    @Override
    public double getAECurrentPower() {
        return Double.MAX_VALUE;
    }

    @Override
    public double getAEMaxPower() {
        return Double.MAX_VALUE;
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        // Must be true so EnergyGridCache.addNode() registers us as a provider
        return true;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return AccessRestriction.READ_WRITE;
    }
}
