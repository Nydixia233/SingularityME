package com.github.singularityme.tile;

import com.github.singularityme.core.SingularityNetworkManager;
import com.github.singularityme.grid.SingularityGrid;

import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.IConfigManager;
import appeng.me.GridNode;
import appeng.tile.grid.AENetworkTile;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;

/**
 * Singularity Terminal — exposes the player's global SingularityGrid storage
 * through the standard AE2 terminal GUI without requiring any local ME cables.
 *
 * <p>
 * Implements {@link ITerminalHost} so that {@code ContainerMEMonitorable} and
 * {@code GuiMEMonitorable} can be reused directly without any custom GUI code.
 */
public class TileSingularityTerminal extends AENetworkTile implements ITerminalHost, IConfigManagerHost {

    private final IConfigManager cm = new ConfigManager(this);

    public TileSingularityTerminal() {
        cm.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        cm.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        cm.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
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

    // ---- IStorageMonitorable (via ITerminalHost) ----

    @Override
    public appeng.api.storage.IMEMonitor<IAEItemStack> getItemInventory() {
        IStorageGrid storage = getStorageGrid();
        return storage == null ? null : storage.getItemInventory();
    }

    @Override
    public appeng.api.storage.IMEMonitor<IAEFluidStack> getFluidInventory() {
        IStorageGrid storage = getStorageGrid();
        return storage == null ? null : storage.getFluidInventory();
    }

    // ---- IConfigurableObject (via ITerminalHost) ----

    @Override
    public IConfigManager getConfigManager() {
        return cm;
    }

    // ---- IConfigManagerHost ----

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        // no-op for now; settings are purely client-side display preferences
    }

    // ---- IActionHost (required by AENetworkTile / ContainerMEMonitorable security checks) ----

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    // ---- helpers ----

    private IStorageGrid getStorageGrid() {
        GridNode node = (GridNode) getProxy().getNode();
        if (node == null) return null;
        int playerID = node.getPlayerID();
        SingularityGrid sg = SingularityNetworkManager.INSTANCE.getGridForPlayer(playerID);
        if (sg == null) return null;
        return sg.getCache(IStorageGrid.class);
    }
}
