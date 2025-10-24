package com.psprofi.etchedytdlp;

import gg.moonflower.etched.api.sound.download.SoundSourceManager;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import static com.psprofi.etchedytdlp.EtchedYtDlpAddon.MOD_ID;

@Mod(MOD_ID)
public class EtchedYtDlpAddon {

    public static final String MOD_ID = "etchedytdlp";

    public EtchedYtDlpAddon() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Register YT-DLP source with Etched
            SoundSourceManager.registerSource(new YtDlpSource());
        });
    }
}