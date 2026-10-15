package com.qwerty.painter.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.qwerty.painter.PainterMod;
import com.qwerty.painter.network.ImageDataS2C;
import com.qwerty.painter.network.PainterNetwork;
import com.qwerty.painter.network.RequestImageC2S;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Pictures turned into GPU textures, downloaded from the server on first use. */
public final class ClientImageCache {
    private static final Map<String, ResourceLocation> LOADED = new HashMap<>();
    private static final Map<String, int[]> SIZES = new HashMap<>();
    private static final Set<String> REQUESTED = new HashSet<>();
    private static final Map<String, byte[]> PARTIAL = new HashMap<>();
    private static final Map<String, Integer> RECEIVED = new HashMap<>();
    /** Pictures the server is known to have (uploaded or downloaded this session). */
    static final Set<String> SERVER_KNOWN = new HashSet<>();

    private ClientImageCache() {}

    @Nullable
    public static ResourceLocation get(String id) {
        return LOADED.get(id);
    }

    @Nullable
    public static ResourceLocation getOrRequest(String id) {
        ResourceLocation rl = LOADED.get(id);
        if (rl != null) return rl;
        if (REQUESTED.add(id)) PainterNetwork.CHANNEL.sendToServer(new RequestImageC2S(id));
        return null;
    }

    /** {width, height} or null when unknown. */
    @Nullable
    public static int[] size(String id) {
        return SIZES.get(id);
    }

    public static ResourceLocation register(String id, NativeImage image) {
        ResourceLocation existing = LOADED.get(id);
        if (existing != null) {
            image.close();
            return existing;
        }
        ResourceLocation rl = new ResourceLocation(PainterMod.MODID, "img/" + id);
        SIZES.put(id, new int[]{image.getWidth(), image.getHeight()});
        Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(image));
        LOADED.put(id, rl);
        return rl;
    }

    public static void onData(ImageDataS2C msg) {
        if (LOADED.containsKey(msg.id()) || msg.total() <= 0 || msg.total() > 64 * 1024 * 1024) return;
        byte[] buf = PARTIAL.get(msg.id());
        if (msg.offset() == 0 || buf == null || buf.length != msg.total()) {
            if (msg.offset() != 0) return;
            buf = new byte[msg.total()];
            PARTIAL.put(msg.id(), buf);
            RECEIVED.put(msg.id(), 0);
        }
        int got = RECEIVED.getOrDefault(msg.id(), 0);
        if (msg.offset() != got || msg.offset() + msg.data().length > buf.length) return;
        System.arraycopy(msg.data(), 0, buf, msg.offset(), msg.data().length);
        got += msg.data().length;
        RECEIVED.put(msg.id(), got);
        if (got < buf.length) return;

        PARTIAL.remove(msg.id());
        RECEIVED.remove(msg.id());
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(buf));
            register(msg.id(), img);
            SERVER_KNOWN.add(msg.id());
        } catch (Exception e) {
            PainterMod.LOGGER.warn("Could not read picture {}", msg.id(), e);
        }
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation rl : LOADED.values()) mc.getTextureManager().release(rl);
        LOADED.clear();
        SIZES.clear();
        REQUESTED.clear();
        PARTIAL.clear();
        RECEIVED.clear();
        SERVER_KNOWN.clear();
    }
}
