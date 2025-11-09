package com.psprofi.etchedytdlp.core;

import com.psprofi.etchedytdlp.core.LocalAudioServer;
import gg.moonflower.etched.api.sound.source.AudioSource;
import gg.moonflower.etched.api.util.AsyncInputStream;
import gg.moonflower.etched.api.util.DownloadProgressListener;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;

/**
 * Audio source that serves from the local server for synchronized playback
 * All players connect to the same local HTTP server and hear the music in sync
 *
 * @author PsProfi
 */
public class SynchronizedAudioSource implements AudioSource {

    private final Path audioFile;
    private final String httpUrl;
    private final DownloadProgressListener progressListener;

    /**
     * Creates a synchronized audio source
     *
     * @param audioFile The cached audio file on the server
     * @param progressListener Optional progress listener
     */
    public SynchronizedAudioSource(Path audioFile, @Nullable DownloadProgressListener progressListener) throws IOException {
        this.audioFile = audioFile;
        this.progressListener = progressListener;

        // Register with local audio server and get HTTP URL
        this.httpUrl = LocalAudioServer.registerFile(audioFile);

        System.out.println("[Etched YT-DLP] Created synchronized audio source: " + httpUrl);
    }

    @Override
    public CompletableFuture<InputStream> openStream() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // First, check if file exists locally (server-side or already cached)
                if (Files.exists(audioFile)) {
                    if (progressListener != null) {
                        progressListener.progressStartRequest(Component.literal("Loading from cache..."));
                    }
                    return Files.newInputStream(audioFile);
                }

                // If not cached locally, download from local HTTP server
                if (progressListener != null) {
                    progressListener.progressStartRequest(Component.literal("Connecting to server..."));
                }

                URL url = new URL(httpUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                AudioSource.getDownloadHeaders().forEach(connection::setRequestProperty);

                int response = connection.getResponseCode();
                if (response != 200) {
                    throw new IOException("Failed to connect to audio server: " + response);
                }

                long contentLength = connection.getContentLengthLong();

                if (progressListener != null) {
                    progressListener.progressStartDownload(contentLength);
                }

                // For streaming playback, use AsyncInputStream for better buffering
                if (contentLength > 10 * 1024 * 1024) { // Files larger than 10MB
                    System.out.println("[Etched YT-DLP] Using streaming mode for large file");
                    return new AsyncInputStream(url::openStream, 8192, 8, ForkJoinPool.commonPool());
                }

                // For smaller files, just return the input stream
                return connection.getInputStream();

            } catch (Throwable t) {
                if (progressListener != null) {
                    progressListener.onFail();
                }
                throw new CompletionException("Failed to open audio stream from " + httpUrl, t);
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Gets the HTTP URL for this audio source
     */
    public String getHttpUrl() {
        return httpUrl;
    }

    /**
     * Gets the local file path
     */
    public Path getAudioFile() {
        return audioFile;
    }
}