package com.github.singularityme.client.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.github.singularityme.core.PermissionBits;
import com.github.singularityme.core.SecurityLevel;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;

import appeng.api.config.SecurityPermissions;

/** 验证共享网络选择表面的局部重建行为。 */
public class NetworkSelectionSurfaceTest {

    /** 不可分配网络只显示权限不足状态，底部动作槽位保持稳定。 */
    @Test
    public void keepsStableActionAreaWhenPrimaryActionUnavailable() {
        final FakeDelegate delegate = new FakeDelegate();
        delegate.networks.add(entry(8, SecurityLevel.PRIVATE, 0));
        delegate.selectedNetworkID = 8;

        final NetworkSelectionSurface surface = new NetworkSelectionSurface(
            NetworkSelectionSurface.Mode.DEVICE_ASSIGN,
            delegate);
        final Flow root = surface.build(208, 267, 179);

        assertEquals(4, root.getChildren().size());
        final IWidget actionArea = root.getChildren().get(3);
        assertTrue(actionArea instanceof Flow);
        assertEquals(1, actionArea.getChildren().size());
        assertTrue(actionArea.getChildren().get(0) instanceof ButtonWidget);

        surface.performPrimaryAction();

        assertEquals(4, root.getChildren().size());
        assertSame(actionArea, root.getChildren().get(3));
        assertEquals(1, actionArea.getChildren().size());
        assertTrue(actionArea.getChildren().get(0) instanceof ButtonWidget);
    }

    /** 网络列表行只展示状态点、名称和状态徽章，不再在网络名称前重复展示 ID 胶囊。 */
    @Test
    public void omitsIdPillBeforeNetworkNameInSelectionRows() {
        final FakeDelegate delegate = new FakeDelegate();
        delegate.networks.add(entry(3, SecurityLevel.PUBLIC, PermissionBits.DEFAULT_MEMBER_BITS));
        delegate.selectedNetworkID = 3;

        final NetworkSelectionSurface surface = new NetworkSelectionSurface(
            NetworkSelectionSurface.Mode.TERMINAL_DEFAULT,
            delegate);
        final Flow root = surface.build(208, 267, 179);
        final ListWidget<?, ?> railList = (ListWidget<?, ?>) root.getChildren().get(2);
        final ButtonWidget<?> row = (ButtonWidget<?>) railList.getChildren().get(0);
        final Flow rowContent = (Flow) row.getChildren().get(0);

        assertEquals(2, rowContent.getChildren().size());
    }

    private static NetworkEntry entry(final int networkID, final SecurityLevel security, final int permissionBits) {
        return new NetworkEntry(
            networkID,
            1,
            false,
            "Encrypted",
            0x4A90E2,
            security.ordinal(),
            permissionBits,
            (permissionBits & (1 << SecurityPermissions.SECURITY.ordinal())) != 0,
            false,
            false);
    }

    private static final class FakeDelegate implements NetworkSelectionSurface.Delegate {

        final List<NetworkEntry> networks = new ArrayList<>();
        int selectedNetworkID;

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
            return 0;
        }

        @Override
        public int x() {
            return 0;
        }

        @Override
        public int y() {
            return 0;
        }

        @Override
        public int z() {
            return 0;
        }

        @Override
        public int dim() {
            return 0;
        }

        @Override
        public void selectNetwork(final int networkID) {
            selectedNetworkID = networkID;
        }

        @Override
        public void requestNetworkData() {}

        @Override
        public void rebuildAfterSurfaceAction() {}
    }
}
