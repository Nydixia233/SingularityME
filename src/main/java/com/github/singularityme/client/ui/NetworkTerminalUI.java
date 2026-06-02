package com.github.singularityme.client.ui;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
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
import com.github.singularityme.core.AccessLevel;
import com.github.singularityme.core.SecurityLevel;
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;
import com.github.singularityme.network.packet.PacketAddMemberByName;
import com.github.singularityme.network.packet.PacketCreateNetwork;
import com.github.singularityme.network.packet.PacketDeleteNetwork;
import com.github.singularityme.network.packet.PacketNetworkStatus;
import com.github.singularityme.network.packet.PacketNetworkStatus.DeviceInfo;
import com.github.singularityme.network.packet.PacketNetworkTabData;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;
import com.github.singularityme.network.packet.PacketRenameNetwork;
import com.github.singularityme.network.packet.PacketRequestNetworkStatus;
import com.github.singularityme.network.packet.PacketRequestNetworkTabData;
import com.github.singularityme.network.packet.PacketSetDefaultNetwork;
import com.github.singularityme.network.packet.PacketSetMemberRole;
import com.github.singularityme.network.packet.PacketSetNetworkSettings;

/**
 * 8 面板网络终端 — MUI2 重写版。
 */
public final class NetworkTerminalUI {

    private static WeakReference<TerminalState> activeState;

    private NetworkTerminalUI() {}

    public static GuiScreen create(final TileEntity te) {
        final int x = te == null ? 0 : te.xCoord;
        final int y = te == null ? 0 : te.yCoord;
        final int z = te == null ? 0 : te.zCoord;
        final int dim = te != null && te.getWorldObj() != null && te.getWorldObj().provider != null
            ? te.getWorldObj().provider.dimensionId : 0;

        final ModularScreen screen = new ModularScreen("singularityme", (ModularGuiContext ctx) -> {
            final TerminalState state = new TerminalState(x, y, z, dim);
            activeState = new WeakReference<>(state);
            return state.buildPanel();
        });
        screen.getContext().setSettings(new UISettings());
        final GuiScreenWrapper wrapper = new GuiScreenWrapper(screen);

        Minecraft.getMinecraft().func_152344_a(() -> {
            final TerminalState state = activeState == null ? null : activeState.get();
            if (state != null) state.requestNetworkData();
        });
        return wrapper;
    }

    public static boolean receiveNetworkData(final PacketNetworkTabData packet) {
        final TerminalState state = activeState == null ? null : activeState.get();
        if (state == null) return false;
        if (Minecraft.getMinecraft().currentScreen instanceof GuiScreenWrapper w
            && w.getScreen().isPanelOpen("network_terminal")) {
            state.receive(packet);
            return true;
        }
        return false;
    }

    public static boolean receiveNetworkStatus(final PacketNetworkStatus packet) {
        final TerminalState state = activeState == null ? null : activeState.get();
        if (state == null) return false;
        if (Minecraft.getMinecraft().currentScreen instanceof GuiScreenWrapper w
            && w.getScreen().isPanelOpen("network_terminal")) {
            state.receiveStatus(packet);
            return true;
        }
        return false;
    }

    private enum Panel { HOME, SELECTION, CONNECTION, MEMBERS, STATISTICS, SETTINGS, HEALTH, CREATE }

    private static final class TerminalState {
        final int x, y, z, dim;
        final List<NetworkEntry> networks = new ArrayList<>();
        int selectedNetworkID;
        int selectedMemberID = -1;
        int defaultNetworkID;
        Panel currentPanel = Panel.SELECTION;
        int selectedColor = 0x4A90E2;
        SecurityLevel selectedSecurity = SecurityLevel.PRIVATE;
        PacketNetworkStatus networkStatus;
        /** 当前面板是否首次渲染（用于区分主动切换 vs 数据刷新触发） */
        boolean panelFirstRender = true;

        ModularPanel panel;
        Flow navBar;
        Flow networkBar;
        Flow contentArea;
        Flow bottomArea;

        TextFieldWidget memberNameInput;
        StringValue memberNameVal = new StringValue("");
        TextFieldWidget filterInput;
        StringValue filterVal = new StringValue("");

        TextFieldWidget createNameInput;
        TextFieldWidget createPasswordInput;
        StringValue createNameVal = new StringValue("");
        StringValue createPwVal = new StringValue("");

        TextFieldWidget settingsNameInput;
        TextFieldWidget settingsPasswordInput;
        StringValue settingsNameVal = new StringValue("");
        StringValue settingsPwVal = new StringValue("");

        TerminalState(int x, int y, int z, int dim) {
            this.x = x; this.y = y; this.z = z; this.dim = dim;
        }

