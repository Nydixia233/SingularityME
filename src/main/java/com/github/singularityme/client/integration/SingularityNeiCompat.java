package com.github.singularityme.client.integration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.github.singularityme.gui.GuiSingularityCraftingTerminal;
import com.github.singularityme.gui.GuiSingularityPatternTerminal;
import com.github.singularityme.gui.GuiSingularityTerminal;

import appeng.integration.modules.NEIHelpers.NEIAETerminalBookmarkContainerHandler;
import appeng.integration.modules.NEIHelpers.NEICraftingHandler;
import appeng.integration.modules.NEIHelpers.TerminalCraftingSlotFinder;
import codechicken.nei.api.API;

/** 为奇点终端 GUI 补齐 AE2 原生 NEI 集成注册。 */
public final class SingularityNeiCompat {

    private static final String CRAFTING_OVERLAY = "crafting";
    private static final int CRAFTING_HANDLER_X = 6;
    private static final int CRAFTING_HANDLER_Y = 75;

    private static final List<CraftingOverlayRegistration> CRAFTING_OVERLAY_REGISTRATIONS = Collections
        .unmodifiableList(
            Arrays.asList(
                new CraftingOverlayRegistration(
                    GuiSingularityCraftingTerminal.class,
                    CRAFTING_OVERLAY,
                    CRAFTING_HANDLER_X,
                    CRAFTING_HANDLER_Y),
                new CraftingOverlayRegistration(
                    GuiSingularityPatternTerminal.class,
                    CRAFTING_OVERLAY,
                    CRAFTING_HANDLER_X,
                    CRAFTING_HANDLER_Y)));

    private static final List<Class<?>> BOOKMARK_CONTAINER_GUI_CLASSES = Collections.unmodifiableList(
        Arrays.asList(
            GuiSingularityTerminal.class,
            GuiSingularityCraftingTerminal.class,
            GuiSingularityPatternTerminal.class));

    private SingularityNeiCompat() {}

    /** 注册 NEI 精确 GUI 类匹配需要的覆盖与书签处理器。 */
    public static void register() {
        for (final CraftingOverlayRegistration registration : craftingOverlayRegistrations()) {
            final Class<? extends GuiContainer> guiClass = registration.guiClass.asSubclass(GuiContainer.class);
            API.registerGuiOverlay(guiClass, registration.overlayName, new TerminalCraftingSlotFinder());
            API.registerGuiOverlayHandler(
                guiClass,
                new NEICraftingHandler(registration.handlerX, registration.handlerY),
                registration.overlayName);
        }

        for (final Class<?> guiClass : bookmarkContainerGuiClasses()) {
            API.registerBookmarkContainerHandler(
                guiClass.asSubclass(GuiContainer.class),
                new NEIAETerminalBookmarkContainerHandler());
        }
    }

    /** 返回需要注册 crafting overlay 的奇点 GUI 清单，供测试锁定 AE2 对齐关系。 */
    public static List<CraftingOverlayRegistration> craftingOverlayRegistrations() {
        return CRAFTING_OVERLAY_REGISTRATIONS;
    }

    /** 返回需要注册 NEI 书签容器处理器的奇点终端 GUI 清单。 */
    public static List<Class<?>> bookmarkContainerGuiClasses() {
        return BOOKMARK_CONTAINER_GUI_CLASSES;
    }

    /** 描述一个 AE2 crafting overlay 注册项。 */
    public static final class CraftingOverlayRegistration {

        private final Class<?> guiClass;
        private final String overlayName;
        private final int handlerX;
        private final int handlerY;

        public CraftingOverlayRegistration(final Class<?> guiClass, final String overlayName, final int handlerX,
            final int handlerY) {
            this.guiClass = guiClass;
            this.overlayName = overlayName;
            this.handlerX = handlerX;
            this.handlerY = handlerY;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CraftingOverlayRegistration)) {
                return false;
            }
            final CraftingOverlayRegistration other = (CraftingOverlayRegistration) obj;
            return this.handlerX == other.handlerX
                && this.handlerY == other.handlerY
                && Objects.equals(this.guiClass, other.guiClass)
                && Objects.equals(this.overlayName, other.overlayName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.guiClass, this.overlayName, this.handlerX, this.handlerY);
        }
    }
}
