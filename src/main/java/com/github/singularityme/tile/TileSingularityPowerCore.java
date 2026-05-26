package com.github.singularityme.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.core.SingularityNetworkManager;
import com.github.singularityme.grid.SingularityGrid;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnits;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.me.GridNode;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import gregtech.api.interfaces.tileentity.IEnergyConnected;

/**
 * Singularity Power Core — draws EU from the adjacent GT cable network and
 * converts it to AE power for the player's SingularityGrid.
 *
 * <p>
 * Implements {@link IEnergyConnected} directly (replicating GTPowerSink logic)
 * to avoid the Mekanism compile dependency that comes with AENetworkPowerTile.
 * EU is converted to AE and stored in an internal buffer; each tick the buffer
 * is drained into the SingularityGrid via {@link IEnergyGrid#injectPower}.
 *
 * <p>
 * Input voltage: up to LuV (512 EU/t × 2 amps = 1024 EU/t max).
 * Buffer: 40 000 AE (≈ 40 seconds at idle).
 */
public class TileSingularityPowerCore extends AENetworkTile implements IGridTickable, IEnergyConnected {

    private static final double MAX_BUFFER = 40_000.0;

    /** Internal AE power buffer, filled by GT EU injection. */
    private double buffer = 0.0;

    public TileSingularityPowerCore() {
        this.getProxy()
            .setIdlePowerUsage(0.0);
    }

    // ---- AE2 lifecycle ----

    @Override
    public void onReady() {
        super.onReady();
        if (worldObj.isRemote) return;
        GridNode node = (GridNode) getProxy().getNode();
        if (node != null && node.getPlayerID() >= 0) {
            SingularityNetworkManager.INSTANCE.registerNode(node.getPlayerID(), node);
        }
    }

    @Override
    public void onChunkUnload() {
        unregister();
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        unregister();
        super.invalidate();
    }

    private void unregister() {
        if (worldObj == null || worldObj.isRemote) return;
        GridNode node = (GridNode) getProxy().getNode();
        if (node != null) {
            SingularityNetworkManager.INSTANCE.unregisterNode(node.getPlayerID(), node);
        }
    }

    // ---- NBT ----

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_PowerCore(final NBTTagCompound data) {
        data.setDouble("aeBuffer", buffer);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_PowerCore(final NBTTagCompound data) {
        buffer = data.getDouble("aeBuffer");
    }

    // ---- IGridTickable — drain buffer into SingularityGrid ----

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(1, 20, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (buffer <= 0.0) return TickRateModulation.SLOWER;

        GridNode gn = (GridNode) getProxy().getNode();
        if (gn == null) return TickRateModulation.SLOWER;

        SingularityGrid sg = SingularityNetworkManager.INSTANCE.getGridForPlayer(gn.getPlayerID());
        if (sg == null) return TickRateModulation.SLOWER;

        IEnergyGrid energyGrid = sg.getCache(IEnergyGrid.class);
        if (energyGrid == null) return TickRateModulation.SLOWER;

        double overflow = energyGrid.injectPower(buffer, Actionable.MODULATE);
        buffer = Math.max(0.0, overflow);

        return buffer > 0.0 ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    // ---- IEnergyConnected — receive EU from GT cables ----

    @Override
    public long injectEnergyUnits(final ForgeDirection side, final long voltage, final long amperage) {
        if (!inputEnergyFrom(side)) return 0;

        double e = PowerUnits.EU.convertTo(PowerUnits.AE, voltage * amperage);
        double space = MAX_BUFFER - buffer;
        if (space <= 0.0) return 0;

        // How much AE overflows?
        double overflow = Math.max(0.0, e - space);
        // How many amps does the overflow represent?
        long unusedAmps = (overflow <= 0.0) ? 0
            : (long) Math.ceil(PowerUnits.AE.convertTo(PowerUnits.EU, overflow) / voltage);
        long used = amperage - unusedAmps;
        if (used <= 0) return 0;

        buffer += PowerUnits.EU.convertTo(PowerUnits.AE, voltage * used);
        if (buffer > MAX_BUFFER) buffer = MAX_BUFFER;
        return used;
    }

    @Override
    public boolean inputEnergyFrom(final ForgeDirection side) {
        return true;
    }

    @Override
    public boolean outputsEnergyTo(final ForgeDirection side) {
        return false;
    }

    @Override
    public byte getColorization() {
        return -1;
    }

    @Override
    public byte setColorization(final byte color) {
        return -1;
    }

    // ---- AE cable connection ----

    @Override
    public appeng.api.util.AECableType getCableConnectionType(final ForgeDirection dir) {
        return appeng.api.util.AECableType.SMART;
    }
}
