package com.qwerty.painter.client;

import com.qwerty.painter.network.ChunkPaintS2C;
import com.qwerty.painter.paint.FaceData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Client copy of painted faces: dimension -> chunk -> block -> 6 faces. */
public final class ClientPaintStore {
    private static final Map<ResourceKey<Level>, Map<Long, Map<Long, FaceData[]>>> DATA = new HashMap<>();

    private ClientPaintStore() {}

    public static void apply(ChunkPaintS2C msg) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, msg.dim());
        Map<Long, Map<Long, FaceData[]>> dim = DATA.computeIfAbsent(key, k -> new HashMap<>());
        long ck = ChunkPos.asLong(msg.chunkX(), msg.chunkZ());
        if (msg.reset()) dim.remove(ck);
        if (msg.entries().isEmpty()) return;
        Map<Long, FaceData[]> chunk = dim.computeIfAbsent(ck, k -> new HashMap<>());
        for (ChunkPaintS2C.Entry e : msg.entries()) chunk.put(e.pos(), e.faces());
    }

    @Nullable
    public static Map<Long, Map<Long, FaceData[]>> forDim(ResourceKey<Level> dim) {
        return DATA.get(dim);
    }

    @Nullable
    public static FaceData[] get(ResourceKey<Level> dim, BlockPos pos) {
        Map<Long, Map<Long, FaceData[]>> d = DATA.get(dim);
        if (d == null) return null;
        Map<Long, FaceData[]> c = d.get(new ChunkPos(pos).toLong());
        return c == null ? null : c.get(pos.asLong());
    }

    /** Shows an edit right away; the server answer replaces it moments later. */
    public static void setLocal(ResourceKey<Level> dim, BlockPos pos, Direction dir, @Nullable FaceData data) {
        Map<Long, FaceData[]> c = DATA.computeIfAbsent(dim, k -> new HashMap<>())
                .computeIfAbsent(new ChunkPos(pos).toLong(), k -> new HashMap<>());
        FaceData[] faces = c.computeIfAbsent(pos.asLong(), k -> new FaceData[6]);
        faces[dir.get3DDataValue()] = data;
    }

    public static void clear() {
        DATA.clear();
    }
}
