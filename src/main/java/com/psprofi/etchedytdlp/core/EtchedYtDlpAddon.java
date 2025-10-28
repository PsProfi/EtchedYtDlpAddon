package com.psprofi.etchedytdlp.core;

import com.psprofi.etchedytdlp.LocalAudioServer;
import com.psprofi.etchedytdlp.YouTube.YtDlpSource;
import com.psprofi.etchedytdlp.YouTube.YtDlpUpdater;
import gg.moonflower.etched.api.sound.download.SoundSourceManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("etchedytdlp")
public class EtchedYtDlpAddon {

    public static final String MOD_ID = "etchedytdlp";

    public EtchedYtDlpAddon() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        // Register for server events
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Register IMMEDIATELY (not in enqueueWork) - runs earlier in mod loading
        YtDlpUpdater.checkAndUpdateIfNeeded();

        // Use YtDlpSource for SoundCloud instead of the fixed version
        // SoundCloud uses MPEG-DASH segments that are too complex to handle manually
        // YT-DLP handles DASH properly and downloads complete audio
        SoundSourceManager.registerSource(new YtDlpSource());

        System.out.println("[Etched YT-DLP] YtDlpSource registered!");
        System.out.println("[Etched YT-DLP] Will handle YouTube, SoundCloud, Spotify, etc.");
        System.out.println("[Etched YT-DLP] Note: May compete with Etched's built-in handlers");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Start local HTTP server for serving cached audio files
        try {
            LocalAudioServer.start();
            System.out.println("[Etched YT-DLP] Local audio server started successfully");
        } catch (Exception e) {
            System.err.println("[Etched YT-DLP] Failed to start local audio server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // Stop local HTTP server
        LocalAudioServer.stop();
        System.out.println("[Etched YT-DLP] Local audio server stopped");
    }
}