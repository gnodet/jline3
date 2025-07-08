/*
 * Copyright (c) 2002-2025, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package org.jline.terminal.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

import org.jline.terminal.Terminal;

/**
 * Implementation of Kitty's Graphics Protocol.
 *
 * <p>The Kitty Graphics Protocol is a modern terminal graphics protocol that supports
 * advanced features like animations, transparency, and efficient image transmission.
 * It uses escape sequences of the form: {@code <ESC>_G<control data>;<payload><ESC>\}</p>
 *
 * <p>This protocol is supported by:</p>
 * <ul>
 *   <li>Kitty terminal</li>
 *   <li>WezTerm</li>
 *   <li>Ghostty (planned)</li>
 * </ul>
 *
 * <p>The protocol supports various transmission formats including base64 encoding,
 * direct file paths, and chunked transmission for large images.</p>
 *
 * @see <a href="https://sw.kovidgoyal.net/kitty/graphics-protocol.html">Kitty Graphics Protocol</a>
 * @since 3.30.0
 */
public class KittyGraphics implements TerminalGraphics {

    private static final String KITTY_GRAPHICS_START = "\033_G";
    private static final String KITTY_GRAPHICS_END = "\033\\";
    private static final AtomicInteger imageIdCounter = new AtomicInteger(1);

    @Override
    public Protocol getProtocol() {
        return Protocol.KITTY;
    }

    @Override
    public boolean isSupported(Terminal terminal) {
        // Check for Kitty terminal
        String termProgram = System.getenv("TERM_PROGRAM");
        if ("kitty".equals(termProgram)) {
            return true;
        }

        // Check for WezTerm
        if ("WezTerm".equals(termProgram)) {
            return true;
        }

        // Check for Ghostty (supports Kitty graphics protocol)
        if ("com.mitchellh.ghostty".equals(termProgram) || "ghostty".equals(termProgram)) {
            return true;
        }

        // Check for iTerm2 (supports Kitty graphics protocol since version 3.5.0)
        if ("iTerm.app".equals(termProgram)) {
            String termProgramVersion = System.getenv("TERM_PROGRAM_VERSION");
            if (termProgramVersion != null) {
                try {
                    String[] versionParts = termProgramVersion.split("\\.");
                    if (versionParts.length >= 2) {
                        int major = Integer.parseInt(versionParts[0]);
                        int minor = Integer.parseInt(versionParts[1]);
                        // iTerm2 supports Kitty graphics protocol since 3.5.0
                        if (major > 3 || (major == 3 && minor >= 5)) {
                            return true;
                        }
                    }
                } catch (NumberFormatException e) {
                    // If version parsing fails, assume it's supported for recent iTerm2
                    return true;
                }
            }
        }

        // Check TERM environment variable
        String term = System.getenv("TERM");
        if (term != null && term.contains("kitty")) {
            return true;
        }

        // Check for Kitty-specific capabilities
        // Kitty sets KITTY_WINDOW_ID when running
        if (System.getenv("KITTY_WINDOW_ID") != null) {
            return true;
        }

        // Check for Ghostty-specific environment variables
        if (System.getenv("GHOSTTY_RESOURCES_DIR") != null) {
            return true;
        }

        // TODO: Add runtime detection by sending a query and checking response
        // This would require terminal capability querying support

        return false;
    }

    @Override
    public int getPriority() {
        // Kitty protocol has high priority due to its advanced features
        return 90;
    }

    @Override
    public void displayImage(Terminal terminal, BufferedImage image) throws IOException {
        displayImage(terminal, image, new ImageOptions());
    }

    @Override
    public void displayImage(Terminal terminal, BufferedImage image, ImageOptions options) throws IOException {
        String kittyData = convertImage(image, options);
        terminal.writer().print(kittyData);
        terminal.writer().flush();
    }

    @Override
    public void displayImage(Terminal terminal, File file) throws IOException {
        displayImage(terminal, file, new ImageOptions());
    }

