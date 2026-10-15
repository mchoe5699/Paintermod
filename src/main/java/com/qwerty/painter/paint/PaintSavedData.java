package com.qwerty.painter.paint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** All painted faces of one dimension, grouped by chunk. */
public class PaintSavedData extends SavedData {
    private static final String NAME = "qwertys_painter_paint";

    private final Map<Long, Map<Long, FaceData[]>> chunks = new HashMap<>();

    public static PaintSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PaintSavedData::load, PaintSavedData::new, NAME);
    }

    @Nullable
    public Map<Long, FaceData[]> chunk(ChunkPos cp) {
        return chunks.get(cp.toLong());
    }

    @Nullable
    public FaceData[] get(BlockPos pos) {
        Map<Long, FaceData[]> m = chunks.get(new ChunkPos(pos).toLong());
        return m == null ? null : m.get(pos.asLong());
    }

    public boolean setFace(BlockPos pos, Direction dir, @Nullable FaceData data) {
        long ck = new ChunkPos(pos).toLong();
        long pk = pos.asLong();
        Map<Long, FaceData[]> m = chunks.get(ck);
        FaceData[] faces = m == null ? null : m.get(pk);
        int i = dir.get3DDataValue();
        if (data == null) {
            if (faces == null || faces[i] == null) return false;
            faces[i] = null;
            boolean empty = true;
            for (FaceData f : faces) if (f != null) { empty = false; break; }
            if (empty) {
                m.remove(pk);
                if (m.isEmpty()) chunks.remove(ck);
            }
        } else {
            if (m == null) {
                m = new HashMap<>();
                chunks.put(ck, m);
            }
            if (faces == null) {
                faces = new FaceData[6];
                m.put(pk, faces);
            }
            faces[i] = data;
        }
        setDirty();
        return true;
    }

    public boolean clearBlock(BlockPos pos) {
        long ck = new ChunkPos(pos).toLong();
        Map<Long, FaceData[]> m = chunks.get(ck);
        if (m == null || m.remove(pos.asLong()) == null) return false;
        if (m.isEmpty()) chunks.remove(ck);
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag blocks = new ListTag();
        for (Map<Long, FaceData[]> m : chunks.values()) {
            for (Map.Entry<Long, FaceData[]> e : m.entrySet()) {
                CompoundTag b = new CompoundTag();
                b.putLong("p", e.getKey());
                ListTag faces = new ListTag();
                FaceData[] arr = e.getValue();
                for (int i = 0; i < 6; i++) {
                    if (arr[i] == null) continue;
                    CompoundTag f = arr[i].save();
                    f.putByte("d", (byte) i);
                    faces.add(f);
                }
                b.put("f", faces);
                blocks.add(b);
            }
        }
        tag.put("blocks", blocks);
        return tag;
    }

    public static PaintSavedData load(CompoundTag tag) {
        PaintSavedData data = new PaintSavedData();
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            BlockPos pos = BlockPos.of(b.getLong("p"));
            ListTag faces = b.getList("f", Tag.TAG_COMPOUND);
            for (int j = 0; j < faces.size(); j++) {
                CompoundTag f = faces.getCompound(j);
                int d = f.getByte("d");
                if (d < 0 || d > 5) continue;
                FaceData fd = FaceData.load(f);
                if (fd.isValid()) data.setFace(pos, Direction.from3DDataValue(d), fd);
            }
        }
        return data;
    }
}
