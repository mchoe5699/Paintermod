package com.qwerty.painter.item;

import com.qwerty.painter.client.ClientHooks;
import com.qwerty.painter.paint.FaceGeometry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
        if (player == null) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openEditor(pos, face));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        RegionSelection.Result r = RegionSelection.click(player, level, pos, face, ctx.getItemInHand());
        if (r != null) {
            ItemStack canvas = PaintCanvasItem.create(r.a(), r.b(), r.face(), r.dim());
            if (!player.getInventory().add(canvas)) player.drop(canvas, false);
            int[] wh = FaceGeometry.regionSize(r.a(), r.b(), r.face());
            player.displayClientMessage(Component.translatable("qwertys_painter.msg.canvas_created", wh[0], wh[1], dirName(r.face())), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && RegionSelection.active(stack)) {
            if (!level.isClientSide) {
                RegionSelection.clear(stack);
                player.displayClientMessage(Component.translatable("qwertys_painter.msg.selection_reset"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    public static Component dirName(Direction d) {
        return Component.translatable("qwertys_painter.dir." + d.getName());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return RegionSelection.active(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("qwertys_painter.tooltip.brush.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("qwertys_painter.tooltip.brush.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("qwertys_painter.tooltip.brush.3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("qwertys_painter.tooltip.brush.4").withStyle(ChatFormatting.GRAY));
    }
}
