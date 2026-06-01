package com.github.singularityme.client.ui;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
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
import com.github.singularityme.core.AccessLevel;
import com.github.singularityme.core.SecurityLevel;
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;
import com.github.singularityme.network.packet.PacketAddMemberByName;
import com.github.singularityme.network.packet.PacketCreateNetwork;
import com.github.singularityme.network.packet.PacketDeleteNetwork;
import com.github.singularityme.network.packet.PacketNetworkTabData;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;
import com.github.singularityme.network.packet.PacketRenameNetwork;
import com.github.singularityme.network.packet.PacketRequestNetworkTabData;
import com.github.singularityme.network.packet.PacketSetDefaultNetwork;
import com.github.singularityme.network.packet.PacketSetMemberRole;
import com.github.singularityme.network.packet.PacketSetNetworkSettings;

/**
 * 5 面板网络终端 �?MUI2 重写版�?
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

    private enum Panel { SELECTION, MEMBERS, SETTINGS, INFO, CREATE }

    private static final class TerminalState {
        final int x, y, z, dim;
        final List<NetworkEntry> networks = new ArrayList<>();
        int selectedNetworkID;
        int selectedMemberID = -1;
        int defaultNetworkID;
        Panel currentPanel = Panel.SELECTION;
        int selectedColor = 0x4A90E2;
        SecurityLevel selectedSecurity = SecurityLevel.PRIVATE;
        /** 当前面板是否首次渲染（用于区分主动切�?vs 数据刷新触发�?*/
        boolean panelFirstRender = true;

        ModularPanel panel;
        Flow navBar;
        Flow networkBar;
        Flow contentArea;
        Flow bottomArea;

        TextFieldWidget memberNameInput;
        StringValue memberNameVal = new StringValue("");

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
                .background(new Rectangle().color(Palette.BG_PANEL));

            final Flow root = Flow.column().widthRel(1f).heightRel(1f);

            // 导航�?
            navBar = Flow.row()
                .childPadding(4).widthRel(1f)
                .padding(4).margin(8)
                .background(new Rectangle().color(Palette.BG_LIST));
            buildNavButtons();
            root.child(navBar);

            // 网络信息�?
            networkBar = Flow.row()
                .childPadding(8).widthRel(1f)
                .padding(6, 12)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER);
            root.child(networkBar);

            // 内容�?
            contentArea = Flow.column().widthRel(1f).expanded();
            root.child(contentArea);

            // 底部操作�?
            bottomArea = Flow.column()
                .childPadding(8).widthRel(1f)
                .padding(10, 12);
            root.child(bottomArea);

            // 输入控件
            memberNameInput = makeInput(memberNameVal);
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
                        renderContent();
                    }
                }));
            }
        }

        private static ButtonWidget<?> makeNavBtn(String text, boolean active, Runnable action) {
            return new ButtonWidget<>()
                .overlay(IKey.str(text))
                .height(30).padding(5, 10)
                .background(new Rectangle().color(active ? Palette.BTN_ACTIVE : 0x00000000))
                .disableHoverBackground()
                .onMousePressed(mb -> { action.run(); return true; });
        }

        // ---- 内容渲染 ----

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void renderContent() {
            contentArea.removeAll();
            updateNetworkBar();
            switch (currentPanel) {
                case SELECTION -> renderSelection();
                case MEMBERS -> renderMembers();
                case SETTINGS -> renderSettings();
                case INFO -> renderInfo();
                case CREATE -> renderCreate();
            }
            panelFirstRender = false;
        }

        // ---- 网络信息�?----

        @SuppressWarnings("unchecked")
        void updateNetworkBar() {
            networkBar.removeAll();
            networkBar.child(new TextWidget(IKey.str(panelTitle(currentPanel)))
                .color(Palette.TEXT_PRIMARY));

            final NetworkEntry sel = selectedEntry();
            if (sel != null) {
                final int c = NetworkUiKit.entryColor(sel);
                networkBar.child(new TextWidget(IKey.str("\u25A0")).color(c));
                networkBar.child(new TextWidget(IKey.str(sel.name)).color(c));
                networkBar.child(new TextWidget(IKey.str(sel.networkID == 0 ? "-" : "#" + sel.networkID))
                    .color(Palette.TEXT_MUTED));
                if (sel.networkID != 0 && sel.networkID == defaultNetworkID) {
                    networkBar.child(new TextWidget(IKey.str("D")).color(Palette.BADGE_DEFAULT));
                }
            }
        }

        // ---- SELECTION ----

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void renderSelection() {
            bottomArea.removeAll();

            contentArea.child(Flow.row()
                .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                .widthRel(1f).padding(4, 12)
                .child(new TextWidget(IKey.str(
                    NetworkUiKit.trf("gui.singularityme.network_tab.sort_by",
                        NetworkUiKit.tr("gui.singularityme.network_tab.name"))))
                    .color(Palette.TEXT_MUTED))
                .child(new TextWidget(IKey.str(
                    NetworkUiKit.trf("gui.singularityme.network_tab.total", visibleNetworkCount())))
                    .color(Palette.TEXT_MUTED)));

            final ListWidget list = new ListWidget();
            list.background(new Rectangle().color(Palette.BG_LIST));
            list.widthRel(1f);
            list.expanded();
            for (final NetworkEntry entry : networks) {
                list.child(buildSelectionRow(entry));
            }
            contentArea.child(list);

            final NetworkEntry sel = selectedEntry();
            contentArea.child(Flow.row()
                .widthRel(1f).padding(8)
                .margin(12, 0)
                .background(new Rectangle().color(Palette.BG_LIST))
                .child(new TextWidget(IKey.str(sel == null
                    ? NetworkUiKit.tr("gui.singularityme.network_tab.no_selection")
                    : NetworkUiKit.trf("gui.singularityme.network_tab.selected",
                        "#" + sel.networkID + " " + sel.name + " "
                            + NetworkUiKit.securityName(sel) + " " + NetworkUiKit.accessMark(sel))))
                    .color(Palette.TEXT_SECONDARY)));

            final boolean isDefault = sel != null && sel.networkID != 0 && sel.networkID == defaultNetworkID;
            final String btnText = isDefault
                ? NetworkUiKit.tr("gui.singularityme.network_terminal.selection.clear_default")
                : NetworkUiKit.tr("gui.singularityme.network_terminal.selection.set_default");
            final boolean canSet = sel != null && sel.networkID != 0 && NetworkUiKit.canAccess(sel);

            bottomArea.child(Flow.row().childPadding(8).widthRel(1f)
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
            final int bg = sel ? NetworkUiKit.darken(c, 0.32f) : Palette.BG_ROW;
            final String name = entry.networkID == 0
                ? NetworkUiKit.tr("gui.singularityme.network_tab.default") : entry.name;

            final TextWidget nameWidget = new TextWidget(IKey.str(name))
                .color(sel ? 0xFFFFFFFF : Palette.TEXT_SECONDARY);
            nameWidget.expanded();

            final Flow rowContent = Flow.row()
                .childPadding(8).widthRel(1f).height(Palette.ROW_H).padding(6)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new TextWidget(IKey.str("\u25A0")).color(c))
                .child(new TextWidget(IKey.str(entry.networkID == 0 ? "-" : "#" + entry.networkID))
                    .color(Palette.TEXT_MUTED))
                .child(nameWidget);
            rowContent.child(new TextWidget(IKey.str(NetworkUiKit.securityShort(entry)))
                .color(NetworkUiKit.securityColor(entry)));
            rowContent.child(new TextWidget(IKey.str(NetworkUiKit.accessShort(entry)))
                .color(NetworkUiKit.accessColor(entry)));
            if (def) rowContent.child(new TextWidget(IKey.str("D")).color(Palette.BADGE_DEFAULT));

            return new ButtonWidget<>()
                .child(rowContent)
                .widthRel(1f).height(Palette.ROW_H)
                .background(new Rectangle().color(bg))
                .disableHoverBackground()
                .onMousePressed(mb -> {
                    selectedNetworkID = entry.networkID;
                    selectedMemberID = -1;
                    renderContent();
                    return true;
                });
        }

        // ---- MEMBERS ----

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void renderMembers() {
            bottomArea.removeAll();
            final NetworkEntry sel = selectedEntry();
            if (sel == null) { contentArea.child(emptyState()); return; }

            final ListWidget list = new ListWidget();
            list.background(new Rectangle().color(Palette.BG_LIST));
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
            contentArea.child(list);

            contentArea.child(Flow.row()
                .childPadding(8).widthRel(1f).padding(6, 12)
                .child(memberNameInput.widthRel(1f).expanded())
                .child(makeBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.members.add"),
                    120, this::addMember)));

            if (sel.isOwner && selectedMemberID >= 0) {
                bottomArea.child(Flow.row().childPadding(8).widthRel(1f)
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
                .childPadding(8).widthRel(1f).height(Palette.ROW_H).padding(6)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new TextWidget(IKey.str(NetworkUiKit.roleName(role))).color(rc))
                .child(memberNameW);

            final ButtonWidget<?> row = new ButtonWidget<>()
                .child(rowContent)
                .widthRel(1f).height(Palette.ROW_H)
                .background(new Rectangle().color(bg))
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

            // 首次进入 SETTINGS 面板时从已选网络读取真实�?
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
                new TextWidget(IKey.str(NetworkUiKit.securityName(selectedSecurity)))
                    .color(Palette.TEXT_PRIMARY)));

            contentArea.child(formRow(
                NetworkUiKit.tr("gui.singularityme.network_terminal.settings.color"),
                new TextWidget(IKey.str("\u25A0 #" + String.format("%06X", selectedColor)))
                    .color(0xFF000000 | selectedColor)));

            bottomArea.child(Flow.row().childPadding(8).widthRel(1f)
                .child(makeBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.settings.cycle_security"),
                    120, this::cycleSecurity))
                .child(makeBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.settings.cycle_color"),
                    120, this::cycleColor))
                .child(makeBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.settings.apply"),
                    140, this::applySettings)));

            if (sel.isOwner) {
                bottomArea.child(Flow.row().childPadding(8).widthRel(1f)
                    .child(makeDangerBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.settings.delete"),
                        140, () -> {
                            SingularityChannel.CHANNEL.sendToServer(new PacketDeleteNetwork(sel.networkID));
                            selectedNetworkID = 0;
                            currentPanel = Panel.SELECTION;
                            requestNetworkData();
                        })));
            }
        }

        void cycleSecurity() {
            final SecurityLevel[] vals = SecurityLevel.values();
            selectedSecurity = vals[(selectedSecurity.ordinal() + 1) % vals.length];
            renderContent();
        }

        /** GTNH 网络 8 色调色板循环 */
        void cycleColor() {
            final int[] palette = {
                0x4A90E2, 0xE24A4A, 0x4AE24A, 0xE2E24A,
                0xE24AE2, 0x4AE2E2, 0xE28E4A, 0xFFFFFF
            };
            int idx = -1;
            for (int i = 0; i < palette.length; i++) {
                if ((palette[i] & 0xFFFFFF) == (selectedColor & 0xFFFFFF)) { idx = i; break; }
            }
            selectedColor = palette[(idx + 1) % palette.length];
            renderContent();
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

        // ---- INFO ----

        @SuppressWarnings("unchecked")
        void renderInfo() {
            bottomArea.removeAll();
            final NetworkEntry sel = selectedEntry();
            if (sel == null) { contentArea.child(emptyState()); return; }

            final Flow info = Flow.column().childPadding(4).widthRel(1f).padding(12);
            info.child(infoRow("ID", "#" + sel.networkID));
            info.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.info.name"), sel.name));
            info.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.info.owner"), sel.ownerName));
            info.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.info.security"),
                NetworkUiKit.securityName(sel)));
            info.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.info.access"),
                NetworkUiKit.accessName(sel)));
            info.child(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.info.members"),
                String.valueOf(sel.adminPlayerIDs.size() + sel.memberPlayerIDs.size() + 1)));
            contentArea.child(info);
        }

        private Flow infoRow(String label, String value) {
            return Flow.row().childPadding(8).widthRel(1f)
                .child(new TextWidget(IKey.str(label + ":")).color(Palette.TEXT_LABEL))
                .child(new TextWidget(IKey.str(value)).color(Palette.TEXT_PRIMARY));
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
                new TextWidget(IKey.str(NetworkUiKit.securityName(selectedSecurity)))
                    .color(Palette.TEXT_PRIMARY)));

            contentArea.child(formRow(
                NetworkUiKit.tr("gui.singularityme.network_terminal.settings.color"),
                new TextWidget(IKey.str("\u25A0 #" + String.format("%06X", selectedColor)))
                    .color(0xFF000000 | selectedColor)));

            bottomArea.child(Flow.row().childPadding(8).widthRel(1f)
                .child(makeBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.settings.cycle_security"),
                    120, this::cycleSecurity))
                .child(makeBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.settings.cycle_color"),
                    120, this::cycleColor))
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

        // ---- 表单�?----

        private Flow formRow(String label, IWidget input) {
            return Flow.row()
                .childPadding(8).widthRel(1f).padding(4, 12)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new TextWidget(IKey.str(label)).color(Palette.TEXT_LABEL))
                .child(input);
        }

        // ---- 空状�?----

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
                .background(new Rectangle().color(enabled ? Palette.BTN_NORMAL : Palette.BTN_DISABLED))
                .onMousePressed(mb -> { if (enabled) action.run(); return true; });
        }

        private static ButtonWidget<?> makeDangerBtn(String text, int w, Runnable action) {
            return new ButtonWidget<>()
                .overlay(IKey.str(text))
                .width(w).height(Palette.ROW_H).padding(0, 12)
                .background(new Rectangle().color(Palette.BTN_DANGER_NORMAL))
                .onMousePressed(mb -> { action.run(); return true; });
        }

        // ---- 输入框工�?----

        private static TextFieldWidget makeInput(StringValue val) {
            return new TextFieldWidget()
                .value(val)
                .height(Palette.ROW_H).expanded()
                .background(new Rectangle().color(Palette.BG_INPUT))
                .autoUpdateOnChange(true);
        }

        // ---- 数据刷新 ----

        void requestNetworkData() {
            SingularityChannel.CHANNEL.sendToServer(new PacketRequestNetworkTabData(x, y, z, dim));
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
            for (final NetworkEntry e : networks) { if (e.networkID != 0) c++; }
            return c;
        }

        private static String panelTitle(Panel p) {
            return NetworkUiKit.tr("gui.singularityme.network_terminal.panel." + p.name().toLowerCase());
        }
    }
}
