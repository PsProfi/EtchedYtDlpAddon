package com.psprofi.etchedytdlp.core;

import com.psprofi.etchedytdlp.YouTube.YtDlpDownloader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;

/**
 * Configuration screen for managing audio cache
 * Allows players to view and clear cached songs
 *
 * @author PsProfi
 */
@OnlyIn(Dist.CLIENT)
public class CacheManagerScreen extends Screen {

    private final Screen parent;
    private String cacheInfo = "Loading...";
    private long cacheSize = 0;
    private int fileCount = 0;
    private boolean cleared = false;

    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.00");

    public CacheManagerScreen(Screen parent) {
        super(Component.translatable("etchedytdlp.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // Refresh cache info
        refreshCacheInfo();

        int buttonWidth = 200;
        int buttonHeight = 20;
        int centerX = this.width / 2;
        int startY = this.height / 2 - 20;

        // Clear Cache button
        this.addRenderableWidget(Button.builder(
                Component.translatable("etchedytdlp.button.clear_all"),
                button -> clearCache()
        ).bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());

        // Clear Old Cache (30+ days)
        this.addRenderableWidget(Button.builder(
                Component.translatable("etchedytdlp.button.clear_old"),
                button -> clearOldCache(30)
        ).bounds(centerX - buttonWidth / 2, startY + 25, buttonWidth, buttonHeight).build());

        // Refresh button
        this.addRenderableWidget(Button.builder(
                Component.translatable("etchedytdlp.button.refresh"),
                button -> {
                    cleared = false;
                    refreshCacheInfo();
                }
        ).bounds(centerX - buttonWidth / 2, startY + 50, buttonWidth, buttonHeight).build());

        // Open Cache Folder button
        this.addRenderableWidget(Button.builder(
                Component.translatable("etchedytdlp.button.open_folder"),
                button -> openCacheFolder()
        ).bounds(centerX - buttonWidth / 2, startY + 75, buttonWidth, buttonHeight).build());

        // Done button
        this.addRenderableWidget(Button.builder(
                Component.translatable("etchedytdlp.button.done"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(centerX - buttonWidth / 2, startY + 110, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        // Title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // Cache info
        int infoY = this.height / 2 - 80;

        if (cleared) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("etchedytdlp.config.cleared_success").getString(),
                    this.width / 2, infoY, 0x00FF00);
            graphics.drawCenteredString(this.font,
                    Component.translatable("etchedytdlp.config.cleared_warning").getString(),
                    this.width / 2, infoY + 15, 0xAAAAAA);
        } else {
            graphics.drawCenteredString(this.font,
                    Component.translatable("etchedytdlp.config.cache_info").getString(),
                    this.width / 2, infoY, 0xFFFFFF);

            graphics.drawCenteredString(this.font,
                    Component.translatable("etchedytdlp.config.total_size" + formatSize(cacheSize)).getString(),
                    this.width / 2, infoY + 15, 0xAAAAAA);

            graphics.drawCenteredString(this.font,
                    Component.translatable("etchedytdlp.config.file_count" + fileCount).getString(),
                    this.width / 2, infoY + 30, 0xAAAAAA);

            graphics.drawCenteredString(this.font,
                    Component.translatable("etchedytdlp.config.location").getString(),
                    this.width / 2, infoY + 45, 0x888888);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    /**
     * Refresh cache information
     */
    private void refreshCacheInfo() {
        try {
            cacheSize = YtDlpDownloader.getCacheSize();
            fileCount = countCachedFiles();

            System.out.println("[Etched YT-DLP] Cache: " + formatSize(cacheSize) +
                    " (" + fileCount + " files)");
        } catch (IOException e) {
            cacheInfo = "Error reading cache";
            System.err.println("[Etched YT-DLP] Failed to read cache info: " + e.getMessage());
        }
    }

    /**
     * Count cached files
     */
    private int countCachedFiles() throws IOException {
        Path cacheDir = YtDlpDownloader.getCacheDirectory();
        if (!Files.exists(cacheDir)) {
            return 0;
        }

        return (int) Files.list(cacheDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".mp3") ||
                        p.toString().endsWith(".ogg") ||
                        p.toString().endsWith(".wav"))
                .count();
    }

    /**
     * Clear all cache
     */
    private void clearCache() {
        try {
            System.out.println("[Etched YT-DLP] Clearing all cache...");
            YtDlpDownloader.clearCache();

            cleared = true;
            cacheSize = 0;
            fileCount = 0;

            System.out.println("[Etched YT-DLP] Cache cleared successfully!");
        } catch (IOException e) {
            System.err.println("[Etched YT-DLP] Failed to clear cache: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clear old cache files
     */
    private void clearOldCache(int daysOld) {
        try {
            System.out.println("[Etched YT-DLP] Clearing cache older than " + daysOld + " days...");

            Path cacheDir = YtDlpDownloader.getCacheDirectory();
            if (!Files.exists(cacheDir)) {
                return;
            }

            long cutoff = System.currentTimeMillis() - (daysOld * 24L * 60 * 60 * 1000);
            int deleted = 0;

            var files = Files.list(cacheDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis() < cutoff;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .toList();

            for (Path file : files) {
                try {
                    Files.delete(file);
                    deleted++;
                } catch (IOException e) {
                    System.err.println("[Etched YT-DLP] Failed to delete: " + file.getFileName());
                }
            }

            System.out.println("[Etched YT-DLP] Deleted " + deleted + " old file(s)");
            refreshCacheInfo();

        } catch (IOException e) {
            System.err.println("[Etched YT-DLP] Failed to clear old cache: " + e.getMessage());
        }
    }

    /**
     * Open cache folder in file explorer
     */
    private void openCacheFolder() {
        try {
            Path cacheDir = YtDlpDownloader.getCacheDirectory();

            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }

            // Open in system file explorer
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                // Windows
                Runtime.getRuntime().exec("explorer.exe " + cacheDir.toAbsolutePath());
            } else if (os.contains("mac")) {
                // macOS
                Runtime.getRuntime().exec("open " + cacheDir.toAbsolutePath());
            } else {
                // Linux
                Runtime.getRuntime().exec("xdg-open " + cacheDir.toAbsolutePath());
            }

            System.out.println("[Etched YT-DLP] Opened cache folder: " + cacheDir);

        } catch (IOException e) {
            System.err.println("[Etched YT-DLP] Failed to open cache folder: " + e.getMessage());
        }
    }

    /**
     * Format file size to human-readable format
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return SIZE_FORMAT.format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return SIZE_FORMAT.format(bytes / (1024.0 * 1024.0)) + " MB";
        } else {
            return SIZE_FORMAT.format(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}