package com.qwerty.painter.paint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Picture painted on one face of one block.
 * <p>
 * Face-local coordinates: x goes left to right, y goes top to bottom, both 0..1, as seen
 * by someone looking straight at the face from outside. {@code fx0..fy1} is the part of
 * the face that is covered; {@code uv} holds texture coordinates for the four corners of
 * that part in the order top-left, top-right, bottom-right, bottom-left (u, v pairs).
 */
public final class FaceData {
    public static final int MAX_ID_LEN = 64;

    public final String image;
    public final float fx0, fy0, fx1, fy1;
    public final float[] uv;
    public final boolean glow;

    public FaceData(String image, float fx0, float fy0, float fx1, float fy1, float[] uv, boolean glow) {
        this.image = image;
        this.fx0 = fx0;
        this.fy0 = fy0;
        this.fx1 = fx1;
        this.fy1 = fy1;
        this.uv = uv;
        this.glow = glow;
    }

    public static FaceData full(String image, boolean glow) {
        return new FaceData(image, 0, 0, 1, 1, new float[]{0, 0, 1, 0, 1, 1, 0, 1}, glow);
    }

    public boolean isValid() {
        if (image == null || !ImageStore.validId(image)) return false;
        if (!inRange(fx0, 0, 1) || !inRange(fx1, 0, 1) || !inRange(fy0, 0, 1) || !inRange(fy1, 0, 1)) return false;
        if (fx1 - fx0 < 1e-4f || fy1 - fy0 < 1e-4f) return false;
        if (uv == null || uv.length != 8) return false;
        for (float f : uv) if (!inRange(f, -16, 16)) return false;
        return true;
    }

    private static boolean inRange(float f, float lo, float hi) {
        return Float.isFinite(f) && f >= lo - 1e-4f && f <= hi + 1e-4f;
    }

    // ---------------------------------------------------------------- transforms

    public FaceData withImage(String newImage) {
        return new FaceData(newImage, fx0, fy0, fx1, fy1, uv.clone(), glow);
    }

    public FaceData withGlow(boolean g) {
        return new FaceData(image, fx0, fy0, fx1, fy1, uv.clone(), g);
    }

    private float u(int corner) { return uv[corner * 2]; }
    private float v(int corner) { return uv[corner * 2 + 1]; }

    private static float[] permute(float[] src, int[] from) {
        float[] out = new float[8];
        for (int i = 0; i < 4; i++) {
            out[i * 2] = src[from[i] * 2];
            out[i * 2 + 1] = src[from[i] * 2 + 1];
        }
        return out;
    }

    /** Rotate what is shown on the face by 90 degrees clockwise. */
    public FaceData rotateCW() {
        // corners: 0=tl 1=tr 2=br 3=bl ; new[i] = old[(i+3)%4]
        float[] n = permute(uv, new int[]{3, 0, 1, 2});
        return new FaceData(image, 1 - fy1, fx0, 1 - fy0, fx1, n, glow);
    }

    /** Rotate what is shown on the face by 90 degrees counter-clockwise. */
    public FaceData rotateCCW() {
        float[] n = permute(uv, new int[]{1, 2, 3, 0});
        return new FaceData(image, fy0, 1 - fx1, fy1, 1 - fx0, n, glow);
    }

    public FaceData flipH() {
        float[] n = permute(uv, new int[]{1, 0, 3, 2});
        return new FaceData(image, 1 - fx1, fy0, 1 - fx0, fy1, n, glow);
    }

    public FaceData flipV() {
        float[] n = permute(uv, new int[]{3, 2, 1, 0});
        return new FaceData(image, fx0, 1 - fy1, fx1, 1 - fy0, n, glow);
    }

    /** factor < 1 zooms in (shows a smaller part of the picture), > 1 zooms out. */
    public FaceData zoom(float factor) {
        float cu = 0, cv = 0;
        for (int i = 0; i < 4; i++) { cu += u(i); cv += v(i); }
        cu /= 4; cv /= 4;
        float[] n = new float[8];
        for (int i = 0; i < 4; i++) {
            n[i * 2] = cu + (u(i) - cu) * factor;
            n[i * 2 + 1] = cv + (v(i) - cv) * factor;
        }
        float[] fitted = fitInside(n);
        return fitted == null ? this : new FaceData(image, fx0, fy0, fx1, fy1, fitted, glow);
    }

