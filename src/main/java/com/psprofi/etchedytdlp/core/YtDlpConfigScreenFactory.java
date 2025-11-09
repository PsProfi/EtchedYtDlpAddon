package com.psprofi.etchedytdlp.core;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Registers the config screen with Forge Mod Menu
 * Allows players to access cache manager from mod list
 *
 * @author PsProfi
 */
public class YtDlpConfigScreenFactory {

    /**
     * Register the config screen
     * Call this from your client setup
     */
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new CacheManagerScreen(parent)
                )
        );

        System.out.println("[Etched YT-DLP] Config screen registered");
    }
}