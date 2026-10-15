package com.qwerty.painter;

import net.minecraftforge.common.ForgeConfigSpec;

public final class PainterConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.IntValue MAX_REGION_SIDE;
    public static final ForgeConfigSpec.IntValue MAX_REGION_BLOCKS;
    public static final ForgeConfigSpec.IntValue MAX_IMAGE_KB;

    public static final ForgeConfigSpec.IntValue MAX_IMAGE_PIXELS;
    public static final ForgeConfigSpec.IntValue RENDER_DISTANCE;

    static {
        ForgeConfigSpec.Builder s = new ForgeConfigSpec.Builder();
        MAX_REGION_SIDE = s.comment("Longest side (in blocks) of a region that one canvas can cover")
                .defineInRange("maxRegionSide", 128, 1, 1024);
        MAX_REGION_BLOCKS = s.comment("Maximum number of blocks inside one canvas region")
                .defineInRange("maxRegionBlocks", 16384, 1, 1_000_000);
        MAX_IMAGE_KB = s.comment("Maximum size of one uploaded picture (PNG, kilobytes)")
                .defineInRange("maxImageKB", 8192, 64, 65536);
        SERVER_SPEC = s.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        MAX_IMAGE_PIXELS = c.comment("Pictures are scaled down so that their longest side is at most this many pixels before upload")
                .defineInRange("maxImagePixels", 1024, 64, 4096);
        RENDER_DISTANCE = c.comment("Painted faces farther than this many blocks are not drawn")
                .defineInRange("renderDistance", 128, 16, 512);
        CLIENT_SPEC = c.build();
    }

    public static int get(ForgeConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    private PainterConfig() {}
}
