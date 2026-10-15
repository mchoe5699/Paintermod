package com.qwerty.painter.network;

import com.qwerty.painter.PainterConfig;
import com.qwerty.painter.item.PaintCanvasItem;
import com.qwerty.painter.paint.FaceData;
import com.qwerty.painter.paint.FaceGeometry;
import com.qwerty.painter.paint.ImageStore;
import com.qwerty.painter.paint.PaintSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Fill (or clear) the region stored in the canvas the player is holding. */
public record FillRegionC2S(InteractionHand hand, int face, boolean clear, String image, FaceGeometry.Placement placement) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(hand);
        buf.writeByte(face);
        buf.writeBoolean(clear);
        buf.writeUtf(image == null ? "" : image, 64);
        buf.writeFloat(placement.width());
        buf.writeFloat(placement.height());
        buf.writeFloat(placement.offX());
        buf.writeFloat(placement.offY());
        buf.writeByte(placement.rotation());
        buf.writeBoolean(placement.flipH());
        buf.writeBoolean(placement.flipV());
        buf.writeBoolean(placement.glow());
    }

    public static FillRegionC2S decode(FriendlyByteBuf buf) {
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        int face = buf.readByte();
        boolean clear = buf.readBoolean();
        String img = buf.readUtf(64);
        FaceGeometry.Placement p = new FaceGeometry.Placement(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readByte() & 3, buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        return new FillRegionC2S(hand, face, clear, img, p);
    }

    public static void handle(FillRegionC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || msg.face < 0 || msg.face > 5) return;
        ItemStack stack = player.getItemInHand(msg.hand);
        PaintCanvasItem.Region region = PaintCanvasItem.region(stack);
        if (region == null) return;
        ServerLevel level = player.serverLevel();
        if (!level.dimension().location().toString().equals(region.dim())) {
            player.displayClientMessage(Component.translatable("qwertys_painter.msg.wrong_dimension"), true);
            return;
        }
        BlockPos a = region.a(), b = region.b();
        Direction face = Direction.from3DDataValue(msg.face);

        if (!checkRegion(player, a, b)) return;
        double cx = (a.getX() + b.getX()) / 2.0 + 0.5, cy = (a.getY() + b.getY()) / 2.0 + 0.5, cz = (a.getZ() + b.getZ()) / 2.0 + 0.5;
        if (player.distanceToSqr(cx, cy, cz) > 256.0 * 256.0) {
            player.displayClientMessage(Component.translatable("qwertys_painter.msg.too_far"), true);
            return;
        }
        if (!level.mayInteract(player, a) || !level.mayInteract(player, b)) return;

        String image = null;
        if (!msg.clear) {
            if (!msg.placement.isValid()) return;
            if (!ImageStore.exists(player.server, msg.image)) {
                player.displayClientMessage(Component.translatable("qwertys_painter.msg.image_missing"), true);
                return;
            }
            image = msg.image;
        }

        Map<BlockPos, FaceData> result = FaceGeometry.fill(a, b, face, image, msg.placement,
                pos -> level.isLoaded(pos) && !level.getBlockState(pos).isAir());
        PaintSavedData data = PaintSavedData.get(level);
        Set<ChunkPos> changed = new HashSet<>();
        int painted = 0;
        for (Map.Entry<BlockPos, FaceData> e : result.entrySet()) {
            if (data.setFace(e.getKey(), face, e.getValue())) changed.add(new ChunkPos(e.getKey()));
            if (e.getValue() != null) painted++;
        }
        PaintSync.broadcast(level, changed);
        player.displayClientMessage(msg.clear
                ? Component.translatable("qwertys_painter.msg.cleared")
                : Component.translatable("qwertys_painter.msg.filled", painted), true);
    }

    public static boolean checkRegion(ServerPlayer player, BlockPos a, BlockPos b) {
        int side = PainterConfig.get(PainterConfig.MAX_REGION_SIDE, 128);
        int blocks = PainterConfig.get(PainterConfig.MAX_REGION_BLOCKS, 16384);
        int dx = Math.abs(a.getX() - b.getX()) + 1, dy = Math.abs(a.getY() - b.getY()) + 1, dz = Math.abs(a.getZ() - b.getZ()) + 1;
        if (Math.max(dx, Math.max(dy, dz)) > side || FaceGeometry.volume(a, b) > blocks) {
            player.displayClientMessage(Component.translatable("qwertys_painter.msg.region_too_big", side, blocks), true);
            return false;
        }
        return true;
    }
}