    /** Move the visible part of the picture; dx/dy are in face directions (right / down) as a fraction of the visible span. */
    public FaceData pan(float dx, float dy) {
        float ru = u(1) - u(0), rv = v(1) - v(0);   // tl -> tr
        float du = u(3) - u(0), dv = v(3) - v(0);   // tl -> bl
        float su = ru * dx + du * dy, sv = rv * dx + dv * dy;
        float[] n = uv.clone();
        for (int i = 0; i < 4; i++) {
            n[i * 2] += su;
            n[i * 2 + 1] += sv;
        }
        float[] fitted = fitInside(n);
        return fitted == null ? this : new FaceData(image, fx0, fy0, fx1, fy1, fitted, glow);
    }

    /** Shift uv so it stays inside the picture; null when it cannot fit. */
    private static float[] fitInside(float[] n) {
        float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            minU = Math.min(minU, n[i * 2]); maxU = Math.max(maxU, n[i * 2]);
            minV = Math.min(minV, n[i * 2 + 1]); maxV = Math.max(maxV, n[i * 2 + 1]);
        }
        if (maxU - minU > 1.0001f || maxV - minV > 1.0001f) return null;
        if (maxU - minU < 0.01f || maxV - minV < 0.01f) return null;
        float su = 0, sv = 0;
        if (minU < 0) su = -minU; else if (maxU > 1) su = 1 - maxU;
        if (minV < 0) sv = -minV; else if (maxV > 1) sv = 1 - maxV;
        float[] out = n.clone();
        for (int i = 0; i < 4; i++) {
            out[i * 2] += su;
            out[i * 2 + 1] += sv;
        }
        return out;
    }

    // ---------------------------------------------------------------- serialization

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(image, MAX_ID_LEN);
        buf.writeFloat(fx0);
        buf.writeFloat(fy0);
        buf.writeFloat(fx1);
        buf.writeFloat(fy1);
        for (float f : uv) buf.writeFloat(f);
        buf.writeBoolean(glow);
    }

    public static FaceData read(FriendlyByteBuf buf) {
        String img = buf.readUtf(MAX_ID_LEN);
        float a = buf.readFloat(), b = buf.readFloat(), c = buf.readFloat(), d = buf.readFloat();
        float[] uv = new float[8];
        for (int i = 0; i < 8; i++) uv[i] = buf.readFloat();
        boolean glow = buf.readBoolean();
        return new FaceData(img, a, b, c, d, uv, glow);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("img", image);
        t.putFloat("x0", fx0);
        t.putFloat("y0", fy0);
        t.putFloat("x1", fx1);
        t.putFloat("y1", fy1);
        for (int i = 0; i < 8; i++) t.putFloat("t" + i, uv[i]);
        if (glow) t.putBoolean("glow", true);
        return t;
    }

    public static FaceData load(CompoundTag t) {
        float[] uv = new float[8];
        for (int i = 0; i < 8; i++) uv[i] = t.getFloat("t" + i);
        return new FaceData(t.getString("img"), t.getFloat("x0"), t.getFloat("y0"), t.getFloat("x1"), t.getFloat("y1"), uv, t.getBoolean("glow"));
    }

    public static void writeFaces(FriendlyByteBuf buf, FaceData[] faces) {
        int mask = 0;
        for (int i = 0; i < 6; i++) if (faces[i] != null) mask |= 1 << i;
        buf.writeByte(mask);
        for (int i = 0; i < 6; i++) if (faces[i] != null) faces[i].write(buf);
    }

    public static FaceData[] readFaces(FriendlyByteBuf buf) {
        int mask = buf.readByte();
        FaceData[] faces = new FaceData[6];
        for (int i = 0; i < 6; i++) if ((mask & (1 << i)) != 0) faces[i] = read(buf);
        return faces;
    }
}
