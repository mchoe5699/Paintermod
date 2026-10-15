package com.qwerty.painter.item;

import com.qwerty.painter.PainterConfig;
import com.qwerty.painter.paint.FaceGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Region picking shared by the brush and an empty canvas:
 * click first point, click last point, click the first point again to finish.
 */
public final class RegionSelection {
    private static final String A = "SelA", B = "SelB", FACE = "SelFace", DIM = "SelDim";

    public record Result(BlockPos a, BlockPos b, Direction face, String dim) {}

    private RegionSelection() {}

    public static boolean active(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(A);
    }

    @Nullable
    public static BlockPos first(ItemStack stack) {
        return active(stack) ? NbtUtils.readBlockPos(stack.getTag().getCompound(A)) : null;
    }

    @Nullable
    public static BlockPos last(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.contains(B) ? NbtUtils.readBlockPos(t.getCompound(B)) : first(stack);
    }

    public static String dim(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getString(DIM) : "";
    }

    public static void clear(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(A);
        tag.remove(B);
        tag.remove(FACE);
        tag.remove(DIM);
        if (tag.isEmpty()) stack.setTag(null);
    }

    /** Server side. Returns the finished region when the first point is clicked again, otherwise null. */
    @Nullable
    public static Result click(Player player, Level level, BlockPos pos, Direction face, ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        String dim = level.dimension().location().toString();
        if (tag.contains(A) && !dim.equals(tag.getString(DIM))) clear(stack);
        tag = stack.getOrCreateTag();

        if (!tag.contains(A)) {
            tag.put(A, NbtUtils.writeBlockPos(pos));
            tag.putInt(FACE, face.get3DDataValue());
            tag.putString(DIM, dim);
            player.displayClientMessage(Component.translatable("qwertys_painter.msg.first_point",
                    pos.getX(), pos.getY(), pos.getZ(), PaintBrushItem.dirName(face)), true);
            return null;
        }

        BlockPos a = NbtUtils.readBlockPos(tag.getCompound(A));
        Direction f = Direction.from3DDataValue(tag.getInt(FACE));
        if (!pos.equals(a)) {
            tag.put(B, NbtUtils.writeBlockPos(pos));
            int[] wh = FaceGeometry.regionSize(a, pos, f);
            player.displayClientMessage(Component.translatable("qwertys_painter.msg.last_point",
                    pos.getX(), pos.getY(), pos.getZ(), wh[0], wh[1]), true);
            return null;
        }

        BlockPos b = tag.contains(B) ? NbtUtils.readBlockPos(tag.getCompound(B)) : a;
        int side = PainterConfig.get(PainterConfig.MAX_REGION_SIDE, 128);
        int blocks = PainterConfig.get(PainterConfig.MAX_REGION_BLOCKS, 16384);
        int dx = Math.abs(a.getX() - b.getX()) + 1, dy = Math.abs(a.getY() - b.getY()) + 1, dz = Math.abs(a.getZ() - b.getZ()) + 1;
        if (Math.max(dx, Math.max(dy, dz)) > side || FaceGeometry.volume(a, b) > blocks) {
            player.displayClientMessage(Component.translatable("qwertys_painter.msg.region_too_big", side, blocks), true);
            return null;
        }
        clear(stack);
        return new Result(a, b, f, dim);
    }
}
