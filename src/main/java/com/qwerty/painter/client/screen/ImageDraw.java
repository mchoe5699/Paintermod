package com.qwerty.painter.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

final class ImageDraw {
    static final float[] FULL_UV = {0, 0, 1, 0, 1, 1, 0, 1};

    private ImageDraw() {}

    /** Draws a textured quad; uv is top-left, top-right, bottom-right, bottom-left (u,v pairs). */
    static void quad(GuiGraphics g, ResourceLocation tex, float x0, float y0, float x1, float y1, float[] uv) {
        g.flush();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(m, x0, y0, 0).uv(uv[0], uv[1]).endVertex();
        bb.vertex(m, x0, y1, 0).uv(uv[6], uv[7]).endVertex();
        bb.vertex(m, x1, y1, 0).uv(uv[4], uv[5]).endVertex();
        bb.vertex(m, x1, y0, 0).uv(uv[2], uv[3]).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Draws a picture filling the rectangle after rotation (clockwise steps) and flips. */
    static void transformed(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, int rotation, boolean flipH, boolean flipV) {
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + h / 2f, 0);
        g.pose().scale(flipH ? -1 : 1, flipV ? -1 : 1, 1);
        g.pose().mulPose(Axis.ZP.rotationDegrees(90 * (rotation & 3)));
        boolean odd = (rotation & 1) == 1;
        float iw = odd ? h : w, ih = odd ? w : h;
        quad(g, tex, -iw / 2f, -ih / 2f, iw / 2f, ih / 2f, FULL_UV);
        g.pose().popPose();
    }

    /** Largest rectangle with the given aspect ratio that fits in a box, centred. Returns {x, y, w, h}. */
    static float[] fit(float boxX, float boxY, float boxW, float boxH, float aspect) {
        float w = boxW, h = boxW / aspect;
        if (h > boxH) {
            h = boxH;
            w = boxH * aspect;
        }
        return new float[]{boxX + (boxW - w) / 2f, boxY + (boxH - h) / 2f, w, h};
    }

    static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
