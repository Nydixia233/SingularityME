package com.github.singularityme.client.ui;

import java.util.List;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IIcon;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.github.singularityme.client.ui.NetworkUiKit.Palette;
import com.github.singularityme.client.ui.NetworkUiKit.Styles;
import com.github.singularityme.core.SingularityNetworkRegistry;
import com.github.singularityme.network.SingularityChannel;
import com.github.singularityme.network.packet.NetworkActionResult;
import com.github.singularityme.network.packet.NetworkActionType;
import com.github.singularityme.network.packet.PacketJoinNetwork;
import com.github.singularityme.network.packet.PacketNetworkActionResult;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;
import com.github.singularityme.network.packet.PacketSetDefaultNetwork;
import com.github.singularityme.network.packet.PacketSetDeviceNetwork;

/**
 * 网络选择共享表面，供网络终端左侧栏与设备分配 GUI 复用。
 *
 * <p>
 * 该类只负责列表渲染、过滤、密码输入、主动作按钮与最近操作反馈，不持有具体页面的右侧内容状态。
 */
public final class NetworkSelectionSurface {

    /** 选择表面的使用场景：终端默认网络模式或设备分配模式。 */
    public enum Mode { TERMINAL_DEFAULT, DEVICE_ASSIGN }

    /** 页面状态代理，由宿主 GUI 提供网络列表、坐标与刷新入口。 */
    public interface Delegate {
        List<NetworkEntry> networks();

        int selectedNetworkID();

        int deviceNetworkID();

        int defaultNetworkID();

        int x();

        int y();

        int z();

        int dim();

        void selectNetwork(int networkID);

        void requestNetworkData();

        void rebuildAfterSurfaceAction();
    }

    private final Mode mode;
    private final Delegate delegate;
    private final StringValue filterValue = new StringValue("");
    private final StringValue passwordValue = new StringValue("");
    private final TextFieldWidget filterInput;
    private final TextFieldWidget passwordInput;

    private Flow root;
    private ListWidget railList;
    private ButtonWidget<?> actionButton;
    private NetworkActionResult lastResult;
    private String lastMessageKey;
    private int lastResultNetworkID;
    private int baseListHeight;
    private boolean passwordMode;

    /** 创建共享网络选择表面。 */
    public NetworkSelectionSurface(final Mode mode, final Delegate delegate) {
        this.mode = mode;
        this.delegate = delegate;
        this.filterInput = new TextFieldWidget()
            .value(this.filterValue)
            .height(Palette.RAIL_FILTER_H)
            .expanded()
            .background(Styles.inputBg())
            .autoUpdateOnChange(true);
        this.passwordInput = new TextFieldWidget()
            .value(this.passwordValue)
            .height(Palette.RAIL_ACTION_H)
            .expanded()
            .background(Styles.inputBg())
            .autoUpdateOnChange(true);
    }

    /** 构建完整选择表面，包含标题、过滤框、列表和底部动作区。 */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Flow build(final int width, final int height, final int listHeight) {
        root = Flow.column()
            .childPadding(4).size(width, height)
            .padding(4)
            .background(Styles.listBg())
            .disableHoverBackground();

        root.child(Flow.row()
            .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
            .widthRel(1f).height(Palette.RAIL_HEADER_H)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .child(new TextWidget(IKey.str(NetworkUiKit.tr("gui.singularityme.network_terminal.rail.title")))
                .color(Palette.TEXT_PRIMARY))
            .child(new TextWidget(IKey.dynamicKey(() -> IKey.str(
                NetworkUiKit.trf("gui.singularityme.network_tab.total", visibleNetworkCount()))))
                .color(Palette.TEXT_MUTED)));

        root.child(Flow.row()
            .widthRel(1f).height(Palette.RAIL_FILTER_H)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .child(filterInput.widthRel(1f).expanded()));

        railList = new ListWidget();
        railList.background(IDrawable.NONE);
        railList.disableHoverBackground();
        railList.childSeparator(IIcon.EMPTY_2PX);
        railList.padding(Palette.LIST_CONTENT_INSET, 0);
        railList.widthRel(1f);
        railList.height(listHeight);
        baseListHeight = listHeight;
        root.child(railList);

        actionButton = new ButtonWidget<>()
            .width(NetworkUiKit.railActionWidth(width))
            .height(Palette.RAIL_ACTION_H)
            .padding(0, 6)
            .disableHoverBackground()
            .onMousePressed(mb -> {
                runPrimaryAction();
                return true;
            });
        rebuild();
        return root;
    }

