package com.psprofi.etchedytdlp.YouTube;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Optional utility to auto-update yt-dlp binary when it gets too old
 * This helps prevent YouTube 403 errors from outdated versions
 * @author PsProfi
 */
public class YtDlpUpdater {

    private static final Path YTDLP_PATH = Paths.get("ytdlp_tools", "yt-dlp");
    private static final Path YTDLP_PATH_EXE = Paths.get("ytdlp_tools", "yt-dlp.exe");
    private static final int MAX_AGE_DAYS = 30; // Force update after 30 days

    /**
     * Checks if yt-dlp is outdated and deletes it if necessary
     * Call this on mod initialization
     */
    public static void checkAndUpdateIfNeeded() {
        try {
            Path ytdlpFile = Files.exists(YTDLP_PATH) ? YTDLP_PATH :
                    Files.exists(YTDLP_PATH_EXE) ? YTDLP_PATH_EXE : null;

            if (ytdlpFile == null) {
                return; // Not installed yet, will be downloaded on first use
            }

            // Check file age
            FileTime lastModified = Files.getLastModifiedTime(ytdlpFile);
            Instant modifiedInstant = lastModified.toInstant();
            Instant now = Instant.now();
            long daysSinceModified = ChronoUnit.DAYS.between(modifiedInstant, now);

            if (daysSinceModified > MAX_AGE_DAYS) {
                System.out.println("[Etched YT-DLP] yt-dlp binary is " + daysSinceModified + " days old. Updating...");
                Files.delete(ytdlpFile);
                System.out.println("[Etched YT-DLP] Old binary deleted. Will re-download latest version on next use.");
            }
        } catch (IOException e) {
            // Silently fail - not critical
            System.err.println("[Etched YT-DLP] Failed to check yt-dlp version: " + e.getMessage());
        }
    }

    /**
     * Forces an update by deleting the current binary
     * Useful when 403 errors occur
     */
    public static void forceUpdate() throws IOException {
        Path ytdlpFile = Files.exists(YTDLP_PATH) ? YTDLP_PATH :
                Files.exists(YTDLP_PATH_EXE) ? YTDLP_PATH_EXE : null;

        if (ytdlpFile != null && Files.exists(ytdlpFile)) {
            Files.delete(ytdlpFile);
            System.out.println("[Etched YT-DLP] yt-dlp binary deleted. Will re-download latest version.");
        }
    }

    /**
     * Gets the age of the current yt-dlp binary in days
     */
    public static long getAgeDays() {
        try {
            Path ytdlpFile = Files.exists(YTDLP_PATH) ? YTDLP_PATH :
                    Files.exists(YTDLP_PATH_EXE) ? YTDLP_PATH_EXE : null;

            if (ytdlpFile == null || !Files.exists(ytdlpFile)) {
                return -1; // Not installed
            }

            FileTime lastModified = Files.getLastModifiedTime(ytdlpFile);
            Instant modifiedInstant = lastModified.toInstant();
            Instant now = Instant.now();
            return ChronoUnit.DAYS.between(modifiedInstant, now);
        } catch (IOException e) {
            return -1;
        }
    }
}