package com.qwerty.painter.client.screen;

import com.qwerty.painter.client.ImagePicker;
import com.qwerty.painter.item.PaintBrushItem;
import com.qwerty.painter.item.PaintCanvasItem;
import com.qwerty.painter.network.FillRegionC2S;
import com.qwerty.painter.network.PainterNetwork;
import com.qwerty.painter.paint.FaceGeometry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

import java.util.Locale;

/** Opened from a canvas: choose picture, rotation, size and fill the region. */
public class FillScreen extends PictureScreen {
    private static final String[] MODE_KEYS = {"stretch", "fit", "cover", "custom"};

    // remembered between openings
    private static int rotation;
    private static boolean flipH, flipV, glow;
    private static int mode;

    private final InteractionHand hand;
    private final PaintCanvasItem.Region region;
    private Direction face;
    private int regionW, regionH;

    private EditBox wBox, hBox, xBox, yBox;
    private Button modeButton, faceButton, glowButton;
    private int left, right, top;

    public FillScreen(InteractionHand hand, PaintCanvasItem.Region region) {
        super(Component.translatable("qwertys_painter.gui.fill_title"));
        this.hand = hand;
        this.region = region;
        this.face = region.face();
        updateRegionSize();
    }

    private void updateRegionSize() {
        int[] wh = FaceGeometry.regionSize(region.a(), region.b(), face);
        regionW = wh[0];
        regionH = wh[1];
    }

