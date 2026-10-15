package com.qwerty.painter.client.screen;

import com.qwerty.painter.client.ClientImageCache;
import com.qwerty.painter.client.ClientPaintStore;
import com.qwerty.painter.client.ImagePicker;
import com.qwerty.painter.item.PaintBrushItem;
import com.qwerty.painter.network.EditFaceC2S;
import com.qwerty.painter.network.PainterNetwork;
import com.qwerty.painter.paint.FaceData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Edit the six faces of one block, each on its own. */
public class EditScreen extends PictureScreen {
    private static final Direction[] ORDER = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};

    @Nullable
    private static FaceData clipboard;

    private final BlockPos pos;
    private Direction selected;
    private final Button[] faceButtons = new Button[6];
    private final List<Button> needsPicture = new ArrayList<>();
    private Button pasteButton, glowButton;
    private int left, right, top;

    public EditScreen(BlockPos pos, Direction face) {
        super(Component.translatable("qwertys_painter.gui.edit_title"));
        this.pos = pos;
        this.selected = face;
    }

    private ResourceKey<Level> dim() {
        return Minecraft.getInstance().level.dimension();
    }

    @Nullable
    private FaceData faceData(Direction d) {
        if (Minecraft.getInstance().level == null) return null;
        FaceData[] f = ClientPaintStore.get(dim(), pos);
        return f == null ? null : f[d.get3DDataValue()];
    }

    @Override
    protected void init() {
        needsPicture.clear();
        int cx = width / 2;
        top = Math.max(4, height / 2 - 112);
        left = cx - 158;
        right = cx + 4;

        for (int i = 0; i < 6; i++) {
            Direction d = ORDER[i];
            int col = i % 3, row = i / 3;
            faceButtons[i] = addRenderableWidget(Button.builder(Component.empty(), b -> selected = d)
                    .bounds(left + col * 52, top + 26 + row * 22, 50, 20).build());
        }

        int y = top + 26;
        addRenderableWidget(Button.builder(Component.translatable("qwertys_painter.gui.pick"), b -> pickWithDialog())
                .bounds(right, y, 154, 20).build());
        y += 22;
        addRenderableWidget(makePathBox(right, y, 106));
        addRenderableWidget(Button.builder(Component.translatable("qwertys_painter.gui.load"), b -> loadFromPathBox())
                .bounds(right + 108, y, 46, 20).build());
        y += 22;
        op("-90°", y, 0, FaceData::rotateCCW);
        op("+90°", y, 1, FaceData::rotateCW);
        opKey("qwertys_painter.gui.flip_h", y, 2, FaceData::flipH);
        opKey("qwertys_painter.gui.flip_v", y, 3, FaceData::flipV);
        y += 22;
        opKey("qwertys_painter.gui.zoom_in", y, 0, f -> f.zoom(0.8f));
        opKey("qwertys_painter.gui.zoom_out", y, 1, f -> f.zoom(1.25f));
        opKey("qwertys_painter.gui.move_left", y, 2, f -> f.pan(-0.1f, 0));
        opKey("qwertys_painter.gui.move_right", y, 3, f -> f.pan(0.1f, 0));
        y += 22;
        opKey("qwertys_painter.gui.move_up", y, 0, f -> f.pan(0, -0.1f));
        opKey("qwertys_painter.gui.move_down", y, 1, f -> f.pan(0, 0.1f));
        glowButton = addRenderableWidget(Button.builder(Component.empty(), b -> transform(f -> f.withGlow(!f.glow)))
                .bounds(right + 78, y, 76, 20).build());
        needsPicture.add(glowButton);
        y += 22;
        Button copy = addRenderableWidget(Button.builder(Component.translatable("qwertys_painter.gui.copy"), b -> {
            clipboard = faceData(selected);
            setStatus(Component.translatable("qwertys_painter.gui.copied"), OK);
        }).bounds(right, y, 76, 20).build());
        needsPicture.add(copy);
        pasteButton = addRenderableWidget(Button.builder(Component.translatable("qwertys_painter.gui.paste"), b -> {
            if (clipboard != null) send(selected, clipboard);
        }).bounds(right + 78, y, 76, 20).build());
        y += 22;
        Button all = addRenderableWidget(Button.builder(Component.translatable("qwertys_painter.gui.apply_all"), b -> {
            FaceData f = faceData(selected);
            if (f == null) return;
            for (Direction d : Direction.values()) send(d, f);
            setStatus(Component.translatable("qwertys_painter.gui.applied_all"), OK);
        }).bounds(right, y, 76, 20).build());
        needsPicture.add(all);
        Button del = addRenderableWidget(Button.builder(Component.translatable("qwertys_painter.gui.delete_face"), b -> send(selected, null))
                .bounds(right + 78, y, 76, 20).build());
        needsPicture.add(del);
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("qwertys_painter.gui.close"), b -> onClose())
                .bounds(right, y, 154, 20).build());

        refreshButtons();
    }

    private void op(String label, int y, int slot, UnaryOperator<FaceData> fn) {
        needsPicture.add(addRenderableWidget(Button.builder(Component.literal(label), b -> transform(fn))
                .bounds(right + slot * 39, y, 37, 20).build()));
    }

    private void opKey(String key, int y, int slot, UnaryOperator<FaceData> fn) {
        needsPicture.add(addRenderableWidget(Button.builder(Component.translatable(key), b -> transform(fn))
                .bounds(right + slot * 39, y, 37, 20).build()));
    }

    private void transform(UnaryOperator<FaceData> fn) {
        FaceData f = faceData(selected);
        if (f == null) {
            setStatus(Component.translatable("qwertys_painter.gui.face_empty"), ERROR);
            return;
        }
        send(selected, fn.apply(f));
    }

    private void send(Direction d, @Nullable FaceData data) {
        ClientPaintStore.setLocal(dim(), pos, d, data);
        PainterNetwork.CHANNEL.sendToServer(new EditFaceC2S(pos, d.get3DDataValue(), data));
    }

    @Override
    protected void onPicked(ImagePicker.Prepared picture) {
        ImagePicker.upload(picture);
        FaceData old = faceData(selected);
        send(selected, FaceData.full(picture.id(), old != null && old.glow));
    }

    private void refreshButtons() {
        for (int i = 0; i < 6; i++) {
            Direction d = ORDER[i];
            MutableComponent label = PaintBrushItem.dirName(d).copy();
            if (faceData(d) != null) label.append(" ✔");
            if (d == selected) label = Component.literal("[").append(label).append("]").withStyle(ChatFormatting.YELLOW);
            faceButtons[i].setMessage(label);
        }
        FaceData cur = faceData(selected);
        for (Button b : needsPicture) b.active = cur != null;
        glowButton.setMessage(Component.translatable(cur != null && cur.glow ? "qwertys_painter.gui.glow_on" : "qwertys_painter.gui.glow_off"));
        pasteButton.active = clipboard != null;
    }

    @Override
    public void tick() {
        super.tick();
        if (pathBox != null) pathBox.tick();
        refreshButtons();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, top, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("qwertys_painter.gui.edit_info", pos.getX(), pos.getY(), pos.getZ(),
                PaintBrushItem.dirName(selected)), width / 2, top + 12, 0xB0B0B0);

        // preview of the selected face as it looks on the block
        int size = 96;
        int bx = left + (154 - size) / 2, by = top + 74;
        g.fill(bx - 2, by - 2, bx + size + 2, by + size + 2, 0xFF505050);
        g.fill(bx, by, bx + size, by + size, 0xFF1E1E1E);
        FaceData f = faceData(selected);
        if (f != null) {
            ResourceLocation tex = ClientImageCache.getOrRequest(f.image);
            if (tex != null) {
                ImageDraw.quad(g, tex, bx + f.fx0 * size, by + f.fy0 * size, bx + f.fx1 * size, by + f.fy1 * size, f.uv);
            } else {
                g.drawCenteredString(font, Component.translatable("qwertys_painter.gui.loading"), bx + size / 2, by + size / 2 - 4, 0x909090);
            }
        } else {
            g.drawCenteredString(font, Component.translatable("qwertys_painter.gui.face_empty_short"), bx + size / 2, by + size / 2 - 4, 0x909090);
        }

        drawStatus(g, left, by + size + 6, 154);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
