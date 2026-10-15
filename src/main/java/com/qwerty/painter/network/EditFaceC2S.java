package com.qwerty.painter.network;

import com.qwerty.painter.paint.FaceData;
import com.qwerty.painter.paint.ImageStore;
import com.qwerty.painter.paint.PaintSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/** Set or remove the picture on a single face of a single block. */
public record EditFaceC2S(BlockPos pos, int face, @Nullable FaceData data) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeByte(face);
        buf.writeBoolean(data != null);
        if (data != null) data.write(buf);
    }

    public static EditFaceC2S decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int face = buf.readByte();
        FaceData d = buf.readBoolean() ? FaceData.read(buf) : null;
        return new EditFaceC2S(pos, face, d);
    }

    public static void handle(EditFaceC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || msg.face < 0 || msg.face > 5) return;
        ServerLevel level = player.serverLevel();
        BlockPos pos = msg.pos;
        if (!level.isLoaded(pos) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0 * 64.0) return;
        if (!level.mayInteract(player, pos)) return;
        if (msg.data != null) {
            if (!msg.data.isValid() || level.getBlockState(pos).isAir()) return;
            if (!ImageStore.exists(player.server, msg.data.image)) {
                player.displayClientMessage(Component.translatable("qwertys_painter.msg.image_missing"), true);
                return;
            }
        }
        PaintSavedData data = PaintSavedData.get(level);
        data.setFace(pos, Direction.from3DDataValue(msg.face), msg.data);
        // always resend so the client view matches the server even if nothing changed
        PaintSync.broadcast(level, List.of(new ChunkPos(pos)));
    }
}
