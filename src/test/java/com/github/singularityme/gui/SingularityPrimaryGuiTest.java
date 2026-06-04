package com.github.singularityme.gui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

import com.github.singularityme.block.BlockSingularityCraftingTerminal;
import com.github.singularityme.block.BlockSingularityDrive;
import com.github.singularityme.block.BlockSingularityExportBus;
import com.github.singularityme.block.BlockSingularityImportBus;
import com.github.singularityme.block.BlockSingularityInterface;
import com.github.singularityme.block.BlockSingularityPatternTerminal;
import com.github.singularityme.block.BlockSingularityStorageBus;
import com.github.singularityme.block.BlockSingularityTerminal;

import appeng.container.PrimaryGui;
import net.minecraftforge.common.util.ForgeDirection;

/** 验证奇点 AE2 风格 GUI 的子页面能返回本模组原始 GUI，而不是落回 AE2 精确容器类映射。 */
public class SingularityPrimaryGuiTest {

    @Test
    public void createsPrimaryGuiThatTargetsSingularityGuiId() {
        final PrimaryGui primaryGui = SingularityPrimaryGui.create(
            BlockSingularityCraftingTerminal.GUI_ID,
            null,
            ForgeDirection.UNKNOWN,
            null);

        assertTrue(primaryGui instanceof SingularityPrimaryGui);
        assertEquals(BlockSingularityCraftingTerminal.GUI_ID, ((SingularityPrimaryGui) primaryGui).getGuiID());
    }

    @Test
    public void coversAllAe2StyleDevicesThatOpenSubGuis() {
        assertArrayEquals(
            new int[] {
                BlockSingularityTerminal.GUI_ID,
                BlockSingularityCraftingTerminal.GUI_ID,
                BlockSingularityPatternTerminal.GUI_ID,
                BlockSingularityStorageBus.GUI_ID,
                BlockSingularityInterface.GUI_ID,
                BlockSingularityExportBus.GUI_ID,
                BlockSingularityDrive.GUI_ID,
                BlockSingularityImportBus.GUI_ID },
            SingularityPrimaryGui.ae2StyleDeviceGuiIDs());
    }

    @Test
    public void ae2StyleContainersOverridePrimaryGuiCreation() throws Exception {
        assertOverridesCreatePrimaryGui(ContainerSingularityTerminal.class);
        assertOverridesCreatePrimaryGui(ContainerSingularityCraftingTerminal.class);
        assertOverridesCreatePrimaryGui(ContainerSingularityPatternTerminal.class);
        assertOverridesCreatePrimaryGui(ContainerSingularityStorageBus.class);
        assertOverridesCreatePrimaryGui(ContainerSingularityInterface.class);
        assertOverridesCreatePrimaryGui(ContainerSingularityExportBus.class);
        assertOverridesCreatePrimaryGui(ContainerSingularityDrive.class);
        assertOverridesCreatePrimaryGui(ContainerSingularityImportBus.class);
    }

    private static void assertOverridesCreatePrimaryGui(final Class<?> containerClass) throws Exception {
        final Method method = containerClass.getMethod("createPrimaryGui");
        assertEquals(containerClass, method.getDeclaringClass());
    }
}
