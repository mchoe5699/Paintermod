package com.qwerty.painter;

import com.mojang.logging.LogUtils;
import com.qwerty.painter.item.ModItems;
import com.qwerty.painter.network.PainterNetwork;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(PainterMod.MODID)
public class PainterMod {
    public static final String MODID = "qwertys_painter";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PainterMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        modBus.addListener(this::addCreative);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, PainterConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PainterConfig.CLIENT_SPEC);

        PainterNetwork.register();
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.PAINT_BRUSH);
        }
    }
}
