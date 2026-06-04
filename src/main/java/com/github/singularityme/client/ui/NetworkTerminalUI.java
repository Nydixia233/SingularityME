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
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IIcon;
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
import com.github.singularityme.client.ui.NetworkUiKit.TerminalLayout;
import com.github.singularityme.core.PermissionBits;
import com.github.singularityme.core.SecurityLevel;
import com.github.singularityme.network.SingularityChannel;
import com.github.singularityme.network.packet.PacketCreateNetwork;
import com.github.singularityme.network.packet.PacketDeleteNetwork;
import com.github.singularityme.network.packet.PacketGrantPermissionByName;
import com.github.singularityme.network.packet.PacketNetworkStatus;
import com.github.singularityme.network.packet.PacketNetworkStatus.DeviceInfo;
import com.github.singularityme.network.packet.PacketNetworkActionResult;
import com.github.singularityme.network.packet.PacketNetworkTabData;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;
import com.github.singularityme.network.packet.PacketRenameNetwork;
import com.github.singularityme.network.packet.PacketRequestNetworkStatus;
import com.github.singularityme.network.packet.PacketRequestNetworkTabData;
import com.github.singularityme.network.packet.PacketSetDefaultNetwork;
import com.github.singularityme.network.packet.PacketSetNetworkSettings;
import com.github.singularityme.network.packet.PacketSetPermissions;

import appeng.api.config.SecurityPermissions;

