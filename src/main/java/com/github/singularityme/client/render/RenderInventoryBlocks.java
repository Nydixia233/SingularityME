package com.github.singularityme.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class RenderInventoryBlocks {

    private RenderInventoryBlocks() {}

    static void renderBox(final Block block, final int metadata, final RenderBlocks renderer, final double minX,
        final double minY, final double minZ, final double maxX, final double maxY, final double maxZ) {
        renderer.setRenderBounds(minX, minY, minZ, maxX, maxY, maxZ);

        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        renderFaces(block, metadata, renderer);
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
    }

    static void renderBoxWithIcons(final Block block, final RenderBlocks renderer, final double minX, final double minY,
        final double minZ, final double maxX, final double maxY, final double maxZ, final IIcon down, final IIcon up,
        final IIcon north, final IIcon south, final IIcon west, final IIcon east) {
        renderer.setRenderBounds(minX, minY, minZ, maxX, maxY, maxZ);

        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        renderFaces(block, renderer, down, up, north, south, west, east);
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
    }

    private static void renderFaces(final Block block, final int metadata, final RenderBlocks renderer) {
        final Tessellator tess = Tessellator.instance;

        tess.startDrawingQuads();
        tess.setNormal(0.0F, -1.0F, 0.0F);
        renderer.renderFaceYNeg(block, 0.0D, 0.0D, 0.0D, icon(block, 0, metadata));
        tess.draw();

        tess.startDrawingQuads();
        tess.setNormal(0.0F, 1.0F, 0.0F);
        renderer.renderFaceYPos(block, 0.0D, 0.0D, 0.0D, icon(block, 1, metadata));
        tess.draw();

        tess.startDrawingQuads();
        tess.setNormal(0.0F, 0.0F, -1.0F);
        renderer.renderFaceZNeg(block, 0.0D, 0.0D, 0.0D, icon(block, 2, metadata));
        tess.draw();

        tess.startDrawingQuads();
        tess.setNormal(0.0F, 0.0F, 1.0F);
        renderer.renderFaceZPos(block, 0.0D, 0.0D, 0.0D, icon(block, 3, metadata));
        tess.draw();

        tess.startDrawingQuads();
        tess.setNormal(-1.0F, 0.0F, 0.0F);
        renderer.renderFaceXNeg(block, 0.0D, 0.0D, 0.0D, icon(block, 4, metadata));
        tess.draw();

        tess.startDrawingQuads();
        tess.setNormal(1.0F, 0.0F, 0.0F);
        renderer.renderFaceXPos(block, 0.0D, 0.0D, 0.0D, icon(block, 5, metadata));
        tess.draw();
    }

    private static void renderFaces(final Block block, final RenderBlocks renderer, final IIcon down, final IIcon up,
        final IIcon north, final IIcon south, final IIcon west, final IIcon east) {
        final Tessellator tess = Tessellator.instance;

        tess.startDrawingQuads();
        tess.setNormal(0.0F, -1.0F, 0.0F);
        renderer.renderFaceYNeg(block, 0.0D, 0.0D, 0.0D, down);
        tess.draw();

        tess.startDrawingQuads();
        tess.setNormal(0.0F, 1.0F, 0.0F);
        renderer.renderFaceYPos(block, 0.0D, 0.0D, 0.0D, up);
        tess.draw();

        tess.startDrawingQuads();
        tess.setNormal(0.0F, 0.0F, -1.0F);
        renderer.renderFaceZNeg(block, 0.0D, 0.0D, 0.0D, north);
        tess.draw();

        tess.startDrawingQuads();
        tess.setNormal(0.0F, 0.0F, 1.0F);
        renderer.renderFaceZPos(block, 0.0D, 0.0D, 0.0D, south);
        tess.draw();

        tess.startDrawingQuads();
        tess.setNormal(-1.0F, 0.0F, 0.0F);
        renderer.renderFaceXNeg(block, 0.0D, 0.0D, 0.0D, west);
        tess.draw();

        tess.startDrawingQuads();
        tess.setNormal(1.0F, 0.0F, 0.0F);
        renderer.renderFaceXPos(block, 0.0D, 0.0D, 0.0D, east);
        tess.draw();
    }

    private static IIcon icon(final Block block, final int side, final int metadata) {
        return block.getIcon(side, metadata);
    }
}