    /** 重新渲染网络列表与底部动作区。 */
    public void rebuild() {
        if (railList == null) return;
        railList.removeAll();
        for (final NetworkEntry entry : delegate.networks()) {
            if (matchesFilter(entry)) {
                railList.child(buildRow(entry));
            }
        }
        railList.scheduleResize();
        rebuildActions();
    }

    /** 接收服务端操作结果并刷新内联反馈。 */
    public void receiveResult(final PacketNetworkActionResult packet) {
        this.lastResult = packet.result;
        this.lastMessageKey = packet.messageKey;
        this.lastResultNetworkID = packet.networkID;
        if (packet.result.success) {
            this.passwordMode = false;
            this.passwordValue.setStringValue("");
        }
        rebuildActions();
    }

    /** 获取当前选中的网络条目。 */
    public NetworkEntry selectedEntry() {
        for (final NetworkEntry entry : delegate.networks()) {
            if (entry.networkID == delegate.selectedNetworkID()) return entry;
        }
        return null;
    }

    /** 判断网络条目是否匹配当前过滤文本。 */
    public boolean matchesFilter(final NetworkEntry entry) {
        final String filter = filterValue.getStringValue();
        if (filter == null || filter.trim().isEmpty()) return true;
        final String needle = filter.trim().toLowerCase();
        final String name = entry.name == null ? "" : entry.name;
        return name.toLowerCase().contains(needle) || ("#" + entry.networkID).contains(needle);
    }

