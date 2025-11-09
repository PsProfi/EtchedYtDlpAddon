package com.psprofi.etchedytdlp.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple local HTTP server to serve cached audio files to Etched
 * This is needed because Etched expects HTTP URLs, not file:// URLs
 * @author PsProfi
 */
public class LocalAudioServer {

    public static HttpServer server;
    private static final int PORT = 25565 + 100; // Use port 25665 (Minecraft port + 100)
    private static final Map<String, Path> fileRegistry = new ConcurrentHashMap<>();
    private static boolean started = false;

    /**
     * Starts the local HTTP server if not already running
     */
    public static synchronized void start() throws IOException {
        if (started) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
            server.createContext("/audio", new AudioFileHandler());
            server.setExecutor(null); // Use default executor
            server.start();
            started = true;

            System.out.println("[Etched YT-DLP] Local audio server started on http://0.0.0.0:" + PORT);
        } catch (IOException e) {
            System.err.println("[Etched YT-DLP] Failed to start local server: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Stops the local HTTP server
     */
    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            started = false;
            fileRegistry.clear();
            System.out.println("[Etched YT-DLP] Local audio server stopped");
        }
    }

    /**
     * Registers a file and returns its HTTP URL
     * @param filePath Path to the audio file
     * @return HTTP URL to access the file
     */
    public static String registerFile(Path filePath) throws IOException {
        if (!started) {
            start();
        }

        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }

        // Generate unique ID for the file
        String fileId = Integer.toHexString(filePath.toString().hashCode());
        fileRegistry.put(fileId, filePath);

        return "http://127.0.0.1:" + PORT + "/audio/" + fileId;
    }

    /**
     * HTTP handler for serving audio files
     */
    public static class AudioFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String fileId = path.substring(path.lastIndexOf('/') + 1);

            Path filePath = fileRegistry.get(fileId);

            if (filePath == null || !Files.exists(filePath)) {
                // File not found
                String response = "File not found";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            // Determine content type based on file extension
            String contentType = "audio/mpeg"; // Default to MP3
            String fileName = filePath.toString().toLowerCase();
            if (fileName.endsWith(".ogg") || fileName.endsWith(".opus")) {
                contentType = "audio/ogg";
            } else if (fileName.endsWith(".m4a")) {
                contentType = "audio/mp4";
            } else if (fileName.endsWith(".wav")) {
                contentType = "audio/wav";
            } else if (fileName.endsWith(".mp3")) {
                contentType = "audio/mpeg";
            }

            // Read file
            byte[] fileBytes = Files.readAllBytes(filePath);

            // Set headers for audio streaming
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileBytes.length));
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000");

            // Handle range requests (for seeking in audio)
            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                handleRangeRequest(exchange, fileBytes, rangeHeader);
            } else {
                // Send full file
                exchange.sendResponseHeaders(200, fileBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fileBytes);
                    os.flush();
                }
            }
        }

        /**
         * Handle HTTP range requests for audio seeking
         */
        private void handleRangeRequest(HttpExchange exchange, byte[] fileBytes, String rangeHeader) throws IOException {
            try {
                // Parse range: "bytes=0-1023" or "bytes=1024-"
                String range = rangeHeader.substring(6);
                String[] parts = range.split("-");

                long start = Long.parseLong(parts[0]);
                long end = parts.length > 1 && !parts[1].isEmpty()
                        ? Long.parseLong(parts[1])
                        : fileBytes.length - 1;

                // Validate range
                if (start < 0 || start >= fileBytes.length || end >= fileBytes.length || start > end) {
                    exchange.sendResponseHeaders(416, -1); // Range Not Satisfiable
                    return;
                }

                long contentLength = end - start + 1;

                // Set partial content headers
                exchange.getResponseHeaders().set("Content-Range",
                        "bytes " + start + "-" + end + "/" + fileBytes.length);
                exchange.sendResponseHeaders(206, contentLength); // Partial Content

                // Send requested range
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fileBytes, (int) start, (int) contentLength);
                    os.flush();
                }
            } catch (Exception e) {
                // Invalid range, send full file
                exchange.sendResponseHeaders(200, fileBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fileBytes);
                    os.flush();
                }
            }
        }
    }

    /**
     * Checks if server is running
     */
    public static boolean isRunning() {
        return started;
    }
}