    @Override
    protected void init() {
        int cx = width / 2;
        top = Math.max(4, height / 2 - 114);
        left = cx - 158;
        right = cx + 4;
        int r1 = top + 118, r2 = top + 140, r3 = top + 162, r4 = top + 184, r5 = top + 206;

        // ---- left column: picture
        addRenderableWidget(Button.builder(Component.translatable("qwertys_painter.gui.pick"), b -> pickWithDialog())
                .bounds(left, r1, 154, 20).build());
        addRenderableWidget(makePathBox(left, r2, 106));
        addRenderableWidget(Button.builder(Component.translatable("qwertys_painter.gui.load"), b -> loadFromPathBox())
                .bounds(left + 108, r2, 46, 20).build());

        addRenderableWidget(Button.builder(Component.literal("↺"), b -> { rotation = (rotation + 3) & 3; syncBoxes(); })
                .bounds(left, r3, 37, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↻"), b -> { rotation = (rotation + 1) & 3; syncBoxes(); })
                .bounds(left + 39, r3, 37, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("qwertys_painter.gui.flip_h"), b -> flipH = !flipH)
                .bounds(left + 78, r3, 37, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("qwertys_painter.gui.flip_v"), b -> flipV = !flipV)
                .bounds(left + 117, r3, 37, 20).build());

        glowButton = addRenderableWidget(Button.builder(glowText(), b -> { glow = !glow; b.setMessage(glowText()); })
                .bounds(left, r4, 76, 20).build());
        faceButton = addRenderableWidget(Button.builder(faceText(), b -> cycleFace())
                .bounds(left + 78, r4, 76, 20).build());

        // ---- right column: size
        modeButton = addRenderableWidget(Button.builder(modeText(), b -> cycleMode())
                .bounds(right, r1, 154, 20).build());
        wBox = addRenderableWidget(numberBox(right + 24, r2));
        hBox = addRenderableWidget(numberBox(right + 104, r2));
        xBox = addRenderableWidget(numberBox(right + 24, r3));
        yBox = addRenderableWidget(numberBox(right + 104, r3));

        addRenderableWidget(Button.builder(Component.translatable("qwertys_painter.gui.apply"), b -> apply())
                .bounds(right, r4, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("qwertys_painter.gui.clear_region"), b -> clearRegion())
                .bounds(right + 78, r4, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("qwertys_painter.gui.close"), b -> onClose())
                .bounds(right, r5, 154, 20).build());

        syncBoxes();
    }

    private EditBox numberBox(int x, int y) {
        EditBox box = new EditBox(font, x, y, 50, 20, Component.empty());
        box.setMaxLength(8);
        box.setFilter(s -> s.matches("-?\\d*(\\.\\d*)?"));
        return box;
    }

    private Component glowText() {
        return Component.translatable(glow ? "qwertys_painter.gui.glow_on" : "qwertys_painter.gui.glow_off");
    }

    private Component faceText() {
        return Component.translatable("qwertys_painter.gui.face", PaintBrushItem.dirName(face));
    }

    private Component modeText() {
        return Component.translatable("qwertys_painter.gui.mode", Component.translatable("qwertys_painter.gui.mode." + MODE_KEYS[mode]));
    }

    private void cycleFace() {
        face = Direction.from3DDataValue((face.get3DDataValue() + 1) % 6);
        updateRegionSize();
        faceButton.setMessage(faceText());
        syncBoxes();
    }

    private void cycleMode() {
        FaceGeometry.Placement current = placement();
        mode = (mode + 1) % MODE_KEYS.length;
        modeButton.setMessage(modeText());
        if (mode == 3) writeBoxes(current);
        syncBoxes();
    }

    /** In automatic modes the boxes only show the computed numbers. */
    private void syncBoxes() {
        boolean custom = mode == 3;
        for (EditBox b : new EditBox[]{wBox, hBox, xBox, yBox}) {
            if (b == null) return;
            b.setEditable(custom);
        }
        if (!custom) writeBoxes(placement());
    }

    private void writeBoxes(FaceGeometry.Placement p) {
        wBox.setValue(fmt(p.width()));
        hBox.setValue(fmt(p.height()));
        xBox.setValue(fmt(p.offX()));
        yBox.setValue(fmt(p.offY()));
    }

    private static String fmt(float f) {
        String s = String.format(Locale.ROOT, "%.2f", f);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    private static float parse(EditBox box, float fallback) {
        try {
            return Float.parseFloat(box.getValue());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Aspect ratio (width / height) of the picture as it will appear after rotation. */
    private float aspect() {
        ImagePicker.Prepared p = lastPicked;
        if (p == null) return 1f;
        float w = p.width(), h = p.height();
        return (rotation & 1) == 1 ? h / w : w / h;
    }

    private FaceGeometry.Placement placement() {
        float W = regionW, H = regionH, ar = aspect();
        float dw, dh, ox = 0, oy = 0;
        switch (mode) {
            case 1 -> { // fit inside, keep ratio
                if (W / H > ar) { dh = H; dw = H * ar; ox = (W - dw) / 2; }
                else { dw = W; dh = W / ar; oy = (H - dh) / 2; }
            }
            case 2 -> { // cover whole region, keep ratio, crop the rest
                if (W / H > ar) { dw = W; dh = W / ar; oy = (H - dh) / 2; }
                else { dh = H; dw = H * ar; ox = (W - dw) / 2; }
            }
            case 3 -> {
                dw = parse(wBox, W);
                dh = parse(hBox, H);
                ox = parse(xBox, 0);
                oy = parse(yBox, 0);
            }
            default -> { dw = W; dh = H; }
        }
        return new FaceGeometry.Placement(dw, dh, ox, oy, rotation, flipH, flipV, glow);
    }

    private void apply() {
        ImagePicker.Prepared p = lastPicked;
        if (p == null) {
            setStatus(Component.translatable("qwertys_painter.gui.no_picture"), ERROR);
            return;
        }
        FaceGeometry.Placement pl = placement();
        if (!pl.isValid()) {
            setStatus(Component.translatable("qwertys_painter.gui.bad_size"), ERROR);
            return;
        }
        ImagePicker.upload(p);
        PainterNetwork.CHANNEL.sendToServer(new FillRegionC2S(hand, face.get3DDataValue(), false, p.id(), pl));
        setStatus(Component.translatable("qwertys_painter.gui.applied"), OK);
    }

    private void clearRegion() {
        PainterNetwork.CHANNEL.sendToServer(new FillRegionC2S(hand, face.get3DDataValue(), true, "",
                new FaceGeometry.Placement(1, 1, 0, 0, 0, false, false, false)));
        setStatus(Component.translatable("qwertys_painter.gui.cleared"), OK);
    }

    /** Used by the automated self test. */
    public static void setPicture(ImagePicker.Prepared picture) {
        lastPicked = picture;
    }

    @Override
    protected void onPicked(ImagePicker.Prepared picture) {
        syncBoxes();
    }

    @Override
    public void tick() {
        super.tick();
        for (EditBox b : new EditBox[]{pathBox, wBox, hBox, xBox, yBox}) if (b != null) b.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, top, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("qwertys_painter.gui.fill_info", regionW, regionH,
                PaintBrushItem.dirName(face), rotation * 90), width / 2, top + 12, 0xB0B0B0);

        // picture preview (left)
        int pbx = left, pby = top + 26, pbw = 154, pbh = 86;
        g.fill(pbx, pby, pbx + pbw, pby + pbh, 0xC0101010);
        ImageDraw.frame(g, pbx, pby, pbw, pbh, 0xFF606060);
        ImagePicker.Prepared p = lastPicked;
        if (p != null) {
            float[] r = ImageDraw.fit(pbx + 4, pby + 4, pbw - 8, pbh - 8, aspect());
            ImageDraw.transformed(g, p.texture(), r[0], r[1], r[2], r[3], rotation, flipH, flipV);
        } else {
            g.drawCenteredString(font, Component.translatable("qwertys_painter.gui.no_picture_hint"), pbx + pbw / 2, pby + pbh / 2 - 4, 0x909090);
        }

        // layout preview (right): region grid with the placed picture
        int rbx = right, rby = top + 26, rbw = 154, rbh = 86;
        g.fill(rbx, rby, rbx + rbw, rby + rbh, 0xC0101010);
        ImageDraw.frame(g, rbx, rby, rbw, rbh, 0xFF606060);
        float s = Math.min((rbw - 8f) / regionW, (rbh - 8f) / regionH);
        float gw = regionW * s, gh = regionH * s;
        int gx0 = Math.round(rbx + (rbw - gw) / 2), gy0 = Math.round(rby + (rbh - gh) / 2);
        int gx1 = Math.round(gx0 + gw), gy1 = Math.round(gy0 + gh);
        g.fill(gx0, gy0, gx1, gy1, 0xFF2A2A2A);
        if (p != null) {
            FaceGeometry.Placement pl = placement();
            if (pl.isValid()) {
                g.enableScissor(gx0, gy0, gx1, gy1);
                ImageDraw.transformed(g, p.texture(), gx0 + pl.offX() * s, gy0 + pl.offY() * s, pl.width() * s, pl.height() * s,
                        rotation, flipH, flipV);
                g.disableScissor();
            }
        }
        if (regionW <= 64 && regionH <= 64 && s >= 3) {
            for (int i = 1; i < regionW; i++) {
                int x = Math.round(gx0 + i * s);
                g.fill(x, gy0, x + 1, gy1, 0x50FFFFFF);
            }
            for (int j = 1; j < regionH; j++) {
                int y = Math.round(gy0 + j * s);
                g.fill(gx0, y, gx1, y + 1, 0x50FFFFFF);
            }
        }
        ImageDraw.frame(g, gx0 - 1, gy0 - 1, gx1 - gx0 + 2, gy1 - gy0 + 2, 0xFFFFD040);

        // size labels
        int r2 = top + 140, r3 = top + 162;
        g.drawString(font, Component.translatable("qwertys_painter.gui.width"), right, r2 + 6, 0xE0E0E0);
        g.drawString(font, Component.translatable("qwertys_painter.gui.height"), right + 80, r2 + 6, 0xE0E0E0);
        g.drawString(font, Component.translatable("qwertys_painter.gui.off_x"), right, r3 + 6, 0xE0E0E0);
        g.drawString(font, Component.translatable("qwertys_painter.gui.off_y"), right + 80, r3 + 6, 0xE0E0E0);

        drawStatus(g, left, top + 208, 154);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
