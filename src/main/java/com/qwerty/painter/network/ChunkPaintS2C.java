package com.qwerty.painter.network;

import com.qwerty.painter.client.ClientPacketHandler;
import com.qwerty.painter.paint.FaceData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Paint data for one chunk. When {@code reset} is set the client first forgets everything it
 * had for that chunk; large chunks are split into several packets, only the first one resets.
 */
public record ChunkPaintS2C(ResourceLocation dim, int chunkX, int chunkZ, boolean reset, List<Entry> entries) {

    public record Entry(long pos, FaceData[] faces) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dim);
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeBoolean(reset);
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeLong(e.pos);
            FaceData.writeFaces(buf, e.faces);
        }
    }

    public static ChunkPaintS2C decode(FriendlyByteBuf buf) {
        ResourceLocation dim = buf.readResourceLocation();
        int cx = buf.readInt(), cz = buf.readInt();
        boolean reset = buf.readBoolean();
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(Math.min(n, 4096));
        for (int i = 0; i < n; i++) list.add(new Entry(buf.readLong(), FaceData.readFaces(buf)));
        return new ChunkPaintS2C(dim, cx, cz, reset, list);
    }

    public static void handle(ChunkPaintS2C msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.chunkPaint(msg));
    }
}