    @Override
    public void displayImage(Terminal terminal, File file, ImageOptions options) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            displayImage(terminal, fis, options);
        }
    }

    @Override
    public void displayImage(Terminal terminal, InputStream inputStream) throws IOException {
        displayImage(terminal, inputStream, new ImageOptions());
    }

    @Override
    public void displayImage(Terminal terminal, InputStream inputStream, ImageOptions options) throws IOException {
        BufferedImage image = ImageIO.read(inputStream);
        if (image == null) {
            throw new IOException("Unable to read image from input stream");
        }
        displayImage(terminal, image, options);
    }

    @Override
    public String convertImage(BufferedImage image, ImageOptions options) throws IOException {
        // Convert BufferedImage to PNG bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();

        // Encode to base64
        String base64Data = Base64.getEncoder().encodeToString(imageBytes);

        // Generate unique image ID
        int imageId = imageIdCounter.getAndIncrement();

        // Build control data
        StringBuilder controlData = new StringBuilder();
        controlData.append("a=T"); // action: transmit and display
        controlData.append(",f=100"); // format: PNG
        controlData.append(",i=").append(imageId); // image ID
        controlData.append(",q=2"); // quiet mode: suppress responses

        // Add dimensions if specified
        if (options.getWidth() != null) {
            controlData.append(",w=").append(options.getWidth());
        }
        if (options.getHeight() != null) {
            controlData.append(",h=").append(options.getHeight());
        }

        // Add name if specified
        if (options.getName() != null) {
            controlData.append(",n=").append(options.getName());
        }

        // For large images, we might need to chunk the data
        // For now, we'll send it all at once
        controlData.append(",m=0"); // more chunks: 0 = no more chunks

        // Build the complete escape sequence
        StringBuilder result = new StringBuilder();
        result.append(KITTY_GRAPHICS_START);
        result.append(controlData.toString());
        result.append(";");
        result.append(base64Data);
        result.append(KITTY_GRAPHICS_END);

        return result.toString();
    }

    /**
     * Converts an image with chunked transmission for large images.
     * This method splits large base64 data into chunks to avoid terminal buffer limits.
     *
     * @param image the image to convert
     * @param options display options
     * @param chunkSize maximum size of each chunk (default: 4096)
     * @return the complete Kitty graphics protocol sequence
     * @throws IOException if an I/O error occurs
     */
    public String convertImageChunked(BufferedImage image, ImageOptions options, int chunkSize) throws IOException {
        // Convert BufferedImage to PNG bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();

        // Encode to base64
        String base64Data = Base64.getEncoder().encodeToString(imageBytes);

        // Generate unique image ID
        int imageId = imageIdCounter.getAndIncrement();

        StringBuilder result = new StringBuilder();

        // If data is small enough, send in one chunk
        if (base64Data.length() <= chunkSize) {
            return convertImage(image, options);
        }

        // Split into chunks
        int totalChunks = (base64Data.length() + chunkSize - 1) / chunkSize;

        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, base64Data.length());
            String chunk = base64Data.substring(start, end);

            StringBuilder controlData = new StringBuilder();

            if (i == 0) {
                // First chunk: include all metadata
                controlData.append("a=T"); // action: transmit and display
                controlData.append(",f=100"); // format: PNG
                controlData.append(",i=").append(imageId); // image ID
                controlData.append(",q=2"); // quiet mode: suppress responses

                // Add dimensions if specified
                if (options.getWidth() != null) {
                    controlData.append(",w=").append(options.getWidth());
                }
                if (options.getHeight() != null) {
                    controlData.append(",h=").append(options.getHeight());
                }

                // Add name if specified
                if (options.getName() != null) {
                    controlData.append(",n=").append(options.getName());
                }
            } else {
                // Subsequent chunks: only image ID and more flag
                controlData.append("i=").append(imageId);
            }

            // Set more chunks flag
            if (i < totalChunks - 1) {
                controlData.append(",m=1"); // more chunks coming
            } else {
                controlData.append(",m=0"); // last chunk
            }

            // Build chunk sequence
            result.append(KITTY_GRAPHICS_START);
            result.append(controlData.toString());
            result.append(";");
            result.append(chunk);
            result.append(KITTY_GRAPHICS_END);
        }

        return result.toString();
    }

    /**
     * Consumes the Kitty graphics protocol response to prevent it from appearing as text.
     * Kitty sends responses like "\x1b_Gi=1;OK\x1b\" after processing graphics commands.
     *
     * @param terminal the terminal
     */
    private void consumeKittyResponse(Terminal terminal) {
        try {
            // Use a longer timeout and more aggressive reading
            // Some terminals might take longer to respond or buffer the response
            org.jline.utils.NonBlockingReader reader = terminal.reader();

            // Try multiple times with different timeouts
            StringBuilder response = new StringBuilder();

            // First, try a quick read (50ms) for immediate responses
            int attempts = 0;
            int maxAttempts = 5;

            while (attempts < maxAttempts) {
                long timeout = (attempts == 0) ? 50 : 200; // First attempt quick, then longer

                int c;
                boolean foundData = false;

                // Read available data with timeout
                while ((c = reader.read(timeout)) != -1) {
                    foundData = true;
                    response.append((char) c);

                    // Check if we've received a complete Kitty response
                    String responseStr = response.toString();

                    // Look for the specific pattern: ESC _ G ... ESC \
                    if (responseStr.contains("\033_G") && responseStr.contains("\033\\")) {
                        // Found complete response, we're done
                        return;
                    }

                    // Also check for the pattern you're seeing: ESC _ G i=1;OK ESC \
                    if (responseStr.matches(".*\033_G.*i=\\d+;.*\033\\\\.*")) {
                        // Found the specific response pattern
                        return;
                    }

                    // If response is getting too long, something's wrong - break out
                    if (response.length() > 200) {
                        return;
                    }

                    // Use a very short timeout for subsequent characters in the same response
                    timeout = 10;
                }

                // If we found some data, try one more time with a longer timeout
                if (foundData) {
                    attempts++;
                } else {
                    // No data found, exit
                    break;
                }
            }

        } catch (IOException e) {
            // If we can't read the response, that's okay - it might not be available
            // or the terminal might not send responses. We'll just continue.
        }
    }

    /**
     * Deletes an image from the terminal's memory.
     *
     * @param terminal the terminal
     * @param imageId the ID of the image to delete
     * @throws IOException if an I/O error occurs
     */
    public void deleteImage(Terminal terminal, int imageId) throws IOException {
        String deleteSequence = KITTY_GRAPHICS_START + "a=d,i=" + imageId + ",q=2" + KITTY_GRAPHICS_END;
        terminal.writer().print(deleteSequence);
        terminal.writer().flush();
    }

    /**
     * Clears all images from the terminal.
     *
     * @param terminal the terminal
     * @throws IOException if an I/O error occurs
     */
    public void clearAllImages(Terminal terminal) throws IOException {
        String clearSequence = KITTY_GRAPHICS_START + "a=d,q=2" + KITTY_GRAPHICS_END;
        terminal.writer().print(clearSequence);
        terminal.writer().flush();
    }
}
