package com.qwerty.painter.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.qwerty.painter.PainterConfig;
import com.qwerty.painter.PainterMod;
import com.qwerty.painter.network.PainterNetwork;
import com.qwerty.painter.network.UploadChunkC2S;
import com.qwerty.painter.paint.ImageStore;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Picks a picture on the PC, shrinks it, turns it into PNG and uploads it. */
public final class ImagePicker {
    public record Prepared(String id, byte[] png, int width, int height, ResourceLocation texture) {}

    private ImagePicker() {}

    @Nullable
    public static Path openDialog(String title, String filterDescription) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String[] patterns = {"*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.PNG", "*.JPG", "*.JPEG"};
            PointerBuffer filters = stack.mallocPointer(patterns.length);
            for (String p : patterns) filters.put(stack.UTF8(p));
            filters.flip();
            String result = TinyFileDialogs.tinyfd_openFileDialog(title, "", filters, filterDescription, false);
            return result == null ? null : Path.of(result);
        } catch (Throwable t) {
            PainterMod.LOGGER.warn("File dialog failed", t);
            return null;
        }
    }

    public static Prepared prepare(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("not a file");
        BufferedImage src = ImageIO.read(path.toFile());
        if (src == null) throw new IOException("unsupported format");

        int max = PainterConfig.get(PainterConfig.MAX_IMAGE_PIXELS, 1024);
        int w = src.getWidth(), h = src.getHeight();
        double scale = Math.min(1.0, (double) max / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * scale)), nh = Math.max(1, (int) Math.round(h * scale));

        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (!ImageIO.write(out, "png", bos)) throw new IOException("png encode failed");
        byte[] png = bos.toByteArray();
        String id = ImageStore.sha1(png);

        ResourceLocation tex = ClientImageCache.get(id);
        if (tex == null) tex = ClientImageCache.register(id, NativeImage.read(new ByteArrayInputStream(png)));
        return new Prepared(id, png, nw, nh, tex);
    }

    /** Sends the picture to the server unless it already has it. */
    public static void upload(Prepared p) {
        if (ClientImageCache.SERVER_KNOWN.contains(p.id())) return;
        byte[] d = p.png();
        for (int off = 0; off < d.length; off += PainterNetwork.UPLOAD_CHUNK) {
            int end = Math.min(d.length, off + PainterNetwork.UPLOAD_CHUNK);
            PainterNetwork.CHANNEL.sendToServer(new UploadChunkC2S(p.id(), d.length, off, Arrays.copyOfRange(d, off, end)));
        }
        ClientImageCache.SERVER_KNOWN.add(p.id());
    }
}
