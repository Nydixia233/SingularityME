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
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.github.singularityme.client.ui.NetworkUiKit.Palette;
import com.github.singularityme.client.ui.NetworkUiKit.Styles;
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;
import com.github.singularityme.network.packet.PacketJoinEncryptedNetwork;
import com.github.singularityme.network.packet.PacketNetworkTabData;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;
import com.github.singularityme.network.packet.PacketRequestNetworkTabData;
import com.github.singularityme.network.packet.PacketSetDeviceNetwork;

/**
 * 设备网络分配标签页 — MUI2 重写版。
 */
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

    // ---- 内部状态机 ----

    private static final class TabState {
        final int x, y, z, dim;
        final List<NetworkEntry> networks = new ArrayList<>();
        int selectedNetworkID;
        int deviceNetworkID;
        int defaultNetworkID;
        boolean passwordMode;

        ModularPanel panel;
        ListWidget networkList;
        Flow bottomArea;
        ButtonWidget<?> selectBtn;
        ButtonWidget<?> joinBtn;
        ButtonWidget<?> cancelBtn;
        TextFieldWidget passwordField;
        StringValue passwordValue = new StringValue("");

        TabState(int x, int y, int z, int dim) {
            this.x = x; this.y = y; this.z = z; this.dim = dim;
        }

        ModularPanel buildPanel() {
            final int guiScale = Math.max(1, Minecraft.getMinecraft().gameSettings.guiScale);
            final int panelW = Math.min(480 * guiScale, 720);
            final int panelH = Math.min(300 * guiScale, 500);

            panel = new ModularPanel("network_tab")
                .size(panelW, panelH)
                .background(new ShadowDrawable(Styles.panelBg(), 5, 0x80000000));

            final Flow root = Flow.column()
                .childPadding(10)
                .widthRel(1f).heightRel(1f)
                .padding(0, 14).margin(14, 0);

            // 标题
            root.child(new TextWidget(IKey.str(NetworkUiKit.tr("gui.singularityme.network_tab.title")))
                .color(Palette.TEXT_PRIMARY));

            // 摘要行
            root.child(buildSummaryRow());

            // 列表头
            root.child(Flow.row()
                .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                .widthRel(1f).height(Palette.TEXT_ROW_H)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new TextWidget(IKey.str(
                    NetworkUiKit.trf("gui.singularityme.network_tab.sort_by",
                        NetworkUiKit.tr("gui.singularityme.network_tab.name"))))
                    .color(Palette.TEXT_MUTED))
                .child(new TextWidget(IKey.str(
                    NetworkUiKit.trf("gui.singularityme.network_tab.total", "0")))
                    .color(Palette.TEXT_MUTED)));

            // 网络列表
            networkList = new ListWidget();
            networkList.background(Styles.listBg());
            networkList.widthRel(1f);
            networkList.expanded();
            root.child(networkList);

            // 按钮
            selectBtn = makeBtn(NetworkUiKit.tr("gui.singularityme.network_tab.select"),
                180, Palette.BTN_NORMAL, this::onSelect);
            joinBtn = makeBtn(NetworkUiKit.tr("gui.singularityme.network_tab.join"),
                110, Palette.BTN_NORMAL, this::onJoin);
            cancelBtn = makeBtn(NetworkUiKit.tr("gui.singularityme.network_tab.cancel"),
                110, Palette.BTN_DANGER_NORMAL, this::onCancel);

            passwordField = new TextFieldWidget()
                .value(passwordValue)
                .widthRel(1f).height(36)
                .background(Styles.inputBg())
                .autoUpdateOnChange(true);

            bottomArea = Flow.column().childPadding(8).widthRel(1f);
            root.child(bottomArea);

            panel.child(root);
            return panel;
        }

        private Flow buildSummaryRow() {
            return Flow.row()
                .childPadding(10)
                .widthRel(1f).height(56)
                .child(summaryBox(NetworkUiKit.tr("gui.singularityme.network_tab.device"), true))
                .child(summaryBox(NetworkUiKit.tr("gui.singularityme.network_tab.default_network"), false));
        }

        private Flow summaryBox(String label, boolean isDevice) {
            return Flow.column()
                .expanded().heightRel(1f).padding(0, 10)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .background(Styles.cardBg())
                .child(new TextWidget(IKey.str(label)).color(Palette.TEXT_MUTED))
                .child(new TextWidget(IKey.dynamicKey(() ->
                    IKey.str(isDevice ? displayDeviceName() : displayDefaultName())))
                    .color(Palette.TEXT_PRIMARY));
        }

        private String displayDeviceName() {
            for (final NetworkEntry e : networks) {
                if (e.networkID == deviceNetworkID) return e.name;
            }
            return deviceNetworkID == 0 ? NetworkUiKit.tr("gui.singularityme.network_tab.default") : "#" + deviceNetworkID;
        }

        private String displayDefaultName() {
            for (final NetworkEntry e : networks) {
                if (e.networkID == defaultNetworkID) return e.name;
            }
            return defaultNetworkID == 0 ? NetworkUiKit.tr("gui.singularityme.network_tab.default") : "#" + defaultNetworkID;
        }

        // ---- 按钮工厂 ----

        private static ButtonWidget<?> makeBtn(String text, int w, int bg, Runnable action) {
            return new ButtonWidget<>()
                .overlay(IKey.str(text))
                .width(w).height(36)
                .background(Styles.rowBg(bg))
                .onMousePressed(mb -> { action.run(); return true; });
        }

        // ---- 交互 ----

        void onSelect() {
            final NetworkEntry sel = selectedEntry();
            if (sel == null) return;
            if (NetworkUiKit.isEncryptedJoinRequired(sel)) {
                passwordMode = true;
                passwordValue.setStringValue("");
                rebuildBottom();
                return;
            }
            SingularityChannel.CHANNEL.sendToServer(
                new PacketSetDeviceNetwork(x, y, z, dim, sel.networkID));
        }

        void onJoin() {
            final NetworkEntry sel = selectedEntry();
            if (sel == null) return;
            final String pw = passwordValue.getStringValue();
            if (pw.isEmpty()) return;
            SingularityChannel.CHANNEL.sendToServer(
                new PacketJoinEncryptedNetwork(x, y, z, dim, sel.networkID,
                    SingularityNetworkRegistry.sha256Hex(pw)));
            passwordValue.setStringValue("");
            passwordMode = false;
            rebuildBottom();
        }

        void onCancel() {
            passwordMode = false;
            passwordValue.setStringValue("");
            rebuildBottom();
        }

        // ---- 数据刷新 ----

        void requestNetworkData() {
            SingularityChannel.CHANNEL.sendToServer(
                new PacketRequestNetworkTabData(x, y, z, dim));
        }

        void receive(final PacketNetworkTabData packet) {
            networks.clear();
            networks.addAll(packet.networks);
            deviceNetworkID = packet.deviceNetworkID;
            defaultNetworkID = packet.defaultNetworkID;
            // 仅在非密码模式时重置密码状态，避免用户输入中被清空
            if (!passwordMode) {
                passwordValue.setStringValue("");
            }
            selectedNetworkID = packet.deviceNetworkID;
            if (selectedEntry() == null && !networks.isEmpty()) {
                selectedNetworkID = networks.get(0).networkID;
            }
            rebuildAll();
        }

        // ---- 重建 ----

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void rebuildAll() {
            networkList.removeAll();
            for (final NetworkEntry entry : networks) {
                networkList.child(buildRow(entry));
            }
            networkList.scheduleResize();
            rebuildBottom();
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void rebuildList() {
            networkList.removeAll();
            for (final NetworkEntry entry : networks) {
                networkList.child(buildRow(entry));
            }
            networkList.scheduleResize();
        }

        @SuppressWarnings("unchecked")
        void rebuildBottom() {
            bottomArea.removeAll();

            final NetworkEntry sel = selectedEntry();
            final String barText = sel == null
                ? NetworkUiKit.tr("gui.singularityme.network_tab.no_selection")
                : NetworkUiKit.trf("gui.singularityme.network_tab.selected", displayEntry(sel));

            bottomArea.child(Flow.row()
                .height(38).widthRel(1f).padding(0, 8)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .background(Styles.cardBg())
                .child(new TextWidget(IKey.str(barText))
                    .color(Palette.TEXT_SECONDARY)));

            if (passwordMode) {
                bottomArea.child(Flow.row()
                    .childPadding(6).widthRel(1f)
                    .child(passwordField)
                    .child(joinBtn)
                    .child(cancelBtn));
            } else {
                final boolean canAssign = sel != null
                    && sel.networkID != deviceNetworkID
                    && !NetworkUiKit.isBlocked(sel)
                    && (sel.networkID == 0 || NetworkUiKit.canAccess(sel)
                        || NetworkUiKit.isEncryptedJoinRequired(sel));
                selectBtn.setEnabled(canAssign);
                bottomArea.child(Flow.row().childPadding(6).widthRel(1f).child(selectBtn));
            }
        }

        // ---- 行构建 ----

        private ButtonWidget<?> buildRow(final NetworkEntry entry) {
            final boolean selected = entry.networkID == selectedNetworkID;
            final boolean current = entry.networkID == deviceNetworkID;
            final boolean isDefault = entry.networkID != 0 && entry.networkID == defaultNetworkID;
            final int color = NetworkUiKit.entryColor(entry);
            final int bg = selected ? NetworkUiKit.darken(color, 0.32f) : Palette.BG_ROW;
            final String name = entry.networkID == 0
                ? NetworkUiKit.tr("gui.singularityme.network_tab.default") : entry.name;

            final TextWidget nameWidget = new TextWidget(IKey.str(name))
                .color(selected ? 0xFFFFFFFF : Palette.TEXT_SECONDARY);
            nameWidget.expanded();

            final Flow rowContent = Flow.row()
                .childPadding(8).widthRel(1f).height(Palette.ROW_H).padding(0, 8)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new TextWidget(IKey.str("\u25A0")).color(color))
                .child(new TextWidget(IKey.str(entry.networkID == 0 ? "-" : "#" + entry.networkID))
                    .color(Palette.TEXT_MUTED))
                .child(nameWidget);
            rowContent.child(new TextWidget(IKey.str(NetworkUiKit.securityShort(entry)))
                .color(NetworkUiKit.securityColor(entry)));
            rowContent.child(new TextWidget(IKey.str(NetworkUiKit.accessShort(entry)))
                .color(NetworkUiKit.accessColor(entry)));
            if (current) rowContent.child(new TextWidget(IKey.str("*")).color(Palette.BADGE_CURRENT));
            if (isDefault) rowContent.child(new TextWidget(IKey.str("D")).color(Palette.BADGE_DEFAULT));

            return new ButtonWidget<>()
                .child(rowContent)
                .widthRel(1f).height(Palette.ROW_H)
                .background(Styles.rowBg(bg))
                .disableHoverBackground()
                .onMousePressed(mb -> {
                    selectedNetworkID = entry.networkID;
                    passwordMode = false;
                    rebuildList();
                    rebuildBottom();
                    return true;
                });
        }

        // ---- 工具 ----

        private NetworkEntry selectedEntry() {
            for (final NetworkEntry e : networks) {
                if (e.networkID == selectedNetworkID) return e;
            }
            return null;
        }

        private String displayEntry(NetworkEntry e) {
            if (e == null) return "-";
            return "#" + e.networkID + " " + e.name + " "
                + NetworkUiKit.securityName(e) + " " + NetworkUiKit.accessMark(e);
        }
    }
}
