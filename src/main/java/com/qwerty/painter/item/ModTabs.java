package com.qwerty.painter.item;

import com.qwerty.painter.PainterMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The mod's own creative inventory tab. */
public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PainterMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.qwertys_painter"))
            .icon(() -> new ItemStack(ModItems.PAINT_BRUSH.get()))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .displayItems((params, output) -> {
                output.accept(ModItems.PAINT_BRUSH.get());
                output.accept(ModItems.PAINT_CANVAS.get());
            })
            .build());

    private ModTabs() {}
}
