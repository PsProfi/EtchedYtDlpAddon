package com.psprofi.etchedytdlp.setup;

import com.psprofi.etchedytdlp.YouTube.YtDlpManager;
import com.psprofi.etchedytdlp.core.EtchedYtDlpAddon;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EtchedYtDlpAddon.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {

    private static boolean shown = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();

        // показуємо лише один раз
        if (!shown && mc.screen == null && !YtDlpManager.isFullyInstalled()) {
            mc.setScreen(new SetupScreen());
            shown = true;
        }
    }
}
