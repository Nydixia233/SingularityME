package com.github.singularityme.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.github.singularityme.tile.TileSingularityCraftingCore;

import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.util.IWideReadableNumberConverter;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;

public final class RenderSingularityCraftingCoreTESR extends TileEntitySpecialRenderer {

    private static final IWideReadableNumberConverter NUMBER_CONVERTER = ReadableNumberConverter.INSTANCE;

    @Override
    public void renderTileEntityAt(final TileEntity tile, final double x, final double y, final double z,
        final float partialTicks) {
        if (!(tile instanceof TileSingularityCraftingCore core) || !core.hasMonitorPanel()) return;

        final IAEStack<?> displayStack = core.getMonitorDisplayStack();
        if (displayStack == null) return;

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5D, y + 0.5D, z + 0.5D);
        this.renderScreen(core, displayStack);
        GL11.glPopMatrix();
    }

    private void renderScreen(final TileSingularityCraftingCore core, final IAEStack<?> displayStack) {
        final ForgeDirection side = getForward(core);
        final ForgeDirection up = getUp(core, side);

        ForgeDirection cursor = side.offsetY != 0 ? ForgeDirection.SOUTH : ForgeDirection.UP;
        int spin = 0;
        int attempts = 5;
        while (cursor != up && attempts > 0) {
            attempts--;
            spin++;
            cursor = Platform.rotateAround(cursor, side);
        }

        GL11.glTranslated(side.offsetX * 0.69D, side.offsetY * 0.69D, side.offsetZ * 0.69D);
        GL11.glScalef(0.7F, 0.7F, 0.7F);

        switch (side) {
            case UP -> {
                GL11.glScalef(1.0F, -1.0F, 1.0F);
                GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
                GL11.glRotatef(spin * 90.0F, 0.0F, 0.0F, 1.0F);
            }
            case DOWN -> {
                GL11.glScalef(1.0F, -1.0F, 1.0F);
                GL11.glRotatef(-90.0F, 1.0F, 0.0F, 0.0F);
                GL11.glRotatef(spin * -90.0F, 0.0F, 0.0F, 1.0F);
            }
            case EAST -> {
                GL11.glScalef(-1.0F, -1.0F, -1.0F);
                GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
            }
            case WEST -> {
                GL11.glScalef(-1.0F, -1.0F, -1.0F);
                GL11.glRotatef(90.0F, 0.0F, 1.0F, 0.0F);
            }
            case NORTH -> GL11.glScalef(-1.0F, -1.0F, -1.0F);
            case SOUTH -> {
                GL11.glScalef(-1.0F, -1.0F, -1.0F);
                GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);
            }
            default -> {}
        }

        try {
            final Tessellator tess = Tessellator.instance;
            final int brightness = 16 << 20 | 16 << 4;
            OpenGlHelper.setLightmapTextureCoords(
                OpenGlHelper.lightmapTexUnit,
                (brightness % 65536) * 0.8F,
                (brightness / 65536) * 0.8F);

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            tess.setColorOpaque_F(1.0F, 1.0F, 1.0F);

            displayStack.drawOnBlockFace(core.getWorldObj());
        } catch (final Exception e) {
            AELog.debug(e);
        } finally {
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_LIGHTING);
        }

        GL11.glTranslatef(0.0F, 0.14F, -0.24F);
        GL11.glScalef(1.0F / 62.0F, 1.0F / 62.0F, 1.0F / 62.0F);

        final String amount = NUMBER_CONVERTER.toWideReadableForm(displayStack.getStackSize());
        final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        final int width = font.getStringWidth(amount);
        GL11.glTranslatef(-0.5F * width, 0.0F, -1.0F);
        font.drawString(amount, 0, 0, 0);
    }

    private static ForgeDirection getForward(final TileSingularityCraftingCore core) {
        final ForgeDirection forward = core.getForward();
        return forward == null || forward == ForgeDirection.UNKNOWN ? ForgeDirection.SOUTH : forward;
    }

    private static ForgeDirection getUp(final TileSingularityCraftingCore core, final ForgeDirection forward) {
        final ForgeDirection up = core.getUp();
        if (up != null && up != ForgeDirection.UNKNOWN && up != forward && up != forward.getOpposite()) {
            return up;
        }
        return forward.offsetY == 0 ? ForgeDirection.UP : ForgeDirection.SOUTH;
    }
}
