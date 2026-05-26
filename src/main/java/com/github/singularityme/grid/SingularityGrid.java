package com.github.singularityme.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

import com.github.singularityme.core.AEReflection;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridCache;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.events.MENetworkEvent;
import appeng.api.util.IReadOnlyCollection;
import appeng.me.Grid;
import appeng.me.GridNode;
import appeng.me.MachineSet;
import appeng.me.NetworkList;
import appeng.util.ReadOnlyCollection;

/**
 * One global grid per player. All ME devices owned by the same player are
 * members of this grid regardless of physical location or dimension.
 *
 * We do NOT extend Grid because its constructor immediately calls
 * center.setGrid(this) and registers with TickHandler — we need full control
 * over that lifecycle. Instead we delegate everything to an internal Grid.
 */
public class SingularityGrid implements IGrid {

    private final int playerID;
    private final UUID id = UUID.randomUUID();

    /** All nodes currently adopted into this global grid. */
    private final Set<GridNode> adoptedNodes = Collections.newSetFromMap(new IdentityHashMap<>());

    /** The real AE2 Grid that acts as the physical host for all our nodes. */
    private Grid internalGrid;

    /** The IGridBlock implementation for the virtual anchor. */
    private SingularityAnchorNode anchorBlock;

    /** The actual GridNode for the anchor, created via AEApi. */
    private GridNode anchorNode;

    public SingularityGrid(int playerID) {
        this.playerID = playerID;
    }

    /**
     * Take ownership of a GridNode that was just created by AE2.
     * Called from MixinAENetworkProxy after the node's playerID is set.
     */
    public synchronized void adoptNode(GridNode node) {
        if (adoptedNodes.contains(node)) return;

        if (internalGrid == null) {
            anchorBlock = new SingularityAnchorNode(this);
            // createGridNode returns IGridNode; the impl is always GridNode
            anchorNode = (GridNode) AEApi.instance()
                .createGridNode(anchorBlock);
            anchorBlock.setNode(anchorNode);
            // Grid constructor takes a GridNode as pivot and registers with TickHandler
            internalGrid = new Grid(anchorNode);
        }

        adoptedNodes.add(node);
        // setGrid() is package-private; use reflection helper (AT opens it at runtime)
        AEReflection.setGrid(node, internalGrid);
    }

    /**
     * Release a node when its tile entity is removed or chunk-unloaded.
     */
    public synchronized void releaseNode(GridNode node) {
        if (!adoptedNodes.remove(node)) return;
        // destroy() removes all connections and calls internalGrid.remove(node).
        // The anchor keeps internalGrid alive even after all real nodes leave.
        node.destroy();
    }

    /** Called when the server is stopping. */
    public synchronized void destroy() {
        for (GridNode node : new ArrayList<>(adoptedNodes)) {
            node.destroy();
        }
        adoptedNodes.clear();
        if (anchorNode != null) {
            anchorNode.destroy();
            anchorNode = null;
        }
        anchorBlock = null;
        internalGrid = null;
    }

    public int getPlayerID() {
        return playerID;
    }

    public Grid getInternalGrid() {
        return internalGrid;
    }

    public boolean hasNode(GridNode node) {
        return adoptedNodes.contains(node);
    }

    public int getAdoptedNodeCount() {
        return adoptedNodes.size();
    }

    // ---- IGrid delegation ----

    @Override
    public <C extends IGridCache> C getCache(Class<? extends IGridCache> iface) {
        if (internalGrid == null) return null;
        return internalGrid.getCache(iface);
    }

    @Override
    public MENetworkEvent postEvent(MENetworkEvent ev) {
        if (internalGrid == null) return ev;
        return internalGrid.postEvent(ev);
    }

    @Override
    public MENetworkEvent postEventTo(IGridNode node, MENetworkEvent ev) {
        if (internalGrid == null) return ev;
        return internalGrid.postEventTo(node, ev);
    }

    @Override
    public IReadOnlyCollection<Class<? extends IGridHost>> getMachinesClasses() {
        if (internalGrid == null) return new ReadOnlyCollection<>(Collections.emptySet());
        return internalGrid.getMachinesClasses();
    }

    @Override
    public IMachineSet getMachines(Class<? extends IGridHost> c) {
        if (internalGrid == null) return new MachineSet(c);
        return internalGrid.getMachines(c);
    }

    @Override
    public IReadOnlyCollection<IGridNode> getNodes() {
        if (internalGrid == null) return new ReadOnlyCollection<>(Collections.emptySet());
        return internalGrid.getNodes();
    }

    @Override
    public boolean isEmpty() {
        return adoptedNodes.isEmpty();
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public IGridNode getPivot() {
        if (internalGrid == null) return null;
        return internalGrid.getPivot();
    }

    @Override
    public NetworkList getGridConnections(Class<? extends IGridHost> accessType) {
        if (internalGrid == null) return new NetworkList();
        return internalGrid.getGridConnections(accessType);
    }

    @Override
    public NetworkList getAllRecursiveGridConnections(Class<? extends IGridHost> accessType) {
        if (internalGrid == null) return new NetworkList();
        return internalGrid.getAllRecursiveGridConnections(accessType);
    }
}
