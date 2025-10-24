package com.psprofi.etchedytdlp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.moonflower.etched.api.record.TrackData;
import gg.moonflower.etched.api.sound.download.SoundDownloadSource;
import gg.moonflower.etched.api.util.DownloadProgressListener;
import gg.moonflower.etched.core.Etched;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * YT-DLP based sound source supporting YouTube, SoundCloud, Spotify and 1000+ sites
 * @author YourName
 */
public class YtDlpSource implements SoundDownloadSource {

    private static final Component BRAND = Component.translatable("sound_source." + Etched.MOD_ID + ".ytdlp")
            .withStyle(style -> style.withColor(TextColor.fromRgb(0xFF0000)));

    private static final String YTDLP_VERSION = "2024.10.22";
    private static final Path TOOLS_DIR = Paths.get("tools");
    private static final Path YTDLP_PATH = TOOLS_DIR.resolve(getYtDlpExecutableName());
    private static final Path CACHE_DIR = TOOLS_DIR.resolve("ytdlp_cache");

    private final Map<String, Boolean> validCache = new WeakHashMap<>();
    private boolean ytdlpInitialized = false;

    private static String getYtDlpExecutableName() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? "yt-dlp.exe" : "yt-dlp";
    }

    private static String getYtDlpDownloadUrl() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "https://github.com/yt-dlp/yt-dlp/releases/download/" + YTDLP_VERSION + "/yt-dlp.exe";
        } else if (os.contains("mac")) {
            return "https://github.com/yt-dlp/yt-dlp/releases/download/" + YTDLP_VERSION + "/yt-dlp_macos";
        }
        return "https://github.com/yt-dlp/yt-dlp/releases/download/" + YTDLP_VERSION + "/yt-dlp";
    }

    private String hashUrl(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return url.replaceAll("[^a-zA-Z0-9]", "_");
        }
    }

    private synchronized void ensureYtDlpInstalled(@Nullable DownloadProgressListener progressListener) throws IOException {
        if (ytdlpInitialized && Files.exists(YTDLP_PATH)) {
            return;
        }

        if (!Files.exists(TOOLS_DIR)) {
            Files.createDirectories(TOOLS_DIR);
        }

        if (!Files.exists(CACHE_DIR)) {
            Files.createDirectories(CACHE_DIR);
        }

        if (!Files.exists(YTDLP_PATH)) {
            if (progressListener != null) {
                progressListener.progressStartRequest(Component.literal("Downloading yt-dlp... (one-time setup)"));
            }

            downloadYtDlp();

            // Make executable on Unix systems
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                YTDLP_PATH.toFile().setExecutable(true);
            }
        }

        ytdlpInitialized = true;
    }

    private void downloadYtDlp() throws IOException {
        String downloadUrl = getYtDlpDownloadUrl();
        try (var in = new URL(downloadUrl).openStream()) {
            Files.copy(in, YTDLP_PATH);
        }
    }

    private JsonObject getVideoInfo(String url, @Nullable DownloadProgressListener progressListener) throws IOException {
        ensureYtDlpInstalled(progressListener);

        if (progressListener != null) {
            progressListener.progressStartRequest(Component.literal("Fetching track information..."));
        }

        List<String> command = new ArrayList<>();
        command.add(YTDLP_PATH.toString());
        command.add("--dump-json");
        command.add("--no-playlist");
        command.add("--no-warnings");
        command.add(url);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            while ((line = errorReader.readLine()) != null) {
                error.append(line).append("\n");
            }

            process.waitFor(30, TimeUnit.SECONDS);

            if (process.exitValue() != 0) {
                throw new IOException("yt-dlp failed: " + error.toString());
            }
        } catch (InterruptedException e) {
            process.destroy();
            throw new IOException("yt-dlp process interrupted", e);
        }

        String jsonOutput = output.toString().trim();
        if (jsonOutput.isEmpty()) {
            throw new IOException("No data returned from yt-dlp");
        }

        return JsonParser.parseString(jsonOutput).getAsJsonObject();
    }

    private String downloadAudio(String url, @Nullable DownloadProgressListener progressListener) throws IOException {
        ensureYtDlpInstalled(progressListener);

        // Check if already cached
        String urlHash = hashUrl(url);
        Path cachedFile = CACHE_DIR.resolve(urlHash + ".mp3");

        if (Files.exists(cachedFile)) {
            if (progressListener != null) {
                progressListener.progressStartRequest(Component.literal("Using cached audio..."));
            }
            return cachedFile.toUri().toString();
        }

        if (progressListener != null) {
            progressListener.progressStartRequest(Component.literal("Downloading audio..."));
        }

        String outputTemplate = CACHE_DIR.resolve(urlHash).toString() + ".%(ext)s";

        List<String> command = new ArrayList<>();
        command.add(YTDLP_PATH.toString());
        command.add("-x"); // Extract audio
        command.add("--audio-format");
        command.add("mp3");
        command.add("--audio-quality");
        command.add("0"); // Best quality
        command.add("--no-playlist");
        command.add("--no-warnings");
        command.add("--no-mtime");
        command.add("-o");
        command.add(outputTemplate);
        command.add(url);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        Process process = pb.start();

        StringBuilder error = new StringBuilder();

        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                error.append(line).append("\n");

                // Parse progress if available
                if (progressListener != null && line.contains("[download]") && line.contains("%")) {
                    // Progress updates for user feedback
                }
            }

            process.waitFor(300, TimeUnit.SECONDS); // 5 minute timeout

            if (process.exitValue() != 0) {
                throw new IOException("yt-dlp download failed: " + error.toString());
            }
        } catch (InterruptedException e) {
            process.destroy();
            throw new IOException("yt-dlp process interrupted", e);
        }

        if (!Files.exists(cachedFile)) {
            throw new IOException("Downloaded file not found at: " + cachedFile);
        }

        return cachedFile.toUri().toString();
    }

    @Override
    public List<URL> resolveUrl(String url, @Nullable DownloadProgressListener progressListener, Proxy proxy) throws IOException {
        String audioUrl = downloadAudio(url, progressListener);
        return Collections.singletonList(new URL(audioUrl));
    }

    @Override
    public List<TrackData> resolveTracks(String url, @Nullable DownloadProgressListener progressListener, Proxy proxy) throws IOException {
        JsonObject info = getVideoInfo(url, progressListener);

        String title = GsonHelper.getAsString(info, "title", "Unknown Title");
        String artist = "Unknown Artist";

        // Try to get artist from different fields in order of preference
        if (info.has("artist") && !info.get("artist").isJsonNull()) {
            artist = GsonHelper.getAsString(info, "artist");
        } else if (info.has("uploader") && !info.get("uploader").isJsonNull()) {
            artist = GsonHelper.getAsString(info, "uploader");
        } else if (info.has("channel") && !info.get("channel").isJsonNull()) {
            artist = GsonHelper.getAsString(info, "channel");
        } else if (info.has("creator") && !info.get("creator").isJsonNull()) {
            artist = GsonHelper.getAsString(info, "creator");
        }

        return Collections.singletonList(new TrackData(url, artist, Component.literal(title)));
    }

    @Override
    public Optional<String> resolveAlbumCover(String url, @Nullable DownloadProgressListener progressListener, Proxy proxy, ResourceManager resourceManager) throws IOException {
        try {
            JsonObject info = getVideoInfo(url, progressListener);

            // Try to get thumbnail - yt-dlp provides the best quality one
            if (info.has("thumbnail") && !info.get("thumbnail").isJsonNull()) {
                return Optional.of(GsonHelper.getAsString(info, "thumbnail"));
            }
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
                if (host.contains("youtube.com") || host.contains("youtu.be")) {
                    return Optional.of(Component.literal("YouTube").withStyle(style -> style.withColor(TextColor.fromRgb(0xFF0000))));
                } else if (host.contains("soundcloud.com")) {
                    return Optional.of(Component.literal("SoundCloud").withStyle(style -> style.withColor(TextColor.fromRgb(0xFF5500))));
                } else if (host.contains("spotify.com")) {
                    return Optional.of(Component.literal("Spotify").withStyle(style -> style.withColor(TextColor.fromRgb(0x1DB954))));
                }
            }
        } catch (URISyntaxException e) {
            // Fall through to default
        }
        return Optional.of(BRAND);
    }
}