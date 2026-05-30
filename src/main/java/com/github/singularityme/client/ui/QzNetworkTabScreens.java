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
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;
import com.github.singularityme.network.packet.PacketJoinEncryptedNetwork;
import com.github.singularityme.network.packet.PacketNetworkTabData;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;
import com.github.singularityme.network.packet.PacketRequestNetworkTabData;
import com.github.singularityme.network.packet.PacketSetDeviceNetwork;

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
import club.heiqi.uilib.ui.style.props.UiTextOverflow;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

public final class QzNetworkTabScreens {

    private static WeakReference<NetworkTabState> activeState;

    private QzNetworkTabScreens() {}

    public static GuiScreen create(final TileEntity te) {
        final int x = te == null ? 0 : te.xCoord;
        final int y = te == null ? 0 : te.yCoord;
        final int z = te == null ? 0 : te.zCoord;
        final int dim = te != null && te.getWorldObj() != null && te.getWorldObj().provider != null
            ? te.getWorldObj().provider.dimensionId
            : 0;

        final NetworkTabState[] stateRef = new NetworkTabState[1];
        final GuiScreen screen = UiDocumentScreens.createDocumentScreen(document -> {
            final NetworkTabState state = new NetworkTabState(document, x, y, z, dim);
            stateRef[0] = state;
            state.build();
        });
        if (stateRef[0] != null) {
            stateRef[0].screen = screen;
            activeState = new WeakReference<>(stateRef[0]);
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    final NetworkTabState state = activeState == null ? null : activeState.get();
                    if (state == stateRef[0]) {
                        state.requestNetworkData();
                    }
                });
        }
        return screen;
    }

    public static boolean receiveNetworkData(final PacketNetworkTabData packet) {
        final NetworkTabState state = activeState == null ? null : activeState.get();
        if (state == null || state.screen != Minecraft.getMinecraft().currentScreen) return false;
        state.receive(packet);
        return true;
    }

    private static final class NetworkTabState {

        // 对应 HTML .panel: width: min(90vw, 720px); height: min(90vh, 500px)
        private static final int PANEL_W = 720;
        private static final int PANEL_H = 500;

        private final UiDocument document;
        private final int x;
        private final int y;
        private final int z;
        private final int dim;
        private final List<NetworkEntry> networks = new ArrayList<>();

        private GuiScreen screen;
        private ElementNode summary;
        private ElementNode listHeader;
        private ElementNode list;
        private ElementNode bottom;
        private DocumentTextInputControl passwordInput;
        private QzNetworkUiKit.MaskedInput maskedPassword;
        private DocumentButtonControl selectButton;
        private DocumentButtonControl joinButton;
        private DocumentButtonControl cancelButton;

        private int selectedNetworkID = 0;
        private int deviceNetworkID = 0;
        private int defaultNetworkID = 0;
        private boolean passwordMode = false;
        /** networkID → 对应的行节点，用于选中态增量更新。数据包刷新时重建。 */
        private final Map<Integer, ElementNode> rowNodes = new HashMap<>();

        private NetworkTabState(final UiDocument document, final int x, final int y, final int z, final int dim) {
            this.document = document;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
        }

        private void build() {
            final ElementNode root = this.document.getRootElement();
            root.clearChildren();
            root.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setBackgroundColor(Palette.BG_OVERLAY);

            final ElementNode panel = div();
            panel.style()
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(0.90F))
                .setHeight(UiStyleLength.percent(0.90F))
                .setMaxWidth(UiStyleLength.px(PANEL_W))
                .setMaxHeight(UiStyleLength.px(PANEL_H))
                .setPadding(UiStyleLength.px(14))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setGap(UiStyleLength.px(10))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setBackgroundColor(Palette.BG_PANEL)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(Palette.BORDER_PANEL)
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_PANEL));
            root.append(panel);

            panel.append(titleRow());

            this.summary = div();
            this.summary.style()
                .setDisplay(UiDisplay.FLEX)
                .setGap(UiStyleLength.px(10))
                .setHeight(UiStyleLength.px(56))
                .setFlexShrink(0.0F);
            panel.append(this.summary);

            this.listHeader = div();
            this.listHeader.style()
                .setDisplay(UiDisplay.FLEX)
                .setJustifyContent(UiJustifyContent.SPACE_BETWEEN)
                .setHeight(UiStyleLength.px(24))
                .setFlexShrink(0.0F)
                .setTextColor(Palette.TEXT_MUTED);
            panel.append(this.listHeader);

            this.list = div();
            this.list.style()
                .setFlexGrow(1.0F)
                .setFlexShrink(1.0F)
                .setFlexBasis(UiStyleLength.px(0))
                .setHeight(UiStyleLength.auto())
                .setMinHeight(UiStyleLength.px(72))
                .setOverflowY(UiOverflow.SCROLL)
                .setOverflowX(UiOverflow.HIDDEN)
                .setScrollbarWidth(UiScrollbarWidth.THIN)
                .setBackgroundColor(Palette.BG_LIST)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(Palette.BORDER_LIST)
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
            panel.append(this.list);

            this.passwordInput = input(QzNetworkUiKit.tr("gui.singularityme.network_tab.password"));
            this.maskedPassword = QzNetworkUiKit.MaskedInput.wrap(this.passwordInput);
            this.selectButton = button(QzNetworkUiKit.tr("gui.singularityme.network_tab.select"), 180);
            this.joinButton = button(QzNetworkUiKit.tr("gui.singularityme.network_tab.join"), 110);
            this.cancelButton = button(QzNetworkUiKit.tr("gui.singularityme.network_tab.cancel"), 110);

            this.selectButton.setActionHandler(event -> selectNetwork());
            this.joinButton.setActionHandler(event -> joinEncryptedNetwork());
            this.cancelButton.setActionHandler(event -> {
                this.passwordMode = false;
                this.maskedPassword.clear();
                renderBottom();
            });
            this.passwordInput.setKeyHandler(event -> {
                if (QzNetworkUiKit.isSubmitKey(event.getKeyCode(), event.getAction())) {
                    joinEncryptedNetwork();
                    return true;
                }
                return false;
            });

            this.bottom = div();
            this.bottom.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setGap(UiStyleLength.px(8))
                .setFlexShrink(0.0F);
            panel.append(this.bottom);

            QzNetworkUiKit.installComponentStyleSheet(this.document);

            render();
        }

        private ElementNode titleRow() {
            final ElementNode row = div();
            row.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setHeight(UiStyleLength.px(34))
                .setFlexShrink(0.0F);

            final ElementNode title = div();
            title.appendText(QzNetworkUiKit.tr("gui.singularityme.network_tab.title"));
            title.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(0))
                .setTextColor(Palette.TEXT_PRIMARY)
                .setFontWeight(UiFontWeight.BOLD)
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setTextOverflow(UiTextOverflow.ELLIPSIS)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
            row.append(title);
            return row;
        }

        private void requestNetworkData() {
            SingularityChannel.CHANNEL.sendToServer(new PacketRequestNetworkTabData(this.x, this.y, this.z, this.dim));
        }

        private void receive(final PacketNetworkTabData packet) {
            this.networks.clear();
            this.networks.addAll(packet.networks);
            this.deviceNetworkID = packet.deviceNetworkID;
            this.defaultNetworkID = packet.defaultNetworkID;
            this.passwordMode = false;
            this.selectedNetworkID = packet.deviceNetworkID;
            if (selectedEntry() == null && !this.networks.isEmpty()) {
                this.selectedNetworkID = this.networks.get(0).networkID;
            }
            render();
        }

        private void render() {
            renderSummary();
            renderListHeader();
            renderList();
            renderBottom();
        }

        private void renderSummary() {
            this.summary.clearChildren();
            this.summary.append(
                summaryBox(
                    QzNetworkUiKit.tr("gui.singularityme.network_tab.device"),
                    displayNetworkName(this.deviceNetworkID),
                    colorForID(this.deviceNetworkID)));
            this.summary.append(
                summaryBox(
                    QzNetworkUiKit.tr("gui.singularityme.network_tab.default_network"),
                    displayNetworkName(this.defaultNetworkID),
                    colorForID(this.defaultNetworkID)));
        }

        private ElementNode summaryBox(final String label, final String value, final int color) {
            final ElementNode box = div();
            box.style()
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(0))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(QzNetworkUiKit.darken(color, 0.18F))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(color)
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));

            final ElementNode labelNode = div();
            labelNode.appendText(label);
            labelNode.style()
                .setTextColor(Palette.TEXT_MUTED);
            box.append(labelNode);

            final ElementNode valueNode = div();
            valueNode.appendText(value);
            valueNode.style()
                .setTextColor(Palette.TEXT_PRIMARY)
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setTextOverflow(UiTextOverflow.ELLIPSIS)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
            box.append(valueNode);
            return box;
        }

        private void renderListHeader() {
            this.listHeader.clearChildren();

            final ElementNode sort = div();
            sort.appendText(
                QzNetworkUiKit.trf(
                    "gui.singularityme.network_tab.sort_by",
                    QzNetworkUiKit.tr("gui.singularityme.network_tab.name")));
            this.listHeader.append(sort);

            final ElementNode total = div();
            total.appendText(QzNetworkUiKit.trf("gui.singularityme.network_tab.total", visibleNetworkCount()));
            this.listHeader.append(total);
        }

        private void renderList() {
            this.list.clearChildren();
            this.rowNodes.clear();
            if (this.networks.isEmpty()) {
                final ElementNode empty = div();
                empty.appendText(QzNetworkUiKit.tr("gui.singularityme.network_tab.no_networks"));
                empty.style()
                    .setPadding(UiStyleLength.px(10))
                    .setTextColor(Palette.TEXT_EMPTY);
                this.list.append(empty);
                return;
            }

            for (final NetworkEntry entry : this.networks) {
                final ElementNode row = networkRow(entry);
                this.rowNodes.put(entry.networkID, row);
                this.list.append(row);
            }
        }

        private ElementNode networkRow(final NetworkEntry entry) {
            final boolean selected = entry.networkID == this.selectedNetworkID;
            final boolean current = entry.networkID == this.deviceNetworkID;
            final boolean defaultNetwork = entry.networkID != 0 && entry.networkID == this.defaultNetworkID;
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
            row.setClickHandler(event -> {
                final int prevID = this.selectedNetworkID;
                this.selectedNetworkID = entry.networkID;
                this.passwordMode = false;
                // 增量更新：只改前一个选中行和新选中行的样式，不重建整个列表
                updateRowSelectionStyle(prevID);
                updateRowSelectionStyle(entry.networkID);
                // bottom 区域（selectedBar + 按钮 enable 态）仍需重建
                renderBottom();
                return true;
            });

            row.append(QzNetworkUiKit.colorSwatch(this.document, color, 18));
            row.append(QzNetworkUiKit.idPill(this.document, entry.networkID));

            final ElementNode name = div();
            name.appendText(
                entry.networkID == 0 ? QzNetworkUiKit.tr("gui.singularityme.network_tab.default") : entry.name);
            name.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(0))
                .setTextColor(selected ? 0xFFFFFFFF : Palette.TEXT_SECONDARY)
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setTextOverflow(UiTextOverflow.ELLIPSIS)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
            row.append(name);

            row.append(
                QzNetworkUiKit
                    .badge(this.document, QzNetworkUiKit.securityShort(entry), QzNetworkUiKit.securityColor(entry)));
            row.append(
                QzNetworkUiKit
                    .badge(this.document, QzNetworkUiKit.accessShort(entry), QzNetworkUiKit.accessColor(entry)));
            if (current) row.append(QzNetworkUiKit.badge(this.document, "*", Palette.BADGE_CURRENT));
            if (defaultNetwork) row.append(QzNetworkUiKit.badge(this.document, "D", Palette.BADGE_DEFAULT));
            return row;
        }

        /**
         * 根据当前 selectedNetworkID 更新指定行的背景色和边框色。
         * 只操作已存在的节点，不重建 DOM。
         */
        private void updateRowSelectionStyle(final int networkID) {
            final ElementNode row = this.rowNodes.get(networkID);
            if (row == null) return;
            final NetworkEntry entry = entryForID(networkID);
            if (entry == null) return;
            final boolean selected = networkID == this.selectedNetworkID;
            final int color = QzNetworkUiKit.entryColor(entry);
            row.style()
                .setBackgroundColor(selected ? QzNetworkUiKit.darken(color, 0.32F) : Palette.BG_ROW)
                .setBorderColor(selected ? color : Palette.BORDER_ROW);
        }

        private NetworkEntry entryForID(final int networkID) {
            for (final NetworkEntry entry : this.networks) {
                if (entry.networkID == networkID) return entry;
            }
            return null;
        }

        private void renderBottom() {
            this.bottom.clearChildren();
            final NetworkEntry selected = selectedEntry();
            this.bottom.append(selectedBar(selected));
            if (this.passwordMode) {
                renderPasswordControls();
            } else {
                renderAssignControls();
            }
        }

        private ElementNode selectedBar(final NetworkEntry selected) {
            final ElementNode bar = div();
            bar.style()
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setGap(UiStyleLength.px(8))
                .setHeight(UiStyleLength.px(38))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(Palette.BG_LIST)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(selected == null ? Palette.BORDER_LIST : QzNetworkUiKit.entryColor(selected))
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
            final ElementNode text = div();
            text.appendText(
                selected == null ? QzNetworkUiKit.tr("gui.singularityme.network_tab.no_selection")
                    : QzNetworkUiKit.trf("gui.singularityme.network_tab.selected", displayEntry(selected)));
            text.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(0))
                .setTextColor(Palette.TEXT_SECONDARY)
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setTextOverflow(UiTextOverflow.ELLIPSIS)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
            bar.append(text);
            return bar;
        }

        private void renderPasswordControls() {
            final ElementNode row = row(6);
            row.append(this.passwordInput.getElement());
            row.append(this.joinButton.getElement());
            row.append(this.cancelButton.getElement());
            this.bottom.append(row);
        }

        private void renderAssignControls() {
            final ElementNode row = row(6);
            row.append(this.selectButton.getElement());
            this.bottom.append(row);

            final NetworkEntry selected = selectedEntry();
            // 0 is the unassigned sentinel; accessible networks assign directly;
            // encrypted NONE entries switch to the password join flow.
            final boolean canAssign = selected != null && selected.networkID != this.deviceNetworkID
                && !QzNetworkUiKit.isBlocked(selected)
                && (selected.networkID == 0 || QzNetworkUiKit.canAccess(selected)
                    || QzNetworkUiKit.isEncryptedJoinRequired(selected));
            this.selectButton.setEnabled(canAssign);
        }

        private void selectNetwork() {
            final NetworkEntry selected = selectedEntry();
            if (selected == null) return;
            if (QzNetworkUiKit.isEncryptedJoinRequired(selected)) {
                this.passwordMode = true;
                this.maskedPassword.clear();
                renderBottom();
                this.passwordInput.getElement()
                    .focus();
                return;
            }
            SingularityChannel.CHANNEL
                .sendToServer(new PacketSetDeviceNetwork(this.x, this.y, this.z, this.dim, selected.networkID));
        }

        private void joinEncryptedNetwork() {
            final NetworkEntry selected = selectedEntry();
            if (selected == null) return;
            final String password = this.maskedPassword.getRealValue();
            if (password.isEmpty()) return;
            SingularityChannel.CHANNEL.sendToServer(
                new PacketJoinEncryptedNetwork(
                    this.x,
                    this.y,
                    this.z,
                    this.dim,
                    selected.networkID,
                    SingularityNetworkRegistry.sha256Hex(password)));
            this.maskedPassword.clear();
            this.passwordMode = false;
            renderBottom();
        }

        private NetworkEntry selectedEntry() {
            for (final NetworkEntry entry : this.networks) {
                if (entry.networkID == this.selectedNetworkID) return entry;
            }
            return null;
        }

        private int visibleNetworkCount() {
            int count = 0;
            for (final NetworkEntry entry : this.networks) {
                if (entry.networkID != 0) count++;
            }
            return count;
        }

        private String displayNetworkName(final int networkID) {
            if (networkID == 0) return QzNetworkUiKit.tr("gui.singularityme.network_tab.default");
            for (final NetworkEntry entry : this.networks) {
                if (entry.networkID == networkID) return entry.name;
            }
            return "#" + networkID;
        }

        private int colorForID(final int networkID) {
            for (final NetworkEntry entry : this.networks) {
                if (entry.networkID == networkID) return QzNetworkUiKit.entryColor(entry);
            }
            return networkID == 0 ? Palette.COLOR_UNASSIGNED : 0xFF536B7F;
        }

        private ElementNode div() {
            return this.document.div();
        }

        private ElementNode row(final int gap) {
            final ElementNode row = div();
            row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setGap(UiStyleLength.px(gap))
                .setFlexWrap(UiFlexWrap.WRAP)
                .setFlexShrink(0.0F);
            return row;
        }

        private DocumentButtonControl button(final String label, final int width) {
            final DocumentButtonControl button = new DocumentButtonControl(this.document, label)
                .setBackgroundColors(Palette.BTN_NORMAL, Palette.BTN_ACTIVE, Palette.BTN_DISABLED)
                .setTextColors(Palette.TEXT_BADGE, Palette.TEXT_INPUT_BUTTON_DISABLED)
                .setFocusBorderColor(Palette.BTN_FOCUS_BORDER);
            button.getElement()
                .style()
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.px(width))
                .setHeight(UiStyleLength.px(36))
                .setPadding(UiStyleLength.px(6))
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
            return button;
        }

        private DocumentTextInputControl input(final String placeholder) {
            final DocumentTextInputControl input = new DocumentTextInputControl(this.document)
                .setPlaceholder(placeholder)
                .setMaxLength(64)
                .setNormalBackgroundColor(Palette.BG_INPUT)
                .setNormalBorderColor(Palette.BORDER_INPUT_NORMAL)
                .setFocusBorderColor(Palette.BORDER_INPUT_FOCUS)
                .setTextColors(Palette.TEXT_INPUT, Palette.TEXT_INPUT_PLACEHOLDER, Palette.TEXT_INPUT_DISABLED);
            input.getElement()
                .style()
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(36))
                .setPadding(UiStyleLength.px(8))
                .setBorderRadius(UiStyleLength.px(Palette.BORDER_RADIUS_ROW));
            return input;
        }
    }

    private static String displayEntry(final NetworkEntry entry) {
        if (entry.networkID == 0) return QzNetworkUiKit.tr("gui.singularityme.network_tab.default");
        return "#" + entry.networkID
            + " "
            + entry.name
            + " "
            + QzNetworkUiKit.securityName(entry)
            + " "
            + QzNetworkUiKit.accessMark(entry);
    }
}
