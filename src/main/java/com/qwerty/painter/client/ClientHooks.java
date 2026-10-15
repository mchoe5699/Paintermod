package com.qwerty.painter.client;

import com.qwerty.painter.client.screen.EditScreen;
import com.qwerty.painter.client.screen.FillScreen;
import com.qwerty.painter.item.PaintCanvasItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;

public final class ClientHooks {
    private ClientHooks() {}

    public static void openEditor(BlockPos pos, Direction face) {
        Minecraft.getInstance().setScreen(new EditScreen(pos, face));
    }

    public static void openFill(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PaintCanvasItem.Region region = PaintCanvasItem.region(mc.player.getItemInHand(hand));
        if (region != null) mc.setScreen(new FillScreen(hand, region));
    }
}
