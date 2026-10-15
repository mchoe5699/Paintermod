package com.qwerty.painter.item;

import com.qwerty.painter.PainterConfig;
import com.qwerty.painter.client.ClientHooks;
import com.qwerty.painter.paint.FaceGeometry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Paint brush.
 * <ul>
 *   <li>Right-click a block: first point, then last point.</li>
 *   <li>Right-click the first point again: makes a canvas item for that region.</li>
 *   <li>Sneak + right-click a block: edit that block's six faces one by one.</li>
 *   <li>Sneak + right-click air: cancel the selection.</li>
 * </ul>
 */
public class PaintBrushItem extends Item {
    public PaintBrushItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        ItemStack stack = ctx.getItemInHand();
        if (player == null) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openEditor(pos, face));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        CompoundTag tag = stack.getOrCreateTag();
        String dim = level.dimension().location().toString();
        if (tag.contains("A") && !dim.equals(tag.getString("Dim"))) clearSelection(stack);

        if (!tag.contains("A")) {
            tag.put("A", NbtUtils.writeBlockPos(pos));
            tag.putInt("Face", face.get3DDataValue());
            tag.putString("Dim", dim);
            player.displayClientMessage(Component.translatable("qwertys_painter.msg.first_point",
                    pos.getX(), pos.getY(), pos.getZ(), dirName(face)), true);
            return InteractionResult.SUCCESS;
        }

        BlockPos a = NbtUtils.readBlockPos(tag.getCompound("A"));
        Direction f = Direction.from3DDataValue(tag.getInt("Face"));
        if (pos.equals(a)) {
            BlockPos b = tag.contains("B") ? NbtUtils.readBlockPos(tag.getCompound("B")) : a;
            int side = PainterConfig.get(PainterConfig.MAX_REGION_SIDE, 128);
            int blocks = PainterConfig.get(PainterConfig.MAX_REGION_BLOCKS, 16384);
            int dx = Math.abs(a.getX() - b.getX()) + 1, dy = Math.abs(a.getY() - b.getY()) + 1, dz = Math.abs(a.getZ() - b.getZ()) + 1;
            if (Math.max(dx, Math.max(dy, dz)) > side || FaceGeometry.volume(a, b) > blocks) {
                player.displayClientMessage(Component.translatable("qwertys_painter.msg.region_too_big", side, blocks), true);
                return InteractionResult.SUCCESS;
            }
            ItemStack canvas = PaintCanvasItem.create(a, b, f, dim);
            if (!player.getInventory().add(canvas)) player.drop(canvas, false);
            clearSelection(stack);
            int[] wh = FaceGeometry.regionSize(a, b, f);
            player.displayClientMessage(Component.translatable("qwertys_painter.msg.canvas_created", wh[0], wh[1], dirName(f)), true);
        } else {
            tag.put("B", NbtUtils.writeBlockPos(pos));
            int[] wh = FaceGeometry.regionSize(a, pos, f);
            player.displayClientMessage(Component.translatable("qwertys_painter.msg.last_point",
                    pos.getX(), pos.getY(), pos.getZ(), wh[0], wh[1]), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.hasTag() && stack.getTag().contains("A")) {
            if (!level.isClientSide) {
                clearSelection(stack);
                player.displayClientMessage(Component.translatable("qwertys_painter.msg.selection_reset"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    public static void clearSelection(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove("A");
        tag.remove("B");
        tag.remove("Face");
        tag.remove("Dim");
        if (tag.isEmpty()) stack.setTag(null);
    }

    public static Component dirName(Direction d) {
        return Component.translatable("qwertys_painter.dir." + d.getName());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("A");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("qwertys_painter.tooltip.brush.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("qwertys_painter.tooltip.brush.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("qwertys_painter.tooltip.brush.3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("qwertys_painter.tooltip.brush.4").withStyle(ChatFormatting.GRAY));
    }
}
