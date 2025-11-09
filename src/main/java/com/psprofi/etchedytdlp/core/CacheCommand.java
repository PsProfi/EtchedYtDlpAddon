package com.psprofi.etchedytdlp.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.psprofi.etchedytdlp.YouTube.YtDlpDownloader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;

/**
 * Commands for managing audio cache
 *
 * @author PsProfi
 */
@Mod.EventBusSubscriber(modid = "etchedytdlp", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CacheCommand {

    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.00");

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("ytcache")
                .requires(source -> source.hasPermission(2)) // Requires OP level 2

                // /ytcache info
                .then(Commands.literal("info")
                        .executes(CacheCommand::executeInfo))

                // /ytcache clear
                .then(Commands.literal("clear")
                        .executes(CacheCommand::executeClear))

                // /ytcache clearold
                .then(Commands.literal("clearold")
                        .executes(CacheCommand::executeClearOld))
        );
    }

    /**
     * Show cache information
     */
    private static int executeInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            long cacheSize = YtDlpDownloader.getCacheSize();
            int fileCount = countCachedFiles();

            source.sendSuccess(() -> Component.translatable("etchedytdlp.command.info.title")
                    .withStyle(ChatFormatting.GOLD), false);

            source.sendSuccess(() -> Component.translatable("etchedytdlp.command.info.size" + formatSize(cacheSize))
                    .withStyle(ChatFormatting.AQUA), false);

            source.sendSuccess(() -> Component.translatable("etchedytdlp.command.info.files" + fileCount + " cached songs")
                    .withStyle(ChatFormatting.AQUA), false);

            source.sendSuccess(() -> Component.translatable("etchedytdlp.command.info.location")
                    .withStyle(ChatFormatting.GRAY), false);

        } catch (IOException e) {
            source.sendFailure(Component.translatable("etchedytdlp.command.error.read" + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        return 1;
    }

    /**
     * Clear all cache
     */
    private static int executeClear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            long sizeBefore = YtDlpDownloader.getCacheSize();
            int filesBefore = countCachedFiles();

            source.sendSuccess(() -> Component.translatable("etchedytdlp.command.clear.clearing")
                    .withStyle(ChatFormatting.YELLOW), true);

            YtDlpDownloader.clearCache();

            source.sendSuccess(() -> Component.translatable("etchedytdlp.command.clear.success")
                    .withStyle(ChatFormatting.GREEN), true);

            source.sendSuccess(() -> Component.translatable("etchedytdlp.command.clear.freed" + formatSize(sizeBefore) +
                            filesBefore)
                    .withStyle(ChatFormatting.GRAY), false);

            source.sendSuccess(() -> Component.translatable("etchedytdlp.command.clear.warning")
                    .withStyle(ChatFormatting.GRAY), false);

            return 1;

        } catch (IOException e) {
            source.sendFailure(Component.translatable("etchedytdlp.command.error.clear" + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * Clear old cache (30+ days)
     */
    private static int executeClearOld(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            Path cacheDir = YtDlpDownloader.getCacheDirectory();
            if (!Files.exists(cacheDir)) {
                source.sendSuccess(() -> Component.translatable("etchedytdlp.command.cache_empty")
                        .withStyle(ChatFormatting.GRAY), false);
                return 1;
            }

            long cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
            int deleted = 0;
            long freedSize = 0;

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
                    long size = Files.size(file);
                    Files.delete(file);
                    deleted++;
                    freedSize += size;
                } catch (IOException e) {
                    System.err.println("[Etched YT-DLP] Failed to delete: " + file.getFileName());
                }
            }

            if (deleted > 0) {
                int finalDeleted = deleted;
                long finalFreedSize = freedSize;
                source.sendSuccess(() -> Component.translatable("etchedytdlp.command.clearold.success" + finalDeleted)
                        .withStyle(ChatFormatting.GREEN), true);
                source.sendSuccess(() -> Component.translatable("etchedytdlp.command.clearold.freed" + formatSize(finalFreedSize))
                        .withStyle(ChatFormatting.GRAY), false);
            } else {
                source.sendSuccess(() -> Component.translatable("etchedytdlp.command.clearold.none")
                        .withStyle(ChatFormatting.GRAY), false);
            }

            return 1;

        } catch (IOException e) {
            source.sendFailure(Component.translatable("etchedytdlp.command.error.clearold" + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * Count cached files
     */
    private static int countCachedFiles() throws IOException {
        Path cacheDir = YtDlpDownloader.getCacheDirectory();
        if (!Files.exists(cacheDir)) {
            return 0;
        }

        return (int) Files.list(cacheDir)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.toString().toLowerCase();
                    return name.endsWith(".mp3") ||
                            name.endsWith(".ogg") ||
                            name.endsWith(".wav");
                })
                .count();
    }

    /**
     * Format file size to human-readable format
     */
    private static String formatSize(long bytes) {
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
}