/**
 * 5 面板网络终端 — MUI2 重写版。
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
        final GuiScreenWrapper wrapper = new TerminalScreenWrapper(screen);

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

    public static boolean receiveActionResult(final PacketNetworkActionResult packet) {
        final TerminalState state = activeState == null ? null : activeState.get();
        if (state == null) return false;
        if (Minecraft.getMinecraft().currentScreen instanceof GuiScreenWrapper w
            && w.getScreen().isPanelOpen("network_terminal")) {
            state.receiveActionResult(packet);
            return true;
        }
        return false;
    }

    private enum Panel { HOME, CONNECTION, MEMBERS, SETTINGS, CREATE }

    /** 网络终端专用屏幕包装器，绘制稳定遮罩以避免背景 hover 时闪烁。 */
    private static final class TerminalScreenWrapper extends GuiScreenWrapper {

        TerminalScreenWrapper(final ModularScreen screen) {
            super(screen);
        }

        @Override
        public void drawWorldBackground(final int tint) {
            drawGradientRect(0, 0, this.width, this.height, Palette.BG_OVERLAY, Palette.BG_OVERLAY_BOTTOM);
        }
    }

    /** 以 guiScale=2 为参考整体缩放终端面板，避免高 GUI 缩放下内部控件比例变胖。 */
    private static final class ReferenceScaledPanel extends ModularPanel {

        private final float visualScale;

        ReferenceScaledPanel(final String name, final float visualScale) {
            super(name);
            this.visualScale = visualScale;
        }

        @Override
        public float getScale() {
            return this.visualScale * super.getScale();
        }
    }

    private static final class TerminalState implements NetworkSelectionSurface.Delegate {
        final int x, y, z, dim;
        final List<NetworkEntry> networks = new ArrayList<>();
        int selectedNetworkID = -1;
        int defaultNetworkID;
        Panel currentPanel = Panel.HOME;
        int selectedColor = 0x4A90E2;
        SecurityLevel selectedSecurity = SecurityLevel.PRIVATE;
        PacketNetworkStatus networkStatus;
        final Map<Integer, PacketNetworkStatus> statusCache = new LinkedHashMap<>();
        /** 当前面板是否首次渲染（用于区分主动切换 vs 数据刷新触发） */
        boolean panelFirstRender = true;
        /** 内容视口复用同一个 ListWidget，切换页面/网络时需要清除旧滚动偏移。 */
        boolean resetContentScrollNextRender = true;

        ModularPanel panel;
        TerminalLayout layout;
        Flow navBar;
        Flow networkBar;
        Flow networkRail;
        NetworkSelectionSurface selectionSurface;
        ListWidget railList;
        ButtonWidget<?> railDefaultButton;
        ListWidget contentViewport;
        Flow contentArea;
        Flow bottomArea;
        int navButtonWidth;

        TextFieldWidget memberNameInput;
        StringValue memberNameVal = new StringValue("");
        TextFieldWidget filterInput;
        StringValue filterVal = new StringValue("");

        TextFieldWidget createNameInput;
        StringValue createNameVal = new StringValue("");

        TextFieldWidget settingsNameInput;
        StringValue settingsNameVal = new StringValue("");

        TerminalState(int x, int y, int z, int dim) {
            this.x = x; this.y = y; this.z = z; this.dim = dim;
        }

        ModularPanel buildPanel() {
            final Minecraft mc = Minecraft.getMinecraft();
            final ScaledResolution scaled = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            final int guiScale = Math.max(1, scaled.getScaleFactor());
            final int panelW = NetworkUiKit.terminalPanelWidth(mc.displayWidth, guiScale);
            final int panelH = NetworkUiKit.terminalPanelHeight(mc.displayHeight, guiScale);
            final float visualScale = NetworkUiKit.terminalVisualScale(guiScale);

            panel = new ReferenceScaledPanel("network_terminal", visualScale)
                .size(panelW, panelH)
                .background(new ShadowDrawable(Styles.panelBg(), 6, 0x80000000));
            panel.disableHoverBackground();

            layout = NetworkUiKit.terminalLayout(panelW, panelH, guiScale);

            // 导航栏
            navBar = Flow.row();
            navBar.childPadding(4).pos(layout.navX, layout.navY).size(layout.navW, layout.navH);
            navBar.padding(3);
            navBar.crossAxisAlignment(Alignment.CrossAxis.CENTER);
            navBar.background(Styles.headerGradient(Palette.BG_LIST));
            navBar.disableHoverBackground();
            navButtonWidth = NetworkUiKit.navButtonWidth(panelW, Panel.values().length);
            buildNavButtons();
            panel.child(navBar);

            // 网络信息栏
            networkBar = Flow.row()
                .childPadding(8).pos(layout.networkX, layout.networkY).size(layout.networkW, layout.networkH)
                .padding(0, 12)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER);
            panel.child(networkBar);

            // 内容区
            networkRail = Flow.column()
                .childPadding(4).pos(layout.railX, layout.railY).size(layout.railW, layout.railH)
                .padding(4)
                .background(Styles.listBg())
                .disableHoverBackground();
            panel.child(networkRail);

            contentViewport = new ListWidget();
            contentViewport.background(Styles.listBg());
            contentViewport.disableHoverBackground();
            contentViewport.pos(layout.contentX, layout.contentY);
            contentViewport.size(layout.contentW, layout.contentH);
            contentViewport.padding(Palette.CONTENT_VIEWPORT_PAD);
            contentViewport.showScrollShadows(false);
            contentArea = Flow.column()
                .childPadding(Palette.TERMINAL_CONTENT_CHILD_GAP).widthRel(1f).coverChildrenHeight();
            contentViewport.child(contentArea);
            panel.child(contentViewport);

            // 底部操作区
            bottomArea = Flow.column()
                .childPadding(8).pos(layout.bottomX, layout.bottomY).size(layout.bottomW, layout.bottomH);
            panel.child(bottomArea);

            // 输入控件
            memberNameInput = makeInput(memberNameVal);
            filterInput = makeRailInput(filterVal);
            createNameInput = makeInput(createNameVal);
            settingsNameInput = makeInput(settingsNameVal);
            selectionSurface = new NetworkSelectionSurface(NetworkSelectionSurface.Mode.TERMINAL_DEFAULT, this);

            return panel;
        }

        // ---- 导航按钮 ----

        @SuppressWarnings("unchecked")
        void buildNavButtons() {
            navBar.removeAll();
            for (Panel p : Panel.values()) {
                final boolean active = p == currentPanel;
                navBar.child(makeNavBtn(panelTitle(p), active, navButtonWidth, () -> {
                    if (currentPanel != p) {
                        currentPanel = p;
                        panelFirstRender = true;
                        resetContentScrollNextRender = true;
                        buildNavButtons();
                        if (isStatusPanel(p) && selectedRealEntry() != null) {
                            requestNetworkStatus();
                        }
                        renderContent(false);
                    }
                }));
            }
        }

        private static ButtonWidget<?> makeNavBtn(String text, boolean active, int width, Runnable action) {
            return new ButtonWidget<>()
                .overlay(IKey.str(text))
                .width(width).height(Palette.ROW_H - 8).padding(0, 8)
                .background(active ? Styles.navActiveBg() : IDrawable.NONE)
                .disableHoverBackground()
                .onMousePressed(mb -> { action.run(); return true; });
        }

        // ---- 内容渲染 ----

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void renderContent() {
            renderContent(true);
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void renderContent(final boolean refreshChrome) {
            if (refreshChrome) {
                renderNetworkRail();
                updateNetworkBar();
            }
            contentArea.removeAll();
            switch (currentPanel) {
                case HOME -> renderHome();
                case CONNECTION -> renderConnection();
                case MEMBERS -> renderMembers();
                case SETTINGS -> renderSettings();
                case CREATE -> renderCreate();
            }
            resetContentViewportScrollIfNeeded();
            contentViewport.scheduleResize();
            panelFirstRender = false;
        }

        private void resetContentViewportScrollIfNeeded() {
            if (!resetContentScrollNextRender) return;
            NetworkUiKit.resetListScroll(contentViewport);
            resetContentScrollNextRender = false;
        }

        // ---- 网络信息栏 ----

        @SuppressWarnings("unchecked")
        void updateNetworkBar() {
            networkBar.removeAll();
            networkBar.child(new TextWidget(IKey.dynamicKey(() -> IKey.str(panelTitle(currentPanel))))
                .color(Palette.TEXT_PRIMARY));

            final NetworkEntry sel = selectedEntry();
            if (sel != null) {
                final int c = NetworkUiKit.entryColor(sel);
                final String name = sel.networkID == 0
                    ? NetworkUiKit.tr("gui.singularityme.network_tab.default")
                    : sel.name;
                networkBar.child(NetworkUiKit.statusDotWidget(c));
                networkBar.child(new TextWidget(IKey.str(name)).color(c));
                if (sel.networkID != 0) {
                    networkBar.child(NetworkUiKit.idPill(sel.networkID));
                }
                if (sel.networkID != 0 && sel.networkID == defaultNetworkID) {
                    networkBar.child(NetworkUiKit.defaultBadge());
                }
            } else {
                networkBar.child(new TextWidget(IKey.str(NetworkUiKit.tr("gui.singularityme.network_tab.no_selection")))
                    .color(Palette.TEXT_EMPTY));
            }
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void renderNetworkRail() {
            if (selectionSurface == null) return;
            if (networkRail.getChildren().isEmpty()) {
                networkRail.child(selectionSurface.build(layout.railW, layout.railH, layout.railListH));
            }
            selectionSurface.rebuild();
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private void buildNetworkRailChrome() {
            networkRail.removeAll();
            networkRail.child(Flow.row()
                .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                .widthRel(1f).height(Palette.RAIL_HEADER_H)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new TextWidget(IKey.str(NetworkUiKit.tr("gui.singularityme.network_terminal.rail.title")))
                    .color(Palette.TEXT_PRIMARY))
                .child(new TextWidget(IKey.dynamicKey(() -> IKey.str(
                    NetworkUiKit.trf("gui.singularityme.network_tab.total", visibleNetworkCount()))))
                    .color(Palette.TEXT_MUTED)));

            networkRail.child(Flow.row()
                .widthRel(1f).height(Palette.RAIL_FILTER_H)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(filterInput.widthRel(1f).expanded()));

            railList = new ListWidget();
            railList.background(IDrawable.NONE);
            railList.disableHoverBackground();
            railList.childSeparator(IIcon.EMPTY_2PX);
            railList.padding(Palette.LIST_CONTENT_INSET, 0);
            railList.widthRel(1f);
            railList.height(layout.railListH);
            networkRail.child(railList);

            railDefaultButton = new ButtonWidget<>()
                .width(NetworkUiKit.railActionWidth(layout.railW)).height(Palette.RAIL_ACTION_H).padding(0, 6)
                .disableHoverBackground()
                .onMousePressed(mb -> {
                    final NetworkEntry sel = selectedRealEntry();
                    if (sel == null || !NetworkUiKit.hasPermission(sel, SecurityPermissions.BUILD)) return false;
                    final boolean isDefault = sel.networkID == defaultNetworkID;
                    final int nextDefault = isDefault ? 0 : sel.networkID;
                    SingularityChannel.CHANNEL.sendToServer(new PacketSetDefaultNetwork(nextDefault));
                    defaultNetworkID = nextDefault;
                    renderContent();
                    return true;
                });
            networkRail.child(railDefaultButton);
        }

        private void rebuildRailList() {
            railList.removeAll();
            railList.height(layout.railListH);
            for (final NetworkEntry entry : networks) {
                if (matchesFilter(entry)) {
                    railList.child(buildRailRow(entry));
                }
            }
            railList.scheduleResize();
        }

        private void updateRailDefaultButton() {
            final NetworkEntry sel = selectedRealEntry();
            final boolean isDefault = sel != null && sel.networkID == defaultNetworkID;
            final String btnText = isDefault
                ? NetworkUiKit.tr("gui.singularityme.network_terminal.selection.clear_default")
                : NetworkUiKit.tr("gui.singularityme.network_terminal.selection.set_default");
            final boolean canSet = sel != null && NetworkUiKit.hasPermission(sel, SecurityPermissions.BUILD);
            railDefaultButton.overlay(IKey.str(btnText));
            railDefaultButton.background(Styles.rowBg(canSet ? Palette.BTN_NORMAL : Palette.BTN_DISABLED));
        }

        private ButtonWidget<?> buildRailRow(final NetworkEntry entry) {
            final boolean selected = entry.networkID == selectedNetworkID;
            final int color = NetworkUiKit.entryColor(entry);
            final int bg = selected ? NetworkUiKit.selectedRowColor(color) : Palette.BG_ROW;
            final String name = entry.networkID == 0
                ? NetworkUiKit.tr("gui.singularityme.network_tab.default")
                : entry.name;
            final TextWidget nameWidget = new TextWidget(IKey.str(name))
                .color(selected ? Palette.TEXT_BADGE : Palette.TEXT_SECONDARY);
            nameWidget.expanded();

            final Flow rowContent = Flow.row()
                .childPadding(4).widthRel(1f).height(Palette.RAIL_ROW_H)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(NetworkUiKit.statusDotWidget(color))
                .child(NetworkUiKit.idPill(entry.networkID))
                .child(nameWidget);

            final ButtonWidget<?> row = new ButtonWidget<>()
                .child(rowContent)
                .widthRel(1f).height(Palette.RAIL_ROW_H)
                .padding(Palette.LIST_ROW_PADDING_H, 0)
                .background(Styles.rowBg(bg))
                .disableHoverBackground()
                .onMousePressed(mb -> {
                    selectNetwork(entry.networkID);
                    return true;
                });
            return row;
        }

        // ---- HOME ----

        @SuppressWarnings("unchecked")
        void renderHome() {
            bottomArea.removeAll();
            final NetworkEntry sel = selectedRealEntry();
            if (sel == null) {
                contentArea.child(unassignedState());
                return;
            }

            contentArea.child(sectionTitle(
                NetworkUiKit.tr("gui.singularityme.network_terminal.home.section.properties")));
            contentArea.child(homeInfoGrid(homePropertyRows(sel)));
            contentArea.child(sectionTitle(NetworkUiKit.tr("gui.singularityme.network_terminal.home.section.health")));
            contentArea.child(homeHealthWarnings());
            contentArea.child(sectionTitle(
                NetworkUiKit.tr("gui.singularityme.network_terminal.home.section.devices"),
                NetworkUiKit.formatHomeOnlineOverview(networkStatus)));
            contentArea.child(homeDeviceStatsGrid());
        }

        private Flow homeInfoGrid(final List<Flow> infoRows) {
            final Flow rows = Flow.column().childPadding(2).widthRel(1f).coverChildrenHeight();
            final int contentInnerW = NetworkUiKit.terminalContentInnerWidth(layout.contentW);
            final int columnWidth = NetworkUiKit.homeInfoColumnWidth(contentInnerW);
            if (NetworkUiKit.homeInfoUsesTwoColumns(contentInnerW)) {
                for (int i = 0; i < infoRows.size(); i += 2) {
                    final Flow pair = Flow.row().childPadding(2).widthRel(1f)
                        .height(Palette.COMPACT_ROW_H)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER);
                    pair.child(infoRows.get(i).width(columnWidth));
                    if (i + 1 < infoRows.size()) {
                        pair.child(infoRows.get(i + 1).width(columnWidth));
                    }
                    rows.child(pair);
                }
            } else {
                for (final Flow row : infoRows) {
                    rows.child(row.width(columnWidth));
                }
            }
            return rows;
        }

        private List<Flow> homePropertyRows(final NetworkEntry sel) {
            final List<Flow> rows = new ArrayList<>();
            rows.add(infoRow("ID", "#" + sel.networkID));
            rows.add(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.info.name"), sel.name));
            rows.add(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.info.owner"), sel.ownerName));
            rows.add(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.info.security"),
                NetworkUiKit.securityName(sel)));
            rows.add(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.info.access"),
                NetworkUiKit.accessName(sel)));
            rows.add(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.info.default"),
                sel.networkID == defaultNetworkID
                    ? NetworkUiKit.tr("gui.singularityme.network_terminal.yes")
                    : NetworkUiKit.tr("gui.singularityme.network_terminal.no")));
            rows.add(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.home.energy"),
                NetworkUiKit.formatHomeEnergyOverview(networkStatus)));
            rows.add(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.home.members"),
                sel.canManagePermissions ? String.valueOf(sel.authorizedPlayerIDs.size() + 1) : "-"));
            rows.add(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.home.created"),
                formatTimestamp(sel.createdAtMillis)));
            rows.add(infoRow(NetworkUiKit.tr("gui.singularityme.network_terminal.home.modified"),
                formatTimestamp(sel.lastModifiedMillis)));
            return rows;
        }

        @SuppressWarnings("unchecked")
        private Flow unassignedState() {
            return Flow.column()
                .childPadding(6).widthRel(1f).height(86)
                .mainAxisAlignment(Alignment.MainAxis.CENTER)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .background(Styles.cardBg())
                .disableHoverBackground()
                .child(new TextWidget(IKey.str(
                    NetworkUiKit.tr("gui.singularityme.network_terminal.home.unassigned_title")))
                    .color(Palette.TEXT_PRIMARY))
                .child(new TextWidget(IKey.str(
                    NetworkUiKit.tr("gui.singularityme.network_terminal.home.unassigned_body")))
                    .color(Palette.TEXT_EMPTY));
        }

        @SuppressWarnings("unchecked")
        private Flow homeHealthWarnings() {
            final Flow warnings = Flow.column().childPadding(3).widthRel(1f).coverChildrenHeight();
            if (networkStatus == null) {
                warnings.child(homeNoticeRow(
                    NetworkUiKit.tr("gui.singularityme.network_terminal.home.loading"),
                    Palette.TEXT_MUTED));
                return warnings;
            }
            boolean hasWarning = false;
            final int total = networkStatus.devices.size();
            final int loaded = countLoadedDevices();
            final int offline = total - loaded;
            if (!hasDeviceType("TileSingularityPowerCore")) {
                warnings.child(homeNoticeRow(
                    NetworkUiKit.tr("gui.singularityme.network_terminal.health.warn.no_power_core"),
                    Palette.BTN_DANGER_NORMAL));
                hasWarning = true;
            }
            if (total > 0 && loaded == 0) {
                warnings.child(homeNoticeRow(
                    NetworkUiKit.tr("gui.singularityme.network_terminal.health.warn.all_offline"),
                    Palette.BTN_DANGER_NORMAL));
                hasWarning = true;
            } else if (offline > 0) {
                warnings.child(homeNoticeRow(
                    NetworkUiKit.trf("gui.singularityme.network_terminal.health.warn.some_offline", offline),
                    Palette.ACCENT_AMBER));
                hasWarning = true;
            }
            if (!hasWarning) {
                warnings.child(homeNoticeRow(
                    NetworkUiKit.tr("gui.singularityme.network_terminal.health.healthy"),
                    Palette.SECURITY_PUBLIC));
            }
            return warnings;
        }

        @SuppressWarnings("unchecked")
        private Flow homeDeviceStatsGrid() {
            if (networkStatus == null) {
                return Flow.column()
                    .childPadding(2).widthRel(1f).coverChildrenHeight()
                    .child(homeNoticeRow(
                        NetworkUiKit.tr("gui.singularityme.network_terminal.home.loading"),
                        Palette.TEXT_MUTED));
            }

            final Map<String, Integer> counts = deviceTypeCounts();
            if (counts.isEmpty()) {
                return Flow.column()
                    .childPadding(2).widthRel(1f).coverChildrenHeight()
                    .child(homeNoticeRow(
                        NetworkUiKit.tr("gui.singularityme.network_terminal.home.no_devices"),
                        Palette.TEXT_EMPTY));
            }

            final List<Flow> rows = new ArrayList<>();
            for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
                rows.add(infoRow(
                    NetworkUiKit.deviceTypeLabel(entry.getKey()),
                    NetworkUiKit.formatCountBadge(entry.getValue())));
            }
            return homeInfoGrid(rows);
        }

        @SuppressWarnings("unchecked")
        private Flow sectionTitle(final String title) {
            return sectionTitle(title, null);
        }

        @SuppressWarnings("unchecked")
        private Flow sectionTitle(final String title, final String detail) {
            final Flow row = Flow.row()
                .childPadding(6).widthRel(1f).height(Palette.TEXT_ROW_H)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(Flow.row()
                    .width(3).heightRel(0.7f)
                    .background(Styles.rowBg(Palette.BTN_NORMAL))
                    .disableHoverBackground())
                .child(new TextWidget(IKey.str(title)).color(Palette.TEXT_PRIMARY));
            if (detail != null && !detail.isEmpty()) {
                row.child(new TextWidget(IKey.str(detail)).color(Palette.TEXT_MUTED));
            }
            return row;
        }

        @SuppressWarnings("unchecked")
        private Flow homeNoticeRow(final String text, final int color) {
            return Flow.row()
                .childPadding(6).widthRel(1f).height(Palette.COMPACT_ROW_H).padding(Palette.LIST_ROW_PADDING_H, 0)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .background(Styles.rowBg(Palette.BG_ROW))
                .disableHoverBackground()
                .child(NetworkUiKit.statusDotWidget(color))
                .child(new TextWidget(IKey.str(text)).color(color));
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
            list.disableHoverBackground();
            list.childSeparator(IIcon.EMPTY_2PX);
            list.padding(Palette.LIST_CONTENT_INSET, 0);
            list.widthRel(1f);
            list.height(NetworkUiKit.selectionListHeight(layout.contentH));
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
            final boolean canSet = sel != null && sel.networkID != 0
                && NetworkUiKit.hasPermission(sel, SecurityPermissions.BUILD);

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
                .childPadding(8).widthRel(1f).height(Palette.LIST_ROW_H)
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

            final ButtonWidget<?> row = new ButtonWidget<>()
                .child(rowContent)
                .widthRel(1f).height(Palette.LIST_ROW_H)
                .padding(Palette.LIST_ROW_PADDING_H, 0)
                .background(Styles.rowBg(bg))
                .disableHoverBackground()
                .onMousePressed(mb -> {
                    selectNetwork(entry.networkID);
                    return true;
                });
            return row;
        }

        // ---- CONNECTION ----

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void renderConnection() {
            bottomArea.removeAll();
            if (selectedRealEntry() == null) { contentArea.child(emptyState()); return; }
            if (networkStatus == null) {
                contentArea.child(statusText(NetworkUiKit.tr("gui.singularityme.network_terminal.home.loading")));
                return;
            }
            if (networkStatus.devices.isEmpty()) { contentArea.child(emptyState()); return; }

            final ListWidget list = new ListWidget();
            list.background(Styles.listBg());
            list.disableHoverBackground();
            list.childSeparator(IIcon.EMPTY_2PX);
            list.padding(Palette.LIST_CONTENT_INSET, 0);
            list.widthRel(1f);
            list.height(NetworkUiKit.connectionListHeight(layout.contentH));
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

            final Flow row = Flow.row()
                .childPadding(8).widthRel(1f).height(Palette.LIST_ROW_H).padding(Palette.LIST_ROW_PADDING_H, 0)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .background(Styles.rowBg(Palette.BG_ROW))
                .disableHoverBackground()
                .child(new TextWidget(IKey.str("\u25A0")).color(color))
                .child(type)
                .child(new TextWidget(IKey.str(formatLocation(device))).color(Palette.TEXT_MUTED))
                .child(new TextWidget(IKey.str(NetworkUiKit.tr(device.loaded
                    ? "gui.singularityme.network_terminal.conn.online"
                    : "gui.singularityme.network_terminal.conn.offline")))
                    .color(device.loaded ? Palette.SECURITY_PUBLIC : Palette.BTN_DANGER_NORMAL));
            return row;
        }

        // ---- MEMBERS ----

        @SuppressWarnings({ "unchecked", "rawtypes" })
        void renderMembers() {
            bottomArea.removeAll();
            final NetworkEntry sel = selectedRealEntry();
            if (sel == null) { contentArea.child(emptyState()); return; }
            if (SecurityLevel.fromOrdinal(sel.securityOrdinal) == SecurityLevel.PUBLIC) {
                contentArea.child(statusText(NetworkUiKit.tr("gui.singularityme.permission.public_hint"),
                    Palette.SECURITY_PUBLIC));
                return;
            }
            if (!sel.canManagePermissions) {
                contentArea.child(statusText(NetworkUiKit.tr("gui.singularityme.permission.no_use"),
                    Palette.BTN_DANGER_NORMAL));
                return;
            }
            final ListWidget list = new ListWidget();
            list.background(Styles.listBg());
            list.disableHoverBackground();
            list.childSeparator(IIcon.EMPTY_2PX);
            list.padding(Palette.LIST_CONTENT_INSET, 0);
            list.widthRel(1f);
            list.height(NetworkUiKit.memberListHeight(layout.contentH));

            list.child(buildPermissionRow(sel.ownerPlayerID, sel.ownerName, -1, false));
            for (int i = 0; i < sel.authorizedPlayerIDs.size(); i++) {
                final int playerID = sel.authorizedPlayerIDs.get(i);
                final String name = i < sel.authorizedPlayerNames.size()
                    ? sel.authorizedPlayerNames.get(i)
                    : "#" + playerID;
                final int bits = i < sel.authorizedPlayerPermBits.size() ? sel.authorizedPlayerPermBits.get(i) : 0;
                list.child(buildPermissionRow(playerID, name, bits, true));
            }
            if (sel.authorizedPlayerIDs.isEmpty()) {
                list.child(statusText(NetworkUiKit.tr("gui.singularityme.network_terminal.members.empty")));
            }
            contentArea.child(list);

            contentArea.child(Flow.row()
                .childPadding(8).widthRel(1f).height(Palette.ROW_H)
                .padding(Palette.LIST_ROW_PADDING_H, 0)
                .margin(0, Palette.MEMBER_ADD_ROW_MARGIN_V)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(memberNameInput.widthRel(1f).expanded())
                .child(makeBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.members.add"),
                    120, this::addMember)));

        }

        private Flow buildPermissionRow(final int playerID, final String playerName, final int permissionBits,
            final boolean editable) {
            final int color = permissionBits < 0 ? Palette.ACCESS_OWNER : NetworkUiKit.permissionColor(permissionBits);

            final TextWidget memberNameW = new TextWidget(IKey.str(playerName))
                .color(Palette.TEXT_PRIMARY);
            memberNameW.expanded();

            final Flow row = Flow.row()
                .childPadding(8).widthRel(1f).height(Palette.LIST_ROW_H)
                .padding(Palette.LIST_ROW_PADDING_H, 0)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(memberNameW)
                .background(Styles.rowBg(Palette.BG_ROW))
                .disableHoverBackground();

            if (permissionBits < 0) {
                row.child(NetworkUiKit.badge(
                    NetworkUiKit.tr("gui.singularityme.network_terminal.access.owner"),
                    color));
            } else {
                row.child(permissionChipRow(playerID, permissionBits, editable));
            }
            return row;
        }

        void addMember() {
            final String name = memberNameVal.getStringValue();
            if (name.isEmpty()) return;
            final NetworkEntry sel = selectedRealEntry();
            if (sel == null) return;
            SingularityChannel.CHANNEL.sendToServer(
                new PacketGrantPermissionByName(sel.networkID, name, PermissionBits.DEFAULT_MEMBER_BITS));
            memberNameVal.setStringValue("");
        }

        @SuppressWarnings("unchecked")
        private Flow permissionChipRow(final int playerID, final int permissionBits, final boolean editable) {
            final Flow chips = Flow.row()
                .childPadding(2).height(Palette.BADGE_H).coverChildrenWidth()
                .crossAxisAlignment(Alignment.CrossAxis.CENTER);
            chips.child(permissionChip(playerID, permissionBits, SecurityPermissions.BUILD, editable));
            chips.child(permissionChip(playerID, permissionBits, SecurityPermissions.CRAFT, editable));
            chips.child(permissionChip(playerID, permissionBits, SecurityPermissions.INJECT, editable));
            chips.child(permissionChip(playerID, permissionBits, SecurityPermissions.EXTRACT, editable));
            chips.child(permissionChip(playerID, permissionBits, SecurityPermissions.SECURITY, editable));
            return chips;
        }

        private ButtonWidget<?> permissionChip(final int playerID, final int permissionBits,
            final SecurityPermissions permission, final boolean editable) {
            final int mask = 1 << permission.ordinal();
            final boolean enabled = (permissionBits & mask) != 0;
            return new ButtonWidget<>()
                .overlay(IKey.str(permissionMark(permission)))
                .width(Palette.PERMISSION_CHIP_W).height(Palette.BADGE_H).padding(0, 2)
                .background(Styles.rowBg(enabled ? Palette.BTN_NORMAL : Palette.BTN_DISABLED))
                .disableHoverBackground()
                .onMousePressed(mb -> {
                    if (!editable) return false;
                    savePermissions(playerID, NetworkUiKit.togglePermissionBit(permissionBits, permission));
                    return true;
                });
        }

        private static String permissionMark(final SecurityPermissions permission) {
            return switch (permission) {
                case BUILD -> "B";
                case CRAFT -> "C";
                case INJECT -> "I";
                case EXTRACT -> "E";
                case SECURITY -> "S";
            };
        }

        private void savePermissions(final int playerID, final int permissionBits) {
            final NetworkEntry sel = selectedRealEntry();
            if (sel == null || playerID < 0) return;
            SingularityChannel.CHANNEL
                .sendToServer(new PacketSetPermissions(sel.networkID, playerID, permissionBits));
            requestNetworkData();
        }

        // ---- SETTINGS ----

        @SuppressWarnings("unchecked")
        void renderSettings() {
            bottomArea.removeAll();
            final NetworkEntry sel = selectedRealEntry();
            if (sel == null) { contentArea.child(emptyState()); return; }
            if (!sel.canEditSettings) {
                contentArea.child(statusText(NetworkUiKit.tr("gui.singularityme.permission.no_use"),
                    Palette.BTN_DANGER_NORMAL));
                return;
            }

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

            contentArea.child(formRow(
                NetworkUiKit.tr("gui.singularityme.network_terminal.settings.security"),
                NetworkUiKit.securitySegmentRow(selectedSecurity, level -> {
                    selectedSecurity = level;
                    renderContent(false);
                })));

            contentArea.child(formRow(
                NetworkUiKit.tr("gui.singularityme.network_terminal.settings.color"),
                NetworkUiKit.colorReadonly(selectedColor)));
            contentArea.child(colorSwatchRow());

            final Flow actionRow = Flow.row().childPadding(8).widthRel(1f).height(Palette.ROW_H)
                .child(makeBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.settings.apply"),
                    140, this::applySettings));
            if (sel.canDeleteNetwork) {
                actionRow.child(makeDangerBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.settings.delete"),
                    140, () -> showDeleteConfirm(sel)));
            }
            bottomArea.child(actionRow);
        }

        void applySettings() {
            final NetworkEntry sel = selectedRealEntry();
            if (sel == null) return;
            final String name = settingsNameVal.getStringValue();
            if (!name.isEmpty() && !name.equals(sel.name)) {
                SingularityChannel.CHANNEL.sendToServer(new PacketRenameNetwork(sel.networkID, name));
            }
            SingularityChannel.CHANNEL.sendToServer(
                new PacketSetNetworkSettings(sel.networkID, selectedColor, selectedSecurity.ordinal()));
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
            contentArea.child(progressBar(energyFraction(), Palette.ACCENT_AMBER));

            contentArea.child(statusText(NetworkUiKit.tr("gui.singularityme.network_terminal.stat.device_counts")));
            final Map<String, Integer> counts = deviceTypeCounts();
            final Flow rows = Flow.column().childPadding(4).widthRel(1f).padding(0, 12);
            for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
                rows.child(infoRow(
                    NetworkUiKit.deviceTypeLabel(entry.getKey()),
                    NetworkUiKit.formatCountBadge(entry.getValue())));
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
                    Palette.ACCENT_AMBER));
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
            return NetworkUiKit.infoRowCompact(label, value);
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
                .background(Styles.listBg())
                .disableHoverBackground();
            final Flow fill = Flow.row()
                .widthRel(clamped).heightRel(1f)
                .background(Styles.rowBg(color))
                .disableHoverBackground();
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
                renderContent(false);
            });
        }

        private void showDeleteConfirm(final NetworkEntry entry) {
            contentArea.removeAll();
            bottomArea.removeAll();
            contentArea.child(statusText(
                NetworkUiKit.trf("gui.singularityme.network_terminal.confirm.delete_body", entry.name),
                Palette.BTN_DANGER_NORMAL));
            contentArea.child(statusText(
                NetworkUiKit.tr("gui.singularityme.network_terminal.confirm.delete_warning"),
                Palette.TEXT_MUTED));
            bottomArea.child(Flow.row().childPadding(8).widthRel(1f).height(Palette.ROW_H)
                .child(makeDangerBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.confirm.yes"),
                    140, () -> {
                        SingularityChannel.CHANNEL.sendToServer(new PacketDeleteNetwork(entry.networkID));
                        selectedNetworkID = 0;
                        networkStatus = null;
                        currentPanel = Panel.HOME;
                        panelFirstRender = true;
                        buildNavButtons();
                        requestNetworkData();
                    }))
                .child(makeBtn(NetworkUiKit.tr("gui.singularityme.network_terminal.confirm.cancel"),
                    140, () -> renderContent(false))));
            contentViewport.scheduleResize();
        }

        // ---- CREATE ----

        @SuppressWarnings("unchecked")
        void renderCreate() {
            bottomArea.removeAll();

            if (panelFirstRender) {
                createNameVal.setStringValue("");
                selectedColor = 0x4A90E2;
                selectedSecurity = SecurityLevel.PRIVATE;
            }
            contentArea.child(formRow(
                NetworkUiKit.tr("gui.singularityme.network_terminal.create.name"), createNameInput));

            contentArea.child(formRow(
                NetworkUiKit.tr("gui.singularityme.network_terminal.settings.security"),
                NetworkUiKit.securitySegmentRow(selectedSecurity, level -> {
                    selectedSecurity = level;
                    renderContent(false);
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
            SingularityChannel.CHANNEL.sendToServer(
                new PacketCreateNetwork(x, y, z, dim, name, selectedColor, selectedSecurity.ordinal()));
            createNameVal.setStringValue("");
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

        private static ButtonWidget<?> makeRailBtn(String text, int w, Runnable action, boolean enabled) {
            return new ButtonWidget<>()
                .overlay(IKey.str(text))
                .width(w).height(Palette.RAIL_ACTION_H).padding(0, 6)
                .background(Styles.rowBg(enabled ? Palette.BTN_NORMAL : Palette.BTN_DISABLED))
                .disableHoverBackground()
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

        private static TextFieldWidget makeRailInput(StringValue val) {
            return new TextFieldWidget()
                .value(val)
                .height(Palette.RAIL_FILTER_H).expanded()
                .background(Styles.inputBg())
                .autoUpdateOnChange(true);
        }

        @Override
        public void selectNetwork(final int networkID) {
            if (selectedNetworkID == networkID) return;
            selectedNetworkID = networkID;
            networkStatus = NetworkUiKit.cachedStatusForNetwork(statusCache, selectedNetworkID);
            resetContentScrollNextRender = true;
            if (currentPanel == Panel.SETTINGS) {
                panelFirstRender = true;
            }
            if (isStatusPanel(currentPanel) && selectedRealEntry() != null) {
                requestNetworkStatus();
            }
            renderContent();
        }

        // ---- 数据刷新 ----

        @Override
        public void requestNetworkData() {
            SingularityChannel.CHANNEL.sendToServer(new PacketRequestNetworkTabData(x, y, z, dim));
        }

        void requestNetworkStatus() {
            if (selectedNetworkID <= 0) return;
            SingularityChannel.CHANNEL.sendToServer(new PacketRequestNetworkStatus(selectedNetworkID));
        }

        void receiveStatus(final PacketNetworkStatus packet) {
            if (packet.networkID > 0) {
                statusCache.put(packet.networkID, packet);
            }
            if (packet.networkID != selectedNetworkID) return;
            networkStatus = packet;
            renderContent(false);
        }

        void receiveActionResult(final PacketNetworkActionResult packet) {
            if (selectionSurface != null) {
                selectionSurface.receiveResult(packet);
            }
            requestNetworkData();
        }

        void receive(final PacketNetworkTabData packet) {
            networks.clear();
            networks.addAll(packet.networks);
            defaultNetworkID = packet.defaultNetworkID;
            if (!containsNetwork(selectedNetworkID)) {
                selectedNetworkID = initialNetworkID(packet.deviceNetworkID);
                networkStatus = NetworkUiKit.cachedStatusForNetwork(statusCache, selectedNetworkID);
                resetContentScrollNextRender = true;
            }
            if (networkStatus != null && networkStatus.networkID != selectedNetworkID) {
                networkStatus = NetworkUiKit.cachedStatusForNetwork(statusCache, selectedNetworkID);
            }
            if (networkStatus == null && isStatusPanel(currentPanel) && selectedRealEntry() != null) {
                requestNetworkStatus();
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

        private NetworkEntry selectedRealEntry() {
            final NetworkEntry entry = selectedEntry();
            return entry != null && entry.networkID != 0 ? entry : null;
        }

        private boolean containsNetwork(final int networkID) {
            for (final NetworkEntry e : networks) {
                if (e.networkID == networkID) return true;
            }
            return false;
        }

        private int initialNetworkID(final int deviceNetworkID) {
            if (deviceNetworkID != 0 && containsNetwork(deviceNetworkID)) return deviceNetworkID;
            if (defaultNetworkID != 0 && containsNetwork(defaultNetworkID)) return defaultNetworkID;
            for (final NetworkEntry e : networks) {
                if (e.networkID != 0) return e.networkID;
            }
            return containsNetwork(0) ? 0 : -1;
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
            final String name = entry.name == null ? "" : entry.name;
            return name.toLowerCase().contains(needle) || ("#" + entry.networkID).contains(needle);
        }

        private static boolean isStatusPanel(final Panel panel) {
            return panel == Panel.HOME || panel == Panel.CONNECTION;
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

        private Map<String, Integer> deviceTypeCounts() {
            return NetworkUiKit.countDeviceTypes(networkStatus);
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
            if (max <= 0.0) return NetworkUiKit.formatCompactEnergy(current);
            return compactEnergyValue(current) + " / " + NetworkUiKit.formatCompactEnergy(max);
        }

        private static String compactEnergyValue(final double value) {
            final String text = NetworkUiKit.formatCompactEnergy(value);
            return text.endsWith(" AE") ? text.substring(0, text.length() - 3) : text;
        }

        private static String formatTimestamp(final long millis) {
            if (millis <= 0L) return "-";
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(millis));
        }

        private static String panelTitle(Panel p) {
            return NetworkUiKit.tr("gui.singularityme.network_terminal.panel." + p.name().toLowerCase());
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
            return 0;
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
        public void rebuildAfterSurfaceAction() {
            requestNetworkData();
            renderContent();
        }
    }
}
