package com.qwerty.painter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qwerty.painter.PainterConfig;
import com.qwerty.painter.PainterMod;
import com.qwerty.painter.item.PaintBrushItem;
import com.qwerty.painter.item.PaintCanvasItem;
import com.qwerty.painter.paint.FaceData;
import com.qwerty.painter.paint.FaceGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = PainterMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    private static final float EPS = 0.0025f;

    private record Quad(int x, int y, int z, Direction dir, FaceData data, int light) {}

    private ClientEvents() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPaintStore.clear();
        ClientImageCache.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        renderPaint(level, ps, buffers, cam);
        renderSelection(mc, ps, buffers, cam);
        buffers.endBatch();
    }

    private static void renderPaint(ClientLevel level, PoseStack ps, MultiBufferSource.BufferSource buffers, Vec3 cam) {
        Map<Long, Map<Long, FaceData[]>> dim = ClientPaintStore.forDim(level.dimension());
        if (dim == null || dim.isEmpty()) return;
        int dist = PainterConfig.get(PainterConfig.RENDER_DISTANCE, 128);
        double maxSq = (double) dist * dist;
        int camCX = (int) Math.floor(cam.x) >> 4, camCZ = (int) Math.floor(cam.z) >> 4;
        int range = (dist >> 4) + 1;

        Map<ResourceLocation, List<Quad>> byTexture = new HashMap<>();
        BlockPos.MutableBlockPos nb = new BlockPos.MutableBlockPos();
        for (Map.Entry<Long, Map<Long, FaceData[]>> ce : dim.entrySet()) {
            long ck = ce.getKey();
            if (Math.abs(ChunkPos.getX(ck) - camCX) > range || Math.abs(ChunkPos.getZ(ck) - camCZ) > range) continue;
            for (Map.Entry<Long, FaceData[]> be : ce.getValue().entrySet()) {
                long pl = be.getKey();
                int x = BlockPos.getX(pl), y = BlockPos.getY(pl), z = BlockPos.getZ(pl);
                double ddx = x + 0.5 - cam.x, ddy = y + 0.5 - cam.y, ddz = z + 0.5 - cam.z;
                if (ddx * ddx + ddy * ddy + ddz * ddz > maxSq) continue;
                BlockPos pos = BlockPos.of(pl);
                if (level.getBlockState(pos).isAir()) continue;
                FaceData[] faces = be.getValue();
                for (Direction d : Direction.values()) {
                    FaceData fd = faces[d.get3DDataValue()];
                    if (fd == null) continue;
                    // skip faces turned away from the camera
                    double toCam = (cam.x - (x + 0.5 + d.getStepX() * 0.5)) * d.getStepX()
                            + (cam.y - (y + 0.5 + d.getStepY() * 0.5)) * d.getStepY()
                            + (cam.z - (z + 0.5 + d.getStepZ() * 0.5)) * d.getStepZ();
                    if (toCam < 0) continue;
                    nb.setWithOffset(pos, d);
                    if (level.getBlockState(nb).isSolidRender(level, nb)) continue;
                    ResourceLocation tex = ClientImageCache.getOrRequest(fd.image);
                    if (tex == null) continue;
                    int light = fd.glow ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, nb);
                    byTexture.computeIfAbsent(tex, k -> new ArrayList<>()).add(new Quad(x, y, z, d, fd, light));
                }
            }
        }
        if (byTexture.isEmpty()) return;

        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (Map.Entry<ResourceLocation, List<Quad>> e : byTexture.entrySet()) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(e.getKey()));
            for (Quad q : e.getValue()) emitFace(vc, m, n, q, cam);
            buffers.endBatch(RenderType.entityCutoutNoCull(e.getKey()));
        }
    }

    private static void emitFace(VertexConsumer vc, Matrix4f m, Matrix3f n, Quad q, Vec3 cam) {
        Direction d = q.dir;
        FaceData f = q.data;
        int[] r = FaceGeometry.right(d), u = FaceGeometry.up(d);
        float nx = d.getStepX(), ny = d.getStepY(), nz = d.getStepZ();
        float cx = (float) (q.x - cam.x) + 0.5f + nx * (0.5f + EPS);
        float cy = (float) (q.y - cam.y) + 0.5f + ny * (0.5f + EPS);
        float cz = (float) (q.z - cam.z) + 0.5f + nz * (0.5f + EPS);
        // corners in emit order: bl, br, tr, tl (counter-clockwise seen from the front)
        float[][] local = {{f.fx0, f.fy1}, {f.fx1, f.fy1}, {f.fx1, f.fy0}, {f.fx0, f.fy0}};
        int[] uvIndex = {3, 2, 1, 0};
        for (int i = 0; i < 4; i++) {
            float a = local[i][0] - 0.5f, b = 0.5f - local[i][1];
            float px = cx + r[0] * a + u[0] * b;
            float py = cy + r[1] * a + u[1] * b;
            float pz = cz + r[2] * a + u[2] * b;
            int c = uvIndex[i];
            vc.vertex(m, px, py, pz)
                    .color(255, 255, 255, 255)
                    .uv(f.uv[c * 2], f.uv[c * 2 + 1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(q.light)
                    .normal(n, nx, ny, nz)
                    .endVertex();
        }
    }

    private static void renderSelection(Minecraft mc, PoseStack ps, MultiBufferSource.BufferSource buffers, Vec3 cam) {
        if (mc.player == null) return;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = mc.player.getItemInHand(hand);
            if (stack.getItem() instanceof PaintBrushItem) {
                CompoundTag tag = stack.getTag();
                if (tag == null || !tag.contains("A")) continue;
                if (!mc.level.dimension().location().toString().equals(tag.getString("Dim"))) continue;
                BlockPos a = NbtUtils.readBlockPos(tag.getCompound("A"));
                BlockPos b = tag.contains("B") ? NbtUtils.readBlockPos(tag.getCompound("B")) : a;
                drawBox(ps, buffers, cam, a, b, 1f, 0.8f, 0.2f);
                drawBox(ps, buffers, cam, a, a, 0.2f, 0.9f, 1f);
                return;
            }
            if (stack.getItem() instanceof PaintCanvasItem) {
                PaintCanvasItem.Region r = PaintCanvasItem.region(stack);
                if (r == null || !mc.level.dimension().location().toString().equals(r.dim())) continue;
                drawBox(ps, buffers, cam, r.a(), r.b(), 0.3f, 1f, 0.4f);
                return;
            }
        }
    }

    private static void drawBox(PoseStack ps, MultiBufferSource.BufferSource buffers, Vec3 cam, BlockPos a, BlockPos b, float red, float green, float blue) {
        AABB box = new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1)
                .inflate(0.004).move(-cam.x, -cam.y, -cam.z);
        LevelRenderer.renderLineBox(ps, buffers.getBuffer(RenderType.lines()), box, red, green, blue, 1f);
    }
}
