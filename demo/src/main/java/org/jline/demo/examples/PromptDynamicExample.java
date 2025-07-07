/*
 * Copyright (c) 2024-2025, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package org.jline.demo.examples;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jline.prompt.*;
import org.jline.prompt.impl.DefaultPromptBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.OSUtils;

/**
 * Dynamic prompt example showing conditional prompts based on user responses.
 * This is a port of the BasicDynamic ConsoleUI demo to the new prompt API.
 */
public class PromptDynamicExample {
    private static final AttributedStyle ITALIC_GREEN =
            AttributedStyle.DEFAULT.italic().foreground(2);
    private static final AttributedStyle BOLD_RED = AttributedStyle.BOLD.foreground(1);

    public static void main(String[] args) {
        try (Terminal terminal = TerminalBuilder.builder().build()) {
            Thread executeThread = Thread.currentThread();
            terminal.handle(Terminal.Signal.INT, signal -> executeThread.interrupt());

            if (terminal.getType().equals(Terminal.TYPE_DUMB)
                    || terminal.getType().equals(Terminal.TYPE_DUMB_COLOR)) {
                System.out.println(terminal.getName() + ": " + terminal.getType());
                throw new IllegalStateException("Dumb terminal detected.\nPrompt requires real terminal to work!\n"
                        + "Note: On Windows Jansi or JNA library must be included in classpath.");
            }

            // Create prompter with appropriate config for the platform
            PrompterConfig config;
            if (OSUtils.IS_WINDOWS) {
                config = PrompterConfig.custom(">", "( )", "(x)", "( )", false);
            } else {
                config = PrompterConfig.custom("\u276F", "\u25EF ", "\u25C9 ", "\u25EF ", false);
            }

            Prompter prompter = PrompterFactory.create(terminal, config);

            List<AttributedString> header = Arrays.asList(
                    new AttributedStringBuilder()
                            .style(ITALIC_GREEN)
                            .append("Hello Dynamic World!")
                            .toAttributedString(),
                    new AttributedString(
                            "This is a demonstration of the new JLine Prompt API. It provides a simple console interface"),
                    new AttributedString(
                            "for querying information from the user. This API is inspired by Inquirer.js and replaces"),
                    new AttributedString("the deprecated ConsoleUI module."));

            // Start the dynamic prompt flow using the proper dynamic prompting API
            Map<String, ? extends PromptResult<? extends Prompt>> results =
                    prompter.prompt(header, PromptDynamicExample::createDynamicPrompts);

            System.out.println("result = " + results);
            if (results.isEmpty()) {
                System.out.println("User cancelled order.");
            } else {
                ConfirmResult delivery = (ConfirmResult) results.get("delivery");
                if (delivery != null && delivery.isConfirmed()) {
                    System.out.println("We will deliver the order in 5 minutes");
                }
            }
        } catch (UserInterruptException e) {
            System.out.println("<ctrl>-c pressed");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Dynamic prompt provider that creates prompts based on previous results.
     */
    private static List<? extends Prompt> createDynamicPrompts(
            Map<String, ? extends PromptResult<? extends Prompt>> previousResults) {

        // Step 1: If no previous results, start with initial prompts
        if (previousResults.isEmpty()) {
            return createInitialPrompts();
        }

        // Step 2: If we have name and product, create product-specific prompts
        if (previousResults.containsKey("name")
                && previousResults.containsKey("product")
                && !previousResults.containsKey("pizzatype")
                && !previousResults.containsKey("hamburgertype")) {

            String product = ((ListResult) previousResults.get("product")).getSelectedId();
            if ("pizza".equals(product)) {
                return createPizzaPrompts();
            } else {
                return createHamburgerPrompts();
            }
        }

        // Step 3: If we have product-specific results, create final prompts
        if ((previousResults.containsKey("pizzatype") || previousResults.containsKey("hamburgertype"))
                && !previousResults.containsKey("payment")) {
            return createFinalPrompts();
        }

        // No more prompts needed
        return null;
    }

    private static List<? extends Prompt> createInitialPrompts() {
        PromptBuilder builder = new DefaultPromptBuilder();

        builder.createInputPrompt()
                .name("name")
                .message("Please enter your name")
                .defaultValue("John Doe")
                .addPrompt();

        builder.createListPrompt()
                .name("product")
                .message("What do you want to order?")
                .newItem("pizza")
                .text("Pizza")
                .add()
                .newItem("hamburger")
                .text("Hamburger")
                .add()
                .addPrompt();

        return builder.build();
    }

    private static List<? extends Prompt> createPizzaPrompts() {
        PromptBuilder builder = new DefaultPromptBuilder();

        builder.createListPrompt()
                .name("pizzatype")
                .message("Which pizza do you want?")
                .newItem("margherita")
                .text("Margherita")
                .add()
                .newItem("veneziana")
                .text("Veneziana")
                .add()
                .newItem("hawai")
                .text("Hawai")
                .add()
                .newItem("quattro")
                .text("Quattro Stagioni")
                .add()
                .addPrompt();

        builder.createCheckboxPrompt()
                .name("topping")
                .message("Please select additional toppings:")
                .newItem("cheese")
                .text("Cheese")
                .add()
                .newItem("bacon")
                .text("Bacon")
                .add()
                .newItem("salami")
                .text("Very hot salami")
                .checked(true)
                .add()
                .newItem("salmon")
                .text("Smoked Salmon")
                .add()
                .newItem("special")
                .text("Anchovies, and olives")
                .checked(true)
                .add()
                .addPrompt();

        return builder.build();
    }

    private static List<? extends Prompt> createHamburgerPrompts() {
        PromptBuilder builder = new DefaultPromptBuilder();

        builder.createListPrompt()
                .name("hamburgertype")
                .message("Which hamburger do you want?")
                .newItem("cheese")
                .text("Cheeseburger")
                .add()
                .newItem("chicken")
                .text("Chickenburger")
                .add()
                .newItem("veggie")
                .text("Veggieburger")
                .add()
                .addPrompt();

        builder.createCheckboxPrompt()
                .name("ingredients")
                .message("Please select additional ingredients:")
                .newItem("tomato")
                .text("Tomato")
                .add()
                .newItem("lettuce")
                .text("Lettuce")
                .add()
                .newItem("crispybacon")
                .text("Crispy Bacon")
                .checked(true)
                .add()
                .addPrompt();

        return builder.build();
    }

    private static List<? extends Prompt> createFinalPrompts() {
        PromptBuilder builder = new DefaultPromptBuilder();

        builder.createListPrompt()
                .name("payment")
                .message("How do you want to pay?")
                .newItem("cash")
                .text("Cash")
                .add()
                .newItem("visa")
                .text("Visa Card")
                .add()
                .newItem("master")
                .text("Master Card")
                .add()
                .newItem("paypal")
                .text("Paypal")
                .add()
                .addPrompt();

        builder.createConfirmPrompt()
                .name("delivery")
                .message("Is this order for delivery?")
                .defaultValue(true)
                .addPrompt();

        return builder.build();
    }
}
