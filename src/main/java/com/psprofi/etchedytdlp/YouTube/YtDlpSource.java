package com.psprofi.etchedytdlp.YouTube;

import com.google.gson.JsonObject;
import com.psprofi.etchedytdlp.LocalAudioServer;
import gg.moonflower.etched.api.record.TrackData;
import gg.moonflower.etched.api.sound.download.SoundDownloadSource;
import gg.moonflower.etched.api.util.DownloadProgressListener;
import gg.moonflower.etched.core.Etched;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

/**
 * YT-DLP based sound source supporting YouTube, SoundCloud, Spotify and 1000+ sites
 * @author PsProfi
 */
public class YtDlpSource implements SoundDownloadSource {

    private static final Component BRAND = Component.translatable("sound_source." + Etched.MOD_ID + ".ytdlp")
            .withStyle(style -> style.withColor(TextColor.fromRgb(0xFF0000)));

    private final Map<String, Boolean> validCache = new WeakHashMap<>();

    @Override
    public List<URL> resolveUrl(String url, @Nullable DownloadProgressListener progressListener, Proxy proxy) throws IOException {
        // Download and cache the audio
        Path audioFile = YtDlpDownloader.downloadAudio(url, progressListener);

        // Register file with local HTTP server and get HTTP URL
        // Etched expects HTTP URLs, not file:// URLs
        String httpUrl = LocalAudioServer.registerFile(audioFile);

        return Collections.singletonList(new URL(httpUrl));
    }

    @Override
    public List<TrackData> resolveTracks(String url, @Nullable DownloadProgressListener progressListener, Proxy proxy) throws IOException {
        // Get video/track information
        JsonObject info = YtDlpManager.getInfo(url, true); // true = no playlist

        String title = GsonHelper.getAsString(info, "title", "Unknown Title");
        String artist = extractArtist(info);

        return Collections.singletonList(new TrackData(url, artist, Component.literal(title)));
    }

    /**
     * Extracts artist name from video info, trying multiple fields
     */
    private String extractArtist(JsonObject info) {
        // Try different fields in order of preference
        if (info.has("artist") && !info.get("artist").isJsonNull()) {
            return GsonHelper.getAsString(info, "artist");
        }
        if (info.has("uploader") && !info.get("uploader").isJsonNull()) {
            return GsonHelper.getAsString(info, "uploader");
        }
        if (info.has("channel") && !info.get("channel").isJsonNull()) {
            return GsonHelper.getAsString(info, "channel");
        }
        if (info.has("creator") && !info.get("creator").isJsonNull()) {
            return GsonHelper.getAsString(info, "creator");
        }

        return "Unknown Artist";
    }

    @Override
    public Optional<String> resolveAlbumCover(String url, @Nullable DownloadProgressListener progressListener, Proxy proxy, ResourceManager resourceManager) throws IOException {
        try {
            JsonObject info = YtDlpManager.getInfo(url, true);

            // Try to get thumbnail URL from metadata
            if (info.has("thumbnail") && !info.get("thumbnail").isJsonNull()) {
                return Optional.of(GsonHelper.getAsString(info, "thumbnail"));
            }

            // Alternative: download thumbnail locally
            // Path thumbnail = YtDlpDownloader.downloadThumbnail(url, progressListener);
            // if (thumbnail != null) {
            //     return Optional.of(thumbnail.toUri().toString());
            // }
        } catch (Exception e) {
            // Silently fail for album art - not critical
        }
        return Optional.empty();
    }

    @Override
    public boolean isValidUrl(String url) {
        return validCache.computeIfAbsent(url, key -> {
            try {
                URI uri = new URI(key);
                String host = uri.getHost();
                if (host == null) {
                    return false;
                }

                host = host.toLowerCase();

                // Support common sites - yt-dlp supports 1800+ sites
                // You can add more sites here or remove this check entirely
                // to support all yt-dlp sites
                return host.contains("youtube.com") ||
                        host.contains("youtu.be") ||
                        host.contains("soundcloud.com") ||
                        host.contains("spotify.com") ||
                        host.contains("bandcamp.com") ||
                        host.contains("vimeo.com") ||
                        host.contains("twitch.tv") ||
                        host.contains("dailymotion.com") ||
                        host.contains("twitter.com") ||
                        host.contains("x.com") ||
                        host.contains("tiktok.com") ||
                        host.contains("reddit.com") ||
                        host.contains("instagram.com");
            } catch (URISyntaxException e) {
                return false;
            }
        });
    }

    @Override
    public boolean isTemporary(String url) {
        // Audio is cached locally, not temporary
        return false;
    }

    @Override
    public String getApiName() {
        return "YT-DLP";
    }

    @Override
    public Optional<Component> getBrandText(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host != null) {
                host = host.toLowerCase();

                // Return platform-specific branding
                if (host.contains("youtube.com") || host.contains("youtu.be")) {
                    return Optional.of(Component.literal("YouTube")
                            .withStyle(style -> style.withColor(TextColor.fromRgb(0xFF0000))));
                } else if (host.contains("soundcloud.com")) {
                    return Optional.of(Component.literal("SoundCloud")
                            .withStyle(style -> style.withColor(TextColor.fromRgb(0xFF5500))));
                } else if (host.contains("spotify.com")) {
                    return Optional.of(Component.literal("Spotify")
                            .withStyle(style -> style.withColor(TextColor.fromRgb(0x1DB954))));
                } else if (host.contains("bandcamp.com")) {
                    return Optional.of(Component.literal("Bandcamp")
                            .withStyle(style -> style.withColor(TextColor.fromRgb(0x629AA9))));
                } else if (host.contains("twitch.tv")) {
                    return Optional.of(Component.literal("Twitch")
                            .withStyle(style -> style.withColor(TextColor.fromRgb(0x9146FF))));
                }
            }
        } catch (URISyntaxException e) {
            // Fall through to default
        }

        return Optional.of(BRAND);
    }
}