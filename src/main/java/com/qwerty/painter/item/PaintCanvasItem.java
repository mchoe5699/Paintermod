package com.qwerty.painter.item;

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

/** Holds a selected region. Right-click opens the picture fill screen. */
public class PaintCanvasItem extends Item {
    public record Region(BlockPos a, BlockPos b, Direction face, String dim) {}

    public PaintCanvasItem(Properties props) {
        super(props);
    }

    public static ItemStack create(BlockPos a, BlockPos b, Direction face, String dim) {
        ItemStack stack = new ItemStack(ModItems.PAINT_CANVAS.get());
        setRegion(stack, a, b, face, dim);
        return stack;
    }

    @Nullable
    public static Region region(ItemStack stack) {
        if (!(stack.getItem() instanceof PaintCanvasItem)) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("A") || !tag.contains("B")) return null;
        int f = tag.getInt("Face");
        if (f < 0 || f > 5) return null;
        return new Region(NbtUtils.readBlockPos(tag.getCompound("A")), NbtUtils.readBlockPos(tag.getCompound("B")),
                Direction.from3DDataValue(f), tag.getString("Dim"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            // sneak + right-click air: forget the region / selection, canvas becomes empty again
            if (!level.isClientSide && (region(stack) != null || RegionSelection.active(stack))) {
                clearRegion(stack);
                player.displayClientMessage(Component.translatable("qwertys_painter.msg.canvas_reset"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (region(stack) == null) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("qwertys_painter.msg.canvas_empty"), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openFill(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = ctx.getItemInHand();
        // a canvas that already has a region opens the fill screen wherever you click
        if (region(stack) != null || player.isShiftKeyDown()) {
            return use(ctx.getLevel(), player, ctx.getHand()).getResult();
        }
        // an empty canvas picks its own region: first point, last point, first point again
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        RegionSelection.Result r = RegionSelection.click(player, level, ctx.getClickedPos(), ctx.getClickedFace(), stack);
        if (r != null) {
            setRegion(stack, r.a(), r.b(), r.face(), r.dim());
            int[] wh = FaceGeometry.regionSize(r.a(), r.b(), r.face());
            player.displayClientMessage(Component.translatable("qwertys_painter.msg.canvas_ready", wh[0], wh[1],
                    PaintBrushItem.dirName(r.face())), true);
        }
        return InteractionResult.SUCCESS;
    }

    public static void setRegion(ItemStack stack, BlockPos a, BlockPos b, Direction face, String dim) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.put("A", NbtUtils.writeBlockPos(a));
        tag.put("B", NbtUtils.writeBlockPos(b));
        tag.putInt("Face", face.get3DDataValue());
        tag.putString("Dim", dim);
    }

    public static void clearRegion(ItemStack stack) {
        RegionSelection.clear(stack);
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove("A");
        tag.remove("B");
        tag.remove("Face");
        tag.remove("Dim");
        if (tag.isEmpty()) stack.setTag(null);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return RegionSelection.active(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        Region r = region(stack);
        if (r == null) return super.getName(stack);
        int[] wh = FaceGeometry.regionSize(r.a(), r.b(), r.face());
        return Component.translatable("item.qwertys_painter.paint_canvas.sized", wh[0], wh[1]);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Region r = region(stack);
        if (r == null) {
            tooltip.add(Component.translatable("qwertys_painter.tooltip.canvas.empty").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("qwertys_painter.tooltip.canvas.empty2").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("qwertys_painter.tooltip.canvas.from", r.a().getX(), r.a().getY(), r.a().getZ()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("qwertys_painter.tooltip.canvas.to", r.b().getX(), r.b().getY(), r.b().getZ()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("qwertys_painter.tooltip.canvas.face", PaintBrushItem.dirName(r.face())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("qwertys_painter.tooltip.canvas.use").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("qwertys_painter.tooltip.canvas.reset").withStyle(ChatFormatting.GRAY));
    }
}
