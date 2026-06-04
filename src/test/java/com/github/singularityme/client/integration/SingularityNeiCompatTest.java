package com.github.singularityme.client.integration;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.github.singularityme.gui.GuiSingularityCraftingTerminal;
import com.github.singularityme.gui.GuiSingularityPatternTerminal;
import com.github.singularityme.gui.GuiSingularityTerminal;

/** 验证奇点终端 GUI 会为 NEI 精确类匹配补齐 AE2 兼容注册。 */
public class SingularityNeiCompatTest {

    @Test
    public void plansCraftingOverlayForSingularityCraftingAndPatternTerminals() {
        final List<SingularityNeiCompat.CraftingOverlayRegistration> registrations = SingularityNeiCompat
            .craftingOverlayRegistrations();

        assertEquals(2, registrations.size());
        assertEquals(
            new SingularityNeiCompat.CraftingOverlayRegistration(GuiSingularityCraftingTerminal.class, "crafting", 6, 75),
            registrations.get(0));
        assertEquals(
            new SingularityNeiCompat.CraftingOverlayRegistration(GuiSingularityPatternTerminal.class, "crafting", 6, 75),
            registrations.get(1));
    }

    @Test
    public void plansBookmarkHandlersForAllSingularityTerminals() {
        final List<Class<?>> registrations = SingularityNeiCompat.bookmarkContainerGuiClasses();

        assertEquals(3, registrations.size());
        assertEquals(GuiSingularityTerminal.class, registrations.get(0));
        assertEquals(GuiSingularityCraftingTerminal.class, registrations.get(1));
        assertEquals(GuiSingularityPatternTerminal.class, registrations.get(2));
    }
}
