package com.github.singularityme.client.ui;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.GuiScreenWrapper;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.github.singularityme.client.ui.NetworkUiKit.Palette;
import com.github.singularityme.client.ui.NetworkUiKit.Styles;
import com.github.singularityme.network.SingularityChannel;
import com.github.singularityme.network.packet.PacketNetworkActionResult;
import com.github.singularityme.network.packet.PacketNetworkTabData;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;
import com.github.singularityme.network.packet.PacketRequestNetworkTabData;

/** 设备网络分配 GUI，复用网络终端左侧的共享网络选择表面。 */
public final class NetworkTabUI {

    private static WeakReference<TabState> activeState;

    private NetworkTabUI() {}

    public static GuiScreen create(final TileEntity te) {
        final int x = te == null ? 0 : te.xCoord;
        final int y = te == null ? 0 : te.yCoord;
        final int z = te == null ? 0 : te.zCoord;
        final int dim = te != null && te.getWorldObj() != null && te.getWorldObj().provider != null
            ? te.getWorldObj().provider.dimensionId : 0;

        final ModularScreen screen = new ModularScreen("singularityme", (ModularGuiContext ctx) -> {
            final TabState state = new TabState(x, y, z, dim);
            activeState = new WeakReference<>(state);
            return state.buildPanel();
        });
        screen.getContext().setSettings(new UISettings());
        final GuiScreenWrapper wrapper = new GuiScreenWrapper(screen);

        Minecraft.getMinecraft().func_152344_a(() -> {
            final TabState state = activeState == null ? null : activeState.get();
            if (state != null) state.requestNetworkData();
        });
        return wrapper;
    }

    public static boolean receiveNetworkData(final PacketNetworkTabData packet) {
        final TabState state = activeState == null ? null : activeState.get();
        if (state == null) return false;
        if (Minecraft.getMinecraft().currentScreen instanceof GuiScreenWrapper w
            && w.getScreen().isPanelOpen("network_tab")) {
            state.receive(packet);
            return true;
        }
        return false;
    }

    public static boolean receiveActionResult(final PacketNetworkActionResult packet) {
        final TabState state = activeState == null ? null : activeState.get();
        if (state == null) return false;
        if (Minecraft.getMinecraft().currentScreen instanceof GuiScreenWrapper w
            && w.getScreen().isPanelOpen("network_tab")) {
            state.receiveActionResult(packet);
            return true;
        }
        return false;
    }

    /** 设备网络分配 GUI 的本地状态与共享选择表面代理。 */
    private static final class TabState implements NetworkSelectionSurface.Delegate {

        final int x, y, z, dim;
        final List<NetworkEntry> networks = new ArrayList<>();
        int selectedNetworkID;
        int deviceNetworkID;
        int defaultNetworkID;

        NetworkSelectionSurface selectionSurface;
        Flow summaryArea;

        TabState(final int x, final int y, final int z, final int dim) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
        }

        ModularPanel buildPanel() {
            final int guiScale = Math.max(1, Minecraft.getMinecraft().gameSettings.guiScale);
            final int panelW = Math.min(480 * guiScale, 720);
            final int panelH = Math.min(300 * guiScale, 500);

            final ModularPanel panel = new ModularPanel("network_tab")
                .size(panelW, panelH)
                .background(new ShadowDrawable(Styles.panelBg(), 5, 0x80000000));

            final Flow root = Flow.column()
                .childPadding(10)
                .widthRel(1f).heightRel(1f)
                .padding(0, 14).margin(14, 0);
            root.child(new TextWidget(IKey.str(NetworkUiKit.tr("gui.singularityme.network_tab.title")))
                .color(Palette.TEXT_PRIMARY));

            selectionSurface = new NetworkSelectionSurface(NetworkSelectionSurface.Mode.DEVICE_ASSIGN, this);
            final int bodyH = Math.max(190, panelH - 82);
            final int railW = Math.min(236, Math.max(200, panelW / 3));
            root.child(Flow.row()
                .childPadding(10).widthRel(1f).height(bodyH)
                .child(selectionSurface.build(railW, bodyH, Math.max(92, bodyH - 112)))
                .child(buildSummaryPanel().expanded()));

            panel.child(root);
            return panel;
        }

        private Flow buildSummaryPanel() {
            summaryArea = Flow.column()
                .childPadding(6).heightRel(1f).padding(8)
                .background(Styles.cardBg())
                .disableHoverBackground();
            rebuildSummary();
            return summaryArea;
        }