        ModularPanel buildPanel() {
            final int guiScale = Math.max(1, Minecraft.getMinecraft().gameSettings.guiScale);
            final int panelW = Math.min(760, (int) (Minecraft.getMinecraft().displayWidth * 0.92f / guiScale));
            final int panelH = Math.min(520, (int) (Minecraft.getMinecraft().displayHeight * 0.92f / guiScale));

            panel = new ModularPanel("network_terminal")
                .size(panelW, panelH)
                .background(new ShadowDrawable(Styles.panelBg(), 6, 0x80000000));

            final Flow root = Flow.column().widthRel(1f).heightRel(1f);

            // 导航栏
            navBar = Flow.row()
                .childPadding(4).widthRel(1f).coverChildrenHeight()
                .padding(4).margin(8)
                .background(Styles.headerGradient(Palette.BG_LIST));
            buildNavButtons();
            root.child(navBar);

            // 网络信息栏
            networkBar = Flow.row()
                .childPadding(8).widthRel(1f)
                .height(Palette.ROW_H).padding(0, 12)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER);
            root.child(networkBar);

            // 内容区
            contentArea = Flow.column().widthRel(1f).expanded();
            root.child(contentArea);

            // 底部操作区
            bottomArea = Flow.column()
                .childPadding(8).widthRel(1f).coverChildrenHeight()
                .padding(0, 12).margin(10, 0);
            root.child(bottomArea);

            // 输入控件
            memberNameInput = makeInput(memberNameVal);
            filterInput = makeInput(filterVal);
            createNameInput = makeInput(createNameVal);
            createPasswordInput = makeInput(createPwVal);
            settingsNameInput = makeInput(settingsNameVal);
            settingsPasswordInput = makeInput(settingsPwVal);

            panel.child(root);
            return panel;
        }

        // ---- 导航按钮 ----

        @SuppressWarnings("unchecked")
        void buildNavButtons() {
            navBar.removeAll();
            for (Panel p : Panel.values()) {
                final boolean active = p == currentPanel;
                navBar.child(makeNavBtn(panelTitle(p), active, () -> {
                    if (currentPanel != p) {
                        currentPanel = p;
                        panelFirstRender = true;
                        buildNavButtons();
                        if (isStatusPanel(p) && selectedEntry() != null) {
                            requestNetworkStatus();
                        }
                        renderContent();
                    }
                }));
            }
        }

        private static ButtonWidget<?> makeNavBtn(String text, boolean active, Runnable action) {
            return new ButtonWidget<>()
                .overlay(IKey.str(text))
                .height(30).padding(0, 10)
                .background(active ? Styles.rowBg(Palette.BG_ROW) : IDrawable.NONE)
                .disableHoverBackground()
                .onMousePressed(mb -> { action.run(); return true; });
        }

        // ---- 内容渲染 ----

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void renderContent() {
            contentArea.removeAll();
            updateNetworkBar();
            switch (currentPanel) {
                case HOME -> renderHome();
                case SELECTION -> renderSelection();
                case CONNECTION -> renderConnection();
                case MEMBERS -> renderMembers();
                case STATISTICS -> renderStatistics();
                case SETTINGS -> renderSettings();
                case HEALTH -> renderHealth();
                case CREATE -> renderCreate();
            }
            panelFirstRender = false;
        }

        // ---- 网络信息栏 ----

        @SuppressWarnings("unchecked")
        void updateNetworkBar() {
            networkBar.removeAll();
            networkBar.child(new TextWidget(IKey.str(panelTitle(currentPanel)))
                .color(Palette.TEXT_PRIMARY));

            final NetworkEntry sel = selectedEntry();
            if (sel != null) {
                final int c = NetworkUiKit.entryColor(sel);
                networkBar.child(NetworkUiKit.statusDotWidget(c));
                networkBar.child(new TextWidget(IKey.str(sel.name)).color(c));
                if (sel.networkID != 0) {
                    networkBar.child(NetworkUiKit.idPill(sel.networkID));
                }
                if (sel.networkID != 0 && sel.networkID == defaultNetworkID) {
                    networkBar.child(NetworkUiKit.defaultBadge());
                }
            }
        }

        // ---- HOME ----

