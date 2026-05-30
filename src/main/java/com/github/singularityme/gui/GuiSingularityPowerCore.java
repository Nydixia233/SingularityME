package com.github.singularityme.gui;

import java.text.DecimalFormat;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.github.singularityme.tile.TileSingularityPowerCore;

import appeng.api.AEApi;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;

/**
 * AE2-style GUI for configuring a Singularity Power Core.
 */
public class GuiSingularityPowerCore extends AEBaseGui {

    private static final DecimalFormat AE_FORMAT = new DecimalFormat("#,###");
    private static final int BAR_X = 32;
    private static final int BAR_Y = 54;
    private static final int BAR_W = 112;
    private static final int BAR_H = 8;
    private static final int[] COMPONENT_SLOT_X = { 62, 80, 98 };
    private static final int COMPONENT_SLOT_Y = 27;

    private final TileSingularityPowerCore te;
    private GuiTabButton btnNetworkTab;

    public GuiSingularityPowerCore(final InventoryPlayer ip, final TileSingularityPowerCore te) {
        super(new ContainerSingularityPowerCore(ip, te));
        this.te = te;
        this.xSize = ContainerSingularityPowerCore.GUI_WIDTH;
        this.ySize = ContainerSingularityPowerCore.GUI_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.btnNetworkTab = new GuiTabButton(
            this.guiLeft + this.xSize - 22,
            this.guiTop + 4,
            2 + 11 * 16,
            "Network",
            itemRender);
        this.buttonList.add(this.btnNetworkTab);
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
        final ContainerSingularityPowerCore c = (ContainerSingularityPowerCore) this.inventorySlots;
        this.fontRendererObj.drawString(
            this.getGuiDisplayName(StatCollector.translateToLocal("gui.singularityme.power_core.title")),
            8,
            6,
            GuiColors.DriveTitle.getColor());
        final String buffer = StatCollector.translateToLocalFormatted(
            "gui.singularityme.power_core.buffer",
            formatAE(c.syncedCurrentBuffer),
            formatAE(c.syncedMaxBuffer));
        this.fontRendererObj.drawString(buffer, 8, 66, 0x404040);
        this.fontRendererObj
            .drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, GuiColors.DriveInventory.getColor());
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        final ContainerSingularityPowerCore c = (ContainerSingularityPowerCore) this.inventorySlots;
        drawRect(offsetX, offsetY, offsetX + this.xSize, offsetY + this.ySize, 0xFFC6C6C6);
        drawRect(offsetX, offsetY, offsetX + this.xSize, offsetY + 1, 0xFFFFFFFF);
        drawRect(offsetX, offsetY, offsetX + 1, offsetY + this.ySize, 0xFFFFFFFF);
        drawRect(offsetX + this.xSize - 1, offsetY, offsetX + this.xSize, offsetY + this.ySize, 0xFF555555);
        drawRect(offsetX, offsetY + this.ySize - 1, offsetX + this.xSize, offsetY + this.ySize, 0xFF555555);

        for (int slot = 0; slot < TileSingularityPowerCore.COMPONENT_SLOT_COUNT; slot++) {
            drawSlotBackground(offsetX + COMPONENT_SLOT_X[slot], offsetY + COMPONENT_SLOT_Y);
            if (this.inventorySlots.getSlot(slot)
                .getStack() == null) {
                this.drawGhostComponent(slot, offsetX + COMPONENT_SLOT_X[slot], offsetY + COMPONENT_SLOT_Y);
            }
        }

        drawRect(
            offsetX + BAR_X - 1,
            offsetY + BAR_Y - 1,
            offsetX + BAR_X + BAR_W + 1,
            offsetY + BAR_Y + BAR_H + 1,
            0xFF555555);
        drawRect(offsetX + BAR_X, offsetY + BAR_Y, offsetX + BAR_X + BAR_W, offsetY + BAR_Y + BAR_H, 0xFF263126);
        if (c.syncedMaxBuffer > 0 && c.syncedCurrentBuffer > 0) {
            final int fill = (int) Math.min(BAR_W, Math.max(1, (c.syncedCurrentBuffer * BAR_W) / c.syncedMaxBuffer));
            drawRect(offsetX + BAR_X, offsetY + BAR_Y, offsetX + BAR_X + fill, offsetY + BAR_Y + BAR_H, 0xFF35A852);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotBackground(
                    offsetX + 8 + col * 18,
                    offsetY + ContainerSingularityPowerCore.PLAYER_INVENTORY_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotBackground(offsetX + 8 + col * 18, offsetY + ContainerSingularityPowerCore.PLAYER_INVENTORY_Y + 58);
        }
    }

    private static String formatAE(final long value) {
        return value == Long.MAX_VALUE ? StatCollector.translateToLocal("waila.singularityme.infinity")
            : AE_FORMAT.format(value);
    }

    private void drawGhostComponent(final int slot, final int x, final int y) {
        final ItemStack ghost = getGhostComponent(slot);
        if (ghost == null) return;

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.35F);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), ghost, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();

        drawRect(x, y, x + 16, y + 16, 0x669A9A9A);
    }

    private static ItemStack getGhostComponent(final int slot) {
        return switch (slot) {
            case TileSingularityPowerCore.SLOT_ENERGY_CELL -> AEApi.instance()
                .definitions()
                .blocks()
                .energyCell()
                .maybeStack(1)
                .orNull();
            case TileSingularityPowerCore.SLOT_DENSE_ENERGY_CELL -> AEApi.instance()
                .definitions()
                .blocks()
                .energyCellDense()
                .maybeStack(1)
                .orNull();
            case TileSingularityPowerCore.SLOT_CREATIVE_ENERGY_CELL -> AEApi.instance()
                .definitions()
                .blocks()
                .energyCellCreative()
                .maybeStack(1)
                .orNull();
            default -> null;
        };
    }

    private static void drawSlotBackground(final int x, final int y) {
        final int borderX = x - 1;
        final int borderY = y - 1;
        drawRect(borderX, borderY, borderX + 18, borderY + 18, 0xFF6A6A6A);
        drawRect(borderX + 1, borderY + 1, borderX + 18, borderY + 18, 0xFFFFFFFF);
        drawRect(borderX + 1, borderY + 1, borderX + 17, borderY + 17, 0xFF9A9A9A);
    }
}