        @SuppressWarnings("unchecked")
        private void rebuildSummary() {
            if (summaryArea == null) return;
            summaryArea.removeAll();
            final NetworkEntry selected = selectedEntry();
            final int accentColor = selected == null ? Palette.TEXT_MUTED : NetworkUiKit.entryColor(selected);

            summaryArea.child(new TextWidget(IKey.str(NetworkUiKit.tr("gui.singularityme.network_tab.summary.title")))
                .color(Palette.TEXT_PRIMARY));
            summaryArea.child(NetworkUiKit.selectionBar(
                selected == null
                    ? NetworkUiKit.tr("gui.singularityme.network_tab.no_selection")
                    : displayEntry(selected),
                accentColor));
            summaryArea.child(NetworkUiKit.infoRowCompact(
                NetworkUiKit.tr("gui.singularityme.network_tab.summary.current"), displayDeviceName()));
            summaryArea.child(NetworkUiKit.infoRowCompact(
                NetworkUiKit.tr("gui.singularityme.network_tab.summary.default"), displayDefaultName()));
            summaryArea.child(NetworkUiKit.infoRowCompact(
                NetworkUiKit.tr("gui.singularityme.network_tab.summary.target"),
                selected == null ? "-" : displayName(selected)));

            if (selected != null) {
                summaryArea.child(NetworkUiKit.infoRowCompact(
                    NetworkUiKit.tr("gui.singularityme.network_terminal.info.security"),
                    NetworkUiKit.securityName(selected)));
                summaryArea.child(NetworkUiKit.infoRowCompact(
                    NetworkUiKit.tr("gui.singularityme.network_terminal.info.access"),
                    NetworkUiKit.accessName(selected)));
                summaryArea.child(NetworkUiKit.infoRowCompact(
                    NetworkUiKit.tr("gui.singularityme.network_terminal.info.default"),
                    selected.networkID != 0 && selected.networkID == defaultNetworkID
                        ? NetworkUiKit.tr("gui.singularityme.network_terminal.yes")
                        : NetworkUiKit.tr("gui.singularityme.network_terminal.no")));
            }

            summaryArea.child(statusLine(
                selected == null ? NetworkUiKit.tr("gui.singularityme.network_tab.no_selection")
                    : NetworkUiKit.deviceAssignmentHint(selected, deviceNetworkID),
                NetworkUiKit.deviceAssignmentHintColor(selected, deviceNetworkID)));
            final ButtonWidget<?> summaryActionButton = new ButtonWidget<>()
                .overlay(IKey.str(NetworkUiKit.deviceAssignmentActionText(selected, deviceNetworkID)))
                .height(Palette.ROW_H).widthRel(1f).padding(0, 8)
                .background(Styles.rowBg(NetworkUiKit.canAssignDeviceTo(selected, deviceNetworkID)
                    ? Palette.BTN_NORMAL
                    : Palette.BTN_DISABLED))
                .disableHoverBackground()
                .onMousePressed(mb -> {
                    if (!NetworkUiKit.canAssignDeviceTo(selectedEntry(), deviceNetworkID)) return false;
                    selectionSurface.performPrimaryAction();
                    return true;
                });
            summaryArea.child(summaryActionButton);
        }

        @SuppressWarnings("unchecked")
        private Flow statusLine(final String text, final int color) {
            return Flow.row()
                .childPadding(6).widthRel(1f).height(Palette.TEXT_ROW_H).padding(Palette.LIST_ROW_PADDING_H, 0)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(NetworkUiKit.statusDotWidget(color))
                .child(new TextWidget(IKey.str(text)).color(color));
        }

        @Override
        public void requestNetworkData() {
            SingularityChannel.CHANNEL.sendToServer(new PacketRequestNetworkTabData(x, y, z, dim));
        }

        void receive(final PacketNetworkTabData packet) {
            networks.clear();
            networks.addAll(packet.networks);
            deviceNetworkID = packet.deviceNetworkID;
            defaultNetworkID = packet.defaultNetworkID;
            selectedNetworkID = packet.deviceNetworkID;
            if (selectedEntry() == null && !networks.isEmpty()) {
                selectedNetworkID = networks.get(0).networkID;
            }
            if (selectionSurface != null) selectionSurface.rebuild();
            rebuildSummary();
        }

        void receiveActionResult(final PacketNetworkActionResult packet) {
            if (selectionSurface != null) selectionSurface.receiveResult(packet);
            requestNetworkData();
        }

        private NetworkEntry selectedEntry() {
            for (final NetworkEntry e : networks) {
                if (e.networkID == selectedNetworkID) return e;
            }
            return null;
        }

        private String displayDeviceName() {
            for (final NetworkEntry e : networks) {
                if (e.networkID == deviceNetworkID) return displayName(e);
            }
            return deviceNetworkID == 0 ? NetworkUiKit.tr("gui.singularityme.network_tab.default") : "#" + deviceNetworkID;
        }

        private String displayDefaultName() {
            for (final NetworkEntry e : networks) {
                if (e.networkID == defaultNetworkID) return displayName(e);
            }
            return defaultNetworkID == 0 ? NetworkUiKit.tr("gui.singularityme.network_tab.default") : "#" + defaultNetworkID;
        }

        private String displayName(final NetworkEntry entry) {
            return entry.networkID == 0 ? NetworkUiKit.tr("gui.singularityme.network_tab.default") : entry.name;
        }

        private String displayEntry(final NetworkEntry entry) {
            if (entry.networkID == 0) return displayName(entry);
            return "#" + entry.networkID + " " + displayName(entry) + " "
                + NetworkUiKit.securityShort(entry) + " " + NetworkUiKit.accessName(entry);
        }

        @Override
        public List<NetworkEntry> networks() {
            return networks;
        }

        @Override
        public int selectedNetworkID() {
            return selectedNetworkID;
        }

        @Override
        public int deviceNetworkID() {
            return deviceNetworkID;
        }

        @Override
        public int defaultNetworkID() {
            return defaultNetworkID;
        }

        @Override
        public int x() {
            return x;
        }

        @Override
        public int y() {
            return y;
        }

        @Override
        public int z() {
            return z;
        }

        @Override
        public int dim() {
            return dim;
        }

        @Override
        public void selectNetwork(final int networkID) {
            selectedNetworkID = networkID;
            if (selectionSurface != null) selectionSurface.rebuild();
            rebuildSummary();
        }

        @Override
        public void rebuildAfterSurfaceAction() {
            requestNetworkData();
            rebuildSummary();
        }
    }
}
