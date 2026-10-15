package com.qwerty.painter.client;

import com.qwerty.painter.network.ChunkPaintS2C;
import com.qwerty.painter.network.ImageDataS2C;

public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void chunkPaint(ChunkPaintS2C msg) {
        ClientPaintStore.apply(msg);
    }

    public static void imageData(ImageDataS2C msg) {
        ClientImageCache.onData(msg);
    }
}
