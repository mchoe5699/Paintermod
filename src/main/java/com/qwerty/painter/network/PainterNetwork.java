package com.qwerty.painter.network;

import com.qwerty.painter.PainterMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PainterNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PainterMod.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    /** Client to server custom payloads are limited to 32767 bytes. */
    public static final int UPLOAD_CHUNK = 30000;
    public static final int DOWNLOAD_CHUNK = 500_000;

    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(UploadChunkC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UploadChunkC2S::encode).decoder(UploadChunkC2S::decode)
                .consumerMainThread(UploadChunkC2S::handle).add();
        CHANNEL.messageBuilder(RequestImageC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestImageC2S::encode).decoder(RequestImageC2S::decode)
                .consumerMainThread(RequestImageC2S::handle).add();
        CHANNEL.messageBuilder(FillRegionC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FillRegionC2S::encode).decoder(FillRegionC2S::decode)
                .consumerMainThread(FillRegionC2S::handle).add();
        CHANNEL.messageBuilder(EditFaceC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EditFaceC2S::encode).decoder(EditFaceC2S::decode)
                .consumerMainThread(EditFaceC2S::handle).add();
        CHANNEL.messageBuilder(ImageDataS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ImageDataS2C::encode).decoder(ImageDataS2C::decode)
                .consumerMainThread(ImageDataS2C::handle).add();
        CHANNEL.messageBuilder(ChunkPaintS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ChunkPaintS2C::encode).decoder(ChunkPaintS2C::decode)
                .consumerMainThread(ChunkPaintS2C::handle).add();
    }

    private PainterNetwork() {}
}
