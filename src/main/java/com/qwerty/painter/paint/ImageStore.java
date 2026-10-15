package com.qwerty.painter.paint;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.regex.Pattern;

/** Pictures are stored as PNG files inside the world folder, named by their SHA-1 hash. */
public final class ImageStore {
    private static final Pattern ID = Pattern.compile("[0-9a-f]{40}");
    public static final int MAX_DIMENSION = 4096;

    private ImageStore() {}

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("qwertys_painter_images");
    }

    private static Path file(MinecraftServer server, String id) {
        return dir(server).resolve(id + ".png");
    }

    public static boolean exists(MinecraftServer server, String id) {
        return validId(id) && Files.isRegularFile(file(server, id));
    }

    public static byte[] read(MinecraftServer server, String id) throws IOException {
        if (!validId(id)) throw new IOException("bad id");
        return Files.readAllBytes(file(server, id));
    }

    public static void write(MinecraftServer server, String id, byte[] data) throws IOException {
        if (!validId(id)) throw new IOException("bad id");
        Path dir = dir(server);
        Files.createDirectories(dir);
        Path target = file(server, id);
        if (Files.exists(target)) return;
        Path tmp = dir.resolve(id + ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Checks the bytes are a PNG of sane size without decoding all pixels. */
    public static boolean isAcceptablePng(byte[] data) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (in == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                if (!"png".equalsIgnoreCase(reader.getFormatName())) return false;
                reader.setInput(in);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                return w > 0 && h > 0 && w <= MAX_DIMENSION && h <= MAX_DIMENSION;
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static String sha1(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(40);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
