package com.qwerty.painter.network;

import com.qwerty.painter.PainterConfig;
import com.qwerty.painter.PainterMod;
import com.qwerty.painter.paint.ImageStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** One piece of a picture the player is uploading. Pieces arrive in order. */
public record UploadChunkC2S(String id, int total, int offset, byte[] data) {

    private static final class Pending {
        final String id;
        final byte[] buf;
        int received;

        Pending(String id, int total) {
            this.id = id;
            this.buf = new byte[total];
        }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    public static void forget(UUID player) {
        PENDING.remove(player);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id, 64);
        buf.writeVarInt(total);
        buf.writeVarInt(offset);
        buf.writeByteArray(data);
    }

    public static UploadChunkC2S decode(FriendlyByteBuf buf) {
        return new UploadChunkC2S(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(PainterNetwork.UPLOAD_CHUNK + 16));
    }

    public static void handle(UploadChunkC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !ImageStore.validId(msg.id)) return;
        int max = PainterConfig.get(PainterConfig.MAX_IMAGE_KB, 8192) * 1024;
        UUID uuid = player.getUUID();

        if (msg.offset == 0) {
            if (msg.total <= 0 || msg.total > max) {
                PENDING.remove(uuid);
                player.displayClientMessage(Component.translatable("qwertys_painter.msg.image_too_big", max / 1024), false);
                return;
            }
            PENDING.put(uuid, new Pending(msg.id, msg.total));
        }
        Pending p = PENDING.get(uuid);
        if (p == null || !p.id.equals(msg.id) || p.buf.length != msg.total || msg.offset != p.received
                || msg.offset + msg.data.length > p.buf.length) {
            return;
        }
        System.arraycopy(msg.data, 0, p.buf, msg.offset, msg.data.length);
        p.received += msg.data.length;
        if (p.received < p.buf.length) return;

        PENDING.remove(uuid);
        if (!ImageStore.sha1(p.buf).equals(p.id) || !ImageStore.isAcceptablePng(p.buf)) {
            player.displayClientMessage(Component.translatable("qwertys_painter.msg.image_invalid"), false);
            return;
        }
        try {
            ImageStore.write(player.server, p.id, p.buf);
        } catch (Exception e) {
            PainterMod.LOGGER.error("Could not save picture {}", p.id, e);
            player.displayClientMessage(Component.translatable("qwertys_painter.msg.image_invalid"), false);
        }
    }
}
