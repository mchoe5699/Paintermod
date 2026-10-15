package com.qwerty.painter.selftest;

import com.qwerty.painter.PainterMod;
import com.qwerty.painter.client.ClientEvents;
import com.qwerty.painter.client.ClientHooks;
import com.qwerty.painter.client.ClientImageCache;
import com.qwerty.painter.client.ClientPaintStore;
import com.qwerty.painter.client.ImagePicker;
import com.qwerty.painter.client.screen.EditScreen;
import com.qwerty.painter.client.screen.FillScreen;
import com.qwerty.painter.item.PaintCanvasItem;
import com.qwerty.painter.network.EditFaceC2S;
import com.qwerty.painter.network.FillRegionC2S;
import com.qwerty.painter.network.PainterNetwork;
import com.qwerty.painter.paint.FaceData;
import com.qwerty.painter.paint.FaceGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.qwerty.painter.item.ModItems;
import com.qwerty.painter.item.ModTabs;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

import java.awt.Color;
import java.lang.reflect.Method;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Client half of the automated in-game test (-Dqwertys_painter.selftest=true). */
@Mod.EventBusSubscriber(modid = PainterMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SelfTestClient {
    private static final int Y = SelfTestServer.Y;
    private static int ticks;
    private static int failures;

    private SelfTestClient() {}

    private static void check(boolean ok, String what) {
        if (!ok) failures++;
        PainterMod.LOGGER.info("SELFTEST CHECK {} : {}", ok ? "OK  " : "FAIL", what);
    }

    private static void click(Minecraft mc, BlockPos pos) {
        click(mc, pos, InteractionHand.OFF_HAND);
    }

    private static void click(Minecraft mc, BlockPos pos, InteractionHand hand) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0, 0.5), Direction.SOUTH, pos, false);
        mc.gameMode.useItemOn(mc.player, hand, hit);
    }

    /** Opens the creative inventory on the mod's tab (dev names; test only). */
    private static void openCreativeTab(Minecraft mc) throws Exception {
        CreativeModeInventoryScreen screen = new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(),
                mc.options.operatorItemsTab().get());
        mc.setScreen(screen);
        for (Method m : CreativeModeInventoryScreen.class.getDeclaredMethods()) {
            if (m.getReturnType() == void.class && m.getParameterCount() == 1 && m.getParameterTypes()[0] == CreativeModeTab.class) {
                m.setAccessible(true);
                m.invoke(screen, ModTabs.MAIN.get());
                return;
            }
        }
        PainterMod.LOGGER.warn("SELFTEST could not select creative tab");
    }

    private static int countFaces() {
        Map<Long, Map<Long, FaceData[]>> dim = ClientPaintStore.forDim(Minecraft.getInstance().level.dimension());
        int n = 0;
        if (dim != null) for (Map<Long, FaceData[]> c : dim.values()) for (FaceData[] f : c.values()) for (FaceData d : f) if (d != null) n++;
        return n;
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> PainterMod.LOGGER.info("SELFTEST screenshot {}", name));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !SelfTestServer.enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameMode == null) return;
        ticks++;
        try {
            step(mc);
        } catch (Exception e) {
            failures++;
            PainterMod.LOGGER.error("SELFTEST step at tick {} failed", ticks, e);
        }
    }

    private static void step(Minecraft mc) throws Exception {
        switch (ticks) {
            case 200 -> {
                check(countFaces() == 37, "server paint synced to client, faces=" + countFaces());
                click(mc, new BlockPos(4, Y, 2));
            }
            case 215 -> click(mc, new BlockPos(5, Y + 1, 2));
            case 230 -> click(mc, new BlockPos(4, Y, 2));
            case 250 -> {
                boolean found = false;
                for (ItemStack s : mc.player.getInventory().items) {
                    PaintCanvasItem.Region r = PaintCanvasItem.region(s);
                    if (r != null && r.a().equals(new BlockPos(4, Y, 2)) && r.b().equals(new BlockPos(5, Y + 1, 2))) {
                        int[] wh = FaceGeometry.regionSize(r.a(), r.b(), r.face());
                        found = wh[0] == 2 && wh[1] == 2 && r.face() == Direction.SOUTH;
                    }
                }
                check(found, "brush: first -> last -> first again made a 2x2 canvas");
            }
            case 260 -> {
                Path file = mc.gameDirectory.toPath().resolve("selftest_upload.png");
                Files.write(file, SelfTestServer.testPicture(new Color(40, 170, 60), new Color(200, 60, 200)));
                ImagePicker.Prepared p = ImagePicker.prepare(file);
                FillScreen.setPicture(p);
                ImagePicker.upload(p);
                PainterNetwork.CHANNEL.sendToServer(new FillRegionC2S(InteractionHand.MAIN_HAND, Direction.SOUTH.get3DDataValue(), false, p.id(),
                        new FaceGeometry.Placement(6, 4, 0, 0, 2, false, false, false)));
            }
            case 300 -> {
                BlockPos topLeft = new BlockPos(-11, Y + 3, -6);
                FaceData[] f = ClientPaintStore.get(mc.level.dimension(), topLeft);
                check(f != null && f[Direction.SOUTH.get3DDataValue()] != null, "wall A top-left block painted");
                if (f != null && f[Direction.SOUTH.get3DDataValue()] != null) {
                    PainterNetwork.CHANNEL.sendToServer(new EditFaceC2S(topLeft, Direction.SOUTH.get3DDataValue(), null));
                }
            }
            case 400 -> {
                FaceData[] c = ClientPaintStore.get(mc.level.dimension(), new BlockPos(3, Y + 3, -6));
                check(c != null && c[Direction.SOUTH.get3DDataValue()] != null, "upload + canvas fill reached the wall");
                FaceData[] removed = ClientPaintStore.get(mc.level.dimension(), new BlockPos(-11, Y + 3, -6));
                check(removed == null || removed[Direction.SOUTH.get3DDataValue()] == null, "single face edit (delete) applied");
                check(ClientEvents.lastFrameFaces > 0, "faces drawn last frame=" + ClientEvents.lastFrameFaces);
                check(ClientImageCache.loadedCount() >= 2, "textures loaded=" + ClientImageCache.loadedCount());
                shot(mc, "selftest_world.png");
            }
            case 420 -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            case 470 -> {
                check(mc.screen instanceof FillScreen, "right-click canvas (from brush) opens fill screen");
                shot(mc, "selftest_fill.png");
            }
            case 480 -> mc.setScreen(new EditScreen(new BlockPos(11, Y + 1, -4), Direction.SOUTH));
            case 530 -> {
                check(mc.screen instanceof EditScreen, "edit screen opened");
                shot(mc, "selftest_edit.png");
            }
            case 540 -> {
                mc.setScreen(null);
                mc.player.setYRot(225f);
                mc.player.setXRot(10f);
                mc.player.setPos(6.5, Y, 1.5);
            }
            case 600 -> shot(mc, "selftest_block.png");
            case 610 -> openCreativeTab(mc);
            case 650 -> {
                boolean has = ModTabs.MAIN.get().getDisplayItems().stream().anyMatch(s -> s.is(ModItems.PAINT_BRUSH.get()))
                        && ModTabs.MAIN.get().getDisplayItems().stream().anyMatch(s -> s.is(ModItems.PAINT_CANVAS.get()));
                check(mc.screen instanceof CreativeModeInventoryScreen && has, "mod creative tab shows brush and canvas");
                shot(mc, "selftest_creative.png");
            }
            case 660 -> {
                mc.setScreen(null);
                mc.player.getInventory().selected = 8;
            }
            case 664 -> {
                ItemStack empty = mc.player.getInventory().getItem(8);
                check(empty.is(ModItems.PAINT_CANVAS.get()) && PaintCanvasItem.region(empty) == null, "empty canvas (as from creative tab) in slot 9");
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            }
            case 670 -> check(mc.screen == null, "empty canvas right-click in air shows a hint, no crash");
            case 675 -> click(mc, new BlockPos(4, Y, 2), InteractionHand.MAIN_HAND);
            case 690 -> click(mc, new BlockPos(5, Y + 1, 2), InteractionHand.MAIN_HAND);
            case 705 -> click(mc, new BlockPos(4, Y, 2), InteractionHand.MAIN_HAND);
            case 725 -> {
                PaintCanvasItem.Region r = PaintCanvasItem.region(mc.player.getInventory().getItem(8));
                check(r != null && r.a().equals(new BlockPos(4, Y, 2)) && r.b().equals(new BlockPos(5, Y + 1, 2)),
                        "empty canvas picked its own region");
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            }
            case 745 -> {
                check(mc.screen instanceof FillScreen, "right-click that canvas opens fill screen");
                shot(mc, "selftest_canvas_open.png");
            }
            case 760 -> {
                mc.setScreen(null);
                click(mc, new BlockPos(5, Y, 2), InteractionHand.MAIN_HAND);
            }
            case 780 -> {
                check(mc.screen instanceof FillScreen, "right-click a block with that canvas also opens fill screen");
                mc.setScreen(null);
            }
            case 800 -> {
                PainterMod.LOGGER.info("SELFTEST DONE failures={}", failures);
                File marker = new File(mc.gameDirectory, "selftest_result.txt");
                Files.writeString(marker.toPath(), "failures=" + failures + "\n");
                mc.stop();
            }
            default -> {}
        }
    }
}
