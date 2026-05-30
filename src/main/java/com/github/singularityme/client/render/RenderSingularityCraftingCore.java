package com.github.singularityme.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.block.BlockSingularityCraftingCore;
import com.github.singularityme.tile.TileSingularityCraftingCore;

import appeng.api.util.AEColor;
import appeng.client.texture.ExtraBlockTextures;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class RenderSingularityCraftingCore implements ISimpleBlockRenderingHandler {

    public static final RenderSingularityCraftingCore INSTANCE = new RenderSingularityCraftingCore();

    private static final double EPSILON = 0.002D;
    private final int renderId = RenderingRegistry.getNextAvailableRenderId();

    private RenderSingularityCraftingCore() {}

    public static void register() {
        BlockSingularityCraftingCore.setRenderTypeId(INSTANCE.getRenderId());
        RenderingRegistry.registerBlockHandler(INSTANCE);
    }

    @Override
    public void renderInventoryBlock(final Block block, final int metadata, final int modelID,
        final RenderBlocks renderer) {
        RenderInventoryBlocks.renderBox(block, metadata, renderer, 0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        renderer.setRenderBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    }

    @Override
    public boolean renderWorldBlock(final IBlockAccess world, final int x, final int y, final int z, final Block block,
        final int modelId, final RenderBlocks renderer) {
        renderer.setRenderBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        final boolean rendered = renderer.renderStandardBlock(block, x, y, z);

        final TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSingularityCraftingCore core && core.hasMonitorPanel()) {
            final ForgeDirection face = getFace(world.getBlockMetadata(x, y, z), core);
            final int brightness = block.getMixedBrightnessForBlock(world, x, y, z);
            renderMonitorLayer(
                x,
                y,
                z,
                face,
                brightness,
                ExtraBlockTextures.BlockCraftingMonitorFit_Dark.getIcon(),
                AEColor.Transparent.blackVariant);
            renderMonitorLayer(
                x,
                y,
                z,
                face,
                brightness,
                ExtraBlockTextures.BlockCraftingMonitorFit_Medium.getIcon(),
                AEColor.Transparent.mediumVariant);
            renderMonitorLayer(
                x,
                y,
                z,
                face,
                brightness,
                ExtraBlockTextures.BlockCraftingMonitorFit_Light.getIcon(),
                AEColor.Transparent.whiteVariant);
        }

        renderer.setRenderBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        return rendered;
    }

    private ForgeDirection getFace(final int metadata, final TileSingularityCraftingCore core) {
        final ForgeDirection forward = core.getForward();
        if (isValidFace(forward)) {
            return forward;
        }
        final ForgeDirection side = ForgeDirection.getOrientation(metadata);
        return isValidFace(side) ? side : ForgeDirection.SOUTH;
    }

    private boolean isValidFace(final ForgeDirection side) {
        return side != null && side != ForgeDirection.UNKNOWN;
    }

    private void renderMonitorLayer(final int x, final int y, final int z, final ForgeDirection face,
        final int brightness, final IIcon icon, final int color) {
        final Tessellator tess = Tessellator.instance;
        tess.setBrightness(brightness);
        tess.setColorOpaque_I(color);

        final double u0 = icon.getMinU();
        final double u1 = icon.getMaxU();
        final double v0 = icon.getMinV();
        final double v1 = icon.getMaxV();

        switch (face) {
            case NORTH -> {
                final double z0 = z - EPSILON;
                tess.addVertexWithUV(x + 1, y + 1, z0, u0, v0);
                tess.addVertexWithUV(x + 1, y, z0, u0, v1);
                tess.addVertexWithUV(x, y, z0, u1, v1);
                tess.addVertexWithUV(x, y + 1, z0, u1, v0);
            }
            case SOUTH -> {
                final double z0 = z + 1 + EPSILON;
                tess.addVertexWithUV(x, y + 1, z0, u0, v0);
                tess.addVertexWithUV(x, y, z0, u0, v1);
                tess.addVertexWithUV(x + 1, y, z0, u1, v1);
                tess.addVertexWithUV(x + 1, y + 1, z0, u1, v0);
            }
            case WEST -> {
                final double x0 = x - EPSILON;
                tess.addVertexWithUV(x0, y + 1, z, u0, v0);
                tess.addVertexWithUV(x0, y, z, u0, v1);
                tess.addVertexWithUV(x0, y, z + 1, u1, v1);
                tess.addVertexWithUV(x0, y + 1, z + 1, u1, v0);
            }
            case EAST -> {
                final double x0 = x + 1 + EPSILON;
                tess.addVertexWithUV(x0, y + 1, z + 1, u0, v0);
                tess.addVertexWithUV(x0, y, z + 1, u0, v1);
                tess.addVertexWithUV(x0, y, z, u1, v1);
                tess.addVertexWithUV(x0, y + 1, z, u1, v0);
            }
            case UP -> {
                final double y0 = y + 1 + EPSILON;
                tess.addVertexWithUV(x, y0, z, u0, v0);
                tess.addVertexWithUV(x, y0, z + 1, u0, v1);
                tess.addVertexWithUV(x + 1, y0, z + 1, u1, v1);
                tess.addVertexWithUV(x + 1, y0, z, u1, v0);
            }
            case DOWN -> {
                final double y0 = y - EPSILON;
                tess.addVertexWithUV(x + 1, y0, z, u0, v0);
                tess.addVertexWithUV(x + 1, y0, z + 1, u0, v1);
                tess.addVertexWithUV(x, y0, z + 1, u1, v1);
                tess.addVertexWithUV(x, y0, z, u1, v0);
            }
            default -> {}
        }

        tess.setColorOpaque_I(0xFFFFFF);
    }

    @Override
    public boolean shouldRender3DInInventory(final int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return this.renderId;
    }
}
