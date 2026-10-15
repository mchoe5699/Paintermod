package com.qwerty.painter.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashMap;
import java.util.Map;

/**
 * For each block face: which world axis is "right" and which is "up" for a viewer looking
 * straight at that face from outside. right x up = face normal.
 */
public final class FaceGeometry {
    private FaceGeometry() {}

    public static int[] right(Direction d) {
        return switch (d) {
            case NORTH -> new int[]{-1, 0, 0};
            case SOUTH -> new int[]{1, 0, 0};
            case EAST -> new int[]{0, 0, -1};
            case WEST -> new int[]{0, 0, 1};
            case UP, DOWN -> new int[]{1, 0, 0};
        };
    }

    public static int[] up(Direction d) {
        return switch (d) {
            case UP -> new int[]{0, 0, -1};
            case DOWN -> new int[]{0, 0, 1};
            default -> new int[]{0, 1, 0};
        };
    }

    public static int dot(int[] v, BlockPos p) {
        return v[0] * p.getX() + v[1] * p.getY() + v[2] * p.getZ();
    }

    /** {width, height} in blocks of region a..b as seen on the given face. */
    public static int[] regionSize(BlockPos a, BlockPos b, Direction face) {
        int[] r = right(face), u = up(face);
        return new int[]{Math.abs(dot(r, a) - dot(r, b)) + 1, Math.abs(dot(u, a) - dot(u, b)) + 1};
    }

    public static long volume(BlockPos a, BlockPos b) {
        return (long) (Math.abs(a.getX() - b.getX()) + 1) * (Math.abs(a.getY() - b.getY()) + 1) * (Math.abs(a.getZ() - b.getZ()) + 1);
    }

    /** Placement of the picture inside the region, in blocks, measured from the region's top-left. */
    public record Placement(float width, float height, float offX, float offY, int rotation, boolean flipH, boolean flipV, boolean glow) {
        public boolean isValid() {
            return Float.isFinite(width) && Float.isFinite(height) && Float.isFinite(offX) && Float.isFinite(offY)
                    && width > 0.001f && height > 0.001f && width <= 4096 && height <= 4096
                    && Math.abs(offX) <= 4096 && Math.abs(offY) <= 4096;
        }
    }

    /** Display coordinates (0..1 over the placed picture) to picture texture coordinates. */
    public static float[] displayToImage(float s, float t, int rotation, boolean flipH, boolean flipV) {
        if (flipH) s = 1 - s;
        if (flipV) t = 1 - t;
        return switch (rotation & 3) {
            case 0 -> new float[]{s, t};
            case 1 -> new float[]{t, 1 - s};
            case 2 -> new float[]{1 - s, 1 - t};
            default -> new float[]{1 - t, s};
        };
    }

    /**
     * Works out which part of the picture goes on the given face of every block in a..b.
     * Blocks outside the placed picture map to null (= remove paint from that face).
     * Positions where {@code isSolid} is false are skipped.
     */
    public static Map<BlockPos, FaceData> fill(BlockPos a, BlockPos b, Direction face, String image, Placement p,
                                               java.util.function.Predicate<BlockPos> isSolid) {
        Map<BlockPos, FaceData> out = new HashMap<>();
        int[] r = right(face), u = up(face);
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        int aMin = Math.min(dot(r, min), dot(r, max));
        int bMax = Math.max(dot(u, min), dot(u, max));

        for (BlockPos m : BlockPos.betweenClosed(min, max)) {
            BlockPos pos = m.immutable();
            if (!isSolid.test(pos)) continue;
            if (image == null) {
                out.put(pos, null);
                continue;
            }
            int gx = dot(r, pos) - aMin;
            int gy = bMax - dot(u, pos);
            float ix0 = Math.max(gx, p.offX()), ix1 = Math.min(gx + 1, p.offX() + p.width());
            float iy0 = Math.max(gy, p.offY()), iy1 = Math.min(gy + 1, p.offY() + p.height());
            if (ix1 - ix0 < 1e-4f || iy1 - iy0 < 1e-4f) {
                out.put(pos, null);
                continue;
            }
            float[] uv = new float[8];
            float[][] corners = {{ix0, iy0}, {ix1, iy0}, {ix1, iy1}, {ix0, iy1}};
            for (int i = 0; i < 4; i++) {
                float s = (corners[i][0] - p.offX()) / p.width();
                float t = (corners[i][1] - p.offY()) / p.height();
                float[] img = displayToImage(s, t, p.rotation(), p.flipH(), p.flipV());
                uv[i * 2] = img[0];
                uv[i * 2 + 1] = img[1];
            }
            out.put(pos, new FaceData(image, ix0 - gx, iy0 - gy, ix1 - gx, iy1 - gy, uv, p.glow()));
        }
        return out;
    }
}
