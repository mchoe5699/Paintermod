package com.qwerty.painter.paint;

import com.qwerty.painter.PainterMod;
import com.qwerty.painter.network.PaintSync;
import com.qwerty.painter.network.UploadChunkC2S;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = PainterMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerPaintEvents {
    private ServerPaintEvents() {}

    @SubscribeEvent
    public static void onWatch(ChunkWatchEvent.Watch event) {
        PaintSync.sendTo(event.getPlayer(), event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onUnWatch(ChunkWatchEvent.UnWatch event) {
        PaintSync.sendClear(event.getPlayer(), event.getLevel(), event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BlockPos pos = event.getPos();
            if (PaintSavedData.get(level).clearBlock(pos)) {
                PaintSync.broadcast(level, List.of(new ChunkPos(pos)));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PaintSavedData data = PaintSavedData.get(level);
            Set<ChunkPos> changed = new HashSet<>();
            for (BlockPos pos : event.getAffectedBlocks()) {
                if (data.clearBlock(pos)) changed.add(new ChunkPos(pos));
            }
            if (!changed.isEmpty()) PaintSync.broadcast(level, changed);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UploadChunkC2S.forget(event.getEntity().getUUID());
    }
}
