package com.github.singularityme.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.block.BlockSingularityDrive;
import com.github.singularityme.tile.TileSingularityDrive;

import appeng.client.texture.ExtraBlockTextures;
import appeng.util.Platform;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class RenderSingularityDrive implements ISimpleBlockRenderingHandler {

    public static final RenderSingularityDrive INSTANCE = new RenderSingularityDrive();

    private static final double EPSILON = 0.001D;
    private final int renderId = RenderingRegistry.getNextAvailableRenderId();

    private RenderSingularityDrive() {}

    public static void register() {
        BlockSingularityDrive.setRenderTypeId(INSTANCE.getRenderId());
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
        if (te instanceof TileSingularityDrive drive) {
            renderLights(world, x, y, z, renderer, drive);
        }
        renderer.setRenderBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        return rendered;
    }

    private void renderLights(final IBlockAccess world, final int x, final int y, final int z,
        final RenderBlocks renderer, final TileSingularityDrive drive) {
        final ForgeDirection face = getFace(world, x, y, z, drive);
        final ForgeDirection up = getUp(drive, face);
        final ForgeDirection west = Platform.crossProduct(face, up);
        final IIcon icon = ExtraBlockTextures.MEStorageCellTextures.getIcon();
        final int blockLight = world
            .getLightBrightnessForSkyBlocks(x + face.offsetX, y + face.offsetY, z + face.offsetZ, 0);
        final int spin = getSpin(face, up);

        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 2; col++) {
                final int slot = row * 2 + (1 - col);
                final int status = drive.getCellStatus(slot);
                final int type = drive.getCellType(slot);

                selectFace(renderer, west, up, face, 2 + col * 7, 7 + col * 7, 1 + row * 3, 3 + row * 3);
                drawSelectedFace(
                    x,
                    y,
                    z,
                    renderer,
                    face,
                    blockLight,
                    0xFFFFFF,
                    icon.getInterpolatedU((spin % 4 < 2) ? 1 : 6),
                    icon.getInterpolatedU(((spin + 1) % 4 < 2) ? 1 : 6),
                    icon.getInterpolatedU(((spin + 2) % 4 < 2) ? 1 : 6),
                    icon.getInterpolatedU(((spin + 3) % 4 < 2) ? 1 : 6),
                    icon.getInterpolatedV(((spin + 1) % 4 < 2) ? typeVMin(type, status) : typeVMax(type, status)),
                    icon.getInterpolatedV(((spin + 2) % 4 < 2) ? typeVMin(type, status) : typeVMax(type, status)),
                    icon.getInterpolatedV(((spin + 3) % 4 < 2) ? typeVMin(type, status) : typeVMax(type, status)),
                    icon.getInterpolatedV(((spin) % 4 < 2) ? typeVMin(type, status) : typeVMax(type, status)));

                if ((face == ForgeDirection.UP && up == ForgeDirection.SOUTH) || face == ForgeDirection.DOWN) {
                    selectFace(renderer, west, up, face, 3 + col * 7, 4 + col * 7, 1 + row * 3, 2 + row * 3);
                } else {
                    selectFace(renderer, west, up, face, 5 + col * 7, 6 + col * 7, 2 + row * 3, 3 + row * 3);
                }

                if (status != 0) {
                    final IIcon whiteIcon = ExtraBlockTextures.White.getIcon();
                    drawSelectedFace(
                        x,
                        y,
                        z,
                        renderer,
                        face,
                        drive.isPowered() ? fullBright() : 0,
                        statusColor(status, drive.isPowered()),
                        whiteIcon.getInterpolatedU((spin % 4 < 2) ? 1 : 6),
                        whiteIcon.getInterpolatedU(((spin + 1) % 4 < 2) ? 1 : 6),
                        whiteIcon.getInterpolatedU(((spin + 2) % 4 < 2) ? 1 : 6),
                        whiteIcon.getInterpolatedU(((spin + 3) % 4 < 2) ? 1 : 6),
                        whiteIcon.getInterpolatedV(((spin + 1) % 4 < 2) ? 1 : 3),
                        whiteIcon.getInterpolatedV(((spin + 2) % 4 < 2) ? 1 : 3),
                        whiteIcon.getInterpolatedV(((spin + 3) % 4 < 2) ? 1 : 3),
                        whiteIcon.getInterpolatedV(((spin) % 4 < 2) ? 1 : 3));
                }
            }
        }
    }

    private ForgeDirection getFace(final IBlockAccess world, final int x, final int y, final int z,
        final TileSingularityDrive drive) {
        ForgeDirection side = drive.getForward();
        if (side == null || side == ForgeDirection.UNKNOWN) {
            side = ForgeDirection.getOrientation(world.getBlockMetadata(x, y, z));
        }
        return side == ForgeDirection.UNKNOWN ? ForgeDirection.SOUTH : side;
    }

    private ForgeDirection getUp(final TileSingularityDrive drive, final ForgeDirection face) {
        ForgeDirection up = drive.getUp();
        if (up == null || up == ForgeDirection.UNKNOWN || up == face || up == face.getOpposite()) {
            up = face.offsetY == 0 ? ForgeDirection.UP : ForgeDirection.SOUTH;
        }
        return up;
    }

    private int typeVMin(final int type, final int status) {
        if (status == 0) return 13;
        return 1 + type * 4;
    }

    private int typeVMax(final int type, final int status) {
        if (status == 0) return 15;
        return 3 + type * 4;
    }

    private int statusColor(final int status, final boolean powered) {
        if (!powered) return 0x000000;
        return switch (status) {
            case 1 -> 0x00FF00;
            case 2 -> 0x00AAFF;
            case 3 -> 0xFFAA00;
            case 4 -> 0xFF0000;
            default -> 0x101010;
        };
    }

    private int fullBright() {
        return 15 << 20 | 15 << 4;
    }

    private void selectFace(final RenderBlocks renderer, final ForgeDirection west, final ForgeDirection up,
        final ForgeDirection forward, final int u1, final int u2, int v1, int v2) {
        v1 = 16 - v1;
        v2 = 16 - v2;

        final double minX = (forward.offsetX > 0 ? 1 : 0) + mapFaceUV(west.offsetX, u1) + mapFaceUV(up.offsetX, v1);
        final double minY = (forward.offsetY > 0 ? 1 : 0) + mapFaceUV(west.offsetY, u1) + mapFaceUV(up.offsetY, v1);
        final double minZ = (forward.offsetZ > 0 ? 1 : 0) + mapFaceUV(west.offsetZ, u1) + mapFaceUV(up.offsetZ, v1);

        final double maxX = (forward.offsetX > 0 ? 1 : 0) + mapFaceUV(west.offsetX, u2) + mapFaceUV(up.offsetX, v2);
        final double maxY = (forward.offsetY > 0 ? 1 : 0) + mapFaceUV(west.offsetY, u2) + mapFaceUV(up.offsetY, v2);
        final double maxZ = (forward.offsetZ > 0 ? 1 : 0) + mapFaceUV(west.offsetZ, u2) + mapFaceUV(up.offsetZ, v2);

        renderer.renderMinX = Math.max(0.0, Math.min(minX, maxX) - (forward.offsetX != 0 ? 0 : EPSILON));
        renderer.renderMaxX = Math.min(1.0, Math.max(minX, maxX) + (forward.offsetX != 0 ? 0 : EPSILON));

        renderer.renderMinY = Math.max(0.0, Math.min(minY, maxY) - (forward.offsetY != 0 ? 0 : EPSILON));
        renderer.renderMaxY = Math.min(1.0, Math.max(minY, maxY) + (forward.offsetY != 0 ? 0 : EPSILON));

        renderer.renderMinZ = Math.max(0.0, Math.min(minZ, maxZ) - (forward.offsetZ != 0 ? 0 : EPSILON));
        renderer.renderMaxZ = Math.min(1.0, Math.max(minZ, maxZ) + (forward.offsetZ != 0 ? 0 : EPSILON));
    }

    private double mapFaceUV(final int offset, final int uv) {
        if (offset == 0) return 0;
        return offset > 0 ? uv / 16.0D : (16.0D - uv) / 16.0D;
    }

    private void drawSelectedFace(final int x, final int y, final int z, final RenderBlocks renderer,
        final ForgeDirection face, final int brightness, final int color, final double u1, final double u2,
        final double u3, final double u4, final double v1, final double v2, final double v3, final double v4) {
        final Tessellator tess = Tessellator.instance;
        tess.setBrightness(brightness);
        tess.setColorOpaque_I(color);

        switch (face.offsetX + face.offsetY * 2 + face.offsetZ * 3) {
            case 1 -> {
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMinZ, u1, v1);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u2, v2);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMinY, z + renderer.renderMaxZ, u3, v3);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMinY, z + renderer.renderMinZ, u4, v4);
            }
            case -1 -> {
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMinY, z + renderer.renderMinZ, u1, v1);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMinY, z + renderer.renderMaxZ, u2, v2);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u3, v3);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMinZ, u4, v4);
            }
            case -2 -> {
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMinZ, u1, v1);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u2, v2);
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u3, v3);
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMaxY, z + renderer.renderMinZ, u4, v4);
            }
            case 2 -> {
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMaxY, z + renderer.renderMinZ, u1, v1);
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u2, v2);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u3, v3);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMinZ, u4, v4);
            }
            case 3 -> {
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMinY, z + renderer.renderMaxZ, u1, v1);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u2, v2);
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u3, v3);
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMinY, z + renderer.renderMaxZ, u4, v4);
            }
            case -3 -> {
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMinY, z + renderer.renderMaxZ, u1, v1);
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u2, v2);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u3, v3);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMinY, z + renderer.renderMaxZ, u4, v4);
            }
            default -> {}
        }
    }

    private int getSpin(final ForgeDirection forward, final ForgeDirection up) {
        int spin = 0;
        switch (forward.offsetX + forward.offsetY * 2 + forward.offsetZ * 3) {
            case 1 -> {
                switch (up) {
                    case UP -> spin = 3;
                    case DOWN -> spin = 1;
                    case NORTH -> spin = 0;
                    case SOUTH -> spin = 2;
                    default -> {}
                }
            }
            case -1 -> {
                switch (up) {
                    case UP -> spin = 1;
                    case DOWN -> spin = 3;
                    case NORTH -> spin = 0;
                    case SOUTH -> spin = 2;
                    default -> {}
                }
            }
            case -2 -> {
                switch (up) {
                    case EAST -> spin = 1;
                    case WEST -> spin = 3;
                    case NORTH -> spin = 2;
                    case SOUTH -> spin = 0;
                    default -> {}
                }
            }
            case 2 -> {
                switch (up) {
                    case EAST -> spin = 1;
                    case WEST -> spin = 3;
                    case NORTH, SOUTH -> spin = 0;
                    default -> {}
                }
            }
            case 3 -> {
                switch (up) {
                    case UP, DOWN -> spin = 2;
                    case EAST -> spin = 3;
                    case WEST -> spin = 1;
                    default -> {}
                }
            }
            case -3 -> {
                switch (up) {
                    case UP, DOWN -> spin = 2;
                    case EAST -> spin = 1;
                    case WEST -> spin = 3;
                    default -> {}
                }
            }
            default -> {}
        }
        return spin;
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
