/*
 * Copyright (c) 2024, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package org.jline.prompt.impl;

import org.jline.prompt.PrompterConfig;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.OSUtils;
import org.jline.utils.StyleResolver;

/**
 * Default implementation of PrompterConfig interface.
 */
public class DefaultPrompterConfig implements PrompterConfig {

    private final String indicator;
    private final String uncheckedBox;
    private final String checkedBox;
    private final String unavailable;
    private final boolean cancellableFirstPrompt;
    private final StyleResolver styleResolver;
    private final AttributedString indicatorAttributed;
    private final AttributedString uncheckedBoxAttributed;
    private final AttributedString checkedBoxAttributed;
    private final AttributedString unavailableAttributed;

    /**
     * Create a default configuration with sensible defaults.
     */
    public DefaultPrompterConfig() {
        this(
                "?",
                OSUtils.IS_WINDOWS ? "o" : "◯",
                OSUtils.IS_WINDOWS ? "x" : "◉",
                OSUtils.IS_WINDOWS ? "-" : "⊝",
                false,
                null);
    }

    /**
     * Create a configuration with specific values.
     */
    public DefaultPrompterConfig(
            String indicator,
            String uncheckedBox,
            String checkedBox,
            String unavailable,
            boolean cancellableFirstPrompt) {
        this(indicator, uncheckedBox, checkedBox, unavailable, cancellableFirstPrompt, null);
    }

    /**
     * Create a configuration with specific values and style resolver.
     */
    public DefaultPrompterConfig(
            String indicator,
            String uncheckedBox,
            String checkedBox,
            String unavailable,
            boolean cancellableFirstPrompt,
            StyleResolver styleResolver) {
        this.indicator = indicator;
        this.uncheckedBox = uncheckedBox;
        this.checkedBox = checkedBox;
        this.unavailable = unavailable;
        this.cancellableFirstPrompt = cancellableFirstPrompt;
        this.styleResolver = styleResolver;

        // Create AttributedString versions with styling
        this.indicatorAttributed = toAttributedString(styleResolver, indicator, ".cu");
        this.uncheckedBoxAttributed = toAttributedString(styleResolver, uncheckedBox, ".be");
        this.checkedBoxAttributed = toAttributedString(styleResolver, checkedBox, ".be");
        this.unavailableAttributed = toAttributedString(styleResolver, unavailable, ".bd");
    }

    private static AttributedString toAttributedString(StyleResolver resolver, String string, String styleKey) {
        if (resolver == null) {
            return new AttributedString(string);
        }
        AttributedStringBuilder asb = new AttributedStringBuilder();
        asb.style(resolver.resolve(styleKey));
        asb.append(string);
        return asb.toAttributedString();
    }

    @Override
    public String indicator() {
        return indicator;
    }

    @Override
    public String uncheckedBox() {
        return uncheckedBox;
    }

    @Override
    public String checkedBox() {
        return checkedBox;
    }

    @Override
    public String unavailable() {
        return unavailable;
    }

    @Override
    public boolean cancellableFirstPrompt() {
        return cancellableFirstPrompt;
    }

    @Override
    public AttributedString indicatorAttributed() {
        return indicatorAttributed;
    }

    @Override
    public AttributedString uncheckedBoxAttributed() {
        return uncheckedBoxAttributed;
    }

    @Override
    public AttributedString checkedBoxAttributed() {
        return checkedBoxAttributed;
    }

    @Override
    public AttributedString unavailableAttributed() {
        return unavailableAttributed;
    }

    @Override
    public StyleResolver styleResolver() {
        return styleResolver;
    }
}
