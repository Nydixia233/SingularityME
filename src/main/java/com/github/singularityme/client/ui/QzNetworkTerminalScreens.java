package com.github.singularityme.client.ui;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.tileentity.TileEntity;

import com.github.singularityme.client.ui.QzNetworkUiKit.Palette;
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

import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.screen.UiDocumentScreens;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiScrollbarWidth;
import club.heiqi.uilib.ui.style.props.UiTextAlign;
import club.heiqi.uilib.ui.style.props.UiTextOverflow;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

public final class QzNetworkTerminalScreens {

    private static WeakReference<TerminalState> activeState;

    private QzNetworkTerminalScreens() {}

    public static GuiScreen create(final TileEntity te) {
        final int x = te == null ? 0 : te.xCoord;
        final int y = te == null ? 0 : te.yCoord;
        final int z = te == null ? 0 : te.zCoord;
        final int dim = te != null && te.getWorldObj() != null && te.getWorldObj().provider != null
            ? te.getWorldObj().provider.dimensionId
            : 0;
        final TerminalState[] stateRef = new TerminalState[1];
        final GuiScreen screen = UiDocumentScreens.createDocumentScreen(document -> {
            final TerminalState state = new TerminalState(document, x, y, z, dim);
            stateRef[0] = state;
            state.build();
        });
        if (stateRef[0] != null) {
            stateRef[0].screen = screen;
            activeState = new WeakReference<>(stateRef[0]);
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    final TerminalState state = activeState == null ? null : activeState.get();
                    if (state == stateRef[0]) {
                        state.requestNetworkData();
                    }
                });
        }
        return screen;
    }

    public static boolean receiveNetworkData(final PacketNetworkTabData packet) {
        final TerminalState state = activeState == null ? null : activeState.get();
        if (state == null || state.screen != Minecraft.getMinecraft().currentScreen) return false;
        state.receive(packet);
        return true;
    }

    private enum Panel {
        SELECTION,
        MEMBERS,
        SETTINGS,
        INFO,
        CREATE
    }

    private static String panelTitle(final Panel target) {
        return QzNetworkUiKit.tr(
            "gui.singularityme.network_terminal.panel." + target.name()
                .toLowerCase());
    }

    private static final class TerminalState {

        final UiDocument document;
        GuiScreen screen;
        final int x;
        final int y;
        final int z;
        final int dim;
        List<NetworkEntry> networks = new ArrayList<>();
        ElementNode nav;
        ElementNode content;
        ElementNode networkBarNode;
        ElementNode selectionSummaryNode;
        ElementNode selectionActionsRow;
        ElementNode memberActionsRow;
        Panel panel = Panel.SELECTION;
        int selectedNetworkID = 0;
        /** networkID → 行节点，用于选中态增量更新。数据包刷新时重建。 */
        final Map<Integer, ElementNode> networkRowNodes = new HashMap<>();
        /** playerID → 成员行节点，用于选中态增量更新。成员列表重建时刷新。 */
        final Map<Integer, ElementNode> memberRowNodes = new HashMap<>();
        int selectedMemberID = -1;
        int defaultNetworkID = 0;
        int selectedColor = 0x4A90E2;
        SecurityLevel selectedSecurity = SecurityLevel.PRIVATE;
        DocumentTextInputControl createNameInput;
        DocumentTextInputControl createPasswordInput;
        QzNetworkUiKit.MaskedInput maskedCreatePassword;
        DocumentTextInputControl settingsNameInput;
        DocumentTextInputControl settingsPasswordInput;
        QzNetworkUiKit.MaskedInput maskedSettingsPassword;
        DocumentTextInputControl memberNameInput;

        TerminalState(final UiDocument document, final int x, final int y, final int z, final int dim) {
            this.document = document;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
        }

        void build() {
            final ElementNode root = document.getRootElement();
            root.clearChildren();
            // 对应 HTML .overlay: flex 居中容器
            // 必须显式设 overflow，否则框架自动兜底 overflow-y:auto 导致外层滚动条
            root.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setBackgroundColor(Palette.BG_OVERLAY);

            // 对应 HTML .frame: width: min(92vw, 760px); height: min(92vh, 520px); overflow: hidden
            // Qz-UILib 布局引擎已工作在 Minecraft GUI 坐标空间，无需手动乘 guiScale
            final ElementNode frame = div();
            frame.style()
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(0.92F))
                .setMaxWidth(UiStyleLength.px(760))
                .setHeight(UiStyleLength.percent(0.92F))
                .setMaxHeight(UiStyleLength.px(520))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setBackgroundColor(Palette.BG_PANEL)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(Palette.BORDER_PANEL)
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_PANEL));
            root.append(frame);

            // 对应 HTML .nav: margin:8px; padding:4px; gap:4px
            nav = div();
            nav.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setFlexShrink(0.0F)
                .setBackgroundColor(Palette.BG_LIST)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(Palette.BORDER_LIST)
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW))
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setMargin(UiStyleLength.px(8))
                .setPadding(UiStyleLength.px(4))
                .setGap(UiStyleLength.px(4));
            frame.append(nav);

            content = div();
            content.style()
                .setFlexGrow(1.0F)
                .setFlexShrink(1.0F)
                .setFlexBasis(UiStyleLength.px(0))
                .setMinHeight(UiStyleLength.px(0))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setBoxSizing(UiBoxSizing.BORDER_BOX);
            frame.append(content);

            createNameInput = input(QzNetworkUiKit.tr("gui.singularityme.network_terminal.create.name_placeholder"));
            createPasswordInput = input(
                QzNetworkUiKit.tr("gui.singularityme.network_terminal.create.password_placeholder"));
            maskedCreatePassword = QzNetworkUiKit.MaskedInput.wrap(createPasswordInput);
            settingsNameInput = input(
                QzNetworkUiKit.tr("gui.singularityme.network_terminal.settings.name_placeholder"));
            settingsPasswordInput = input(
                QzNetworkUiKit.tr("gui.singularityme.network_terminal.settings.password_placeholder"));
            maskedSettingsPassword = QzNetworkUiKit.MaskedInput.wrap(settingsPasswordInput);
            memberNameInput = input(QzNetworkUiKit.tr("gui.singularityme.network_terminal.members.placeholder"));

            installKeyHandlers();
            QzNetworkUiKit.installComponentStyleSheet(document);
            render();
        }

        void requestNetworkData() {
            SingularityChannel.CHANNEL.sendToServer(new PacketRequestNetworkTabData(x, y, z, dim));
        }

        void receive(final PacketNetworkTabData packet) {
            networks = packet.networks == null ? new ArrayList<>() : new ArrayList<>(packet.networks);
            defaultNetworkID = packet.defaultNetworkID;
            if (selectedNetworkID != 0) {
                boolean found = false;
                for (final NetworkEntry e : networks) {
                    if (e.networkID == selectedNetworkID) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    selectedNetworkID = 0;
                    selectedMemberID = -1;
                }
            }
            render();
        }

        void render() {
            final ElementNode focused = focusedInput();
            renderNav();
            content.clearChildren();
            content.append(networkBar());
            switch (panel) {
                case SELECTION:
                    renderSelection();
                    break;
                case MEMBERS:
                    renderMembers();
                    break;
                case SETTINGS:
                    renderSettings();
                    break;
                case INFO:
                    renderInfo();
                    break;
                case CREATE:
                    renderCreate();
                    break;
                default:
                    renderSelection();
                    break;
            }
            restoreFocus(focused);
        }

        void renderNav() {
            nav.clearChildren();
            nav.append(navButton(Panel.SELECTION, "compass"));
            nav.append(navButton(Panel.MEMBERS, "name_tag"));
            nav.append(navButton(Panel.SETTINGS, "redstone"));
            nav.append(navButton(Panel.INFO, "paper"));
            nav.append(navButton(Panel.CREATE, "nether_star"));
        }

        ElementNode navButton(final Panel target, final String icon) {
            final boolean active = panel == target;
            final DocumentButtonControl btn = button(panelTitle(target));
            // 对应 HTML .nav-btn: height:30px; padding:5px 10px; font-weight:600
            btn.getElement()
                .setClassName("nav-btn")
                .style()
                .setBackgroundColor(active ? Palette.BTN_ACTIVE : 0x00000000)
                .setHeight(UiStyleLength.px(30))
                .setPaddingLeft(UiStyleLength.px(10))
                .setPaddingRight(UiStyleLength.px(10))
                .setPaddingTop(UiStyleLength.px(5))
                .setPaddingBottom(UiStyleLength.px(5))
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW))
                .setTextColor(active ? Palette.TEXT_PRIMARY : Palette.TEXT_MUTED)
                .setFontWeight(UiFontWeight.BOLD);
            btn.setActionHandler(e -> {
                panel = target;
                render();
            });
            return btn.getElement();
        }

        ElementNode networkBar() {
            final ElementNode bar = div();
            bar.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setFlexShrink(0.0F)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setPaddingLeft(UiStyleLength.px(12))
                .setPaddingRight(UiStyleLength.px(12))
                .setPaddingTop(UiStyleLength.px(6))
                .setPaddingBottom(UiStyleLength.px(6))
                .setGap(UiStyleLength.px(8));
            this.networkBarNode = bar;
            populateNetworkBar(bar);
            return bar;
        }

        void populateNetworkBar(final ElementNode bar) {
            bar.clearChildren();

            final ElementNode title = div();
            title.appendText(panelTitle(panel));
            title.style()
                .setTextColor(Palette.TEXT_PRIMARY)
                .setFontWeight(UiFontWeight.BOLD)
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(0))
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTextOverflow(UiTextOverflow.ELLIPSIS);
            bar.append(title);

            final NetworkEntry sel = selectedEntry();
            if (sel != null) {
                final int color = QzNetworkUiKit.entryColor(sel);
                bar.append(QzNetworkUiKit.colorSwatch(document, color, 10));
                final ElementNode nameEl = div();
                nameEl.appendText(sel.name);
                nameEl.style()
                    .setTextColor(color)
                    .setWhiteSpace(UiWhiteSpace.NOWRAP)
                    .setOverflowX(UiOverflow.HIDDEN)
                    .setOverflowY(UiOverflow.HIDDEN)
                    .setTextOverflow(UiTextOverflow.ELLIPSIS);
                bar.append(nameEl);
                bar.append(QzNetworkUiKit.idPill(document, sel.networkID));
                if (sel.networkID != 0 && sel.networkID == defaultNetworkID) {
                    bar.append(QzNetworkUiKit.badge(document, "D", Palette.BADGE_DEFAULT));
                }
            }
        }

        void updateNetworkBar() {
            if (this.networkBarNode != null) {
                populateNetworkBar(this.networkBarNode);
            }
        }

        void renderSelection() {
            content.append(listMeta());
            content.append(scrollBox(networkList()));
            content.append(selectionSummary());

            final ElementNode actions = div();
            actions.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setFlexShrink(0.0F)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setPaddingLeft(UiStyleLength.px(12))
                .setPaddingRight(UiStyleLength.px(12))
                .setPaddingBottom(UiStyleLength.px(10))
                .setGap(UiStyleLength.px(8));
            content.append(actions);
            this.selectionActionsRow = actions;
            renderSelectionActions();
        }

        void renderSelectionActions() {
            if (this.selectionActionsRow == null) return;
            this.selectionActionsRow.clearChildren();
            final NetworkEntry sel = selectedEntry();
            final boolean isDefault = sel != null && sel.networkID != 0 && sel.networkID == defaultNetworkID;
            final DocumentButtonControl setDefaultBtn = button(
                isDefault ? QzNetworkUiKit.tr("gui.singularityme.network_terminal.selection.clear_default")
                    : QzNetworkUiKit.tr("gui.singularityme.network_terminal.selection.set_default"));
            setDefaultBtn.getElement()
                .style()
                .setHeight(UiStyleLength.px(Palette.ROW_H))
                .setPaddingLeft(UiStyleLength.px(14))
                .setPaddingRight(UiStyleLength.px(14))
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
            setDefaultBtn.setEnabled(sel != null && sel.networkID != 0 && QzNetworkUiKit.canAccess(sel));
            setDefaultBtn.setActionHandler(ev -> {
                if (sel == null) return;
                final int newDefault = isDefault ? 0 : sel.networkID;
                SingularityChannel.CHANNEL.sendToServer(new PacketSetDefaultNetwork(newDefault));
                final int oldDefault = defaultNetworkID;
                defaultNetworkID = newDefault;
                updateNetworkRowStyle(oldDefault);
                updateNetworkRowStyle(newDefault);
                updateNetworkBar();
                renderSelectionActions();
            });
            this.selectionActionsRow.append(setDefaultBtn.getElement());
        }

        ElementNode listMeta() {
            final ElementNode row = div();
            row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setJustifyContent(UiJustifyContent.SPACE_BETWEEN)
                .setAlignItems(UiAlignItems.CENTER)
                .setFlexShrink(0.0F)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setPaddingLeft(UiStyleLength.px(12))
                .setPaddingRight(UiStyleLength.px(12))
                .setPaddingTop(UiStyleLength.px(4))
                .setPaddingBottom(UiStyleLength.px(4));

            final ElementNode sortLabel = div();
            sortLabel.appendText(
                QzNetworkUiKit.trf(
                    "gui.singularityme.network_tab.sort_by",
                    QzNetworkUiKit.tr("gui.singularityme.network_tab.name")));
            sortLabel.style()
                .setTextColor(Palette.TEXT_MUTED);
            row.append(sortLabel);

            final ElementNode totalLabel = div();
            totalLabel.appendText(QzNetworkUiKit.trf("gui.singularityme.network_tab.total", visibleNetworkCount()));
            totalLabel.style()
                .setTextColor(Palette.TEXT_MUTED);
            row.append(totalLabel);
            return row;
        }

        ElementNode selectionSummary() {
            final ElementNode bar = div();
            bar.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setFlexShrink(0.0F)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setMarginLeft(UiStyleLength.px(12))
                .setMarginRight(UiStyleLength.px(12))
                .setMarginTop(UiStyleLength.px(6))
                .setPadding(UiStyleLength.px(8))
                .setGap(UiStyleLength.px(8))
                .setBackgroundColor(Palette.BG_LIST)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
            this.selectionSummaryNode = bar;
            populateSelectionSummary(bar);
            return bar;
        }

        void populateSelectionSummary(final ElementNode bar) {
            bar.clearChildren();
            final NetworkEntry sel = selectedEntry();
            bar.style()
                .setBorderColor(sel == null ? Palette.BORDER_LIST : QzNetworkUiKit.entryColor(sel));

            final ElementNode text = div();
            text.appendText(
                sel == null ? QzNetworkUiKit.tr("gui.singularityme.network_tab.no_selection")
                    : QzNetworkUiKit.trf(
                        "gui.singularityme.network_tab.selected",
                        "#" + sel.networkID
                            + " "
                            + sel.name
                            + " "
                            + QzNetworkUiKit.securityName(sel)
                            + " "
                            + QzNetworkUiKit.accessMark(sel)));
            text.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(0))
                .setTextColor(Palette.TEXT_SECONDARY)
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTextOverflow(UiTextOverflow.ELLIPSIS);
            bar.append(text);
        }

        void updateSelectionSummary() {
            if (this.selectionSummaryNode != null) {
                populateSelectionSummary(this.selectionSummaryNode);
            }
        }

        void renderMembers() {
            final NetworkEntry sel = selectedEntry();
            if (sel == null) {
                content.append(emptyState());
                return;
            }
            content.append(scrollBox(memberList(sel)));

            final ElementNode addRow = div();
            addRow.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setFlexShrink(0.0F)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setPaddingLeft(UiStyleLength.px(12))
                .setPaddingRight(UiStyleLength.px(12))
                .setPaddingTop(UiStyleLength.px(6))
                .setPaddingBottom(UiStyleLength.px(6))
                .setGap(UiStyleLength.px(8));
            content.append(addRow);

            memberNameInput.getElement()
                .style()
                .setFlexGrow(1.0F)
                .setHeight(UiStyleLength.px(Palette.ROW_H))
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW))
                .setPaddingLeft(UiStyleLength.px(8))
                .setPaddingRight(UiStyleLength.px(8))
                .setBoxSizing(UiBoxSizing.BORDER_BOX);
            addRow.append(memberNameInput.getElement());

            final DocumentButtonControl addBtn = button(
                QzNetworkUiKit.tr("gui.singularityme.network_terminal.members.add"));
            addBtn.getElement()
                .style()
                .setHeight(UiStyleLength.px(Palette.ROW_H))
                .setPaddingLeft(UiStyleLength.px(12))
                .setPaddingRight(UiStyleLength.px(12))
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW))
                .setBoxSizing(UiBoxSizing.BORDER_BOX);
            addBtn.setActionHandler(ev -> addMember());
            addRow.append(addBtn.getElement());

            final ElementNode roleRow = div();
            roleRow.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setFlexShrink(0.0F)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setPaddingLeft(UiStyleLength.px(12))
                .setPaddingRight(UiStyleLength.px(12))
                .setPaddingBottom(UiStyleLength.px(10))
                .setGap(UiStyleLength.px(8));
            content.append(roleRow);
            this.memberActionsRow = roleRow;
            renderMemberActions();
        }

        void renderMemberActions() {
            if (this.memberActionsRow == null) return;
            this.memberActionsRow.clearChildren();
            final NetworkEntry sel = selectedEntry();
            if (sel == null) return;
            if (sel.isOwner && selectedMemberID >= 0) {
                final DocumentButtonControl promoteBtn = button(
                    QzNetworkUiKit.tr("gui.singularityme.network_terminal.members.promote"));
                promoteBtn.getElement()
                    .style()
                    .setHeight(UiStyleLength.px(Palette.ROW_H))
                    .setPaddingLeft(UiStyleLength.px(12))
                    .setPaddingRight(UiStyleLength.px(12))
                    .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
                promoteBtn.setActionHandler(ev -> setMemberRole(sel.networkID, selectedMemberID, AccessLevel.ADMIN));
                this.memberActionsRow.append(promoteBtn.getElement());

                final DocumentButtonControl demoteBtn = dangerButton(
                    QzNetworkUiKit.tr("gui.singularityme.network_terminal.members.demote"));
                demoteBtn.getElement()
                    .style()
                    .setHeight(UiStyleLength.px(Palette.ROW_H))
                    .setPaddingLeft(UiStyleLength.px(12))
                    .setPaddingRight(UiStyleLength.px(12))
                    .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
                demoteBtn.setActionHandler(ev -> setMemberRole(sel.networkID, selectedMemberID, AccessLevel.MEMBER));
                this.memberActionsRow.append(demoteBtn.getElement());

                final DocumentButtonControl removeBtn = dangerButton(
                    QzNetworkUiKit.tr("gui.singularityme.network_terminal.members.remove"));
                removeBtn.getElement()
                    .style()
                    .setHeight(UiStyleLength.px(Palette.ROW_H))
                    .setPaddingLeft(UiStyleLength.px(12))
                    .setPaddingRight(UiStyleLength.px(12))
                    .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
                removeBtn.setActionHandler(ev -> setMemberRole(sel.networkID, selectedMemberID, AccessLevel.NONE));
                this.memberActionsRow.append(removeBtn.getElement());
            }
        }

        ElementNode memberList(final NetworkEntry net) {
            memberRowNodes.clear();
            final ElementNode list = div();
            list.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setBackgroundColor(Palette.BG_LIST)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(Palette.BORDER_LIST)
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));

            list.append(memberRow(net.ownerPlayerID, net.ownerName, AccessLevel.OWNER));
            for (int i = 0; i < net.adminPlayerIDs.size(); i++) {
                final int pid = net.adminPlayerIDs.get(i);
                final String pname = i < net.adminNames.size() ? net.adminNames.get(i) : "#" + pid;
                final ElementNode row = memberRow(pid, pname, AccessLevel.ADMIN);
                memberRowNodes.put(pid, row);
                list.append(row);
            }
            for (int i = 0; i < net.memberPlayerIDs.size(); i++) {
                final int pid = net.memberPlayerIDs.get(i);
                final String pname = i < net.memberNames.size() ? net.memberNames.get(i) : "#" + pid;
                final ElementNode row = memberRow(pid, pname, AccessLevel.MEMBER);
                memberRowNodes.put(pid, row);
                list.append(row);
            }
            for (int i = 0; i < net.blockedPlayerIDs.size(); i++) {
                final int pid = net.blockedPlayerIDs.get(i);
                final String pname = i < net.blockedNames.size() ? net.blockedNames.get(i) : "#" + pid;
                final ElementNode row = memberRow(pid, pname, AccessLevel.BLOCKED);
                memberRowNodes.put(pid, row);
                list.append(row);
            }

            return list;
        }

        ElementNode memberRow(final int playerID, final String playerName, final AccessLevel role) {
            final boolean selected = playerID == selectedMemberID;
            final int roleColor = QzNetworkUiKit.accessColor(role);
            final ElementNode row = div();
            if (role != AccessLevel.OWNER) {
                row.setClassName("member-row");
            }
            row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setHeight(UiStyleLength.px(Palette.ROW_H))
                .setPaddingLeft(UiStyleLength.px(10))
                .setPaddingRight(UiStyleLength.px(8))
                .setGap(UiStyleLength.px(8))
                .setBackgroundColor(selected ? QzNetworkUiKit.darken(roleColor, 0.25F) : Palette.BG_ROW)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(selected ? roleColor : Palette.BORDER_ROW)
                .setBoxSizing(UiBoxSizing.BORDER_BOX);
            if (role != AccessLevel.OWNER) {
                row.style()
                    .setCursor(UiCursor.POINTER);
                row.setClickHandler(ev -> {
                    final int prevMemberID = selectedMemberID;
                    selectedMemberID = playerID;
                    updateMemberRowStyle(prevMemberID);
                    updateMemberRowStyle(playerID);
                    renderMemberActions();
                    return true;
                });
            }

            row.append(QzNetworkUiKit.badge(document, QzNetworkUiKit.roleName(role), roleColor));

            final ElementNode nameEl = div();
            nameEl.appendText(playerName);
            nameEl.style()
                .setFlexGrow(1.0F)
                .setTextColor(Palette.TEXT_PRIMARY)
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTextOverflow(UiTextOverflow.ELLIPSIS);
            row.append(nameEl);
            return row;
        }

        void renderSettings() {
            final NetworkEntry sel = selectedEntry();
            if (sel == null) {
                content.append(emptyState());
                return;
            }
            content.append(scrollBox(editForm(false, sel)));

            final ElementNode btnRow = div();
            btnRow.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setFlexShrink(0.0F)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setPaddingLeft(UiStyleLength.px(12))
                .setPaddingRight(UiStyleLength.px(12))
                .setPaddingBottom(UiStyleLength.px(10))
                .setGap(UiStyleLength.px(8));
            content.append(btnRow);

            final DocumentButtonControl cycleSecBtn = button(
                QzNetworkUiKit.tr("gui.singularityme.network_terminal.settings.cycle_security"));
            cycleSecBtn.getElement()
                .style()
                .setHeight(UiStyleLength.px(Palette.ROW_H))
                .setPaddingLeft(UiStyleLength.px(12))
                .setPaddingRight(UiStyleLength.px(12))
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
            cycleSecBtn.setActionHandler(ev -> cycleSecurity());
            btnRow.append(cycleSecBtn.getElement());

            final DocumentButtonControl applyBtn = button(
                QzNetworkUiKit.tr("gui.singularityme.network_terminal.settings.apply"));
            applyBtn.getElement()
                .style()
                .setHeight(UiStyleLength.px(Palette.ROW_H))
                .setPaddingLeft(UiStyleLength.px(12))
                .setPaddingRight(UiStyleLength.px(12))
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
            applyBtn.setActionHandler(ev -> applySettings());
            btnRow.append(applyBtn.getElement());

            if (sel.isOwner) {
                final DocumentButtonControl deleteBtn = dangerButton(
                    QzNetworkUiKit.tr("gui.singularityme.network_terminal.settings.delete"));
                deleteBtn.getElement()
                    .style()
                    .setHeight(UiStyleLength.px(Palette.ROW_H))
                    .setPaddingLeft(UiStyleLength.px(12))
                    .setPaddingRight(UiStyleLength.px(12))
                    .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
                deleteBtn.setActionHandler(ev -> {
                    SingularityChannel.CHANNEL.sendToServer(new PacketDeleteNetwork(sel.networkID));
                    selectedNetworkID = 0;
                    panel = Panel.SELECTION;
                    requestNetworkData();
                });
                btnRow.append(deleteBtn.getElement());
            }
        }

        void renderCreate() {
            content.append(scrollBox(editForm(true, null)));

            final ElementNode btnRow = div();
            btnRow.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setFlexShrink(0.0F)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setPaddingLeft(UiStyleLength.px(12))
                .setPaddingRight(UiStyleLength.px(12))
                .setPaddingBottom(UiStyleLength.px(10))
                .setGap(UiStyleLength.px(8));
            content.append(btnRow);

            final DocumentButtonControl cycleSecBtn = button(
                QzNetworkUiKit.tr("gui.singularityme.network_terminal.settings.cycle_security"));
            cycleSecBtn.getElement()
                .style()
                .setHeight(UiStyleLength.px(Palette.ROW_H))
                .setPaddingLeft(UiStyleLength.px(12))
                .setPaddingRight(UiStyleLength.px(12))
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
            cycleSecBtn.setActionHandler(ev -> cycleSecurity());
            btnRow.append(cycleSecBtn.getElement());

            final DocumentButtonControl createBtn = button(
                QzNetworkUiKit.tr("gui.singularityme.network_terminal.create.confirm"));
            createBtn.getElement()
                .style()
                .setHeight(UiStyleLength.px(Palette.ROW_H))
                .setPaddingLeft(UiStyleLength.px(12))
                .setPaddingRight(UiStyleLength.px(12))
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
            createBtn.setActionHandler(ev -> createNetwork());
            btnRow.append(createBtn.getElement());
        }

        ElementNode editForm(final boolean isCreate, final NetworkEntry sel) {
            final ElementNode form = div();
            form.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setGap(UiStyleLength.px(8))
                .setPadding(UiStyleLength.px(12))
                .setBoxSizing(UiBoxSizing.BORDER_BOX);

            final DocumentTextInputControl nameCtrl = isCreate ? createNameInput : settingsNameInput;
            if (!isCreate && sel != null
                && nameCtrl.getText()
                    .isEmpty()) {
                nameCtrl.setText(sel.name);
            }
            form.append(
                fieldRow(
                    QzNetworkUiKit.tr("gui.singularityme.network_terminal.create.name_label"),
                    nameCtrl.getElement()));

            if (selectedSecurity == SecurityLevel.ENCRYPTED) {
                final DocumentTextInputControl pwCtrl = isCreate ? createPasswordInput : settingsPasswordInput;
                form.append(
                    fieldRow(
                        QzNetworkUiKit.tr("gui.singularityme.network_terminal.create.password_label"),
                        pwCtrl.getElement()));
            }

            form.append(
                fieldRow(
                    QzNetworkUiKit.tr("gui.singularityme.network_terminal.settings.security"),
                    readOnly(QzNetworkUiKit.securityName(selectedSecurity))));

            form.append(label(QzNetworkUiKit.tr("gui.singularityme.network_terminal.settings.color")));
            form.append(colorPalette());

            form.append(previewBar(isCreate ? defaultNetworkName() : (sel != null ? sel.name : "")));

            return form;
        }

        ElementNode previewBar(final String name) {
            final int color = selectedColorFull();
            final ElementNode bar = div();
            bar.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setGap(UiStyleLength.px(8))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(Palette.BG_LIST)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(color)
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW))
                .setBoxSizing(UiBoxSizing.BORDER_BOX);

            bar.append(QzNetworkUiKit.colorSwatch(document, color, 12));

            final ElementNode nameEl = div();
            nameEl.appendText(
                name.isEmpty() ? QzNetworkUiKit.tr("gui.singularityme.network_terminal.create.name_placeholder")
                    : name);
            nameEl.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(0))
                .setTextColor(color)
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTextOverflow(UiTextOverflow.ELLIPSIS);
            bar.append(nameEl);

            bar.append(
                QzNetworkUiKit.badge(
                    document,
                    QzNetworkUiKit.securityName(selectedSecurity),
                    QzNetworkUiKit.securityColor(selectedSecurity)));
            return bar;
        }

        void renderInfo() {
            final NetworkEntry sel = selectedEntry();
            if (sel == null) {
                content.append(emptyState());
                return;
            }
            final ElementNode infoContent = div();
            infoContent.append(infoLine(QzNetworkUiKit.tr("gui.singularityme.network_terminal.info.id"), "#" + sel.networkID));
            infoContent.append(infoLine(QzNetworkUiKit.tr("gui.singularityme.network_terminal.info.name"), sel.name));
            infoContent.append(
                infoLine(
                    QzNetworkUiKit.tr("gui.singularityme.network_terminal.info.security"),
                    QzNetworkUiKit.securityName(sel)));
            infoContent.append(
                infoLine(
                    QzNetworkUiKit.tr("gui.singularityme.network_terminal.info.access"),
                    QzNetworkUiKit.accessName(sel)));
            infoContent.append(
                infoLine(
                    QzNetworkUiKit.tr("gui.singularityme.network_terminal.info.members"),
                    String.valueOf(sel.adminPlayerIDs.size() + sel.memberPlayerIDs.size())));
            infoContent.append(infoLine(QzNetworkUiKit.tr("gui.singularityme.network_terminal.info.owner"), sel.ownerName));
            infoContent.append(
                infoLine(
                    QzNetworkUiKit.tr("gui.singularityme.network_terminal.info.password"),
                    sel.isPasswordProtected ? QzNetworkUiKit.tr("gui.singularityme.network_terminal.info.password_yes")
                        : QzNetworkUiKit.tr("gui.singularityme.network_terminal.info.password_no")));
            content.append(scrollBox(infoContent));
        }

        ElementNode networkList() {
            networkRowNodes.clear();
            final ElementNode list = div();
            list.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setBackgroundColor(Palette.BG_LIST)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(Palette.BORDER_LIST)
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));

            if (networks.isEmpty()) {
                final ElementNode empty = div();
                empty.appendText(QzNetworkUiKit.tr("gui.singularityme.network_tab.no_networks"));
                empty.style()
                    .setPadding(UiStyleLength.px(10))
                    .setTextColor(Palette.TEXT_EMPTY)
                    .setTextAlign(UiTextAlign.CENTER);
                list.append(empty);
            } else {
                for (final NetworkEntry entry : networks) {
                    final ElementNode row = networkRow(entry);
                    networkRowNodes.put(entry.networkID, row);
                    list.append(row);
                }
            }
            return list;
        }

        ElementNode networkRow(final NetworkEntry entry) {
            final boolean selected = entry.networkID == selectedNetworkID;
            final boolean isDefault = entry.networkID != 0 && entry.networkID == defaultNetworkID;
            final int color = QzNetworkUiKit.entryColor(entry);

            final ElementNode row = div();
            row.setClassName("net-row");
            row.style()
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setHeight(UiStyleLength.px(Palette.ROW_H))
                .setPadding(UiStyleLength.px(6))
                .setGap(UiStyleLength.px(8))
                .setBackgroundColor(selected ? QzNetworkUiKit.darken(color, 0.32F) : Palette.BG_ROW)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(selected ? color : Palette.BORDER_ROW)
                .setCursor(UiCursor.POINTER);
            row.setClickHandler(ev -> {
                final int prevID = selectedNetworkID;
                selectedNetworkID = entry.networkID;
                selectedMemberID = -1;
                loadSelectedFields();
                updateNetworkRowStyle(prevID);
                updateNetworkRowStyle(entry.networkID);
                updateNetworkBar();
                updateSelectionSummary();
                renderSelectionActions();
                return true;
            });

            row.append(QzNetworkUiKit.colorSwatch(document, color, 14));
            row.append(QzNetworkUiKit.idPill(document, entry.networkID));

            final ElementNode name = div();
            name.appendText(
                entry.networkID == 0 ? QzNetworkUiKit.tr("gui.singularityme.network_tab.default") : entry.name);
            name.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(0))
                .setTextColor(selected ? Palette.TEXT_BADGE : Palette.TEXT_SECONDARY)
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTextOverflow(UiTextOverflow.ELLIPSIS);
            row.append(name);

            row.append(
                QzNetworkUiKit
                    .badge(document, QzNetworkUiKit.securityShort(entry), QzNetworkUiKit.securityColor(entry)));
            row.append(
                QzNetworkUiKit.badge(document, QzNetworkUiKit.accessShort(entry), QzNetworkUiKit.accessColor(entry)));
            if (isDefault) row.append(QzNetworkUiKit.badge(document, "D", Palette.BADGE_DEFAULT));
            return row;
        }

        ElementNode colorPalette() {
            final int[] colors = { 0xFF4A90E2, 0xFF7B68EE, 0xFFE24A4A, 0xFFE2A84A, 0xFF4AE24A, 0xFF4AE2D0, 0xFFE24AB0,
                0xFFFFFFFF };
            final ElementNode row = div();
            row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setGap(UiStyleLength.px(6));
            for (final int c : colors) {
                final boolean sel = (c & 0xFFFFFF) == (selectedColor & 0xFFFFFF);
                final ElementNode swatch = QzNetworkUiKit.colorSwatch(document, c, sel ? 18 : 14);
                swatch.setClassName("swatch-btn");
                swatch.style()
                    .setCursor(UiCursor.POINTER);
                swatch.setClickHandler(ev -> {
                    selectedColor = c & 0xFFFFFF;
                    render();
                    return true;
                });
                row.append(swatch);
            }
            return row;
        }

        ElementNode fieldRow(final String labelText, final ElementNode field) {
            final ElementNode row = div();
            row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setGap(UiStyleLength.px(8));

            final ElementNode lbl = div();
            lbl.appendText(labelText);
            lbl.style()
                .setWidth(UiStyleLength.px(80))
                .setFlexShrink(0.0F)
                .setTextColor(Palette.TEXT_LABEL)
                .setTextAlign(UiTextAlign.END);
            row.append(lbl);
            row.append(field);
            return row;
        }

        ElementNode readOnly(final String value) {
            final ElementNode el = div();
            el.appendText(value);
            el.style()
                .setFlexGrow(1.0F)
                .setHeight(UiStyleLength.px(Palette.ROW_H))
                .setPaddingLeft(UiStyleLength.px(8))
                .setPaddingRight(UiStyleLength.px(8))
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setBackgroundColor(Palette.BG_INPUT)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(Palette.BORDER_INPUT_NORMAL)
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW))
                .setTextColor(Palette.TEXT_MUTED)
                .setBoxSizing(UiBoxSizing.BORDER_BOX);
            return el;
        }

        ElementNode infoLine(final String labelText, final String value) {
            final ElementNode row = div();
            row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setHeight(UiStyleLength.px(Palette.ROW_H))
                .setPaddingLeft(UiStyleLength.px(10))
                .setPaddingRight(UiStyleLength.px(10))
                .setGap(UiStyleLength.px(8))
                .setBackgroundColor(Palette.BG_ROW)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(Palette.BORDER_ROW)
                .setBoxSizing(UiBoxSizing.BORDER_BOX);

            final ElementNode lbl = div();
            lbl.appendText(labelText);
            lbl.style()
                .setWidth(UiStyleLength.px(100))
                .setFlexShrink(0.0F)
                .setTextColor(Palette.TEXT_LABEL);
            row.append(lbl);

            final ElementNode val = div();
            val.appendText(value);
            val.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(0))
                .setTextColor(Palette.TEXT_SECONDARY)
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTextOverflow(UiTextOverflow.ELLIPSIS);
            row.append(val);
            return row;
        }

        ElementNode actionRow() {
            final ElementNode row = div();
            row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setFlexShrink(0.0F)
                .setGap(UiStyleLength.px(8))
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setPaddingLeft(UiStyleLength.px(12))
                .setPaddingRight(UiStyleLength.px(12))
                .setPaddingBottom(UiStyleLength.px(10));
            return row;
        }

        /**
         * 创建两层滚动容器：外层占据 flex 剩余空间（不设 height:auto 避免 Qz 误判），
         * 内层以 height:auto 填满外层并独立驱动 overflow-y:scroll。
         */
        ElementNode scrollBox(final ElementNode child) {
            // 外层：只负责 flex-grow 占据剩余空间，height 设为 px(1) 确保非 auto
            final ElementNode outer = div();
            outer.style()
                .setFlexGrow(1.0F)
                .setFlexShrink(1.0F)
                .setFlexBasis(UiStyleLength.px(0))
                .setHeight(UiStyleLength.px(1))
                .setMinHeight(UiStyleLength.px(120))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setBoxSizing(UiBoxSizing.BORDER_BOX);
            // 内层：填满外层，height:auto 独立生效驱动滚动
            final ElementNode inner = div();
            inner.style()
                .setHeight(UiStyleLength.auto())
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.SCROLL)
                .setScrollbarWidth(UiScrollbarWidth.THIN)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setPaddingLeft(UiStyleLength.px(12))
                .setPaddingRight(UiStyleLength.px(18))
                .setPaddingTop(UiStyleLength.px(6))
                .setPaddingBottom(UiStyleLength.px(6));
            if (child != null) inner.append(child);
            outer.append(inner);
            return outer;
        }

        ElementNode emptyState() {
            final ElementNode el = div();
            el.appendText(QzNetworkUiKit.tr("gui.singularityme.network_terminal.hint.select_network"));
            el.style()
                .setFlexGrow(1.0F)
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setTextColor(Palette.TEXT_EMPTY);
            return el;
        }

        ElementNode label(final String text) {
            final ElementNode el = div();
            el.appendText(text);
            el.style()
                .setTextColor(Palette.TEXT_LABEL)
                .setFontWeight(UiFontWeight.BOLD)
                .setMarginBottom(UiStyleLength.px(2));
            return el;
        }

        void cycleSecurity() {
            final SecurityLevel[] vals = SecurityLevel.values();
            selectedSecurity = vals[(selectedSecurity.ordinal() + 1) % vals.length];
            if (selectedSecurity != SecurityLevel.ENCRYPTED) {
                if (panel == Panel.CREATE) {
                    maskedCreatePassword.clear();
                } else if (panel == Panel.SETTINGS) {
                    maskedSettingsPassword.clear();
                }
            }
            render();
        }

        void applySettings() {
            final NetworkEntry sel = selectedEntry();
            if (sel == null) return;
            final String newName = settingsNameInput.getText()
                .trim();
            final String name = newName.isEmpty() ? sel.name : newName;
            final String pw = selectedSecurity == SecurityLevel.ENCRYPTED
                ? SingularityNetworkRegistry.sha256Hex(maskedSettingsPassword.getRealValue())
                : "";
            SingularityChannel.CHANNEL.sendToServer(
                new PacketSetNetworkSettings(
                    sel.networkID,
                    selectedColorFull() & 0xFFFFFF,
                    selectedSecurity.ordinal(),
                    pw));
            if (!newName.isEmpty() && !newName.equals(sel.name)) {
                SingularityChannel.CHANNEL.sendToServer(new PacketRenameNetwork(sel.networkID, name));
            }
            requestNetworkData();
        }

        void createNetwork() {
            final String name = createNameInput.getText()
                .trim();
            if (name.isEmpty()) return;
            final String pw = selectedSecurity == SecurityLevel.ENCRYPTED
                ? SingularityNetworkRegistry.sha256Hex(maskedCreatePassword.getRealValue())
                : "";
            SingularityChannel.CHANNEL.sendToServer(
                new PacketCreateNetwork(
                    x,
                    y,
                    z,
                    dim,
                    name,
                    selectedColorFull() & 0xFFFFFF,
                    selectedSecurity.ordinal(),
                    pw));
            createNameInput.setText("");
            maskedCreatePassword.clear();
            panel = Panel.SELECTION;
            requestNetworkData();
        }

        void addMember() {
            final NetworkEntry sel = selectedEntry();
            if (sel == null) return;
            final String name = memberNameInput.getText()
                .trim();
            if (name.isEmpty()) return;
            SingularityChannel.CHANNEL.sendToServer(new PacketAddMemberByName(sel.networkID, name));
            memberNameInput.setText("");
            requestNetworkData();
        }

        void setMemberRole(final int networkID, final int playerID, final AccessLevel role) {
            SingularityChannel.CHANNEL.sendToServer(new PacketSetMemberRole(networkID, playerID, role.ordinal()));
            requestNetworkData();
        }

        void loadSelectedFields() {
            final NetworkEntry sel = selectedEntry();
            if (sel != null) {
                selectedColor = sel.color & 0xFFFFFF;
                selectedSecurity = SecurityLevel.fromOrdinal(sel.securityOrdinal);
                settingsNameInput.setText(sel.name);
                maskedSettingsPassword.clear();
            }
        }

        void installKeyHandlers() {
            createNameInput.setKeyHandler(ev -> {
                if (QzNetworkUiKit.isSubmitKey(ev.getKeyCode(), ev.getAction())) {
                    createNetwork();
                    return true;
                }
                return false;
            });
            createPasswordInput.setKeyHandler(ev -> {
                if (QzNetworkUiKit.isSubmitKey(ev.getKeyCode(), ev.getAction())) {
                    createNetwork();
                    return true;
                }
                return false;
            });
            settingsNameInput.setKeyHandler(ev -> {
                if (QzNetworkUiKit.isSubmitKey(ev.getKeyCode(), ev.getAction())) {
                    applySettings();
                    return true;
                }
                return false;
            });
            settingsPasswordInput.setKeyHandler(ev -> {
                if (QzNetworkUiKit.isSubmitKey(ev.getKeyCode(), ev.getAction())) {
                    applySettings();
                    return true;
                }
                return false;
            });
            memberNameInput.setKeyHandler(ev -> {
                if (QzNetworkUiKit.isSubmitKey(ev.getKeyCode(), ev.getAction())) {
                    addMember();
                    return true;
                }
                return false;
            });
        }

        ElementNode focusedInput() {
            if (createNameInput.isFocused()) return createNameInput.getElement();
            if (createPasswordInput.isFocused()) return createPasswordInput.getElement();
            if (settingsNameInput.isFocused()) return settingsNameInput.getElement();
            if (settingsPasswordInput.isFocused()) return settingsPasswordInput.getElement();
            if (memberNameInput.isFocused()) return memberNameInput.getElement();
            return null;
        }

        void restoreFocus(final ElementNode focused) {
            if (focused != null) focused.focus();
        }

        boolean isInputVisible(final DocumentTextInputControl ctrl) {
            return ctrl.isEnabled();
        }

        NetworkEntry selectedEntry() {
            for (final NetworkEntry e : networks) {
                if (e.networkID == selectedNetworkID) return e;
            }
            return null;
        }

        NetworkEntry entryByID(final int id) {
            for (final NetworkEntry e : networks) {
                if (e.networkID == id) return e;
            }
            return null;
        }

        int firstNetworkID() {
            for (final NetworkEntry e : networks) {
                if (e.networkID != 0) return e.networkID;
            }
            return 0;
        }

        int visibleNetworkCount() {
            int count = 0;
            for (final NetworkEntry e : networks) {
                if (e.networkID != 0) count++;
            }
            return count;
        }

        boolean hasMember(final NetworkEntry net, final int playerID) {
            return net.adminPlayerIDs.contains(playerID) || net.memberPlayerIDs.contains(playerID)
                || net.blockedPlayerIDs.contains(playerID);
        }

        String defaultNetworkName() {
            final NetworkEntry e = entryByID(defaultNetworkID);
            return e != null ? e.name : QzNetworkUiKit.tr("gui.singularityme.network_tab.default");
        }

        int selectedColorFull() {
            return 0xFF000000 | (selectedColor & 0xFFFFFF);
        }

        /** 根据当前 selectedNetworkID 更新指定网络行的背景色和边框色。 */
        void updateNetworkRowStyle(final int networkID) {
            final ElementNode row = networkRowNodes.get(networkID);
            if (row == null) return;
            final NetworkEntry entry = entryByID(networkID);
            if (entry == null) return;
            final boolean selected = networkID == selectedNetworkID;
            final int color = QzNetworkUiKit.entryColor(entry);
            row.style()
                .setBackgroundColor(selected ? QzNetworkUiKit.darken(color, 0.32F) : Palette.BG_ROW)
                .setBorderColor(selected ? color : Palette.BORDER_ROW);
        }

        /** 根据当前 selectedMemberID 更新指定成员行的背景色和边框色。 */
        void updateMemberRowStyle(final int playerID) {
            final ElementNode row = memberRowNodes.get(playerID);
            if (row == null) return;
            final AccessLevel role = memberRole(selectedEntry(), playerID);
            if (role == AccessLevel.NONE) return;
            final boolean selected = playerID == selectedMemberID;
            final int roleColor = QzNetworkUiKit.accessColor(role);
            row.style()
                .setBackgroundColor(selected ? QzNetworkUiKit.darken(roleColor, 0.25F) : Palette.BG_ROW)
                .setBorderColor(selected ? roleColor : Palette.BORDER_ROW);
        }

        AccessLevel memberRole(final NetworkEntry net, final int playerID) {
            if (net == null) return AccessLevel.NONE;
            if (playerID == net.ownerPlayerID) return AccessLevel.OWNER;
            if (net.adminPlayerIDs.contains(playerID)) return AccessLevel.ADMIN;
            if (net.memberPlayerIDs.contains(playerID)) return AccessLevel.MEMBER;
            if (net.blockedPlayerIDs.contains(playerID)) return AccessLevel.BLOCKED;
            return AccessLevel.NONE;
        }

        DocumentButtonControl button(final String label) {
            final DocumentButtonControl btn = new DocumentButtonControl(document, label)
                .setBackgroundColors(Palette.BTN_NORMAL, Palette.BTN_ACTIVE, Palette.BTN_DISABLED)
                .setTextColors(Palette.TEXT_BADGE, Palette.TEXT_INPUT_BUTTON_DISABLED)
                .setFocusBorderColor(Palette.BTN_FOCUS_BORDER);
            btn.getElement()
                .style()
                .setFontWeight(UiFontWeight.BOLD);
            return btn;
        }

        ElementNode div() {
            return document.div();
        }

        DocumentButtonControl dangerButton(final String label) {
            final DocumentButtonControl btn = new DocumentButtonControl(document, label)
                .setBackgroundColors(Palette.BTN_DANGER_NORMAL, Palette.BTN_DANGER_ACTIVE, Palette.BTN_DANGER_DISABLED)
                .setTextColors(Palette.TEXT_BADGE, Palette.TEXT_INPUT_BUTTON_DISABLED)
                .setFocusBorderColor(Palette.BTN_FOCUS_BORDER);
            btn.getElement()
                .style()
                .setFontWeight(UiFontWeight.BOLD);
            return btn;
        }

        DocumentTextInputControl input(final String placeholder) {
            final DocumentTextInputControl ctrl = new DocumentTextInputControl(document).setPlaceholder(placeholder)
                .setMaxLength(64)
                .setNormalBackgroundColor(Palette.BG_INPUT)
                .setNormalBorderColor(Palette.BORDER_INPUT_NORMAL)
                .setFocusBorderColor(Palette.BORDER_INPUT_FOCUS)
                .setTextColors(Palette.TEXT_INPUT, Palette.TEXT_INPUT_PLACEHOLDER, Palette.TEXT_INPUT_DISABLED);
            ctrl.getElement()
                .style()
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(Palette.ROW_H))
                .setPadding(UiStyleLength.px(8))
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
            return ctrl;
        }
    }
}
