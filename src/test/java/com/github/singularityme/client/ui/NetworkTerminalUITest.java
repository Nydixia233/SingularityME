package com.github.singularityme.client.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.github.singularityme.core.PermissionBits;
import com.github.singularityme.core.SecurityLevel;
import com.github.singularityme.network.packet.PacketNetworkTabData.NetworkEntry;

/** 验证网络终端 UI 的局部重绘行为。 */
public class NetworkTerminalUITest {

    /** 切换网络时左侧设为默认按钮应保持同一个 Widget，避免 remove/re-add 造成视觉闪烁。 */
    @Test
    public void keepsRailDefaultButtonStableAcrossNetworkSelectionRenders() throws Exception {
        final Object state = newTerminalState();
        setField(state, "layout", NetworkUiKit.terminalLayout(594, 359));
        setField(state, "networkRail", Flow.column());
        setField(state, "filterInput", new TextFieldWidget().value(new StringValue("")));
        setField(state, "selectionSurface",
            new NetworkSelectionSurface(
                NetworkSelectionSurface.Mode.TERMINAL_DEFAULT,
                (NetworkSelectionSurface.Delegate) state));
        addNetwork(state, newEntry(1, "Alpha"));
        addNetwork(state, newEntry(2, "Beta"));

        setField(state, "selectedNetworkID", 1);
        renderNetworkRail(state);
        final ButtonWidget<?> firstButton = railDefaultButton(state);

        setField(state, "selectedNetworkID", 2);
        renderNetworkRail(state);

        assertSame(firstButton, railDefaultButton(state));
    }

    /** 权限页添加玩家输入行必须稳定露出，不能被成员列表顶出内容视口。 */
    @Test
    public void renderMembersKeepsAddMemberRowVisibleBelowMemberList() throws Exception {
        final Object state = newTerminalState();
        setField(state, "layout", NetworkUiKit.terminalLayout(594, 312));
        setField(state, "contentArea",
            Flow.column().childPadding(NetworkUiKit.Palette.TERMINAL_CONTENT_CHILD_GAP)
                .widthRel(1f).coverChildrenHeight());
        setField(state, "bottomArea", Flow.column());
        setField(state, "memberNameInput", new TextFieldWidget().value(new StringValue("")));
        addNetwork(state, newEntry(1, "Alpha"));
        setField(state, "selectedNetworkID", 1);

        renderMembers(state);

        final Flow contentArea = (Flow) getField(state, "contentArea");
        assertEquals(2, contentArea.getChildren().size());
        assertTrue(contentArea.getChildren().get(0) instanceof ListWidget);
        final IWidget addRow = contentArea.getChildren().get(1);
        assertTrue(addRow instanceof Flow);
        assertTrue(addRow.resizer().hasHeight());
        assertEquals(0, addRow.getArea().getPadding().getTop());
        assertEquals(0, addRow.getArea().getPadding().getBottom());
        assertEquals(NetworkUiKit.Palette.MEMBER_ADD_ROW_MARGIN_V, addRow.getArea().getMargin().getTop());
        assertEquals(NetworkUiKit.Palette.MEMBER_ADD_ROW_MARGIN_V, addRow.getArea().getMargin().getBottom());

        final int contentHeight = ((NetworkUiKit.TerminalLayout) getField(state, "layout")).contentH;
        final int addRowBudget = NetworkUiKit.Palette.ROW_H
            + NetworkUiKit.Palette.MEMBER_ADD_ROW_MARGIN_V * 2
            + contentArea.getChildPadding()
            + NetworkUiKit.Palette.CONTENT_VIEWPORT_PAD * 2;
        assertTrue(NetworkUiKit.memberListHeight(contentHeight) + addRowBudget <= contentHeight);
    }

    private static Object newTerminalState() throws Exception {
        final Class<?> cls = Class.forName(NetworkTerminalUI.class.getName() + "$TerminalState");
        final Constructor<?> ctor = cls.getDeclaredConstructor(int.class, int.class, int.class, int.class);
        ctor.setAccessible(true);
        return ctor.newInstance(0, 0, 0, 0);
    }

    @SuppressWarnings("unchecked")
    private static void addNetwork(final Object state, final NetworkEntry entry) throws Exception {
        ((List<NetworkEntry>) getField(state, "networks")).add(entry);
    }

    private static NetworkEntry newEntry(final int id, final String name) {
        return new NetworkEntry(
            id,
            1,
            true,
            name,
            0x4A90E2,
            SecurityLevel.PRIVATE.ordinal(),
            PermissionBits.DEFAULT_MEMBER_BITS,
            true,
            true,
            true);
    }

    private static void renderNetworkRail(final Object state) throws Exception {
        final Method method = state.getClass().getDeclaredMethod("renderNetworkRail");
        method.setAccessible(true);
        method.invoke(state);
    }

    private static void renderMembers(final Object state) throws Exception {
        final Method method = state.getClass().getDeclaredMethod("renderMembers");
        method.setAccessible(true);
        method.invoke(state);
    }

    private static ButtonWidget<?> railDefaultButton(final Object state) throws Exception {
        final Object surface = getField(state, "selectionSurface");
        return (ButtonWidget<?>) getField(surface, "actionButton");
    }

    private static Object getField(final Object target, final String name) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(final Object target, final String name, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
