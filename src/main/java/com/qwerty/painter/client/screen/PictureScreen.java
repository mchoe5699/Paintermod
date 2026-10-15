package com.qwerty.painter.client.screen;

import com.qwerty.painter.client.ImagePicker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/** Shared picture loading: file dialog, typed path, or drag and drop onto the window. */
abstract class PictureScreen extends Screen {
    static final int OK = 0x7CFC7C;
    static final int ERROR = 0xFF6060;
    static final int INFO = 0xD0D0D0;

    /** Last picture loaded in any screen, so reopening a screen keeps it. */
    @Nullable
    static ImagePicker.Prepared lastPicked;

    protected Component status = Component.empty();
    protected int statusColor = INFO;
    protected EditBox pathBox;

    protected PictureScreen(Component title) {
        super(title);
    }

    protected abstract void onPicked(ImagePicker.Prepared picture);

    protected void setStatus(Component text, int color) {
        this.status = text;
        this.statusColor = color;
    }

    protected EditBox makePathBox(int x, int y, int w) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.translatable("qwertys_painter.gui.path"));
        box.setMaxLength(1024);
        box.setHint(Component.translatable("qwertys_painter.gui.path_hint"));
        if (pathBox != null) box.setValue(pathBox.getValue());
        pathBox = box;
        return box;
    }

    protected void pickWithDialog() {
        Path p = ImagePicker.openDialog(I18n.get("qwertys_painter.gui.pick_title"), I18n.get("qwertys_painter.gui.pick_filter"));
        if (p != null) load(p);
    }

    protected void loadFromPathBox() {
        if (pathBox == null) return;
        String s = pathBox.getValue().trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        if (s.isEmpty()) return;
        try {
            load(Path.of(s));
        } catch (InvalidPathException e) {
            setStatus(Component.translatable("qwertys_painter.gui.load_failed"), ERROR);
        }
    }

    protected void load(Path path) {
        try {
            ImagePicker.Prepared p = ImagePicker.prepare(path);
            lastPicked = p;
            if (pathBox != null) pathBox.setValue(path.toString());
            setStatus(Component.translatable("qwertys_painter.gui.loaded", path.getFileName().toString(), p.width(), p.height()), OK);
            onPicked(p);
        } catch (Exception e) {
            setStatus(Component.translatable("qwertys_painter.gui.load_failed"), ERROR);
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (!paths.isEmpty()) load(paths.get(0));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected void drawStatus(GuiGraphics g, int x, int y, int maxWidth) {
        if (status.getString().isEmpty()) return;
        g.drawWordWrap(font, status, x, y, maxWidth, statusColor);
    }
}
