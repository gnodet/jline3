/*
 * Copyright (c) 2002-2025, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package org.jline.terminal.impl;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

import org.jline.terminal.Terminal;

/**
 * Implementation of the Sixel Graphics Protocol using the existing SixelGraphics utility.
 *
 * <p>This class wraps the existing {@link SixelGraphics} utility to provide a unified
 * interface through the {@link TerminalGraphics} interface. Sixel is a bitmap graphics
 * format that allows displaying raster graphics directly in terminals.</p>
 *
 * <p>Sixel graphics are supported by many terminals including:</p>
 * <ul>
 *   <li>xterm</li>
 *   <li>iTerm2</li>
 *   <li>foot</li>
 *   <li>WezTerm</li>
 *   <li>Konsole</li>
 *   <li>VS Code (with enableImages setting)</li>
 * </ul>
 *
 * <p>The name "Sixel" comes from "six pixels" because each character cell
 * represents 6 pixels arranged vertically.</p>
 *
 * @see SixelGraphics
 * @since 3.30.0
 */
public class SixelGraphicsImpl implements TerminalGraphics {

    @Override
    public Protocol getProtocol() {
        return Protocol.SIXEL;
    }

    @Override
    public boolean isSupported(Terminal terminal) {
        return SixelGraphics.isSixelSupported(terminal);
    }

    @Override
    public int getPriority() {
        // Sixel has lower priority than modern protocols but is widely supported
        return 50;
    }

    @Override
    public void displayImage(Terminal terminal, BufferedImage image) throws IOException {
        SixelGraphics.displayImage(terminal, image);
    }

    @Override
    public void displayImage(Terminal terminal, BufferedImage image, ImageOptions options) throws IOException {
        // Apply options by modifying the image before conversion
        BufferedImage processedImage = applyImageOptions(image, options);
        SixelGraphics.displayImage(terminal, processedImage);
    }

    @Override
    public void displayImage(Terminal terminal, File file) throws IOException {
        SixelGraphics.displayImage(terminal, file);
    }

    @Override
    public void displayImage(Terminal terminal, File file, ImageOptions options) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            displayImage(terminal, fis, options);
        }
    }

    @Override
    public void displayImage(Terminal terminal, InputStream inputStream) throws IOException {
        SixelGraphics.displayImage(terminal, inputStream);
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
        // Apply options by modifying the image before conversion
        BufferedImage processedImage = applyImageOptions(image, options);
        return SixelGraphics.convertToSixel(processedImage);
    }

    /**
     * Applies image options to a BufferedImage.
     * This method handles resizing and other transformations based on the provided options.
     *
     * @param image the original image
     * @param options the display options to apply
     * @return the processed image
     */
    private BufferedImage applyImageOptions(BufferedImage image, ImageOptions options) {
        BufferedImage result = image;

        // Apply resizing if width or height is specified
        if (options.getWidth() != null || options.getHeight() != null) {
            result = resizeImage(result, options);
        }

        return result;
    }

    /**
     * Resizes an image according to the specified options.
     *
     * @param image the image to resize
     * @param options the options containing width and height specifications
     * @return the resized image
     */
    private BufferedImage resizeImage(BufferedImage image, ImageOptions options) {
        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();

        int targetWidth = originalWidth;
        int targetHeight = originalHeight;

        // Determine target dimensions
        if (options.getWidth() != null && options.getHeight() != null) {
            // Both dimensions specified
            targetWidth = options.getWidth();
            targetHeight = options.getHeight();

            // Apply aspect ratio preservation if requested
            if (options.getPreserveAspectRatio() != null && options.getPreserveAspectRatio()) {
                double aspectRatio = (double) originalWidth / originalHeight;
                double targetAspectRatio = (double) targetWidth / targetHeight;

                if (aspectRatio > targetAspectRatio) {
                    // Image is wider than target - fit to width
                    targetHeight = (int) (targetWidth / aspectRatio);
                } else {
                    // Image is taller than target - fit to height
                    targetWidth = (int) (targetHeight * aspectRatio);
                }
            }
        } else if (options.getWidth() != null) {
            // Only width specified
            targetWidth = options.getWidth();
            if (options.getPreserveAspectRatio() == null || options.getPreserveAspectRatio()) {
                double aspectRatio = (double) originalWidth / originalHeight;
                targetHeight = (int) (targetWidth / aspectRatio);
            }
        } else if (options.getHeight() != null) {
            // Only height specified
            targetHeight = options.getHeight();
            if (options.getPreserveAspectRatio() == null || options.getPreserveAspectRatio()) {
                double aspectRatio = (double) originalWidth / originalHeight;
                targetWidth = (int) (targetHeight * aspectRatio);
            }
        }

        // If no resizing is needed, return original image
        if (targetWidth == originalWidth && targetHeight == originalHeight) {
            return image;
        }

        // Create resized image
        int imageType = image.getTransparency() == BufferedImage.OPAQUE
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D g = resized.createGraphics();

        // Use high quality rendering
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw the resized image
        g.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        g.dispose();

        return resized;
    }

    /**
     * Sets the Sixel support override for testing purposes.
     * This delegates to the underlying SixelGraphics utility.
     *
     * @param supported true to force Sixel support, false to disable it, null for automatic detection
     */
    public static void setSixelSupportOverride(Boolean supported) {
        SixelGraphics.setSixelSupportOverride(supported);
    }
}
