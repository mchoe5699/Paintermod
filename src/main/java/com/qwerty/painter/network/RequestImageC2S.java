package com.qwerty.painter.network;

import com.qwerty.painter.PainterMod;
import com.qwerty.painter.paint.ImageStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.function.Supplier;

/** Client asks for a picture it has not got yet. */
public record RequestImageC2S(String id) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id, 64);
    }

    public static RequestImageC2S decode(FriendlyByteBuf buf) {
        return new RequestImageC2S(buf.readUtf(64));
    }

    public static void handle(RequestImageC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !ImageStore.exists(player.server, msg.id)) return;
        try {
            byte[] data = ImageStore.read(player.server, msg.id);
            for (int off = 0; off < data.length; off += PainterNetwork.DOWNLOAD_CHUNK) {
                int end = Math.min(data.length, off + PainterNetwork.DOWNLOAD_CHUNK);
                PainterNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new ImageDataS2C(msg.id, data.length, off, Arrays.copyOfRange(data, off, end)));
            }
        } catch (Exception e) {
            PainterMod.LOGGER.warn("Could not send picture {}", msg.id, e);
        }
    }
}
