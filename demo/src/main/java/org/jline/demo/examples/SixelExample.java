/*
 * Copyright (c) 2002-2025, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package org.jline.demo.examples;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.SixelGraphics;

/**
 * Example demonstrating how to use Sixel graphics in JLine.
 */
public class SixelExample {

    // SNIPPET_START: SixelExample
    /**
     * Force enable or disable Sixel support, overriding automatic detection.
     * This is useful for testing or when automatic detection fails.
     *
     * @param enabled true to force enable, false to force disable, null for automatic detection
     */
    public static void forceSixelSupport(Boolean enabled) {
        SixelGraphics.setSixelSupportOverride(enabled);
    }

    public static void displayImageWithSixel(Terminal terminal, String imagePath) throws IOException {
        // Check if the terminal supports Sixel graphics
        if (!SixelGraphics.isSixelSupported(terminal)) {
            terminal.writer().println("Terminal does not support Sixel graphics");
            terminal.writer().println("Use forceSixelSupport(true) to override detection if needed");
            return;
        }

        // Load and display the image
        File imageFile = new File(imagePath);
        if (imageFile.exists()) {
            SixelGraphics.displayImage(terminal, imageFile);
        } else {
            terminal.writer().println("Image file not found: " + imagePath);
        }
    }

    public static void displayResourceImageWithSixel(Terminal terminal, String resourcePath) throws IOException {
        // Check if the terminal supports Sixel graphics
        if (!SixelGraphics.isSixelSupported(terminal)) {
            terminal.writer().println("Terminal does not support Sixel graphics");
            return;
        }

        // Load and display the image from resources
        try (InputStream is = SixelExample.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                SixelGraphics.displayImage(terminal, is);
            } else {
                terminal.writer().println("Resource not found: " + resourcePath);
            }
        }
    }

    public static void displayBufferedImageWithSixel(Terminal terminal, BufferedImage image) throws IOException {
        // Check if the terminal supports Sixel graphics
        if (!SixelGraphics.isSixelSupported(terminal)) {
            terminal.writer().println("Terminal does not support Sixel graphics");
            return;
        }

        // Display the BufferedImage
        SixelGraphics.displayImage(terminal, image);
    }

    /**
     * Creates a simple test image with text and a gradient background.
     * This is useful when no external images are available.
     *
     * @return a BufferedImage containing a test pattern
     */
    public static BufferedImage createTestImage() {
        int width = 300;
        int height = 150;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Set rendering hints for better quality
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Create a gradient background
        for (int y = 0; y < height; y++) {
            Color color = new Color(
                    Math.max(0, Math.min(255, (int) (255 * y / (double) height))),
                    Math.max(0, Math.min(255, (int) (255 * (1 - y / (double) height)))),
                    Math.max(0, Math.min(255, (int) (128 + 127 * Math.sin(y * Math.PI / height)))));
            g2d.setColor(color);
            g2d.drawLine(0, y, width, y);
        }

        // Draw text
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 24));
        g2d.drawString("JLine Sixel Test", 50, 50);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g2d.drawString("Terminal Graphics Support", 70, 80);

        // Draw a border
        g2d.setColor(Color.WHITE);
        g2d.drawRect(10, 10, width - 20, height - 20);

        g2d.dispose();
        return image;
    }
    // SNIPPET_END: SixelExample

    /**
     * Demonstrates how to force enable or disable Sixel support.
     *
     * @param terminal the terminal to use
     * @param enable true to force enable, false to force disable
     */
    public static void demonstrateSixelOverride(Terminal terminal, boolean enable) {
        // Save the current detection state
        boolean originalSupport = SixelGraphics.isSixelSupported(terminal);

        terminal.writer().println("Original sixel support: " + originalSupport);

        // Override the detection
        SixelGraphics.setSixelSupportOverride(enable);
        terminal.writer().println("Sixel support after override: " + SixelGraphics.isSixelSupported(terminal));

        // Reset to automatic detection
        SixelGraphics.setSixelSupportOverride(null);
        terminal.writer().println("Sixel support after reset: " + SixelGraphics.isSixelSupported(terminal));
    }

    /**
     * Main method to demonstrate Sixel graphics.
     */
    public static void main(String[] args) {
        try (Terminal terminal = TerminalBuilder.builder().build()) {
            // Check for command line arguments
            if (args.length > 0) {
                if (args[0].equals("--force-enable")) {
                    // Force enable sixel support for testing
                    SixelGraphics.setSixelSupportOverride(true);
                    terminal.writer().println("Forced sixel support enabled");
                } else if (args[0].equals("--force-disable")) {
                    // Force disable sixel support for testing
                    SixelGraphics.setSixelSupportOverride(false);
                    terminal.writer().println("Forced sixel support disabled");
                } else if (args[0].equals("--demo-override")) {
                    // Demonstrate the override feature
                    demonstrateSixelOverride(terminal, true);
                    return;
                } else if (args[0].equals("--test-image")) {
                    // Use the test image directly
                    terminal.writer().println("Displaying test image");
                    displayBufferedImageWithSixel(terminal, createTestImage());
                    return;
                }
            }

            if (SixelGraphics.isSixelSupported(terminal)) {
                terminal.writer().println("Terminal supports Sixel graphics");

                // If an image path is provided as an argument, display it
                if (args.length > 0 && !args[0].startsWith("--")) {
                    displayImageWithSixel(terminal, args[0]);
                } else {
                    // Otherwise, try to display a sample image from resources
                    try {
                        displayResourceImageWithSixel(terminal, "/images/jline-logo.png");
                    } catch (IOException e) {
                        // If resource image fails, use a programmatically generated test image
                        terminal.writer().println("Resource image not found, using test image instead");
                        displayBufferedImageWithSixel(terminal, createTestImage());
                    }
                }
            } else {
                terminal.writer().println("Terminal does not support Sixel graphics");
                terminal.writer().println("Try running in a terminal that supports Sixel, such as:");
                terminal.writer().println("- XTerm (with --enable-sixel-graphics)");
                terminal.writer().println("- MLTerm");
                terminal.writer().println("- Mintty (>= 2.6.0)");
                terminal.writer().println("- iTerm2 (>= 3.3.0)");
                terminal.writer().println("- Konsole (>= 22.04)");
                terminal.writer().println("- foot");
                terminal.writer().println("- WezTerm");
                terminal.writer().println("\nCommand line options:");
                terminal.writer().println("  --force-enable   Override detection and force enable sixel support");
                terminal.writer().println("  --force-disable  Override detection and force disable sixel support");
                terminal.writer().println("  --demo-override  Demonstrate the override feature");
                terminal.writer().println("  --test-image     Display a programmatically generated test image");
                terminal.writer().println("  <image-path>     Display the specified image file");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
