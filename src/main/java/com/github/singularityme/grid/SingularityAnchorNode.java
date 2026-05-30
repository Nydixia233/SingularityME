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
 * Virtual anchor node that keeps a player's SingularityGrid alive without scanning the world.
 *
 * <p>
 * The anchor is not a power source. Singularity grids start at 0 AE and only become powered when
 * Singularity Power Core tiles join the same player grid.
 */
public class SingularityAnchorNode implements IGridBlock, IGridHost, IAEPowerStorage {

    private static final EnumSet<GridFlags> FLAGS = EnumSet.of(GridFlags.DENSE_CAPACITY);
    // Virtual anchors are not world-accessible; keep the dummy dimension impossible to match
    // during vanilla AE2 WorldEvent.Unload sweeps.
    private static final DimensionalCoord DUMMY_COORD = new DimensionalCoord(0, -256, 0, Integer.MIN_VALUE);

    private final SingularityGrid owner;
    private GridNode node;
    private volatile boolean destroyed;

    public SingularityAnchorNode(final SingularityGrid owner) {
        this.owner = owner;
    }

    public GridNode getNode() {
        return this.node;
    }

    public void setNode(final GridNode node) {
        this.node = node;
        this.destroyed = false;
    }

    public void destroy() {
        if (this.node != null) {
            this.node.destroy();
            this.node = null;
        }
        this.destroyed = true;
    }

    public boolean isDestroyed() {
        return this.destroyed;
    }

    @Override
    public double getIdlePowerUsage() {
        return 0.0;
    }

    @Override
    public EnumSet<GridFlags> getFlags() {
        return FLAGS;
    }

    @Override
    public boolean isWorldAccessible() {
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
    public void onGridNotification(final GridNotification notification) {}

    @Override
    public void setNetworkStatus(final IGrid grid, final int channelsInUse) {}

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

    @Override
    public IGridNode getGridNode(final ForgeDirection dir) {
        return this.node;
    }

    @Override
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.DENSE;
    }

    @Override
    public void securityBreak() {}

    public SingularityGrid getOwner() {
        return this.owner;
    }

    @Override
    public double injectAEPower(final double amt, final Actionable mode) {
        return amt;
    }

    @Override
    public double getAEMaxPower() {
        return this.owner.getVirtualAEMaxPower();
    }

    @Override
    public double getAECurrentPower() {
        return this.owner.getVirtualAECurrentPower();
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return true;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return AccessRestriction.READ;
    }

    @Override
    public boolean isInfinite() {
        return false;
    }

    @Override
    public double extractAEPower(final double amt, final Actionable mode, final PowerMultiplier pm) {
        return this.owner.extractVirtualAEPower(amt, mode, pm);
    }
}