    private int visibleNetworkCount() {
        int count = 0;
        for (final NetworkEntry entry : delegate.networks()) {
            if (entry.networkID != 0 && matchesFilter(entry)) count++;
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private ButtonWidget<?> buildRow(final NetworkEntry entry) {
        final boolean selected = entry.networkID == delegate.selectedNetworkID();
        final boolean current = entry.networkID == delegate.deviceNetworkID();
        final boolean isDefault = entry.networkID != 0 && entry.networkID == delegate.defaultNetworkID();
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
        if (mode == Mode.DEVICE_ASSIGN && current) rowContent.child(NetworkUiKit.currentBadge());
        if (isDefault) rowContent.child(NetworkUiKit.defaultBadge());

        return new ButtonWidget<>()
            .child(rowContent)
            .widthRel(1f).height(Palette.RAIL_ROW_H)
            .padding(Palette.LIST_ROW_PADDING_H, 0)
            .background(Styles.rowBg(bg))
            .disableHoverBackground()
            .onMousePressed(mb -> {
                delegate.selectNetwork(entry.networkID);
                passwordMode = false;
                rebuild();
                return true;
            });
    }

    private void rebuildActions() {
        if (root == null || actionButton == null) return;
        while (root.getChildren().size() > 3) {
            root.getChildren().remove(root.getChildren().size() - 1);
        }
        final boolean hasResult = lastResult != null && lastMessageKey != null && !lastMessageKey.isEmpty();
        if (railList != null) {
            final int reservedResultHeight = hasResult ? Palette.TEXT_ROW_H + 4 : 0;
            railList.height(Math.max(Palette.TERMINAL_RAIL_LIST_MIN_H, baseListHeight - reservedResultHeight));
        }
        if (hasResult) {
            root.child(resultRow());
        }
        if (passwordMode) {
            root.child(passwordRow());
        } else {
            updateActionButton();
            root.child(actionButton);
        }
        root.scheduleResize();
    }

    @SuppressWarnings("unchecked")
    private Flow resultRow() {
        final String text = NetworkUiKit.tr(lastMessageKey);
        final int color = NetworkUiKit.actionResultColor(lastResult);
        return Flow.row()
            .childPadding(4).widthRel(1f).height(Palette.TEXT_ROW_H).padding(Palette.LIST_ROW_PADDING_H, 0)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .child(NetworkUiKit.statusDotWidget(color))
            .child(new TextWidget(IKey.str(lastResultNetworkID > 0 ? "#" + lastResultNetworkID + " " + text : text))
                .color(color));
    }

    @SuppressWarnings("unchecked")
    private Flow passwordRow() {
        return Flow.row()
            .childPadding(4).widthRel(1f).height(Palette.RAIL_ACTION_H)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .child(passwordInput.widthRel(1f).expanded())
            .child(makeSmallButton(NetworkUiKit.tr("gui.singularityme.network_tab.join"), Palette.BTN_NORMAL,
                this::submitPasswordJoin))
            .child(makeSmallButton(NetworkUiKit.tr("gui.singularityme.network_tab.cancel"), Palette.BTN_DANGER_NORMAL,
                () -> {
                    passwordMode = false;
                    passwordValue.setStringValue("");
                    rebuildActions();
                }));
    }

    private ButtonWidget<?> makeSmallButton(final String text, final int color, final Runnable action) {
        return new ButtonWidget<>()
            .overlay(IKey.str(text))
            .width(48)
            .height(Palette.RAIL_ACTION_H)
            .padding(0, 4)
            .background(Styles.rowBg(color))
            .disableHoverBackground()
            .onMousePressed(mb -> {
                action.run();
                return true;
            });
    }

    private void updateActionButton() {
        final NetworkEntry selected = selectedEntry();
        final String text = primaryActionText(selected);
        final boolean enabled = isPrimaryActionEnabled(selected);
        actionButton.overlay(IKey.str(text));
        actionButton.background(Styles.rowBg(enabled ? Palette.BTN_NORMAL : Palette.BTN_DISABLED));
    }

    private String primaryActionText(final NetworkEntry selected) {
        if (mode == Mode.TERMINAL_DEFAULT) {
            final boolean isDefault = selected != null && selected.networkID != 0
                && selected.networkID == delegate.defaultNetworkID();
            return isDefault
                ? NetworkUiKit.tr("gui.singularityme.network_terminal.selection.clear_default")
                : NetworkUiKit.tr("gui.singularityme.network_terminal.selection.set_default");
        }
        if (selected != null && NetworkUiKit.canSelfJoin(selected)) {
            return NetworkUiKit.tr("gui.singularityme.network_action.join");
        }
        return NetworkUiKit.tr("gui.singularityme.network_tab.select");
    }

    private boolean isPrimaryActionEnabled(final NetworkEntry selected) {
        if (selected == null) return false;
        if (mode == Mode.TERMINAL_DEFAULT) {
            return selected.networkID != 0 && NetworkUiKit.canAccess(selected);
        }
        return selected.networkID != delegate.deviceNetworkID()
            && !NetworkUiKit.isBlocked(selected)
            && (selected.networkID == 0 || NetworkUiKit.canAccess(selected) || NetworkUiKit.canSelfJoin(selected));
    }

    private void runPrimaryAction() {
        final NetworkEntry selected = selectedEntry();
        if (!isPrimaryActionEnabled(selected)) return;
        if (mode == Mode.TERMINAL_DEFAULT) {
            final boolean isDefault = selected.networkID == delegate.defaultNetworkID();
            SingularityChannel.CHANNEL.sendToServer(new PacketSetDefaultNetwork(isDefault ? 0 : selected.networkID));
            delegate.rebuildAfterSurfaceAction();
            return;
        }
        if (NetworkUiKit.isPublicJoinAvailable(selected)) {
            sendJoin(selected, "");
            return;
        }
        if (NetworkUiKit.isEncryptedJoinRequired(selected)) {
            passwordMode = true;
            passwordValue.setStringValue("");
            rebuildActions();
            return;
        }
        SingularityChannel.CHANNEL.sendToServer(
            new PacketSetDeviceNetwork(delegate.x(), delegate.y(), delegate.z(), delegate.dim(), selected.networkID));
    }

    private void submitPasswordJoin() {
        final NetworkEntry selected = selectedEntry();
        if (selected == null) return;
        final String password = passwordValue.getStringValue();
        if (password == null || password.isEmpty()) {
            lastResult = NetworkActionResult.PASSWORD_REQUIRED;
            lastMessageKey = lastResult.translationKey;
            lastResultNetworkID = selected.networkID;
            rebuildActions();
            return;
        }
        sendJoin(selected, SingularityNetworkRegistry.sha256Hex(password));
        passwordValue.setStringValue("");
    }

    private void sendJoin(final NetworkEntry selected, final String passwordHash) {
        SingularityChannel.CHANNEL.sendToServer(
            new PacketJoinNetwork(
                delegate.x(),
                delegate.y(),
                delegate.z(),
                delegate.dim(),
                selected.networkID,
                passwordHash,
                mode == Mode.DEVICE_ASSIGN));
    }
}
