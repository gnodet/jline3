/*
 * Copyright (c) 2024, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package org.jline.prompt;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.jline.prompt.impl.DefaultPrompterConfig;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.StyleResolver;

/**
 * Configuration interface for customizing the visual appearance and behavior of prompts.
 *
 * <p>
 * The configuration controls various visual elements used in the prompt display,
 * including indicators, checkbox symbols, and interaction behavior. The API provides
 * platform-specific defaults that work well on different operating systems.
 * </p>
 *
 * <h4>Default Configurations:</h4>
 * <ul>
 *   <li><strong>Windows:</strong> {@code ">"}, {@code "( )"}, {@code "(x)"}, {@code "( )"}</li>
 *   <li><strong>Unix/Linux/macOS:</strong> {@code "❯"}, {@code "◯ "}, {@code "◉ "}, {@code "◯ "}</li>
 * </ul>
 *
 * <h4>Example Custom Configuration:</h4>
 * <pre>{@code
 * Prompter.Config config = new DefaultPrompter.DefaultConfig(
 *     "→",     // indicator
 *     "☐ ",    // unchecked box
 *     "☑ ",    // checked box
 *     "☐ "     // unavailable item
 * );
 *
 * Prompter prompter = PrompterFactory.create(terminal, config);
 * }</pre>
 *
 * @see PrompterFactory#create(Terminal, PrompterConfig)
 * @since 3.30.0
 */
public interface PrompterConfig {

    /**
     * Get the indicator character/string.
     *
     * @return the indicator
     */
    String indicator();

    /**
     * Get the unchecked box character/string.
     *
     * @return the unchecked box
     */
    String uncheckedBox();

    /**
     * Get the checked box character/string.
     *
     * @return the checked box
     */
    String checkedBox();

    /**
     * Get the unavailable item character/string.
     *
     * @return the unavailable item
     */
    String unavailable();

    /**
     * Get the indicator as an AttributedString.
     *
     * @return the indicator with styling
     */
    default AttributedString indicatorAttributed() {
        return new AttributedString(indicator());
    }

    /**
     * Get the unchecked box as an AttributedString.
     *
     * @return the unchecked box with styling
     */
    default AttributedString uncheckedBoxAttributed() {
        return new AttributedString(uncheckedBox());
    }

    /**
     * Get the checked box as an AttributedString.
     *
     * @return the checked box with styling
     */
    default AttributedString checkedBoxAttributed() {
        return new AttributedString(checkedBox());
    }

    /**
     * Get the unavailable item as an AttributedString.
     *
     * @return the unavailable item with styling
     */
    default AttributedString unavailableAttributed() {
        return new AttributedString(unavailable());
    }

    /**
     * Get the style resolver for this configuration.
     *
     * @return the style resolver, or null if not available
     */
    default StyleResolver styleResolver() {
        return null;
    }

    /**
     * Whether the first prompt can be cancelled.
     *
     * @return true if the first prompt can be cancelled
     */
    boolean cancellableFirstPrompt();

    default PrompterConfig withCancellableFirstPrompt(boolean cancellable) {
        return custom(indicator(), uncheckedBox(), checkedBox(), unavailable(), cancellable);
    }

    /**
     * Create a configuration with platform-specific defaults.
     *
     * @return a configuration with platform defaults
     */
    static PrompterConfig defaults() {
        return new DefaultPrompterConfig();
    }

    /**
     * Create a configuration with Windows-specific defaults.
     *
     * @return a configuration with Windows defaults
     */
    static PrompterConfig windows() {
        return new DefaultPrompterConfig(">", "( )", "(x)", "( )", false);
    }

    /**
     * Create a configuration with Unix/Linux-specific defaults.
     *
     * @return a configuration with Unix defaults
     */
    static PrompterConfig unix() {
        return new DefaultPrompterConfig("\u276F", "\u25EF ", "\u25C9 ", "\u25EF ", false);
    }

    /**
     * Create a custom configuration.
     *
     * @param indicator the indicator character/string
     * @param uncheckedBox the unchecked box character/string
     * @param checkedBox the checked box character/string
     * @param unavailable the unavailable item character/string
     * @param cancellableFirstPrompt whether the first prompt can be cancelled
     * @return a custom configuration
     */
    static PrompterConfig custom(
            String indicator,
            String uncheckedBox,
            String checkedBox,
            String unavailable,
            boolean cancellableFirstPrompt) {
        return new DefaultPrompterConfig(indicator, uncheckedBox, checkedBox, unavailable, cancellableFirstPrompt);
    }

    /**
     * Create a custom configuration with style resolver support.
     *
     * @param indicator the indicator character/string
     * @param uncheckedBox the unchecked box character/string
     * @param checkedBox the checked box character/string
     * @param unavailable the unavailable item character/string
     * @param cancellableFirstPrompt whether the first prompt can be cancelled
     * @param styleResolver the style resolver for applying styles
     * @return a custom configuration with styling support
     */
    static PrompterConfig custom(
            String indicator,
            String uncheckedBox,
            String checkedBox,
            String unavailable,
            boolean cancellableFirstPrompt,
            StyleResolver styleResolver) {
        return new DefaultPrompterConfig(
                indicator, uncheckedBox, checkedBox, unavailable, cancellableFirstPrompt, styleResolver);
    }

    /**
     * Create a configuration with default styling support.
     * <p>
     * This method creates a configuration similar to UiConfig, with default color styling
     * based on environment variables or built-in defaults. The style keys used are:
     * </p>
     * <ul>
     *   <li><code>.cu</code> - cursor/indicator style</li>
     *   <li><code>.be</code> - box element style (checked/unchecked boxes)</li>
     *   <li><code>.bd</code> - disabled/unavailable item style</li>
     * </ul>
     *
     * @param indicator the indicator character/string
     * @param uncheckedBox the unchecked box character/string
     * @param checkedBox the checked box character/string
     * @param unavailable the unavailable item character/string
     * @param cancellableFirstPrompt whether the first prompt can be cancelled
     * @return a configuration with default styling support
     */
    static PrompterConfig styled(
            String indicator,
            String uncheckedBox,
            String checkedBox,
            String unavailable,
            boolean cancellableFirstPrompt) {
        String defaultColors = "cu=36:be=32:bd=37:pr=32:me=1:an=36:se=36:cb=100";
        String envColors = System.getenv("PROMPTER_COLORS");
        String colors = envColors != null ? envColors : defaultColors;

        Map<String, String> colorMap = Arrays.stream(colors.split(":"))
                .collect(Collectors.toMap(s -> s.substring(0, s.indexOf('=')), s -> s.substring(s.indexOf('=') + 1)));

        StyleResolver resolver = new StyleResolver(colorMap::get);
        return new DefaultPrompterConfig(
                indicator, uncheckedBox, checkedBox, unavailable, cancellableFirstPrompt, resolver);
    }

    /**
     * Create a configuration with default styling and platform-specific symbols.
     *
     * @return a configuration with default styling and platform defaults
     */
    static PrompterConfig defaultStyled() {
        return styled("❯", "◯ ", "◉ ", "⊝ ", false);
    }
}
