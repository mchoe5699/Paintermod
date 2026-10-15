package com.qwerty.painter.selftest;

import com.qwerty.painter.PainterMod;
import com.qwerty.painter.item.ModItems;
import com.qwerty.painter.item.PaintCanvasItem;
import com.qwerty.painter.network.PaintSync;
import com.qwerty.painter.paint.FaceData;
import com.qwerty.painter.paint.FaceGeometry;
import com.qwerty.painter.paint.ImageStore;
import com.qwerty.painter.paint.PaintSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Automated in-game test, only active with -Dqwertys_painter.selftest=true.
 * Builds a test scene next to the joining player and paints it.
 */
@Mod.EventBusSubscriber(modid = PainterMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SelfTestServer {
    public static final int Y = 150;

    private SelfTestServer() {}

    public static boolean enabled() {
        return Boolean.getBoolean("qwertys_painter.selftest");
    }

    /** 128x64 test picture: top white stripe, yellow square top-left, green arrow on the right pointing right. */
    public static byte[] testPicture(Color left, Color right) throws Exception {
        BufferedImage img = new BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(left);
        g.fillRect(0, 0, 64, 64);
        g.setColor(right);
        g.fillRect(64, 0, 64, 64);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 128, 6);
        g.setColor(Color.YELLOW);
        g.fillRect(4, 10, 20, 20);
        g.setColor(Color.GREEN);
        g.fillPolygon(new int[]{90, 122, 90}, new int[]{18, 36, 54}, 3);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            build(player);
        } catch (Exception e) {
            PainterMod.LOGGER.error("SELFTEST server setup failed", e);
        }
    }

    private static void build(ServerPlayer player) throws Exception {
        ServerLevel level = player.serverLevel();
        // platform and empty space
        for (int x = -14; x <= 14; x++) {
            for (int z = -10; z <= 6; z++) {
                level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = Y; y <= Y + 8; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        // three walls 6 wide x 4 high at z=-6, gaps between
        for (int x : new int[]{-11, -10, -9, -8, -7, -6, -4, -3, -2, -1, 0, 1, 3, 4, 5, 6, 7, 8}) {
            for (int y = Y; y < Y + 4; y++) level.setBlockAndUpdate(new BlockPos(x, y, -6), Blocks.STONE.defaultBlockState());
        }
        // single block for per-face editing, and blocks for the brush test
        level.setBlockAndUpdate(new BlockPos(11, Y + 1, -4), Blocks.OAK_PLANKS.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(11, Y, -4), Blocks.STONE.defaultBlockState());
        for (int x = 4; x <= 5; x++) for (int y = Y; y <= Y + 1; y++) level.setBlockAndUpdate(new BlockPos(x, y, 2), Blocks.WHITE_WOOL.defaultBlockState());

        byte[] png = testPicture(new Color(200, 40, 40), new Color(40, 60, 200));
        String id = ImageStore.sha1(png);
        ImageStore.write(player.server, id, png);

        PaintSavedData data = PaintSavedData.get(level);
        Set<ChunkPos> changed = new HashSet<>();
        // wall A: stretch, no rotation
        apply(level, data, changed, new BlockPos(-11, Y, -6), new BlockPos(-6, Y + 3, -6), id,
                new FaceGeometry.Placement(6, 4, 0, 0, 0, false, false, false));
        // wall B: rotated 90 degrees clockwise, fit (rotated aspect 0.5 -> 2 x 4, centred)
        apply(level, data, changed, new BlockPos(-4, Y, -6), new BlockPos(1, Y + 3, -6), id,
                new FaceGeometry.Placement(2, 4, 2, 0, 1, false, false, true));
        // single block: different design on each face
        BlockPos c = new BlockPos(11, Y + 1, -4);
        FaceData f = FaceData.full(id, false);
        data.setFace(c, Direction.SOUTH, f);
        data.setFace(c, Direction.WEST, f.rotateCW());
        data.setFace(c, Direction.UP, f.flipH());
        data.setFace(c, Direction.EAST, f.zoom(0.5f));
        data.setFace(c, Direction.NORTH, f.rotateCCW());
        changed.add(new ChunkPos(c));
        PaintSync.broadcast(level, changed);

        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        player.setGameMode(GameType.CREATIVE);
        player.teleportTo(level, 0.5, Y, 4.5, 180f, 5f);
        // canvas for wall C in main hand (the client fills it with an uploaded picture), brush in off hand
        player.setItemInHand(InteractionHand.MAIN_HAND, PaintCanvasItem.create(new BlockPos(3, Y, -6), new BlockPos(8, Y + 3, -6),
                Direction.SOUTH, level.dimension().location().toString()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.PAINT_BRUSH.get()));
        PainterMod.LOGGER.info("SELFTEST server scene ready, picture {}", id);
    }

    private static void apply(ServerLevel level, PaintSavedData data, Set<ChunkPos> changed, BlockPos a, BlockPos b, String id,
                              FaceGeometry.Placement p) {
        Map<BlockPos, FaceData> m = FaceGeometry.fill(a, b, Direction.SOUTH, id, p, pos -> !level.getBlockState(pos).isAir());
        for (Map.Entry<BlockPos, FaceData> e : m.entrySet()) {
            data.setFace(e.getKey(), Direction.SOUTH, e.getValue());
            changed.add(new ChunkPos(e.getKey()));
        }
        PainterMod.LOGGER.info("SELFTEST filled {} faces ({} painted)", m.size(), m.values().stream().filter(v -> v != null).count());
    }
}
