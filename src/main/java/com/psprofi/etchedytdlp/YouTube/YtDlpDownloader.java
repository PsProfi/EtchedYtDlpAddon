package com.psprofi.etchedytdlp.YouTube;

import com.psprofi.etchedytdlp.core.DownloadTracker;
import gg.moonflower.etched.api.util.DownloadProgressListener;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Handles audio downloading and caching via yt-dlp with automatic format conversion
 * @author PsProfi
 */
public class YtDlpDownloader {

    private static final Path CACHE_DIR = Paths.get("ytdlp_tools", "ytdlp_cache");
    private static final Set<String> SUPPORTED_FORMATS = new HashSet<>();

    static {
        // Formats supported by Etched/Minecraft
        SUPPORTED_FORMATS.add("mp3");
        SUPPORTED_FORMATS.add("ogg");
        SUPPORTED_FORMATS.add("wav");

        try {
            if (!Files.exists(CACHE_DIR)) {
                Files.createDirectories(CACHE_DIR);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generates a hash for the URL to use as cache filename
     */
    private static String hashUrl(String url) {
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
            // Fallback to simple sanitization
            return url.replaceAll("[^a-zA-Z0-9]", "_");
        }
    }

    /**
     * Gets the cached file path for a URL
     */
    private static Path getCachedPath(String url, String extension) {
        String hash = hashUrl(url);
        return CACHE_DIR.resolve(hash + "." + extension);
    }

    /**
     * Checks if audio is already cached
     */
    public static boolean isCached(String url) {
        return Files.exists(getCachedPath(url, "mp3"));
    }

    /**
     * Gets the file extension from a path
     */
    private static String getFileExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Converts an audio file to mp3 format using FFmpeg
     */
    private static Path convertToMp3(Path inputFile, @Nullable DownloadProgressListener progressListener, @Nullable UUID downloadId) throws IOException {
        // Check if cancelled before conversion
        if (downloadId != null && DownloadTracker.isCancelled(downloadId)) {
            throw new IOException("Download cancelled before conversion");
        }

        if (progressListener != null) {
            progressListener.progressStartRequest(Component.translatable("etchedytdlp.progress.converting"));
        }

        String inputFormat = getFileExtension(inputFile);
        Path outputFile = inputFile.getParent().resolve(
                inputFile.getFileName().toString().replaceFirst("\\.[^.]+$", ".mp3")
        );

        // If already mp3, just return it
        if ("mp3".equals(inputFormat)) {
            return inputFile;
        }

        // If output already exists, delete input and return output
        if (Files.exists(outputFile)) {
            Files.deleteIfExists(inputFile);
            return outputFile;
        }

        Path ffmpegPath = YtDlpManager.getFfmpegPath();
        if (!Files.exists(ffmpegPath)) {
            throw new IOException("FFmpeg not found. Cannot convert audio format.");
        }

        // Build ffmpeg command for high-quality MP3
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath.toString());
        command.add("-i");
        command.add(inputFile.toString());
        command.add("-vn"); // No video
        command.add("-acodec");
        command.add("libmp3lame");
        command.add("-b:a");
        command.add("320k");
        command.add("-ar");
        command.add("44100");
        command.add("-ac");
        command.add("2"); // Stereo
        command.add("-y"); // Overwrite output file
        command.add(outputFile.toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        try {
            System.out.println("[Etched YT-DLP] Converting " + inputFormat + " to mp3...");
            Process process = pb.start();

            // Read output to prevent blocking
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Check periodically during conversion if cancelled
                    if (downloadId != null && DownloadTracker.isCancelled(downloadId)) {
                        process.destroy();
                        Files.deleteIfExists(outputFile);
                        throw new IOException("Download cancelled during conversion");
                    }
                }
            }

            boolean finished = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new IOException("FFmpeg conversion timed out after 120 seconds");
            }

            if (process.exitValue() != 0) {
                throw new IOException("FFmpeg conversion failed with exit code: " + process.exitValue());
            }

            if (!Files.exists(outputFile) || Files.size(outputFile) < 1000) {
                throw new IOException("Conversion produced invalid file");
            }

            // Delete original file after successful conversion
            Files.deleteIfExists(inputFile);

            System.out.println("[Etched YT-DLP] Successfully converted to mp3: " + outputFile.getFileName() +
                    " (" + (Files.size(outputFile) / 1024) + " KB)");

            return outputFile;

        } catch (InterruptedException e) {
            throw new IOException("Conversion interrupted", e);
        }
    }

    /**
     * Downloads audio from URL and caches it, with automatic format conversion
     * @param url The URL to download from
     * @param progressListener Optional progress listener
     * @param downloadId Optional download ID for cancellation tracking
     * @return Path to the cached audio file (always mp3)
     */
    public static Path downloadAudio(String url, @Nullable DownloadProgressListener progressListener, @Nullable UUID downloadId)
            throws IOException {

        // Check if cancelled before starting
        if (downloadId != null && DownloadTracker.isCancelled(downloadId)) {
            System.out.println("[Etched YT-DLP] Download cancelled before start: " + url);
            throw new IOException("Download cancelled before start");
        }

        YtDlpManager.ensureInstalled(progressListener);
        Path cachedFile = getCachedPath(url, "mp3");

        // Return cached file if exists
        if (Files.exists(cachedFile)) {
            if (progressListener != null) {
                progressListener.progressStartRequest(Component.translatable("etchedytdlp.progress.cached"));
            }
            System.out.println("[Etched YT-DLP] Using cached file for: " + url);
            return cachedFile;
        }

        if (progressListener != null) {
            progressListener.progressStartRequest(Component.translatable("etchedytdlp.progress.downloading"));
        }

        // Check again after initialization
        if (downloadId != null && DownloadTracker.isCancelled(downloadId)) {
            System.out.println("[Etched YT-DLP] Download cancelled during setup: " + url);
            throw new IOException("Download cancelled during setup");
        }

        String urlHash = hashUrl(url);
        String outputTemplate = CACHE_DIR.resolve(urlHash).toString();

        List<String> args = new ArrayList<>();

        // Extract audio only
        args.add("-x");

        // Specify format to convert to
        args.add("--audio-format");
        args.add("mp3");

        // Best audio quality
        args.add("--audio-quality");
        args.add("0");

        // Don't download playlists
        args.add("--no-playlist");

        // Don't preserve file modification time
        args.add("--no-mtime");

        // Prefer ffmpeg for post-processing
        args.add("--prefer-ffmpeg");

        // Select best audio format
        args.add("-f");
        args.add("bestaudio/best");

        // Post-processor args for high quality MP3
        args.add("--postprocessor-args");
        args.add("ffmpeg:-acodec libmp3lame -b:a 320k -ar 44100");

        args.add("--cache-dir");
        args.add(CACHE_DIR.resolve("metadata_cache").toString());
        args.add("--no-check-certificates");
        args.add("--no-warnings");
        args.add("--quiet");
        args.add("--ignore-errors");
        args.add("--skip-unavailable-fragments");


        // Output template (yt-dlp will add extension)
        args.add("-o");
        args.add(outputTemplate + ".%(ext)s");

        // The URL to download
        args.add(url);

        try {
            YtDlpManager.executeWithProgress(args, 600, progressListener);
        } catch (IOException e) {
            // If download failed, clean up and rethrow
            System.err.println("[Etched YT-DLP] Download failed: " + e.getMessage());
            throw new IOException("Failed to download audio: " + e.getMessage(), e);
        }

        // Wait for download to complete (check for .part files) with periodic cancellation checks
        for (int i = 0; i < 5; i++) {
            if (Files.exists(cachedFile)) break;

        // Check if cancelled during wait
            if (downloadId != null && DownloadTracker.isCancelled(downloadId)) {
                System.out.println("[Etched YT-DLP] Download cancelled during processing, cleaning up...");
                cleanupPartialDownload(urlHash);
                throw new IOException("Download cancelled");
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}

            // Check if .part file exists (download in progress)
            boolean hasPartFile = Files.list(CACHE_DIR)
                    .anyMatch(p -> p.getFileName().toString().startsWith(urlHash) &&
                            p.getFileName().toString().endsWith(".part"));

            if (!hasPartFile && Files.exists(cachedFile)) {
                break;
            }
        }

        // If mp3 doesn't exist, find downloaded file and convert if necessary
        if (!Files.exists(cachedFile)) {
            // Check one more time before conversion
            if (downloadId != null && DownloadTracker.isCancelled(downloadId)) {
                System.out.println("[Etched YT-DLP] Download cancelled before file processing");
                cleanupPartialDownload(urlHash);
                throw new IOException("Download cancelled before conversion");
            }

            Path downloadedFile = findDownloadedFile(urlHash);

            if (downloadedFile == null) {
                throw new IOException("Download failed: No audio file was created. Check yt-dlp logs.");
            }

            String extension = getFileExtension(downloadedFile);
            System.out.println("[Etched YT-DLP] Found downloaded file: " + downloadedFile.getFileName() + " (format: " + extension + ")");

            // Check if format needs conversion
            if (!"mp3".equals(extension)) {
                System.out.println("[Etched YT-DLP] Format is not MP3, converting...");
                downloadedFile = convertToMp3(downloadedFile, progressListener, downloadId);
            }

            // Final check before moving to cache
            if (downloadId != null && DownloadTracker.isCancelled(downloadId)) {
                System.out.println("[Etched YT-DLP] Download cancelled after conversion");
                Files.deleteIfExists(downloadedFile);
                throw new IOException("Download cancelled after conversion");
            }

            // Move to final location if needed
            if (!downloadedFile.equals(cachedFile)) {
                Files.move(downloadedFile, cachedFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // Final check before returning
        if (downloadId != null && DownloadTracker.isCancelled(downloadId)) {
            System.out.println("[Etched YT-DLP] Download cancelled, removing completed file");
            // Delete the file we just created since it was cancelled
            Files.deleteIfExists(cachedFile);
            throw new IOException("Download cancelled after completion");
        }

        if (!Files.exists(cachedFile)) {
            throw new IOException("Download incomplete: MP3 file not created. Check ffmpeg installation.");
        }

        // Validate the final file
        validateAudioFile(cachedFile);

        System.out.println("[Etched YT-DLP] Successfully downloaded and cached: " + url);
        return cachedFile;
    }

    /**
     * Cleans up partial downloads for a given URL hash
     */
    private static void cleanupPartialDownload(String urlHash) {
        try {
            Files.list(CACHE_DIR)
                    .filter(p -> p.getFileName().toString().startsWith(urlHash))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                            System.out.println("[Etched YT-DLP] Cleaned up partial file: " + p.getFileName());
                        } catch (IOException e) {
                            System.err.println("[Etched YT-DLP] Failed to cleanup file: " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("[Etched YT-DLP] Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Finds the downloaded file with the given hash prefix
     */
    private static Path findDownloadedFile(String hashPrefix) throws IOException {
        try (var files = Files.list(CACHE_DIR)) {
            return files
                    .filter(f -> f.getFileName().toString().startsWith(hashPrefix))
                    .filter(f -> !f.toString().endsWith(".part"))
                    .filter(f -> !f.toString().endsWith(".ytdl"))
                    .filter(f -> !f.toString().endsWith("_debug.txt"))
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Validates that an audio file has proper headers
     */
    private static void validateAudioFile(Path audioFile) throws IOException {
        if (Files.size(audioFile) < 1000) {
            throw new IOException("Audio file too small (< 1KB), likely corrupted");
        }

        String extension = getFileExtension(audioFile);
        byte[] header = new byte[12];

        try (var in = Files.newInputStream(audioFile)) {
            int read = in.read(header);
            if (read < 10) {
                throw new IOException("Audio file too short");
            }
        }

        boolean valid = false;

        switch (extension) {
            case "mp3":
                // Check for ID3 tag or MP3 frame sync
                boolean hasId3 = (header[0] == 'I' && header[1] == 'D' && header[2] == '3');
                boolean hasMp3Sync = ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0);
                valid = hasId3 || hasMp3Sync;
                if (!valid) {
                    System.err.println("[Etched YT-DLP] Warning: MP3 file may be corrupted (invalid headers)");
                }
                break;

            case "ogg":
                // Check for OGG header "OggS"
                valid = (header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S');
                if (!valid) {
                    System.err.println("[Etched YT-DLP] Warning: OGG file may be corrupted (invalid headers)");
                }
                break;

            case "wav":
                // Check for RIFF header
                valid = (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F');
                if (!valid) {
                    System.err.println("[Etched YT-DLP] Warning: WAV file may be corrupted (invalid headers)");
                }
                break;

            default:
                System.err.println("[Etched YT-DLP] Warning: Unknown format, skipping validation: " + extension);
                return;
        }
    }

    /**
     * Downloads video thumbnail/album art
     * @param url The URL to get thumbnail from
     * @param progressListener Optional progress listener
     * @return Path to the cached thumbnail file, or null if not available
     */
    @Nullable
    public static Path downloadThumbnail(String url, @Nullable DownloadProgressListener progressListener) throws IOException {
        YtDlpManager.ensureInstalled(progressListener);

        Path cachedFile = getCachedPath(url, "jpg");

        if (Files.exists(cachedFile)) {
            return cachedFile;
        }

        String urlHash = hashUrl(url);
        String outputTemplate = CACHE_DIR.resolve(urlHash).toString() + ".%(ext)s";

        List<String> args = new ArrayList<>();
        args.add("--write-thumbnail");
        args.add("--skip-download");
        args.add("--convert-thumbnails");
        args.add("jpg");
        args.add("--no-warnings");
        args.add("-o");
        args.add(outputTemplate);
        args.add(url);

        try {
            YtDlpManager.execute(args, 30);

            if (Files.exists(cachedFile)) {
                return cachedFile;
            }
        } catch (IOException e) {
            // Thumbnail download failed, not critical
        }

        return null;
    }

    /**
     * Clears the entire cache
     */
    public static void clearCache() throws IOException {
        if (Files.exists(CACHE_DIR)) {
            Files.walk(CACHE_DIR)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    /**
     * Clears cache for a specific URL
     */
    public static void clearCacheForUrl(String url) throws IOException {
        String hash = hashUrl(url);

        // Delete all files with this hash
        try (var files = Files.list(CACHE_DIR)) {
            files.filter(f -> f.getFileName().toString().startsWith(hash))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    /**
     * Gets the cache directory
     */
    public static Path getCacheDirectory() {
        return CACHE_DIR;
    }

    /**
     * Gets the total size of cached files in bytes
     */
    public static long getCacheSize() throws IOException {
        if (!Files.exists(CACHE_DIR)) {
            return 0;
        }

        return Files.walk(CACHE_DIR)
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
    }
}