        @SuppressWarnings("unchecked")
        void renderHome() {
            bottomArea.removeAll();
            final NetworkEntry sel = selectedEntry();
            if (sel == null) { contentArea.child(emptyState()); return; }

            final Flow rows = Flow.column().childPadding(2).widthRel(1f).coverChildrenHeight().padding(0, 12);
            final Flow lineA = Flow.row().childPadding(4).widthRel(1f).height(Palette.ROW_H);
            lineA.child(infoRow("ID", "#" + sel.networkID));
            lineA.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.info.name"), sel.name));
            rows.child(lineA);

            final Flow lineB = Flow.row().childPadding(4).widthRel(1f).height(Palette.ROW_H);
            lineB.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.info.owner"), sel.ownerName));
            lineB.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.info.security"),
                NetworkUiKit.securityName(sel)));
            rows.child(lineB);

            final Flow lineC = Flow.row().childPadding(4).widthRel(1f).height(Palette.ROW_H);
            lineC.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.info.access"),
                NetworkUiKit.accessName(sel)));
            lineC.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.home.members"),
                String.valueOf(sel.adminPlayerIDs.size() + sel.memberPlayerIDs.size() + 1)));
            rows.child(lineC);

            if (networkStatus == null) {
                rows.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.home.loading"), "-"));
            } else {
                final int devices = networkStatus.devices.size();
                final int online = countLoadedDevices();
                final Flow lineD = Flow.row().childPadding(4).widthRel(1f).height(Palette.ROW_H);
                lineD.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.home.devices"),
                    devices + " / " + online));
                lineD.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.home.energy"),
                    formatEnergy(networkStatus.currentPower, networkStatus.maxPower)));
                rows.child(lineD);
            }

            final Flow lineE = Flow.row().childPadding(4).widthRel(1f).height(Palette.ROW_H);
            lineE.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.home.created"),
                formatTimestamp(sel.createdAtMillis)));
            lineE.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.home.modified"),
                formatTimestamp(sel.lastModifiedMillis)));
            rows.child(lineE);
            contentArea.child(rows);
        }

        // ---- SELECTION ----

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void renderSelection() {
            bottomArea.removeAll();

            contentArea.child(Flow.row()
                .childPadding(8).widthRel(1f).height(Palette.ROW_H).padding(0, 12)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new TextWidget(IKey.str(NetworkUiKit.tr("gui.singularityme.network_terminal.selection.filter")))
                    .color(Palette.TEXT_LABEL))
                .child(filterInput.widthRel(1f).expanded()));

            contentArea.child(Flow.row()
                .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                .widthRel(1f).height(Palette.TEXT_ROW_H).padding(0, 12)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new TextWidget(IKey.str(
                    NetworkUiKit.trf("gui.singularityme.network_tab.sort_by",
                        NetworkUiKit.tr("gui.singularityme.network_tab.name"))))
                    .color(Palette.TEXT_MUTED))
                .child(new TextWidget(IKey.str(
                    NetworkUiKit.trf("gui.singularityme.network_tab.total", visibleNetworkCount())))
                    .color(Palette.TEXT_MUTED)));

            final ListWidget list = new ListWidget();
            list.background(Styles.listBg());
            list.widthRel(1f);
            list.expanded();
            for (final NetworkEntry entry : networks) {
                if (matchesFilter(entry)) {
                    list.child(buildSelectionRow(entry));
                }
            }
            contentArea.child(list);

            final NetworkEntry sel = selectedEntry();
            final String barText = sel == null
                ? NetworkUiKit.tr("gui.singularityme.network_tab.no_selection")
                : NetworkUiKit.trf("gui.singularityme.network_tab.selected",
                    "#" + sel.networkID + " " + sel.name + " "
                        + NetworkUiKit.securityName(sel) + " " + NetworkUiKit.accessMark(sel));
            final int accentColor = sel == null ? Palette.TEXT_MUTED : 0xFF000000 | sel.color;
            contentArea.child(NetworkUiKit.selectionBar(barText, accentColor).margin(12, 0));

            final boolean isDefault = sel != null && sel.networkID != 0 && sel.networkID == defaultNetworkID;
            final String btnText = isDefault
                ? NetworkUiKit.tr("gui.singularityme.network_terminal.selection.clear_default")
                : NetworkUiKit.tr("gui.singularityme.network_terminal.selection.set_default");
            final boolean canSet = sel != null && sel.networkID != 0 && NetworkUiKit.canAccess(sel);

            bottomArea.child(Flow.row().childPadding(8).widthRel(1f).height(Palette.ROW_H)
                .child(makeBtn(btnText, 180, () -> {
                    if (sel == null) return;
                    final int nd = isDefault ? 0 : sel.networkID;
                    SingularityChannel.CHANNEL.sendToServer(new PacketSetDefaultNetwork(nd));
                    defaultNetworkID = nd;
                    renderContent();
                }, canSet)));
        }

        private ButtonWidget<?> buildSelectionRow(final NetworkEntry entry) {
            final boolean sel = entry.networkID == selectedNetworkID;
            final boolean def = entry.networkID != 0 && entry.networkID == defaultNetworkID;
            final int c = NetworkUiKit.entryColor(entry);
            final int bg = sel ? NetworkUiKit.selectedRowColor(c) : Palette.BG_ROW;
            final String name = entry.networkID == 0
                ? NetworkUiKit.tr("gui.singularityme.network_tab.default") : entry.name;

            final TextWidget nameWidget = new TextWidget(IKey.str(name))
                .color(sel ? 0xFFFFFFFF : Palette.TEXT_SECONDARY);
            nameWidget.expanded();

            final Flow rowContent = Flow.row()
                .childPadding(8).widthRel(1f).height(Palette.ROW_H).padding(0, 8)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(NetworkUiKit.statusDotWidget(c));
            if (entry.networkID != 0) {
                rowContent.child(NetworkUiKit.idPill(entry.networkID));
            } else {
                rowContent.child(new TextWidget(IKey.str("-")).color(Palette.TEXT_MUTED));
            }
            rowContent.child(nameWidget);
            rowContent.child(NetworkUiKit.securityBadge(entry));
            rowContent.child(NetworkUiKit.accessBadge(entry));
            if (def) rowContent.child(NetworkUiKit.defaultBadge());

            return new ButtonWidget<>()
                .child(rowContent)
                .widthRel(1f).height(Palette.ROW_H)
                .background(Styles.rowBg(bg))
                .disableHoverBackground()
                .onMousePressed(mb -> {
                    selectedNetworkID = entry.networkID;
                    selectedMemberID = -1;
                    networkStatus = null;
                    if (isStatusPanel(currentPanel)) {
                        requestNetworkStatus();
                    }
                    renderContent();
                    return true;
                });
        }

        // ---- CONNECTION ----

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void renderConnection() {
            bottomArea.removeAll();
            if (selectedEntry() == null) { contentArea.child(emptyState()); return; }
            if (networkStatus == null) {
                contentArea.child(statusText(NetworkUiKit.tr("gui.singularityme.network_terminal.home.loading")));
                return;
            }
            if (networkStatus.devices.isEmpty()) { contentArea.child(emptyState()); return; }

            final ListWidget list = new ListWidget();
            list.background(Styles.listBg());
            list.widthRel(1f);
            list.expanded();
            for (final DeviceInfo device : networkStatus.devices) {
                list.child(buildDeviceRow(device));
            }
            contentArea.child(list);
        }

        private Flow buildDeviceRow(final DeviceInfo device) {
            final int baseColor = NetworkUiKit.deviceTypeColor(device.type);
            final int color = device.loaded ? baseColor : NetworkUiKit.darken(baseColor, 0.45f);
            final TextWidget type = new TextWidget(IKey.str(NetworkUiKit.deviceTypeLabel(device.type)))
                .color(color);
            type.expanded();

            return Flow.row()
                .childPadding(8).widthRel(1f).height(Palette.ROW_H).padding(0, 8)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .background(Styles.rowBg(Palette.BG_ROW))
                .child(new TextWidget(IKey.str("\u25A0")).color(color))
                .child(type)
                .child(new TextWidget(IKey.str(formatLocation(device))).color(Palette.TEXT_MUTED))
                .child(new TextWidget(IKey.str(NetworkUiKit.tr(device.loaded
                    ? "gui.singularityme.network_terminal.conn.online"
                    : "gui.singularityme.network_terminal.conn.offline")))
                    .color(device.loaded ? Palette.SECURITY_PUBLIC : Palette.BTN_DANGER_NORMAL));
        }

        // ---- MEMBERS ----

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void renderMembers() {
            bottomArea.removeAll();
            final NetworkEntry sel = selectedEntry();
            if (sel == null) { contentArea.child(emptyState()); return; }

            final ListWidget list = new ListWidget();
            list.background(Styles.listBg());
            list.widthRel(1f);
            list.expanded();

            list.child(buildMemberRow(sel.ownerPlayerID, sel.ownerName, AccessLevel.OWNER, false));
            for (int i = 0; i < sel.adminPlayerIDs.size(); i++)
                list.child(buildMemberRow(sel.adminPlayerIDs.get(i),
                    i < sel.adminNames.size() ? sel.adminNames.get(i) : "#" + sel.adminPlayerIDs.get(i),
                    AccessLevel.ADMIN, true));
            for (int i = 0; i < sel.memberPlayerIDs.size(); i++)
                list.child(buildMemberRow(sel.memberPlayerIDs.get(i),
                    i < sel.memberNames.size() ? sel.memberNames.get(i) : "#" + sel.memberPlayerIDs.get(i),
                    AccessLevel.MEMBER, true));
            for (int i = 0; i < sel.blockedPlayerIDs.size(); i++)
                list.child(buildMemberRow(sel.blockedPlayerIDs.get(i),
                    i < sel.blockedNames.size() ? sel.blockedNames.get(i) : "#" + sel.blockedPlayerIDs.get(i),
                    AccessLevel.BLOCKED, true));
            if (sel.adminPlayerIDs.isEmpty() && sel.memberPlayerIDs.isEmpty() && sel.blockedPlayerIDs.isEmpty()) {
                list.child(statusText(NetworkUiKit.tr("gui.singularityme.network_terminal.members.empty")));
            }
            contentArea.child(list);

            contentArea.child(Flow.row()
                .childPadding(8).widthRel(1f).padding(0, 12).margin(6, 0)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(memberNameInput.widthRel(1f).expanded())
                .child(makeBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.members.add"),
                    120, this::addMember)));

            if (sel.isOwner && selectedMemberID >= 0) {
                bottomArea.child(Flow.row().childPadding(8).widthRel(1f).height(Palette.ROW_H)
                    .child(makeBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.members.promote"),
                        140, () -> setMemberRole(sel.networkID, selectedMemberID, AccessLevel.ADMIN)))
                    .child(makeDangerBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.members.demote"),
                        140, () -> setMemberRole(sel.networkID, selectedMemberID, AccessLevel.MEMBER)))
                    .child(makeDangerBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.members.remove"),
                        140, () -> setMemberRole(sel.networkID, selectedMemberID, AccessLevel.NONE))));
            }
        }

        private ButtonWidget<?> buildMemberRow(int pid, String pname, AccessLevel role, boolean clickable) {
            final boolean sel = pid == selectedMemberID;
            final int rc = NetworkUiKit.accessColor(role);
            final int bg = sel ? NetworkUiKit.darken(rc, 0.25f) : Palette.BG_ROW;

            final TextWidget memberNameW = new TextWidget(IKey.str(pname))
                .color(Palette.TEXT_PRIMARY);
            memberNameW.expanded();

            final Flow rowContent = Flow.row()
                .childPadding(8).widthRel(1f).height(Palette.ROW_H).padding(0, 8)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(NetworkUiKit.badge(NetworkUiKit.roleName(role), rc))
                .child(memberNameW)
                .child(NetworkUiKit.idPill(pid));

            final ButtonWidget<?> row = new ButtonWidget<>()
                .child(rowContent)
                .widthRel(1f).height(Palette.ROW_H)
                .background(Styles.rowBg(bg))
                .disableHoverBackground();
            if (clickable) {
                row.onMousePressed(mb -> { selectedMemberID = pid; renderContent(); return true; });
            }
            return row;
        }

        void addMember() {
            final String name = memberNameVal.getStringValue();
            if (name.isEmpty()) return;
            final NetworkEntry sel = selectedEntry();
            if (sel == null) return;
            SingularityChannel.CHANNEL.sendToServer(new PacketAddMemberByName(sel.networkID, name));
            memberNameVal.setStringValue("");
        }

        void setMemberRole(int nid, int pid, AccessLevel role) {
            SingularityChannel.CHANNEL.sendToServer(new PacketSetMemberRole(nid, pid, role.ordinal()));
        }

        // ---- SETTINGS ----

        @SuppressWarnings("unchecked")
        void renderSettings() {
            bottomArea.removeAll();
            final NetworkEntry sel = selectedEntry();
            if (sel == null) { contentArea.child(emptyState()); return; }

            // 首次进入 SETTINGS 面板时从已选网络读取真实值
            if (panelFirstRender) {
                selectedColor = sel.color;
                selectedSecurity = SecurityLevel.fromOrdinal(sel.securityOrdinal);
            }

            // 名称只在首次渲染时重置，避免数据刷新覆盖用户编辑
            if (panelFirstRender) {
                settingsNameVal.setStringValue(sel.name);
            }
            contentArea.child(formRow(
                NetworkUiKit.tr("gui.singularityme.network_terminal.settings.name"), settingsNameInput));

            if (panelFirstRender) {
                settingsPwVal.setStringValue("");
            }
            contentArea.child(formRow(
                NetworkUiKit.tr("gui.singularityme.network_terminal.settings.password"), settingsPasswordInput));

            contentArea.child(formRow(
                NetworkUiKit.tr("gui.singularityme.network_terminal.settings.security"),
                NetworkUiKit.securitySegmentRow(selectedSecurity, level -> {
                    selectedSecurity = level;
                    renderContent();
                })));

            contentArea.child(formRow(
                NetworkUiKit.tr("gui.singularityme.network_terminal.settings.color"),
                NetworkUiKit.colorReadonly(selectedColor)));
            contentArea.child(colorSwatchRow());

            bottomArea.child(Flow.row().childPadding(8).widthRel(1f).height(Palette.ROW_H)
                .child(makeBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.settings.apply"),
                    140, this::applySettings)));

            if (sel.isOwner) {
                bottomArea.child(Flow.row().childPadding(8).widthRel(1f).height(Palette.ROW_H)
                    .child(makeDangerBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.settings.delete"),
                        140, () -> showDeleteConfirm(sel))));
            }
        }

        void applySettings() {
            final NetworkEntry sel = selectedEntry();
            if (sel == null) return;
            final String name = settingsNameVal.getStringValue();
            final String pw = settingsPwVal.getStringValue();
            if (!name.isEmpty() && !name.equals(sel.name)) {
                SingularityChannel.CHANNEL.sendToServer(new PacketRenameNetwork(sel.networkID, name));
            }
            SingularityChannel.CHANNEL.sendToServer(
                new PacketSetNetworkSettings(sel.networkID, selectedColor,
                    selectedSecurity.ordinal(),
                    pw.isEmpty() ? "" : SingularityNetworkRegistry.sha256Hex(pw)));
        }

        // ---- STATISTICS ----

        @SuppressWarnings("unchecked")
        void renderStatistics() {
            bottomArea.removeAll();
            if (selectedEntry() == null) { contentArea.child(emptyState()); return; }
            if (networkStatus == null) {
                contentArea.child(statusText(NetworkUiKit.tr("gui.singularityme.network_terminal.home.loading")));
                return;
            }
            if (networkStatus.devices.isEmpty()) { contentArea.child(emptyState()); return; }

            contentArea.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.stat.energy"),
                formatEnergy(networkStatus.currentPower, networkStatus.maxPower)));
            contentArea.child(progressBar(energyFraction(), Palette.SECURITY_ENCRYPTED));

            contentArea.child(statusText(NetworkUiKit.tr("gui.singularityme.network_terminal.stat.device_counts")));
            final Map<String, Integer> counts = new LinkedHashMap<>();
            for (final DeviceInfo device : networkStatus.devices) {
                counts.put(device.type, counts.getOrDefault(device.type, 0) + 1);
            }
            final Flow rows = Flow.column().childPadding(4).widthRel(1f).padding(0, 12);
            for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
                rows.child(infoRow(
                    NetworkUiKit.deviceTypeLabel(entry.getKey()),
                    NetworkUiKit.trf("gui.singularityme.network_terminal.stat.count_row", entry.getValue())));
            }
            contentArea.child(rows);
        }

        // ---- HEALTH ----

        @SuppressWarnings("unchecked")
        void renderHealth() {
            bottomArea.removeAll();
            if (selectedEntry() == null) { contentArea.child(emptyState()); return; }
            if (networkStatus == null) {
                contentArea.child(statusText(NetworkUiKit.tr("gui.singularityme.network_terminal.home.loading")));
                return;
            }

            final int total = networkStatus.devices.size();
            final int loaded = countLoadedDevices();
            final int offline = total - loaded;
            final float onlineRate = total <= 0 ? 0f : (float) loaded / (float) total;
            contentArea.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.health.online_rate"),
                loaded + "/" + total));
            contentArea.child(progressBar(onlineRate, Palette.SECURITY_PUBLIC));

            final boolean powered = networkStatus.maxPower > 0.0 && networkStatus.currentPower > 0.0;
            final int powerColor = powered ? Palette.SECURITY_PUBLIC : Palette.BTN_DANGER_NORMAL;
            contentArea.child(Flow.row()
                .childPadding(8).widthRel(1f).height(Palette.ROW_H).padding(0, 12)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new TextWidget(IKey.str("\u25A0")).color(powerColor))
                .child(new TextWidget(IKey.str(NetworkUiKit.tr("gui.singularityme.network_terminal.health.power")))
                    .color(Palette.TEXT_LABEL))
                .child(new TextWidget(IKey.str(NetworkUiKit.tr(powered
                    ? "gui.singularityme.network_terminal.health.powered"
                    : "gui.singularityme.network_terminal.health.unpowered")))
                    .color(powerColor)));

            final Flow warnings = Flow.column().childPadding(4).widthRel(1f).padding(0, 12);
            boolean hasWarning = false;
            if (!hasDeviceType("TileSingularityPowerCore")) {
                warnings.child(statusText(
                    NetworkUiKit.tr("gui.singularityme.network_terminal.health.warn.no_power_core"),
                    Palette.BTN_DANGER_NORMAL));
                hasWarning = true;
            }
            if (total > 0 && loaded == 0) {
                warnings.child(statusText(
                    NetworkUiKit.tr("gui.singularityme.network_terminal.health.warn.all_offline"),
                    Palette.BTN_DANGER_NORMAL));
                hasWarning = true;
            } else if (offline > 0) {
                warnings.child(statusText(
                    NetworkUiKit.trf("gui.singularityme.network_terminal.health.warn.some_offline", offline),
                    Palette.SECURITY_ENCRYPTED));
                hasWarning = true;
            }
            if (!hasWarning) {
                warnings.child(statusText(
                    NetworkUiKit.tr("gui.singularityme.network_terminal.health.healthy"),
                    Palette.SECURITY_PUBLIC));
            }
            contentArea.child(warnings);
        }

        private Flow infoRow(String label, String value) {
            return NetworkUiKit.infoRowFixed(label, value);
        }

        private Flow statusText(final String text) {
            return statusText(text, Palette.TEXT_MUTED);
        }

        private Flow statusText(final String text, final int color) {
            return Flow.row().widthRel(1f).height(Palette.TEXT_ROW_H).padding(0, 12)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new TextWidget(IKey.str(text)).color(color));
        }

        private Flow progressBar(final float fraction, final int color) {
            final float clamped = Math.max(0f, Math.min(1f, fraction));
            final Flow track = Flow.row()
                .widthRel(1f).height(14).margin(4, 12)
                .background(Styles.listBg());
            final Flow fill = Flow.row()
                .widthRel(clamped).heightRel(1f)
                .background(Styles.rowBg(color));
            track.child(fill);
            return track;
        }

        private static final int[] COLOR_PRESETS = {
            0x4A90E2, 0xE24A4A, 0x4AE24A, 0xE2E24A,
            0xE24AE2, 0x4AE2E2, 0xE28E4A, 0xFFFFFF
        };

        private Flow colorSwatchRow() {
            return NetworkUiKit.colorSwatchRow(COLOR_PRESETS, selectedColor, c -> {
                selectedColor = c;
                renderContent();
            });
        }

        private void showDeleteConfirm(final NetworkEntry entry) {
            bottomArea.removeAll();
            bottomArea.child(statusText(
                NetworkUiKit.trf("gui.singularityme.network_terminal.confirm.delete_body", entry.name),
                Palette.BTN_DANGER_NORMAL));
            bottomArea.child(statusText(
                NetworkUiKit.tr("gui.singularityme.network_terminal.confirm.delete_warning"),
                Palette.TEXT_MUTED));
            bottomArea.child(Flow.row().childPadding(8).widthRel(1f).height(Palette.ROW_H)
                .child(makeDangerBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.confirm.yes"),
                    140, () -> {
                        SingularityChannel.CHANNEL.sendToServer(new PacketDeleteNetwork(entry.networkID));
                        selectedNetworkID = 0;
                        currentPanel = Panel.SELECTION;
                        requestNetworkData();
                    }))
                .child(makeBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.confirm.cancel"),
                    140, this::renderContent)));
        }

        // ---- CREATE ----

        @SuppressWarnings("unchecked")
        void renderCreate() {
            bottomArea.removeAll();

            if (panelFirstRender) {
                createNameVal.setStringValue("");
                createPwVal.setStringValue("");
                selectedColor = 0x4A90E2;
                selectedSecurity = SecurityLevel.PRIVATE;
            }
            contentArea.child(formRow(
                NetworkUiKit.tr("gui.singularityme.network_terminal.create.name"), createNameInput));

            contentArea.child(formRow(
                NetworkUiKit.tr("gui.singularityme.network_terminal.create.password"), createPasswordInput));

            contentArea.child(formRow(
                NetworkUiKit.tr("gui.singularityme.network_terminal.settings.security"),
                NetworkUiKit.securitySegmentRow(selectedSecurity, level -> {
                    selectedSecurity = level;
                    renderContent();
                })));

            contentArea.child(formRow(
                NetworkUiKit.tr("gui.singularityme.network_terminal.settings.color"),
                NetworkUiKit.colorReadonly(selectedColor)));
            contentArea.child(colorSwatchRow());

            bottomArea.child(Flow.row().childPadding(8).widthRel(1f).height(Palette.ROW_H)
                .child(makeBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.create.confirm"),
                    140, this::confirmCreate)));
        }

        void confirmCreate() {
            final String name = createNameVal.getStringValue();
            if (name.isEmpty()) return;
            final String pw = createPwVal.getStringValue();
            SingularityChannel.CHANNEL.sendToServer(
                new PacketCreateNetwork(x, y, z, dim, name, selectedColor,
                    selectedSecurity.ordinal(),
                    pw.isEmpty() ? "" : SingularityNetworkRegistry.sha256Hex(pw)));
            createNameVal.setStringValue("");
            createPwVal.setStringValue("");
        }

        // ---- 表单行 ----

        private Flow formRow(String label, IWidget input) {
            return NetworkUiKit.formRow(label, input);
        }

        // ---- 空状态 ----

        private Flow emptyState() {
            return Flow.row()
                .widthRel(1f).height(60)
                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new TextWidget(IKey.str(
                    NetworkUiKit.tr("gui.singularityme.network_tab.no_selection")))
                    .color(Palette.TEXT_EMPTY));
        }

        // ---- 按钮工厂 ----

        private static ButtonWidget<?> makeBtn(String text, int w, Runnable action) {
            return makeBtn(text, w, action, true);
        }

        private static ButtonWidget<?> makeBtn(String text, int w, Runnable action, boolean enabled) {
            return new ButtonWidget<>()
                .overlay(IKey.str(text))
                .width(w).height(Palette.ROW_H).padding(0, 12)
                .background(Styles.rowBg(enabled ? Palette.BTN_NORMAL : Palette.BTN_DISABLED))
                .onMousePressed(mb -> {
                    if (!enabled) return false;
                    action.run();
                    return true;
                });
        }

        private static ButtonWidget<?> makeDangerBtn(String text, int w, Runnable action) {
            return new ButtonWidget<>()
                .overlay(IKey.str(text))
                .width(w).height(Palette.ROW_H).padding(0, 12)
                .background(Styles.rowBg(Palette.BTN_DANGER_NORMAL))
                .onMousePressed(mb -> { action.run(); return true; });
        }

        // ---- 输入框工厂 ----

        private static TextFieldWidget makeInput(StringValue val) {
            return new TextFieldWidget()
                .value(val)
                .height(Palette.ROW_H).expanded()
                .background(Styles.inputBg())
                .autoUpdateOnChange(true);
        }

        // ---- 数据刷新 ----

        void requestNetworkData() {
            SingularityChannel.CHANNEL.sendToServer(new PacketRequestNetworkTabData(x, y, z, dim));
        }

        void requestNetworkStatus() {
            networkStatus = null;
            SingularityChannel.CHANNEL.sendToServer(new PacketRequestNetworkStatus(selectedNetworkID));
        }

        void receiveStatus(final PacketNetworkStatus packet) {
            if (packet.networkID != selectedNetworkID) return;
            networkStatus = packet;
            renderContent();
        }

        void receive(final PacketNetworkTabData packet) {
            networks.clear();
            networks.addAll(packet.networks);
            defaultNetworkID = packet.defaultNetworkID;
            if (selectedNetworkID != 0) {
                boolean found = false;
                for (final NetworkEntry e : networks) {
                    if (e.networkID == selectedNetworkID) { found = true; break; }
                }
                if (!found) { selectedNetworkID = 0; selectedMemberID = -1; }
            }
            if (networkStatus != null && networkStatus.networkID != selectedNetworkID) {
                networkStatus = null;
            }
            renderContent();
        }

        // ---- 工具 ----

        private NetworkEntry selectedEntry() {
            for (final NetworkEntry e : networks) {
                if (e.networkID == selectedNetworkID) return e;
            }
            return null;
        }

        private int visibleNetworkCount() {
            int c = 0;
            for (final NetworkEntry e : networks) { if (e.networkID != 0 && matchesFilter(e)) c++; }
            return c;
        }

        private boolean matchesFilter(final NetworkEntry entry) {
            final String filter = filterVal.getStringValue();
            if (filter == null || filter.trim().isEmpty()) return true;
            final String needle = filter.trim().toLowerCase();
            return entry.name.toLowerCase().contains(needle) || ("#" + entry.networkID).contains(needle);
        }

        private static boolean isStatusPanel(final Panel panel) {
            return panel == Panel.HOME || panel == Panel.CONNECTION || panel == Panel.STATISTICS
                || panel == Panel.HEALTH;
        }

        private int countLoadedDevices() {
            if (networkStatus == null) return 0;
            int count = 0;
            for (final DeviceInfo device : networkStatus.devices) {
                if (device.loaded) count++;
            }
            return count;
        }

        private boolean hasDeviceType(final String type) {
            if (networkStatus == null) return false;
            for (final DeviceInfo device : networkStatus.devices) {
                if (type.equals(device.type)) return true;
            }
            return false;
        }

        private float energyFraction() {
            if (networkStatus == null || networkStatus.maxPower <= 0.0) return 0f;
            return (float) Math.max(0.0, Math.min(1.0, networkStatus.currentPower / networkStatus.maxPower));
        }

        private static String formatLocation(final DeviceInfo device) {
            return NetworkUiKit.trf(
                "gui.singularityme.network_tab.location",
                device.dim,
                device.x,
                device.y,
                device.z);
        }

        private static String formatEnergy(final double current, final double max) {
            return String.format("%.0f / %.0f AE", current, max);
        }

        private static String formatTimestamp(final long millis) {
            if (millis <= 0L) return "-";
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(millis));
        }

        private static String panelTitle(Panel p) {
            return NetworkUiKit.tr("gui.singularityme.network_terminal.panel." + p.name().toLowerCase());
        }
    }
}
