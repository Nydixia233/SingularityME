package com.github.singularityme.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.github.singularityme.core.AEReflection;
import com.github.singularityme.tile.TileSingularityPowerCore;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridCache;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.events.MENetworkEvent;
import appeng.api.networking.events.MENetworkPowerStorage;
import appeng.api.networking.events.MENetworkPowerStorage.PowerEventType;
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
    private final int networkID;
    private final UUID id = UUID.randomUUID();

    /** All nodes currently adopted into this global grid. */
    private final Set<GridNode> adoptedNodes = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Power Cores currently contributing to the virtual network-level energy store. */
    private final Set<TileSingularityPowerCore> powerCores = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Phantom placeholders for devices whose chunks are currently unloaded.
     * Keyed by "dim:x:y:z". Prevents the grid from being destroyed when all
     * real nodes are temporarily absent due to chunk unloading.
     */
    private final Map<String, PhantomSingularityNode> phantomNodes = new HashMap<>();

    /** The real AE2 Grid that acts as the physical host for all our nodes. */
    private Grid internalGrid;

    /** The IGridBlock implementation for the virtual anchor. */
    private SingularityAnchorNode anchorBlock;

    /** The actual GridNode for the anchor, created via AEApi. */
    private GridNode anchorNode;

    private TileSingularityPowerCore effectivePowerCore;

    public SingularityGrid(final int playerID, final int networkID) {
        this.playerID = playerID;
        this.networkID = networkID;
    }

    /** Backward-compatible constructor for the default network (networkID=0). */
    public SingularityGrid(final int playerID) {
        this(playerID, 0);
    }

    /**
     * Take ownership of a GridNode that was just created by AE2.
     * Called from MixinAENetworkProxy after the node's playerID is set.
     */
    public synchronized void adoptNode(GridNode node) {
        if (adoptedNodes.contains(node)) return;

        this.ensureInternalGrid();
        adoptedNodes.add(node);
        // setGrid() is package-private; use reflection helper (AT opens it at runtime)
        AEReflection.setGrid(node, internalGrid);
        registerPowerCore(node);
    }

    /**
     * Create the backing AE2 grid and anchor without adopting a physical node.
     * Used by bridge devices that expose an external AE network to this
     * SingularityGrid through their own virtual node.
     */
    public synchronized void ensureInternalGrid() {
        if (internalGrid != null && anchorNode != null
            && anchorBlock != null
            && !anchorBlock.isDestroyed()
            && internalGrid.getPivot() != null) {
            return;
        }

        if (anchorBlock != null && !anchorBlock.isDestroyed()) {
            anchorBlock.destroy();
        } else if (anchorBlock == null && anchorNode != null && isNodeStillInInternalGrid(anchorNode)) {
            anchorNode.destroy();
        }
        anchorBlock = null;
        anchorNode = null;
        internalGrid = null;

        anchorBlock = new SingularityAnchorNode(this);
        // createGridNode returns IGridNode; the impl is always GridNode
        anchorNode = (GridNode) AEApi.instance()
            .createGridNode(anchorBlock);
        anchorBlock.setNode(anchorNode);
        // Grid constructor takes a GridNode as pivot and registers with TickHandler
        internalGrid = new Grid(anchorNode);
    }

    /**
     * Release a node when its tile entity is removed or chunk-unloaded.
     */
    public synchronized void releaseNode(GridNode node) {
        if (!detachNode(node)) return;
        destroyDetachedNode(node);
    }

    /**
     * Remove a node from the Singularity ownership set without firing AE2 teardown callbacks.
     * Callers that hold external map locks can use this first, then destroy outside that lock.
     */
    public synchronized boolean detachNode(final GridNode node) {
        unregisterPowerCore(node);
        return adoptedNodes.remove(node);
    }

    public synchronized void destroyDetachedNode(final GridNode node) {
        if (isNodeStillInInternalGrid(node)) {
            // destroy() removes all connections and calls internalGrid.remove(node).
            node.destroy();
        }
    }

    /** Called when the server is stopping. */
    public synchronized void destroy() {
        for (GridNode node : new ArrayList<>(adoptedNodes)) {
            if (isNodeStillInInternalGrid(node)) {
                node.destroy();
            }
        }
        adoptedNodes.clear();
        powerCores.clear();
        effectivePowerCore = null;
        if (anchorBlock != null) {
            anchorBlock.destroy();
        } else if (anchorNode != null) {
            anchorNode.destroy();
        }
        anchorNode = null;
        anchorBlock = null;
        internalGrid = null;
    }

    public int getPlayerID() {
        return playerID;
    }

    public int getNetworkID() {
        return networkID;
    }

    public synchronized Grid getInternalGrid() {
        return internalGrid;
    }

    public synchronized boolean hasNode(GridNode node) {
        return adoptedNodes.contains(node);
    }

    public synchronized int getAdoptedNodeCount() {
        return adoptedNodes.size() + phantomNodes.size();
    }

    public synchronized List<GridNode> getAdoptedNodeSnapshot() {
        return new ArrayList<>(adoptedNodes);
    }

    public synchronized List<PhantomSingularityNode> getPhantomNodeSnapshot() {
        return new ArrayList<>(phantomNodes.values());
    }

    public synchronized double getVirtualAEMaxPower() {
        return effectivePowerCore == null ? 0.0 : effectivePowerCore.getConfiguredPowerCapacity();
    }

    public synchronized double getVirtualAECurrentPower() {
        return effectivePowerCore == null ? 0.0 : effectivePowerCore.getStoredPowerForVirtualStorage();
    }

    public synchronized double extractVirtualAEPower(final double amt, final Actionable mode,
        final PowerMultiplier multiplier) {
        return multiplier.divide(this.extractVirtualAEPower(multiplier.multiply(amt), mode));
    }

    public synchronized void refreshPowerCoreContribution(final TileSingularityPowerCore core) {
        if (core != null && !powerCores.contains(core)) return;
        recomputeEffectivePowerCore();
        advertiseVirtualPowerStorage();
    }

    private double extractVirtualAEPower(final double amt, final Actionable mode) {
        if (effectivePowerCore == null || amt <= 0.0) return 0.0;
        return effectivePowerCore.extractPowerForVirtualStorage(amt, mode);
    }

    private void registerPowerCore(final GridNode node) {
        final IGridHost machine = node.getMachine();
        if (!(machine instanceof TileSingularityPowerCore core)) return;
        powerCores.add(core);
        recomputeEffectivePowerCore();
        advertiseVirtualPowerStorage();
    }

    private void unregisterPowerCore(final GridNode node) {
        final IGridHost machine = node.getMachine();
        if (!(machine instanceof TileSingularityPowerCore core)) return;
        powerCores.remove(core);
        recomputeEffectivePowerCore();
        advertiseVirtualPowerStorage();
    }

    private void recomputeEffectivePowerCore() {
        TileSingularityPowerCore best = null;
        double bestCapacity = 0.0;

        for (final TileSingularityPowerCore core : powerCores) {
            if (!core.isPowerCoreContributionAvailable()) continue;

            final double capacity = core.getConfiguredPowerCapacity();
            if (!Double.isFinite(capacity) || capacity <= 0.0) continue;

            if (best == null || comparePowerCoreCandidate(core, capacity, best, bestCapacity) > 0) {
                best = core;
                bestCapacity = capacity;
            }
        }

        effectivePowerCore = best;
    }

    private void advertiseVirtualPowerStorage() {
        if (internalGrid == null || anchorBlock == null) return;
        final PowerEventType type = this.getVirtualAECurrentPower() > 0.001 ? PowerEventType.PROVIDE_POWER
            : PowerEventType.REQUEST_POWER;
        internalGrid.postEvent(new MENetworkPowerStorage(anchorBlock, type));
    }

    private static int comparePowerCoreCandidate(final TileSingularityPowerCore candidate,
        final double candidateCapacity, final TileSingularityPowerCore current, final double currentCapacity) {
        final int capacityCompare = Double.compare(candidateCapacity, currentCapacity);
        if (capacityCompare != 0) return capacityCompare;

        int compare = Integer.compare(powerCoreDimension(current), powerCoreDimension(candidate));
        if (compare != 0) return compare;
        compare = Integer.compare(current.xCoord, candidate.xCoord);
        if (compare != 0) return compare;
        compare = Integer.compare(current.yCoord, candidate.yCoord);
        if (compare != 0) return compare;
        compare = Integer.compare(current.zCoord, candidate.zCoord);
        if (compare != 0) return compare;
        return Integer.compare(System.identityHashCode(current), System.identityHashCode(candidate));
    }

    private static int powerCoreDimension(final TileSingularityPowerCore core) {
        return core.getWorldObj() == null ? Integer.MAX_VALUE : core.getWorldObj().provider.dimensionId;
    }

    // ---- Phantom node management ----

    /**
     * Adds a phantom placeholder for a device that just had its chunk unloaded.
     * The phantom keeps the grid alive until the chunk reloads.
     */
    public synchronized void addPhantom(final PhantomSingularityNode phantom) {
        phantomNodes.put(phantom.key, phantom);
    }

    /**
     * Removes the phantom for the given position, if any.
     * Called when the real node is re-registered on chunk reload.
     */
    public synchronized void removePhantom(final int x, final int y, final int z, final int dim) {
        phantomNodes.remove(dim + ":" + x + ":" + y + ":" + z);
    }

    public synchronized int getPhantomNodeCount() {
        return phantomNodes.size();
    }

    private boolean isNodeStillInInternalGrid(final GridNode node) {
        if (node == null || internalGrid == null) return false;
        for (final IGridNode current : internalGrid.getNodes()) {
            if (current == node) return true;
        }
        return false;
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
    public synchronized boolean isEmpty() {
        return adoptedNodes.isEmpty() && phantomNodes.isEmpty();
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
