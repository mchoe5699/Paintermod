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
        CompoundTag tag = stack.getOrCreateTag();
        tag.put("A", NbtUtils.writeBlockPos(a));
        tag.put("B", NbtUtils.writeBlockPos(b));
        tag.putInt("Face", face.get3DDataValue());
        tag.putString("Dim", dim);
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
        if (region(stack) == null) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openFill(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() == null) return InteractionResult.PASS;
        return use(ctx.getLevel(), ctx.getPlayer(), ctx.getHand()).getResult();
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
            return;
        }
        tooltip.add(Component.translatable("qwertys_painter.tooltip.canvas.from", r.a().getX(), r.a().getY(), r.a().getZ()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("qwertys_painter.tooltip.canvas.to", r.b().getX(), r.b().getY(), r.b().getZ()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("qwertys_painter.tooltip.canvas.face", PaintBrushItem.dirName(r.face())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("qwertys_painter.tooltip.canvas.use").withStyle(ChatFormatting.YELLOW));
    }
}
