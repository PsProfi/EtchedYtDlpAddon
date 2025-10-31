package com.psprofi.etchedytdlp.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active downloads to prevent duplicate record spawns when players
 * pick up records before they finish loading.
 *
 * This solves the race condition where:
 * 1. Player burns a record with a URL
 * 2. Download starts in background (takes 10-60 seconds)
 * 3. Player picks up the placeholder record
 * 4. Download completes and spawns a duplicate record
 *
 * With this tracker, when the player picks up the record early,
 * we can cancel the download and prevent the duplicate spawn.
 *
 * @author PsProfi
 */
public class DownloadTracker {

    // Maps download ID to context information
    private static final Map<UUID, DownloadContext> activeDownloads = new ConcurrentHashMap<>();

    /**
     * Context information for a download
     */
    public static class DownloadContext {
        private volatile boolean cancelled = false;
        private final String url;
        private final long startTime;

        public DownloadContext(String url) {
            this.url = url;
            this.startTime = System.currentTimeMillis();
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void cancel() {
            this.cancelled = true;
        }

        public String getUrl() {
            return url;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getElapsedTime() {
            return System.currentTimeMillis() - startTime;
        }
    }

    /**
     * Starts tracking a new download
     * @param url The URL being downloaded
     * @return Download ID to track this request
     */
    public static UUID startDownload(String url) {
        UUID downloadId = UUID.randomUUID();
        activeDownloads.put(downloadId, new DownloadContext(url));
        System.out.println("[Etched YT-DLP] Started download tracking: " + downloadId + " for " + url);
        return downloadId;
    }

    /**
     * Checks if a download was cancelled
     * @param downloadId The download ID
     * @return true if cancelled or not found
     */
    public static boolean isCancelled(UUID downloadId) {
        if (downloadId == null) {
            return false;
        }
        DownloadContext context = activeDownloads.get(downloadId);
        return context != null && context.isCancelled();
    }

    /**
     * Cancels a download (e.g., when player picks up the record early)
     * @param downloadId The download ID
     */
    public static void cancelDownload(UUID downloadId) {
        if (downloadId == null) {
            return;
        }

        DownloadContext context = activeDownloads.get(downloadId);
        if (context != null) {
            context.cancel();
            System.out.println("[Etched YT-DLP] Cancelled download: " + downloadId +
                    " (was running for " + context.getElapsedTime() + "ms)");
        }
    }

    /**
     * Completes a download and removes it from tracking
     * @param downloadId The download ID
     * @return true if download was not cancelled (i.e., completed successfully)
     */
    public static boolean completeDownload(UUID downloadId) {
        if (downloadId == null) {
            return true; // No tracking, assume success
        }

        DownloadContext context = activeDownloads.remove(downloadId);
        if (context == null) {
            System.out.println("[Etched YT-DLP] Download completed but not tracked: " + downloadId);
            return false;
        }

        boolean success = !context.isCancelled();
        String status = success ? "completed successfully" : "was cancelled";
        System.out.println("[Etched YT-DLP] Download " + status + ": " + downloadId +
                " (took " + context.getElapsedTime() + "ms)");
        return success;
    }

    /**
     * Gets the context for a download
     * @param downloadId The download ID
     * @return The download context, or null if not found
     */
    public static DownloadContext getContext(UUID downloadId) {
        return activeDownloads.get(downloadId);
    }

    /**
     * Cleanup old downloads (in case completion wasn't called)
     * Should be called periodically from a tick event or scheduler
     */
    public static void cleanupStaleDownloads() {
        long now = System.currentTimeMillis();
        int removed = 0;

        for (var iterator = activeDownloads.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            DownloadContext context = entry.getValue();

            // Remove downloads older than 10 minutes (600,000ms)
            if (now - context.getStartTime() > 600000) {
                iterator.remove();
                removed++;
                System.out.println("[Etched YT-DLP] Cleaned up stale download: " + entry.getKey() +
                        " for URL: " + context.getUrl());
            }
        }

        if (removed > 0) {
            System.out.println("[Etched YT-DLP] Cleanup removed " + removed + " stale download(s)");
        }
    }

    /**
     * Gets active download count (for debugging/monitoring)
     */
    public static int getActiveDownloadCount() {
        return activeDownloads.size();
    }

    /**
     * Gets all active download IDs (for debugging)
     */
    public static java.util.Set<UUID> getActiveDownloadIds() {
        return new java.util.HashSet<>(activeDownloads.keySet());
    }

    /**
     * Checks if there are any active downloads
     */
    public static boolean hasActiveDownloads() {
        return !activeDownloads.isEmpty();
    }

    /**
     * Clears all tracked downloads (for mod shutdown or emergencies)
     */
    public static void clear() {
        int count = activeDownloads.size();
        activeDownloads.clear();
        if (count > 0) {
            System.out.println("[Etched YT-DLP] Cleared " + count + " tracked download(s)");
        }
    }

    /**
     * Cancels all active downloads (emergency stop)
     */
    public static void cancelAll() {
        System.out.println("[Etched YT-DLP] Cancelling all active downloads...");
        for (DownloadContext context : activeDownloads.values()) {
            context.cancel();
        }
        System.out.println("[Etched YT-DLP] Cancelled " + activeDownloads.size() + " download(s)");
    }
}