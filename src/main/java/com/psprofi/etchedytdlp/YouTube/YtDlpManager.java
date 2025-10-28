package com.psprofi.etchedytdlp.YouTube;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.moonflower.etched.api.util.DownloadProgressListener;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages yt-dlp binary installation and execution
 * @author PsProfi
 */
public class YtDlpManager {

    private static final String YTDLP_VERSION = "2025.10.22";  // IMPORTANT: Update regularly for YouTube fixes
    private static final Path TOOLS_DIR = Paths.get("tools");
    private static final Path YTDLP_PATH = TOOLS_DIR.resolve(getExecutableName("yt-dlp"));
    private static final Path FFMPEG_PATH = TOOLS_DIR.resolve(getExecutableName("ffmpeg"));
    private static final Path FFPROBE_PATH = TOOLS_DIR.resolve(getExecutableName("ffprobe"));

    private static boolean initialized = false;

    private static String getExecutableName(String baseName) {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? baseName + ".exe" : baseName;
    }

    private static String getDownloadUrl() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "https://github.com/yt-dlp/yt-dlp/releases/download/" + YTDLP_VERSION + "/yt-dlp.exe";
        } else if (os.contains("mac")) {
            return "https://github.com/yt-dlp/yt-dlp/releases/download/" + YTDLP_VERSION + "/yt-dlp_macos";
        }
        return "https://github.com/yt-dlp/yt-dlp/releases/download/" + YTDLP_VERSION + "/yt-dlp";
    }

    private static String getFfmpegDownloadUrl() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        if (os.contains("win")) {
            // Windows x64
            return "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip";
        } else if (os.contains("mac")) {
            // macOS - use static builds
            if (arch.contains("aarch64") || arch.contains("arm")) {
                return "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-macos-arm64-gpl.zip";
            }
            return "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-macos-amd64-gpl.zip";
        } else {
            // Linux
            return "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz";
        }
    }

    /**
     * Ensures yt-dlp is installed and ready to use
     */
    public static synchronized void ensureInstalled(@Nullable DownloadProgressListener progressListener) throws IOException {
        if (initialized && Files.exists(YTDLP_PATH) && Files.exists(FFMPEG_PATH) && Files.exists(FFPROBE_PATH)) {
            return;
        }

        if (!Files.exists(TOOLS_DIR)) {
            Files.createDirectories(TOOLS_DIR);
        }

        // Download yt-dlp
        if (!Files.exists(YTDLP_PATH)) {
            if (progressListener != null) {
                progressListener.progressStartRequest(Component.literal("Downloading yt-dlp... (one-time setup)"));
            }

            downloadBinary(getDownloadUrl(), YTDLP_PATH);

            // Make executable on Unix systems
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                YTDLP_PATH.toFile().setExecutable(true);
            }
        }

        // Download ffmpeg and ffprobe if not present
        if (!Files.exists(FFMPEG_PATH) || !Files.exists(FFPROBE_PATH)) {
            if (progressListener != null) {
                progressListener.progressStartRequest(Component.literal("Downloading ffmpeg... (one-time setup)"));
            }

            downloadFfmpeg();
        }

        initialized = true;
    }

    private static void downloadBinary(String url, Path destination) throws IOException {
        try (var in = new URL(url).openStream()) {
            Files.copy(in, destination);
        }
    }

    private static void downloadFfmpeg() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        String ffmpegUrl = getFfmpegDownloadUrl();

        // Download to temporary file
        Path tempArchive = TOOLS_DIR.resolve("ffmpeg_temp" + (os.contains("win") ? ".zip" : ".tar.xz"));

        try (var in = new URL(ffmpegUrl).openStream()) {
            Files.copy(in, tempArchive);
        }

        // Extract ffmpeg and ffprobe from archive
        try {
            if (os.contains("win")) {
                extractZip(tempArchive);
            } else {
                extractTarXz(tempArchive);
            }
        } finally {
            // Clean up temp file
            Files.deleteIfExists(tempArchive);
        }

        // Make executable on Unix
        if (!os.contains("win")) {
            FFMPEG_PATH.toFile().setExecutable(true);
            FFPROBE_PATH.toFile().setExecutable(true);
        }
    }

    private static void extractZip(Path zipFile) throws IOException {
        // Simple ZIP extraction for Windows
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // Extract ffmpeg.exe and ffprobe.exe
                if (name.endsWith("ffmpeg.exe")) {
                    Files.copy(zis, FFMPEG_PATH);
                } else if (name.endsWith("ffprobe.exe")) {
                    Files.copy(zis, FFPROBE_PATH);
                }

                zis.closeEntry();

                // Stop if we have both files
                if (Files.exists(FFMPEG_PATH) && Files.exists(FFPROBE_PATH)) {
                    break;
                }
            }
        }
    }

    private static void extractTarXz(Path tarFile) throws IOException {
        // For Linux/Mac, try to use tar command
        String os = System.getProperty("os.name").toLowerCase();

        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (os.contains("mac")) {
                // macOS might need to install xz tools
                pb.command("tar", "-xf", tarFile.toString(), "-C", TOOLS_DIR.toString(),
                        "--strip-components=2", "*/bin/ffmpeg", "*/bin/ffprobe");
            } else {
                // Linux
                pb.command("tar", "-xJf", tarFile.toString(), "-C", TOOLS_DIR.toString(),
                        "--strip-components=2", "--wildcards", "*/bin/ffmpeg", "*/bin/ffprobe");
            }

            Process process = pb.start();
            process.waitFor(60, TimeUnit.SECONDS);

            if (process.exitValue() != 0) {
                throw new IOException("Failed to extract ffmpeg archive");
            }
        } catch (InterruptedException e) {
            throw new IOException("Extraction interrupted", e);
        }
    }

    /**
     * Executes yt-dlp with given arguments and returns the output
     */
    public static String execute(List<String> args, int timeoutSeconds) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(YTDLP_PATH.toString());
        addAntiBlockingArgs(command);
        command.addAll(args);

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

            process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (process.exitValue() != 0) {
                throw new IOException("yt-dlp failed: " + error.toString());
            }
        } catch (InterruptedException e) {
            process.destroy();
            throw new IOException("yt-dlp process interrupted", e);
        }

        return output.toString();
    }

    /**
     * Adds common anti-blocking arguments to command
     */
    private static void addAntiBlockingArgs(List<String> args) {
        // Use modern user agent to avoid 403 errors
        args.add("--user-agent");
        args.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // Add more headers
        args.add("--add-header");
        args.add("Accept:text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

        args.add("--add-header");
        args.add("Accept-Language:en-us,en;q=0.5");

        // Use cookies if available (helps with YouTube 403 errors)
        Path cookiesPath = TOOLS_DIR.resolve("cookies.txt");
        if (Files.exists(cookiesPath)) {
            args.add("--cookies");
            args.add(cookiesPath.toString());
        }

        // Specify ffmpeg location (our bundled version)
        if (Files.exists(FFMPEG_PATH)) {
            args.add("--ffmpeg-location");
            args.add(FFMPEG_PATH.toString());
        }
    }

    /**
     * Executes yt-dlp with progress monitoring
     */
    public static void executeWithProgress(List<String> args, int timeoutSeconds, @Nullable DownloadProgressListener progressListener) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(YTDLP_PATH.toString());
        addAntiBlockingArgs(command);
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        Process process = pb.start();

        StringBuilder error = new StringBuilder();

        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                error.append(line).append("\n");

                // Parse progress for user feedback
                if (progressListener != null && line.contains("[download]") && line.contains("%")) {
                    // Could parse percentage here and update progress
                }
            }

            process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (process.exitValue() != 0) {
                throw new IOException("yt-dlp download failed: " + error.toString());
            }
        } catch (InterruptedException e) {
            process.destroy();
            throw new IOException("yt-dlp process interrupted", e);
        }
    }

    /**
     * Gets video/audio information as JSON
     */
    public static JsonObject getInfo(String url, boolean noPlaylist) throws IOException {
        List<String> args = new ArrayList<>();
        args.add("--dump-json");
        args.add("--no-warnings");
        if (noPlaylist) {
            args.add("--no-playlist");
        }
        args.add(url);

        String output = execute(args, 30);

        String jsonOutput = output.trim();
        if (jsonOutput.isEmpty()) {
            throw new IOException("No data returned from yt-dlp");
        }

        return JsonParser.parseString(jsonOutput).getAsJsonObject();
    }

    /**
     * Gets the yt-dlp installation path
     */
    public static Path getInstallPath() {
        return YTDLP_PATH;
    }

    /**
     * Checks if yt-dlp is installed
     */
    public static boolean isInstalled() {
        return Files.exists(YTDLP_PATH);
    }

    /**
     * Checks if ffmpeg is installed
     */
    public static boolean isFfmpegInstalled() {
        return Files.exists(FFMPEG_PATH) && Files.exists(FFPROBE_PATH);
    }

    /**
     * Checks if all required tools are installed
     */
    public static boolean isFullyInstalled() {
        return isInstalled() && isFfmpegInstalled();
    }

    /**
     * Gets the FFmpeg executable path (for direct use in conversions)
     */
    public static Path getFfmpegPath() {
        return FFMPEG_PATH;
    }
}