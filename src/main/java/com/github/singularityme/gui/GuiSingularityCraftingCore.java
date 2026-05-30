package com.github.singularityme.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;

import com.github.singularityme.tile.TileSingularityCraftingCore;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;

/**
 * Minimal AE2-style GUI for configuring a Singularity Crafting Core.
 */
public class GuiSingularityCraftingCore extends AEBaseGui {

    private GuiTabButton btnNetworkTab;
    private final TileSingularityCraftingCore te;

    public GuiSingularityCraftingCore(final InventoryPlayer ip, final TileSingularityCraftingCore te) {
        super(new ContainerSingularityCraftingCore(ip, te));
        this.te = te;
        this.xSize = ContainerSingularityCraftingCore.GUI_WIDTH;
        this.ySize = ContainerSingularityCraftingCore.GUI_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.add(
            this.btnNetworkTab = new GuiTabButton(
                this.guiLeft + this.xSize - 22,
                this.guiTop + 4,
                2 + 11 * 16,
                "Network",
                itemRender));
    }

    @Override
    protected void actionPerformed(final GuiButton button) {
        super.actionPerformed(button);
        if (button == this.btnNetworkTab) {
            NetworkTabClientActions.open(this.te);
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        final ContainerSingularityCraftingCore c = (ContainerSingularityCraftingCore) this.inventorySlots;
        this.fontRendererObj.drawString(
            this.getGuiDisplayName(StatCollector.translateToLocal("gui.singularityme.crafting_core")),
            8,
            6,
            GuiColors.DriveTitle.getColor());
        this.fontRendererObj.drawString(formatStorage(c.storageBytes), 8, 65, 0x404040);
        this.fontRendererObj.drawString(
            StatCollector.translateToLocalFormatted("gui.singularityme.crafting_core.coprocessors", c.coProcessors),
            8,
            77,
            0x404040);
        this.fontRendererObj.drawString(
            StatCollector.translateToLocalFormatted("gui.singularityme.crafting_core.monitors", c.monitorCount),
            8,
            89,
            0x404040);
        this.fontRendererObj.drawString(
            c.busy ? StatCollector.translateToLocal("gui.singularityme.crafting_core.busy")
                : StatCollector.translateToLocal("gui.singularityme.crafting_core.idle"),
            118,
            89,
            c.busy ? 0xA04040 : 0x407040);
        this.fontRendererObj
            .drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, GuiColors.DriveInventory.getColor());
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        drawRect(offsetX, offsetY, offsetX + this.xSize, offsetY + this.ySize, 0xFFC6C6C6);
        drawRect(offsetX, offsetY, offsetX + this.xSize, offsetY + 1, 0xFFFFFFFF);
        drawRect(offsetX, offsetY, offsetX + 1, offsetY + this.ySize, 0xFFFFFFFF);
        drawRect(offsetX + this.xSize - 1, offsetY, offsetX + this.xSize, offsetY + this.ySize, 0xFF555555);
        drawRect(offsetX, offsetY + this.ySize - 1, offsetX + this.xSize, offsetY + this.ySize, 0xFF555555);

        for (int row = 0; row < ContainerSingularityCraftingCore.COMPONENT_ROWS; row++) {
            for (int col = 0; col < ContainerSingularityCraftingCore.COMPONENT_COLS; col++) {
                drawSlotBackground(
                    offsetX + ContainerSingularityCraftingCore.COMPONENT_START_X + col * 18,
                    offsetY + ContainerSingularityCraftingCore.COMPONENT_START_Y + row * 18);
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotBackground(
                    offsetX + ContainerSingularityCraftingCore.PLAYER_INVENTORY_X + col * 18,
                    offsetY + ContainerSingularityCraftingCore.PLAYER_INVENTORY_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotBackground(
                offsetX + ContainerSingularityCraftingCore.PLAYER_INVENTORY_X + col * 18,
                offsetY + ContainerSingularityCraftingCore.PLAYER_INVENTORY_Y + 58);
        }
    }

    private static String formatStorage(final long storageBytes) {
        if (storageBytes == Long.MAX_VALUE) {
            return StatCollector.translateToLocal("gui.singularityme.crafting_core.storage_infinite");
        }
        return StatCollector.translateToLocalFormatted("gui.singularityme.crafting_core.storage", storageBytes);
    }

    private static void drawSlotBackground(final int x, final int y) {
        final int borderX = x - 1;
        final int borderY = y - 1;
        drawRect(borderX, borderY, borderX + 18, borderY + 18, 0xFF6A6A6A);
        drawRect(borderX + 1, borderY + 1, borderX + 18, borderY + 18, 0xFFFFFFFF);
        drawRect(borderX + 1, borderY + 1, borderX + 17, borderY + 17, 0xFF9A9A9A);
    }
}
