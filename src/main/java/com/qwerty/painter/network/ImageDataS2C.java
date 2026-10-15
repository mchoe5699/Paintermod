package com.qwerty.painter.network;

import com.qwerty.painter.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ImageDataS2C(String id, int total, int offset, byte[] data) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id, 64);
        buf.writeVarInt(total);
        buf.writeVarInt(offset);
        buf.writeByteArray(data);
    }

    public static ImageDataS2C decode(FriendlyByteBuf buf) {
        return new ImageDataS2C(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(PainterNetwork.DOWNLOAD_CHUNK + 16));
    }

    public static void handle(ImageDataS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.imageData(msg));
    }
}
