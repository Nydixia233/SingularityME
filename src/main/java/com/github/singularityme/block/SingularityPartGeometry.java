package com.github.singularityme.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Shared part-like geometry for Singularity blocks.
 *
 * <p>
 * Coordinates are authored for the SOUTH face in sixteenths of a block, matching the
 * renderer's original local coordinate system. The same transformed boxes are used for
 * rendering, collision, and selection.
 */
public final class SingularityPartGeometry {

    public enum Kind {
        STORAGE_BUS,
        IMPORT_BUS,
        EXPORT_BUS,
        INTERFACE,
        TERMINAL
    }

    private static final Box[] STORAGE_BUS = boxes(
        new Box(3, 3, 15, 13, 13, 16),
        new Box(2, 2, 14, 14, 14, 15),
        new Box(5, 5, 12, 11, 11, 14));
    private static final Box[] IMPORT_BUS = boxes(
        new Box(4, 4, 14, 12, 12, 16),
        new Box(5, 5, 13, 11, 11, 14),
        new Box(6, 6, 12, 10, 10, 13),
        new Box(6, 6, 11, 10, 10, 12));
    private static final Box[] EXPORT_BUS = boxes(
        new Box(4, 4, 12, 12, 12, 14),
        new Box(5, 5, 14, 11, 11, 15),
        new Box(6, 6, 15, 10, 10, 16),
        new Box(6, 6, 11, 10, 10, 12));
    private static final Box[] INTERFACE = boxes(
        new Box(2, 2, 14, 14, 14, 16),
        new Box(5, 5, 12, 11, 11, 13),
        new Box(5, 5, 13, 11, 11, 14));
    private static final Box[] TERMINAL = boxes(new Box(2, 2, 14, 14, 14, 16), new Box(4, 4, 13, 12, 12, 14));

    private SingularityPartGeometry() {}

    public static Box[] getBoxes(final Kind kind, final ForgeDirection face) {
        final Box[] local = switch (kind) {
            case STORAGE_BUS -> STORAGE_BUS;
            case IMPORT_BUS -> IMPORT_BUS;
            case EXPORT_BUS -> EXPORT_BUS;
            case INTERFACE -> INTERFACE;
            case TERMINAL -> TERMINAL;
        };

        final List<Box> transformed = new ArrayList<>(local.length);
        for (final Box box : local) {
            transformed.add(transform(box, safeFace(face)));
        }
        return transformed.toArray(new Box[0]);
    }

    public static AxisAlignedBB getUnionBox(final Kind kind, final ForgeDirection face) {
        AxisAlignedBB union = null;
        for (final Box box : getBoxes(kind, face)) {
            final AxisAlignedBB bb = box.toAABB(0, 0, 0);
            if (union == null) {
                union = bb;
            } else {
                union.setBounds(
                    Math.min(union.minX, bb.minX),
                    Math.min(union.minY, bb.minY),
                    Math.min(union.minZ, bb.minZ),
                    Math.max(union.maxX, bb.maxX),
                    Math.max(union.maxY, bb.maxY),
                    Math.max(union.maxZ, bb.maxZ));
            }
        }
        return union == null ? AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 1, 1) : union;
    }

    public static void setUnionBounds(final Block block, final Kind kind, final ForgeDirection face) {
        final AxisAlignedBB bb = getUnionBox(kind, face);
        block.setBlockBounds(
            (float) bb.minX,
            (float) bb.minY,
            (float) bb.minZ,
            (float) bb.maxX,
            (float) bb.maxY,
            (float) bb.maxZ);
    }

    private static ForgeDirection safeFace(final ForgeDirection face) {
        return face == null || face == ForgeDirection.UNKNOWN ? ForgeDirection.SOUTH : face;
    }

    private static Box transform(final Box box, final ForgeDirection face) {
        final double x1 = box.minX;
        final double y1 = box.minY;
        final double z1 = box.minZ;
        final double x2 = box.maxX;
        final double y2 = box.maxY;
        final double z2 = box.maxZ;

        return switch (face) {
            case NORTH -> new Box(x1, y1, 1.0D - z2, x2, y2, 1.0D - z1);
            case SOUTH -> new Box(x1, y1, z1, x2, y2, z2);
            case WEST -> new Box(1.0D - z2, y1, x1, 1.0D - z1, y2, x2);
            case EAST -> new Box(z1, y1, x1, z2, y2, x2);
            case DOWN -> new Box(x1, 1.0D - z2, y1, x2, 1.0D - z1, y2);
            case UP -> new Box(x1, z1, y1, x2, z2, y2);
            default -> new Box(x1, y1, z1, x2, y2, z2);
        };
    }

    private static Box[] boxes(final Box... boxes) {
        return boxes;
    }

    public static final class Box {

        public final double minX;
        public final double minY;
        public final double minZ;
        public final double maxX;
        public final double maxY;
        public final double maxZ;

        private Box(final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ) {
            this(minX / 16.0D, minY / 16.0D, minZ / 16.0D, maxX / 16.0D, maxY / 16.0D, maxZ / 16.0D);
        }

        private Box(final double minX, final double minY, final double minZ, final double maxX, final double maxY,
            final double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public AxisAlignedBB toAABB(final int x, final int y, final int z) {
            return AxisAlignedBB.getBoundingBox(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ);
        }

        public void applyTo(final Block block) {
            block.setBlockBounds((float) minX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ);
        }
    }
}
