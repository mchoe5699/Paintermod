package com.qwerty.painter.network;

import com.qwerty.painter.paint.FaceData;
import com.qwerty.painter.paint.PaintSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class PaintSync {
    private static final int BATCH = 1024;

    private PaintSync() {}

    /** Builds packets for a chunk. Returns an empty list when there is nothing to send and {@code alwaysSend} is false. */
    public static List<ChunkPaintS2C> build(ServerLevel level, ChunkPos cp, boolean alwaysSend) {
        List<ChunkPaintS2C> out = new ArrayList<>();
        PaintSavedData data = PaintSavedData.get(level);
        Map<Long, FaceData[]> m = data.chunk(cp);
        if (m != null && level.hasChunk(cp.x, cp.z)) {
            // drop paint on blocks that were removed some other way (commands, pistons, ...)
            List<Long> gone = new ArrayList<>();
            for (Long p : m.keySet()) if (level.getBlockState(BlockPos.of(p)).isAir()) gone.add(p);
            for (Long p : gone) data.clearBlock(BlockPos.of(p));
            m = data.chunk(cp);
        }
        if (m == null || m.isEmpty()) {
            if (alwaysSend) out.add(new ChunkPaintS2C(level.dimension().location(), cp.x, cp.z, true, List.of()));
            return out;
        }
        List<ChunkPaintS2C.Entry> batch = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<Long, FaceData[]> e : m.entrySet()) {
            batch.add(new ChunkPaintS2C.Entry(e.getKey(), e.getValue().clone()));
            if (batch.size() >= BATCH) {
                out.add(new ChunkPaintS2C(level.dimension().location(), cp.x, cp.z, first, batch));
                batch = new ArrayList<>();
                first = false;
            }
        }
        if (!batch.isEmpty() || first) out.add(new ChunkPaintS2C(level.dimension().location(), cp.x, cp.z, first, batch));
        return out;
    }

    public static void sendTo(ServerPlayer player, ServerLevel level, ChunkPos cp) {
        for (ChunkPaintS2C pkt : build(level, cp, false)) {
            PainterNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
        }
    }

    public static void sendClear(ServerPlayer player, ServerLevel level, ChunkPos cp) {
        if (PaintSavedData.get(level).chunk(cp) == null) return;
        PainterNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ChunkPaintS2C(level.dimension().location(), cp.x, cp.z, true, List.of()));
    }

    /** Re-sends the given chunks to every player who can see them. */
    public static void broadcast(ServerLevel level, Collection<ChunkPos> chunks) {
        for (ChunkPos cp : chunks) {
            if (!level.hasChunk(cp.x, cp.z)) continue;
            LevelChunk chunk = level.getChunk(cp.x, cp.z);
            for (ChunkPaintS2C pkt : build(level, cp, true)) {
                PainterNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), pkt);
            }
        }
    }
}
