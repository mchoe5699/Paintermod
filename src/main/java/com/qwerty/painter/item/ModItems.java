package com.qwerty.painter.item;

import com.qwerty.painter.PainterMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PainterMod.MODID);

    public static final RegistryObject<Item> PAINT_BRUSH = ITEMS.register("paint_brush",
            () -> new PaintBrushItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PAINT_CANVAS = ITEMS.register("paint_canvas",
            () -> new PaintCanvasItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
