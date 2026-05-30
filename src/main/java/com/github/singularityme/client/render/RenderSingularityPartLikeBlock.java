package com.github.singularityme.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.singularityme.block.BlockSingularityCraftingTerminal;
import com.github.singularityme.block.BlockSingularityExportBus;
import com.github.singularityme.block.BlockSingularityImportBus;
import com.github.singularityme.block.BlockSingularityInterface;
import com.github.singularityme.block.BlockSingularityPatternTerminal;
import com.github.singularityme.block.BlockSingularityStorageBus;
import com.github.singularityme.block.BlockSingularityTerminal;
import com.github.singularityme.block.SingularityPartGeometry;
import com.github.singularityme.tile.TileSingularityTerminal;

import appeng.api.util.AEColor;
import appeng.client.texture.CableBusTextures;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class RenderSingularityPartLikeBlock implements ISimpleBlockRenderingHandler {

    public static final RenderSingularityPartLikeBlock INSTANCE = new RenderSingularityPartLikeBlock();

    private final int renderId = RenderingRegistry.getNextAvailableRenderId();

    private RenderSingularityPartLikeBlock() {}

    public static void register() {
        final int id = INSTANCE.getRenderId();
        BlockSingularityStorageBus.setRenderTypeId(id);
        BlockSingularityImportBus.setRenderTypeId(id);
        BlockSingularityExportBus.setRenderTypeId(id);
        BlockSingularityInterface.setRenderTypeId(id);
        BlockSingularityTerminal.setRenderTypeId(id);
        RenderingRegistry.registerBlockHandler(INSTANCE);
    }

    @Override
    public void renderInventoryBlock(final Block block, final int metadata, final int modelID,
        final RenderBlocks renderer) {
        if (block instanceof BlockSingularityTerminal) {
            renderTerminalInventoryBlock(block, metadata, renderer);
            renderer.setRenderBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
            return;
        }

        for (final SingularityPartGeometry.Box box : getBoxes(block, ForgeDirection.SOUTH)) {
            RenderInventoryBlocks
                .renderBox(block, metadata, renderer, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }
        renderer.setRenderBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    }

    @Override
    public boolean renderWorldBlock(final IBlockAccess world, final int x, final int y, final int z, final Block block,
        final int modelId, final RenderBlocks renderer) {
        final ForgeDirection face = getFace(world.getBlockMetadata(x, y, z));
        if (block instanceof BlockSingularityTerminal) {
            return renderTerminalWorldBlock(world, x, y, z, block, face, renderer);
        }

        final SingularityPartGeometry.Box[] boxes = getBoxes(block, face);
        if (boxes.length == 0) return false;

        boolean rendered = false;
        for (final SingularityPartGeometry.Box box : boxes) {
            setBounds(renderer, box);
            rendered |= renderer.renderStandardBlock(block, x, y, z);
        }
        renderer.setRenderBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        return rendered;
    }

    private void renderTerminalInventoryBlock(final Block block, final int metadata, final RenderBlocks renderer) {
        final IIcon side = CableBusTextures.PartMonitorSides.getIcon();
        final IIcon back = CableBusTextures.PartMonitorBack.getIcon();
        final IIcon front = block.getIcon(ForgeDirection.SOUTH.ordinal(), metadata);
        final IIcon status = CableBusTextures.PartMonitorSidesStatus.getIcon();

        final SingularityPartGeometry.Box[] boxes = getBoxes(block, ForgeDirection.SOUTH);
        if (boxes.length < 2) return;

        RenderInventoryBlocks.renderBoxWithIcons(
            block,
            renderer,
            boxes[0].minX,
            boxes[0].minY,
            boxes[0].minZ,
            boxes[0].maxX,
            boxes[0].maxY,
            boxes[0].maxZ,
            side,
            side,
            back,
            front,
            side,
            side);

        renderTerminalInventoryFront(block, renderer, boxes[0]);

        RenderInventoryBlocks.renderBoxWithIcons(
            block,
            renderer,
            boxes[1].minX,
            boxes[1].minY,
            boxes[1].minZ,
            boxes[1].maxX,
            boxes[1].maxY,
            boxes[1].maxZ,
            status,
            status,
            back,
            front,
            status,
            status);
    }

    private void renderTerminalInventoryFront(final Block block, final RenderBlocks renderer,
        final SingularityPartGeometry.Box box) {
        renderer.setRenderBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);

        final Tessellator tess = Tessellator.instance;
        renderInventoryOverlayFace(block, renderer, terminalBright(block), AEColor.Transparent.whiteVariant);
        renderInventoryOverlayFace(block, renderer, terminalDark(block), AEColor.Transparent.mediumVariant);
        renderInventoryOverlayFace(block, renderer, terminalColored(block), AEColor.Transparent.blackVariant);
        tess.setColorOpaque_I(0xFFFFFF);
    }

    private void renderInventoryOverlayFace(final Block block, final RenderBlocks renderer, final IIcon icon,
        final int color) {
        final Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setNormal(0.0F, 0.0F, 1.0F);
        tess.setColorOpaque_I(color);
        renderer.renderFaceZPos(block, -0.5D, -0.5D, -0.5D, icon);
        tess.draw();
    }

    private boolean renderTerminalWorldBlock(final IBlockAccess world, final int x, final int y, final int z,
        final Block block, final ForgeDirection face, final RenderBlocks renderer) {
        final SingularityPartGeometry.Box[] boxes = getBoxes(block, face);
        if (boxes.length < 2) return false;

        final TileEntity te = world.getTileEntity(x, y, z);
        final TileSingularityTerminal terminal = te instanceof TileSingularityTerminal t ? t : null;
        final int spin = terminal == null ? 0 : terminal.getSpin();
        final boolean active = terminal != null && terminal.isTerminalActive();
        final boolean powered = terminal != null && terminal.isTerminalPowered();

        final IIcon side = CableBusTextures.PartMonitorSides.getIcon();
        final IIcon back = CableBusTextures.PartMonitorBack.getIcon();
        final IIcon front = block.getIcon(face.ordinal(), world.getBlockMetadata(x, y, z));
        final IIcon status = CableBusTextures.PartMonitorSidesStatus.getIcon();

        renderTerminalBox(world, x, y, z, block, renderer, boxes[0], face, side, back, front);
        renderTerminalFrontLayers(world, x, y, z, block, renderer, boxes[0], face, spin, powered);
        renderTerminalBox(world, x, y, z, block, renderer, boxes[1], face, status, back, front);
        renderTerminalStatusLights(world, x, y, z, block, renderer, boxes[1], face, active, powered);

        renderer.setRenderBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        return true;
    }

    private void renderTerminalBox(final IBlockAccess world, final int x, final int y, final int z, final Block block,
        final RenderBlocks renderer, final SingularityPartGeometry.Box box, final ForgeDirection frontFace,
        final IIcon sideIcon, final IIcon backIcon, final IIcon frontIcon) {
        setBounds(renderer, box);
        final Tessellator tess = Tessellator.instance;
        tess.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z));
        tess.setColorOpaque_I(0xFFFFFF);
        for (final ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            renderWorldFace(
                renderer,
                block,
                x,
                y,
                z,
                side,
                iconForTerminalFace(side, frontFace, sideIcon, backIcon, frontIcon));
        }
    }

    private IIcon iconForTerminalFace(final ForgeDirection side, final ForgeDirection frontFace, final IIcon sideIcon,
        final IIcon backIcon, final IIcon frontIcon) {
        if (side == frontFace) return frontIcon;
        if (side == frontFace.getOpposite()) return backIcon;
        return sideIcon;
    }

    private void renderTerminalFrontLayers(final IBlockAccess world, final int x, final int y, final int z,
        final Block block, final RenderBlocks renderer, final SingularityPartGeometry.Box box,
        final ForgeDirection frontFace, final int spin, final boolean powered) {
        setBounds(renderer, box);
        final Tessellator tess = Tessellator.instance;
        if (powered) {
            final int light = 13;
            tess.setBrightness(light << 20 | light << 4);
        } else {
            tess.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z));
        }

        setUvRotation(renderer, spin);
        renderColoredWorldFace(
            renderer,
            block,
            x,
            y,
            z,
            frontFace,
            terminalBright(block),
            AEColor.Transparent.whiteVariant);
        renderColoredWorldFace(
            renderer,
            block,
            x,
            y,
            z,
            frontFace,
            terminalDark(block),
            AEColor.Transparent.mediumVariant);
        renderColoredWorldFace(
            renderer,
            block,
            x,
            y,
            z,
            frontFace,
            terminalColored(block),
            AEColor.Transparent.blackVariant);
        setUvRotation(renderer, 0);
        tess.setColorOpaque_I(0xFFFFFF);
        tess.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z));
    }

    private void renderTerminalStatusLights(final IBlockAccess world, final int x, final int y, final int z,
        final Block block, final RenderBlocks renderer, final SingularityPartGeometry.Box box,
        final ForgeDirection frontFace, final boolean active, final boolean powered) {
        setBounds(renderer, box);
        final Tessellator tess = Tessellator.instance;
        final IIcon lightIcon = CableBusTextures.PartMonitorSidesStatusLights.getIcon();

        if (active) {
            final int light = 14;
            tess.setBrightness(light << 20 | light << 4);
            tess.setColorOpaque_I(AEColor.Transparent.blackVariant);
        } else if (powered) {
            final int light = 9;
            tess.setBrightness(light << 20 | light << 4);
            tess.setColorOpaque_I(AEColor.Transparent.whiteVariant);
        } else {
            tess.setBrightness(0);
            tess.setColorOpaque_I(0x000000);
        }

        for (final ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (side != frontFace && side != frontFace.getOpposite()) {
                renderWorldFace(renderer, block, x, y, z, side, lightIcon);
            }
        }

        tess.setColorOpaque_I(0xFFFFFF);
        tess.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z));
    }

    private void renderColoredWorldFace(final RenderBlocks renderer, final Block block, final int x, final int y,
        final int z, final ForgeDirection side, final IIcon icon, final int color) {
        Tessellator.instance.setColorOpaque_I(color);
        renderWorldFace(renderer, block, x, y, z, side, icon);
    }

    private void renderWorldFace(final RenderBlocks renderer, final Block block, final int x, final int y, final int z,
        final ForgeDirection side, final IIcon icon) {
        switch (side) {
            case DOWN -> renderer.renderFaceYNeg(block, x, y, z, icon);
            case UP -> renderer.renderFaceYPos(block, x, y, z, icon);
            case NORTH -> renderer.renderFaceZNeg(block, x, y, z, icon);
            case SOUTH -> renderer.renderFaceZPos(block, x, y, z, icon);
            case WEST -> renderer.renderFaceXNeg(block, x, y, z, icon);
            case EAST -> renderer.renderFaceXPos(block, x, y, z, icon);
            default -> {}
        }
    }

    private void setUvRotation(final RenderBlocks renderer, final int spin) {
        renderer.uvRotateBottom = spin;
        renderer.uvRotateEast = spin;
        renderer.uvRotateNorth = spin;
        renderer.uvRotateSouth = spin;
        renderer.uvRotateTop = spin;
        renderer.uvRotateWest = spin;
    }

    private SingularityPartGeometry.Box[] getBoxes(final Block block, final ForgeDirection face) {
        if (block instanceof BlockSingularityStorageBus) {
            return SingularityPartGeometry.getBoxes(SingularityPartGeometry.Kind.STORAGE_BUS, face);
        }
        if (block instanceof BlockSingularityImportBus) {
            return SingularityPartGeometry.getBoxes(SingularityPartGeometry.Kind.IMPORT_BUS, face);
        }
        if (block instanceof BlockSingularityExportBus) {
            return SingularityPartGeometry.getBoxes(SingularityPartGeometry.Kind.EXPORT_BUS, face);
        }
        if (block instanceof BlockSingularityInterface) {
            return SingularityPartGeometry.getBoxes(SingularityPartGeometry.Kind.INTERFACE, face);
        }
        if (block instanceof BlockSingularityTerminal) {
            return SingularityPartGeometry.getBoxes(SingularityPartGeometry.Kind.TERMINAL, face);
        }
        return new SingularityPartGeometry.Box[0];
    }

    private ForgeDirection getFace(final int metadata) {
        final ForgeDirection side = ForgeDirection.getOrientation(metadata);
        return side == ForgeDirection.UNKNOWN ? ForgeDirection.SOUTH : side;
    }

    private IIcon terminalBright(final Block block) {
        if (block instanceof BlockSingularityCraftingTerminal)
            return CableBusTextures.PartCraftingTerm_Bright.getIcon();
        if (block instanceof BlockSingularityPatternTerminal) return CableBusTextures.PartPatternTerm_Bright.getIcon();
        return CableBusTextures.PartTerminal_Bright.getIcon();
    }

    private IIcon terminalDark(final Block block) {
        if (block instanceof BlockSingularityCraftingTerminal) return CableBusTextures.PartCraftingTerm_Dark.getIcon();
        if (block instanceof BlockSingularityPatternTerminal) return CableBusTextures.PartPatternTerm_Dark.getIcon();
        return CableBusTextures.PartTerminal_Dark.getIcon();
    }

    private IIcon terminalColored(final Block block) {
        if (block instanceof BlockSingularityCraftingTerminal) {
            return CableBusTextures.PartCraftingTerm_Colored.getIcon();
        }
        if (block instanceof BlockSingularityPatternTerminal) {
            return CableBusTextures.PartPatternTerm_Colored.getIcon();
        }
        return CableBusTextures.PartTerminal_Colored.getIcon();
    }

    private void setBounds(final RenderBlocks renderer, final SingularityPartGeometry.Box box) {
        renderer.setRenderBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
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
