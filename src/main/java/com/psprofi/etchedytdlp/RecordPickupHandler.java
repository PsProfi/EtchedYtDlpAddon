package com.psprofi.etchedytdlp;

import com.psprofi.etchedytdlp.YouTube.YtDlpSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles record pickup events to cancel pending downloads.
 * This prevents duplicate records from spawning when player picks up
 * a record before it finishes loading.
 *
 * How it works:
 * 1. Player burns a record → Download starts in background
 * 2. Player picks up the placeholder/loading record → This event fires
 * 3. We cancel the download → No duplicate spawns when download finishes
 *
 * @author PsProfi
 */
@Mod.EventBusSubscriber(modid = "etchedytdlp", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RecordPickupHandler {

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        ItemStack stack = event.getItem().getItem();

        // Check if this item has NBT data
        if (!stack.hasTag()) {
            return;
        }

        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }

        // Try to get URL from various possible NBT keys
        String url = extractUrlFromNBT(tag);

        if (url != null && !url.isEmpty()) {
            // Check if this URL has an active download
            if (YtDlpSource.hasActiveDownload(url)) {
                System.out.println("[Etched YT-DLP] Player picked up record with active download: " + url);
                System.out.println("[Etched YT-DLP] Cancelling download to prevent duplicate spawn");

                // Cancel the download
                YtDlpSource.cancelDownload(url);
            }
        }
    }

    /**
     * Extracts URL from NBT data
     * Tries multiple common keys that Etched might use
     */
    private static String extractUrlFromNBT(CompoundTag tag) {
        // Try common NBT keys
        if (tag.contains("url")) {
            return tag.getString("url");
        }

        if (tag.contains("URL")) {
            return tag.getString("URL");
        }

        if (tag.contains("sound_url")) {
            return tag.getString("sound_url");
        }

        if (tag.contains("etched:url")) {
            return tag.getString("etched:url");
        }

        if (tag.contains("music_url")) {
            return tag.getString("music_url");
        }

        // Check if there's a nested "etched" or "music" compound tag
        if (tag.contains("etched")) {
            CompoundTag etchedTag = tag.getCompound("etched");
            if (etchedTag.contains("url")) {
                return etchedTag.getString("url");
            }
        }

        if (tag.contains("music")) {
            CompoundTag musicTag = tag.getCompound("music");
            if (musicTag.contains("url")) {
                return musicTag.getString("url");
            }
        }

        // Debug: Print all NBT keys if none of the expected ones are found
        // This helps us figure out what key Etched actually uses
        if (tag.getAllKeys().size() > 0) {
            boolean hasEtchedData = tag.getAllKeys().stream()
                    .anyMatch(key -> key.toLowerCase().contains("etched") ||
                            key.toLowerCase().contains("music") ||
                            key.toLowerCase().contains("sound"));

            if (hasEtchedData) {
                System.out.println("[Etched YT-DLP] Found record with NBT keys: " + tag.getAllKeys());
                System.out.println("[Etched YT-DLP] Full NBT: " + tag.toString());
            }
        }

        return null;
    }
}