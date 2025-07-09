/*
 * Copyright (c) 2024, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package org.jline.prompt.impl;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.prompt.*;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Display;

import static org.jline.keymap.KeyMap.*;
import static org.jline.utils.InfoCmp.Capability.*;

/**
 * Default implementation of the Prompter interface.
 * This is now the native implementation that doesn't depend on console-ui.
 */
public class DefaultPrompter implements Prompter {

    private final Terminal terminal;
    private final LineReader reader;
    private final PrompterConfig config;
    private final Display display;
    private final BindingReader bindingReader;
    private Attributes attributes;
    private List<AttributedString> header = new ArrayList<>();

    // Default timeout for escape sequences
    public static final long DEFAULT_TIMEOUT_WITH_ESC = 150L;

    // Default page size for lists
    private static final int DEFAULT_PAGE_SIZE = 10;

    // Terminal size tracking
    private final Size size = new Size();

    // List range for pagination
    private ListRange range = null;

    // First row where items start (after header and message)
    private int firstItemRow;

    // Column layout support
    private int columns = 1;
    private int lines = 1;
    private boolean rowsFirst = true; // true = row-first layout, false = column-first
    private static final int MARGIN_BETWEEN_COLUMNS = 2;
    private static final int MIN_ITEMS_FOR_MULTICOLUMN =
            6; // Minimum items to enable multi-column layout (increased from 4)

    /**
     * Create a new DefaultPrompter with the given terminal.
     *
     * @param terminal the terminal to use
     */
    public DefaultPrompter(Terminal terminal) {
        this(null, terminal, PrompterConfig.defaults());
    }

    /**
     * Create a new DefaultPrompter with the given terminal and configuration.
     *
     * @param terminal the terminal to use
     * @param config the configuration to use
     */
    public DefaultPrompter(Terminal terminal, PrompterConfig config) {
        this(null, terminal, config);
    }

    /**
     * Create a new DefaultPrompter with the given line reader, terminal, and configuration.
     *
     * @param reader the line reader to use
     * @param terminal the terminal to use
     * @param config the configuration to use
     */
    public DefaultPrompter(LineReader reader, Terminal terminal, PrompterConfig config) {
        this.terminal = terminal;
        this.config = config;
        if (reader == null) {
            reader = LineReaderBuilder.builder().terminal(terminal).build();
        }
        this.reader = reader;
        this.display = new Display(terminal, false); // Don't use full screen mode like console-ui
        this.bindingReader = new BindingReader(terminal.reader());
    }

    // Operation enums for different prompt types
    private enum ListOperation {
        FORWARD_ONE_LINE,
        BACKWARD_ONE_LINE,
        FORWARD_ONE_COLUMN,
        BACKWARD_ONE_COLUMN,
        INSERT,
        EXIT,
        CANCEL,
        ESCAPE,
        IGNORE // For unmatched keys (like console-ui behavior)
    }

    private enum CheckboxOperation {
        FORWARD_ONE_LINE,
        BACKWARD_ONE_LINE,
        FORWARD_ONE_COLUMN,
        BACKWARD_ONE_COLUMN,
        TOGGLE,
        EXIT,
        CANCEL,
        ESCAPE,
        IGNORE // For unmatched keys (like console-ui behavior)
    }

    private enum ChoiceOperation {
        INSERT,
        EXIT,
        CANCEL,
        ESCAPE
    }

    @Override
    public PromptBuilder newBuilder() {
        return new DefaultPromptBuilder();
    }

    @Override
    public Map<String, ? extends PromptResult<? extends Prompt>> prompt(
            List<AttributedString> header, List<? extends Prompt> prompts) throws IOException, UserInterruptException {
        // Handle empty prompt list directly
        if (prompts == null || prompts.isEmpty()) {
            return new HashMap<>();
        }

        // Simple implementation for static lists that follows ConsolePrompt patterns
        Map<String, PromptResult<? extends Prompt>> resultMap = new HashMap<>();

        try {
            open();

            promptInternal(header, prompts, resultMap, config.cancellableFirstPrompt());

            return removeNoResults(resultMap);
        } finally {
            close();
        }
    }

    @Override
    public Map<String, ? extends PromptResult<? extends Prompt>> prompt(
            List<AttributedString> header,
            Function<Map<String, ? extends PromptResult<? extends Prompt>>, List<? extends Prompt>> promptsProvider)
            throws IOException {

        Map<String, PromptResult<? extends Prompt>> resultMap = new HashMap<>();
        Deque<List<? extends Prompt>> prevLists = new ArrayDeque<>();
        Deque<Map<String, PromptResult<? extends Prompt>>> prevResults = new ArrayDeque<>();
        boolean cancellable = config.cancellableFirstPrompt();

        header = new ArrayList<>(header);
        try {
            open();
            // Get our first list of prompts
            List<? extends Prompt> promptList = promptsProvider.apply(new HashMap<>());
            Map<String, PromptResult<? extends Prompt>> promptResult = new HashMap<>();

            while (promptList != null) {
                // Second and later prompts should always be cancellable
                boolean cancellableFirstPrompt = !prevLists.isEmpty() || cancellable;

                // Prompt the user
                promptInternal(header, promptList, promptResult, cancellableFirstPrompt);

                if (promptResult.isEmpty()) {
                    // The prompt was cancelled by the user, so let's go back to the
                    // previous list of prompts and its results (if any)
                    promptList = prevLists.pollFirst();
                    promptResult = prevResults.pollFirst();
                    if (promptResult != null) {
                        // Remove the results of the previous prompt from the main result map
                        promptResult.forEach((k, v) -> resultMap.remove(k));
                        header.remove(header.size() - 1);
                    }
                } else {
                    // We remember the list of prompts and their results
                    prevLists.push(promptList);
                    prevResults.push(promptResult);
                    // Add the results to the main result map
                    resultMap.putAll(promptResult);
                    // And we get our next list of prompts (if any)
                    promptList = promptsProvider.apply(resultMap);
                    promptResult = new HashMap<>();
                }
            }

            return removeNoResults(resultMap);
        } finally {
            close();
        }
    }

    @Override
    public Terminal getTerminal() {
        return terminal;
    }

    /**
     * Internal prompt method that mirrors ConsolePrompt.prompt() logic.
     * Handles header accumulation and backward navigation.
     */
    protected void promptInternal(
            List<AttributedString> headerIn,
            List<? extends Prompt> promptList,
            Map<String, PromptResult<? extends Prompt>> resultMap,
            boolean cancellableFirstPrompt)
            throws IOException {

        if (!terminalInRawMode()) {
            throw new IllegalStateException("Terminal is not in raw mode! Maybe Prompter is closed?");
        }

        // Initialize header from input
        this.header = headerIn;

        boolean backward = false;
        for (int i = resultMap.isEmpty() ? 0 : resultMap.size() - 1; i < promptList.size(); i++) {
            Prompt prompt = promptList.get(i);
            try {
                if (backward) {
                    removePreviousResult(prompt, resultMap);
                    backward = false;
                }

                PromptResult<? extends Prompt> oldResult = resultMap.get(prompt.getName());
                PromptResult<? extends Prompt> result = promptElement(this.header, prompt, oldResult);

                if (result == null) {
                    // Prompt was cancelled by the user
                    if (i > 0) {
                        // Go back to previous prompt
                        i -= 2;
                        backward = true;
                        continue;
                    } else {
                        if (cancellableFirstPrompt) {
                            resultMap.clear();
                            return;
                        } else {
                            // Repeat current prompt
                            i -= 1;
                            continue;
                        }
                    }
                }

                // Add result to header for next prompt (like ConsolePrompt)
                if (prompt instanceof TextPrompt) {
                    // For text prompts, add the text content to header
                    TextPrompt textPrompt = (TextPrompt) prompt;
                    this.header.add(new AttributedString(textPrompt.getText()));
                } else {
                    String resp = result.getResult();
                    if (result instanceof ConfirmResult) {
                        ConfirmResult cr = (ConfirmResult) result;
                        resp = cr.getConfirmed() == ConfirmResult.ConfirmationValue.YES ? "Yes" : "No";
                    }
                    AttributedStringBuilder message = createMessage(prompt.getMessage(), resp);
                    this.header.add(message.toAttributedString());
                }

                resultMap.put(prompt.getName(), result);
            } catch (UserInterruptException e) {
                throw e;
            } catch (Exception e) {
                // Log error and continue
                terminal.writer().println("Error executing prompt '" + prompt.getName() + "': " + e.getMessage());
                terminal.flush();
            }
        }
    }

    @Override
    public LineReader getLineReader() {
        return reader;
    }

    /**
     * Remove results that have no meaningful value (like ConsolePrompt).
     */
    private Map<String, PromptResult<? extends Prompt>> removeNoResults(
            Map<String, PromptResult<? extends Prompt>> resultMap) {
        Map<String, PromptResult<? extends Prompt>> filtered = new HashMap<>();
        for (Map.Entry<String, PromptResult<? extends Prompt>> entry : resultMap.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getResult() != null) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    /**
     * Remove previous result when going backward (like ConsolePrompt).
     */
    private void removePreviousResult(Prompt prompt, Map<String, PromptResult<? extends Prompt>> resultMap) {
        resultMap.remove(prompt.getName());
        // Also remove from header if it was added
        if (!this.header.isEmpty()) {
            this.header.remove(this.header.size() - 1);
        }
    }

    /**
     * Create a message with prompt and response (like ConsolePrompt).
     */
    private AttributedStringBuilder createMessage(String message, String response) {
        AttributedStringBuilder asb = new AttributedStringBuilder();
        asb.style(config.style(PrompterConfig.PR)).append("? ");
        asb.style(config.style(PrompterConfig.ME)).append(message).append(" ");
        if (response != null) {
            asb.style(config.style(PrompterConfig.AN)).append(response);
        }
        return asb;
    }

    /**
     * Execute a single prompt element (like ConsolePrompt.promptElement).
     */
    protected PromptResult<? extends Prompt> promptElement(
            List<AttributedString> header, Prompt prompt, PromptResult<? extends Prompt> oldResult)
            throws UserInterruptException {
        try {
            // Header is managed by individual prompt methods (like ConsolePrompt)
            return executePrompt(header, prompt);
        } catch (UserInterruptException e) {
            // Propagate Ctrl+C to exit the whole demo
            throw e;
        } catch (Exception e) {
            terminal.writer().println("Error: " + e.getMessage());
            terminal.flush();
            return null;
        }
    }

    /**
     * Execute a single prompt and return its result.
     */
    @SuppressWarnings("unchecked")
    private PromptResult<? extends Prompt> executePrompt(List<AttributedString> header, Prompt prompt)
            throws IOException, UserInterruptException {

        if (prompt instanceof InputPrompt) {
            return executeInputPrompt(header, (InputPrompt) prompt);
        } else if (prompt instanceof ListPrompt) {
            return executeListPrompt(header, (ListPrompt) prompt);
        } else if (prompt instanceof CheckboxPrompt) {
            return executeCheckboxPrompt(header, (CheckboxPrompt) prompt);
        } else if (prompt instanceof ChoicePrompt) {
            return executeChoicePrompt(header, (ChoicePrompt) prompt);
        } else if (prompt instanceof ConfirmPrompt) {
            return executeConfirmPrompt(header, (ConfirmPrompt) prompt);
        } else if (prompt instanceof TextPrompt) {
            return executeTextPrompt(header, (TextPrompt) prompt);
        } else {
            throw new IllegalArgumentException("Unknown prompt type: " + prompt.getClass());
        }
    }

    private InputResult executeInputPrompt(List<AttributedString> header, InputPrompt prompt)
            throws IOException, UserInterruptException {

        // Build display lines including header + prompt message
        List<AttributedString> displayLines = new ArrayList<>();
        if (header != null) {
            displayLines.addAll(header);
        }

        // Create prompt message using proper styling like ConsolePrompt
        AttributedStringBuilder asb = createMessage(prompt.getMessage(), null);

        String defaultValue = prompt.getDefaultValue();
        if (defaultValue != null) {
            asb.append("(").append(defaultValue).append(") ");
        }
        displayLines.add(asb.toAttributedString());

        // Copy ConsolePrompt's exact behavior: use Display system with direct character input
        size.copy(terminal.getSize());

        // Set up key bindings like ConsolePrompt
        KeyMap<InputOperation> keyMap = new KeyMap<>();
        bindInputKeys(keyMap);

        StringBuilder buffer = new StringBuilder();
        StringBuilder displayBuffer = new StringBuilder();
        Character mask = prompt.getMask();
        int startColumn = asb.columnLength();
        int column = startColumn;

        while (true) {
            // Build display lines exactly like ConsolePrompt: header + message + buffer
            List<AttributedString> out = new ArrayList<>();
            if (header != null) {
                out.addAll(header);
            }

            // Create message line with current input buffer
            AttributedStringBuilder messageBuilder = new AttributedStringBuilder();
            messageBuilder.append(asb);
            if (mask != null) {
                messageBuilder.append(displayBuffer.toString());
            } else {
                messageBuilder.append(buffer.toString());
            }
            out.add(messageBuilder.toAttributedString());

            // Update display exactly like ConsolePrompt
            display.resize(size.getRows(), size.getColumns());
            int cursorRow = out.size() - 1;
            display.update(out, size.cursorPos(cursorRow, column));

            // Read input like ConsolePrompt
            InputOperation op = bindingReader.readBinding(keyMap);
            switch (op) {
                case INSERT:
                    String ch = bindingReader.getLastBinding();
                    buffer.append(ch);
                    if (mask != null) {
                        displayBuffer.append(mask);
                    } else {
                        displayBuffer.append(ch);
                    }
                    column++;
                    break;

                case BACKSPACE:
                    if (buffer.length() > 0) {
                        buffer.deleteCharAt(buffer.length() - 1);
                        if (displayBuffer.length() > 0) {
                            displayBuffer.deleteCharAt(displayBuffer.length() - 1);
                        }
                        if (column > startColumn) {
                            column--;
                        }
                    }
                    break;

                case EXIT:
                    String input = buffer.toString();
                    if (input.trim().isEmpty() && defaultValue != null) {
                        input = defaultValue;
                    }
                    return new DefaultInputResult(input, input, prompt);

                case ESCAPE:
                    return null; // Go back to previous prompt

                case CANCEL:
                    throw new UserInterruptException("User cancelled");
            }
        }
    }

    /**
     * Input operations for direct character input (copied from ConsolePrompt).
     */
    private enum InputOperation {
        INSERT,
        BACKSPACE,
        DELETE,
        RIGHT,
        LEFT,
        UP,
        DOWN,
        BEGINNING_OF_LINE,
        END_OF_LINE,
        SELECT_CANDIDATE,
        EXIT,
        CANCEL,
        ESCAPE
    }

    /**
     * Confirm operations for direct character input (copied from ConsolePrompt).
     */
    private enum ConfirmOperation {
        YES,
        NO,
        EXIT,
        CANCEL,
        ESCAPE
    }

    /**
     * Bind keys for input operations (copied from ConsolePrompt).
     */
    private void bindInputKeys(KeyMap<InputOperation> keyMap) {
        // Bind printable characters to INSERT
        keyMap.setUnicode(InputOperation.INSERT);
        for (char i = 32; i < 127; i++) {
            keyMap.bind(InputOperation.INSERT, Character.toString(i));
        }

        // Bind special keys like ConsolePrompt
        keyMap.bind(InputOperation.BACKSPACE, "\b", "\u007f");
        keyMap.bind(InputOperation.DELETE, "\u001b[3~");
        keyMap.bind(InputOperation.EXIT, "\r", "\n");
        keyMap.bind(InputOperation.CANCEL, "\u0003"); // Ctrl+C
        keyMap.bind(InputOperation.ESCAPE, "\u001b"); // Escape key
        keyMap.bind(InputOperation.LEFT, "\u001b[D");
        keyMap.bind(InputOperation.RIGHT, "\u001b[C");
        keyMap.bind(InputOperation.UP, "\u001b[A");
        keyMap.bind(InputOperation.DOWN, "\u001b[B");
        keyMap.bind(InputOperation.BEGINNING_OF_LINE, "\u0001"); // Ctrl+A
        keyMap.bind(InputOperation.END_OF_LINE, "\u0005"); // Ctrl+E
        keyMap.bind(InputOperation.SELECT_CANDIDATE, "\t"); // Tab for completion
    }

    /**
     * Bind keys for confirm operations (copied from ConsolePrompt).
     */
    private void bindConfirmKeys(KeyMap<ConfirmOperation> keyMap) {
        keyMap.bind(ConfirmOperation.YES, "y", "Y");
        keyMap.bind(ConfirmOperation.NO, "n", "N");
        keyMap.bind(ConfirmOperation.EXIT, "\r", "\n");
        keyMap.bind(ConfirmOperation.CANCEL, "\u0003"); // Ctrl+C
        keyMap.bind(ConfirmOperation.ESCAPE, "\u001b"); // Escape key
    }

    private ListResult executeListPrompt(List<AttributedString> header, ListPrompt prompt)
            throws IOException, UserInterruptException {

        List<ListItem> items = prompt.getItems();
        if (items.isEmpty()) {
            return new DefaultListResult("", prompt);
        }

        // Initialize display
        resetDisplay();
        firstItemRow = (header != null ? header.size() : 0) + 1; // Header + message line

        // Calculate column layout
        calculateColumnLayout(items);

        // Find first selectable item
        int selectRow = nextRow(firstItemRow - 1, firstItemRow, items);

        // Set up key bindings
        KeyMap<ListOperation> keyMap = new KeyMap<>();
        bindListKeys(keyMap, columns > 1); // Only bind column navigation for multi-column layouts

        // Interactive selection loop
        while (true) {
            // Update display with current selection
            refreshListDisplay(header, prompt.getMessage(), items, selectRow);

            // Read user input using BindingReader
            ListOperation op = bindingReader.readBinding(keyMap);

            switch (op) {
                case FORWARD_ONE_LINE:
                    selectRow = nextRow(selectRow, firstItemRow, items);
                    break;
                case BACKWARD_ONE_LINE:
                    selectRow = prevRow(selectRow, firstItemRow, items);
                    break;
                case FORWARD_ONE_COLUMN:
                    selectRow = nextColumn(selectRow, firstItemRow, items, columns, lines, rowsFirst);
                    break;
                case BACKWARD_ONE_COLUMN:
                    selectRow = prevColumn(selectRow, firstItemRow, items, columns, lines, rowsFirst);
                    break;
                case INSERT:
                    // Handle character-based selection (if items have keys)
                    String ch = bindingReader.getLastBinding();
                    int id = 0;
                    for (ListItem item : items) {
                        if (item instanceof ChoiceItem) {
                            ChoiceItem choiceItem = (ChoiceItem) item;
                            if (choiceItem.isSelectable()
                                    && choiceItem.getKey() != null
                                    && choiceItem.getKey().toString().equals(ch)) {
                                selectRow = firstItemRow + id;
                                break;
                            }
                        }
                        id++;
                    }
                    break;
                case EXIT:
                    ListItem selectedItem = items.get(selectRow - firstItemRow);
                    return new DefaultListResult(selectedItem.getName(), prompt);
                case ESCAPE:
                    return null; // Go back to previous prompt
                case CANCEL:
                    throw new UserInterruptException("User cancelled");
                case IGNORE:
                    // Ignore unmatched keys (like console-ui behavior)
                    break;
            }
        }
    }

    private CheckboxResult executeCheckboxPrompt(List<AttributedString> header, CheckboxPrompt prompt)
            throws IOException, UserInterruptException {

        List<CheckboxItem> items = prompt.getItems();
        Set<String> selectedIds = new HashSet<>();

        // Initialize with initially checked items
        for (CheckboxItem item : items) {
            if (item.isInitiallyChecked()) {
                selectedIds.add(item.getName());
            }
        }

        if (items.isEmpty()) {
            return new DefaultCheckboxResult(selectedIds, prompt);
        }

        // Initialize display
        resetDisplay();
        firstItemRow = (header != null ? header.size() : 0) + 1; // Header + message line

        // Calculate column layout
        calculateColumnLayout(items);

        // Find first selectable item
        int selectRow = nextRow(firstItemRow - 1, firstItemRow, items);

        // Set up key bindings
        KeyMap<CheckboxOperation> keyMap = new KeyMap<>();
        bindCheckboxKeys(keyMap, columns > 1); // Only bind column navigation for multi-column layouts

        // Interactive selection loop
        while (true) {
            // Update display with current selection and checkbox states
            refreshCheckboxDisplay(header, prompt.getMessage(), items, selectRow, selectedIds);

            // Read user input using BindingReader
            CheckboxOperation op = bindingReader.readBinding(keyMap);

            switch (op) {
                case FORWARD_ONE_LINE:
                    selectRow = nextRow(selectRow, firstItemRow, items);
                    break;
                case BACKWARD_ONE_LINE:
                    selectRow = prevRow(selectRow, firstItemRow, items);
                    break;
                case FORWARD_ONE_COLUMN:
                    selectRow = nextColumn(selectRow, firstItemRow, items, columns, lines, rowsFirst);
                    break;
                case BACKWARD_ONE_COLUMN:
                    selectRow = prevColumn(selectRow, firstItemRow, items, columns, lines, rowsFirst);
                    break;
                case TOGGLE:
                    CheckboxItem currentItem = items.get(selectRow - firstItemRow);
                    if (currentItem.isSelectable()) {
                        if (selectedIds.contains(currentItem.getName())) {
                            selectedIds.remove(currentItem.getName());
                        } else {
                            selectedIds.add(currentItem.getName());
                        }
                    }
                    break;
                case EXIT:
                    return new DefaultCheckboxResult(selectedIds, prompt);
                case ESCAPE:
                    return null; // Go back to previous prompt
                case CANCEL:
                    throw new UserInterruptException("User cancelled");
                case IGNORE:
                    // Ignore unmatched keys (like console-ui behavior)
                    break;
            }
        }
    }

    private ChoiceResult executeChoicePrompt(List<AttributedString> header, ChoicePrompt prompt)
            throws IOException, UserInterruptException {

        size.copy(terminal.getSize());
        display.resize(size.getRows(), size.getColumns());

        List<ChoiceItem> items = prompt.getItems();
        if (items.isEmpty()) {
            return new DefaultChoiceResult("", prompt);
        }

        // Find default choice if any
        ChoiceItem defaultChoice = null;
        for (ChoiceItem item : items) {
            if (item.isDefaultChoice() && item.isSelectable()) {
                defaultChoice = item;
                break;
            }
        }

        // Build initial display with header, message, choices, and prompt
        List<AttributedString> out = new ArrayList<>();
        if (header != null) {
            out.addAll(header);
        }

        // Add message line
        AttributedStringBuilder messageBuilder = createMessage(prompt.getMessage(), null);
        out.add(messageBuilder.toAttributedString());

        // Add choice items
        out.addAll(buildChoiceItemsDisplay(items));

        // Add choice prompt line
        AttributedStringBuilder choiceBuilder = new AttributedStringBuilder();
        choiceBuilder.styled(config.style(PrompterConfig.PR), "Choice: ");
        out.add(choiceBuilder.toAttributedString());

        display.update(out, out.size() - 1);

        // Set up key bindings
        KeyMap<ChoiceOperation> keyMap = new KeyMap<>();
        bindChoiceKeys(keyMap);

        // Interactive selection loop
        while (true) {
            ChoiceOperation op = bindingReader.readBinding(keyMap);

            switch (op) {
                case INSERT:
                    // Check if the input character matches any choice key
                    String ch = bindingReader.getLastBinding();
                    for (ChoiceItem item : items) {
                        if (item.isSelectable()
                                && item.getKey() != null
                                && item.getKey().toString().equalsIgnoreCase(ch)) {
                            // Found matching choice - update display with answer
                            updateChoiceDisplay(out, ch);
                            return new DefaultChoiceResult(item.getName(), prompt);
                        }
                    }
                    // Invalid choice, continue waiting
                    break;
                case EXIT:
                    // Use default choice if available
                    if (defaultChoice != null) {
                        String defaultKey = defaultChoice.getKey() != null
                                ? defaultChoice.getKey().toString()
                                : "";
                        updateChoiceDisplay(out, defaultKey);
                        return new DefaultChoiceResult(defaultChoice.getName(), prompt);
                    }
                    // No default, continue waiting for input
                    break;
                case ESCAPE:
                    return null; // Go back to previous prompt
                case CANCEL:
                    throw new UserInterruptException("User cancelled");
            }
        }
    }

    /**
     * Update choice display with the selected answer.
     */
    private void updateChoiceDisplay(List<AttributedString> out, String answer) {
        // Update the last line (choice prompt) with the answer
        AttributedStringBuilder choiceBuilder = new AttributedStringBuilder();
        choiceBuilder.styled(config.style(PrompterConfig.PR), "Choice: ");
        choiceBuilder.styled(config.style(PrompterConfig.AN), answer);

        // Replace the last line with the updated one
        out.set(out.size() - 1, choiceBuilder.toAttributedString());
        display.update(out, -1);
    }

    private ConfirmResult executeConfirmPrompt(List<AttributedString> header, ConfirmPrompt prompt)
            throws IOException, UserInterruptException {

        // Copy ConsolePrompt's exact behavior for confirm prompts
        size.copy(terminal.getSize());

        // Set up key bindings like ConsolePrompt
        KeyMap<ConfirmOperation> keyMap = new KeyMap<>();
        bindConfirmKeys(keyMap);

        // Create prompt message using proper styling like ConsolePrompt
        AttributedStringBuilder asb = createMessage(prompt.getMessage(), null);
        asb.append("(y/N) ");

        ConfirmResult.ConfirmationValue confirm = ConfirmResult.ConfirmationValue.NO; // Default
        StringBuilder buffer = new StringBuilder();

        while (true) {
            // Build display lines exactly like ConsolePrompt: header + message + buffer
            List<AttributedString> out = new ArrayList<>();
            if (header != null) {
                out.addAll(header);
            }

            // Create message line with current input buffer
            AttributedStringBuilder messageBuilder = new AttributedStringBuilder();
            messageBuilder.append(asb);
            messageBuilder.append(buffer.toString());
            out.add(messageBuilder.toAttributedString());

            // Update display exactly like ConsolePrompt
            display.resize(size.getRows(), size.getColumns());
            int cursorRow = out.size() - 1;
            int column = asb.columnLength() + buffer.length();
            display.update(out, size.cursorPos(cursorRow, column));

            // Read input like ConsolePrompt
            ConfirmOperation op = bindingReader.readBinding(keyMap);
            switch (op) {
                case YES:
                    buffer = new StringBuilder("y");
                    confirm = ConfirmResult.ConfirmationValue.YES;
                    break;

                case NO:
                    buffer = new StringBuilder("n");
                    confirm = ConfirmResult.ConfirmationValue.NO;
                    break;

                case EXIT:
                    return new DefaultConfirmResult(confirm, prompt);

                case ESCAPE:
                    return null; // Go back to previous prompt

                case CANCEL:
                    throw new UserInterruptException("User cancelled");
            }
        }
    }

    private PromptResult<TextPrompt> executeTextPrompt(List<AttributedString> header, TextPrompt prompt)
            throws IOException, UserInterruptException {

        // Build display lines including header + text
        List<AttributedString> displayLines = new ArrayList<>();
        if (header != null) {
            displayLines.addAll(header);
        }

        // Add text content
        displayLines.add(new AttributedString(prompt.getText()));

        // Update size and display using Display system
        size.copy(terminal.getSize());
        display.resize(size.getRows(), size.getColumns());
        display.update(displayLines, -1);

        // Text prompts don't require user input, just display
        return new AbstractPromptResult<TextPrompt>(prompt) {
            @Override
            public String getResult() {
                return "TEXT_DISPLAYED";
            }
        };
    }

    private void displayPromptMessage(String message) {
        terminal.writer().print("? " + message + " ");
        terminal.flush();
    }

    private void displayText(String text) {
        terminal.writer().println(text);
        terminal.flush();
    }

    private void displayError(String error) {
        terminal.writer().println("Error: " + error);
        terminal.flush();
    }

    private void open() throws IOException {
        if (!terminalInRawMode()) {
            attributes = terminal.enterRawMode();
            terminal.puts(keypad_xmit);
            terminal.writer().flush();
        }
    }

    private void close() throws IOException {
        if (terminalInRawMode()) {
            // Update display with final header state
            int cursor = (terminal.getWidth() + 1) * header.size();
            display.update(header, cursor);
            terminal.setAttributes(attributes);
            terminal.puts(keypad_local);
            terminal.writer().println();
            terminal.writer().flush();
            attributes = null;
        }
    }

    private boolean terminalInRawMode() {
        return attributes != null;
    }

    /**
     * Bind keys for list prompt operations.
     */
    private void bindListKeys(KeyMap<ListOperation> map, boolean multiColumn) {
        // Bind printable characters to INSERT operation
        for (char i = 32; i < KEYMAP_LENGTH; i++) {
            map.bind(ListOperation.INSERT, Character.toString(i));
        }
        // Bind navigation keys
        map.bind(ListOperation.FORWARD_ONE_LINE, "e", ctrl('E'), key(terminal, key_down));
        map.bind(ListOperation.BACKWARD_ONE_LINE, "y", ctrl('Y'), key(terminal, key_up));

        // Only bind column navigation for multi-column layouts (like console-ui behavior)
        if (multiColumn) {
            map.bind(ListOperation.FORWARD_ONE_COLUMN, key(terminal, key_right));
            map.bind(ListOperation.BACKWARD_ONE_COLUMN, key(terminal, key_left));
        }

        // Bind action keys
        map.bind(ListOperation.EXIT, "\r");
        map.bind(ListOperation.ESCAPE, esc()); // Escape goes back to previous prompt
        map.bind(ListOperation.CANCEL, ctrl('C')); // Ctrl+C cancels

        // Set up fallback for unmatched keys (like console-ui behavior)
        map.setNomatch(ListOperation.IGNORE);
        map.setAmbiguousTimeout(DEFAULT_TIMEOUT_WITH_ESC);
    }

    /**
     * Refresh the display for list prompts using JLine's Display class.
     */
    private void refreshListDisplay(
            List<AttributedString> header, String message, List<ListItem> items, int cursorRow) {
        size.copy(terminal.getSize());
        display.resize(size.getRows(), size.getColumns());
        display.update(
                buildListDisplayLines(header, message, items, cursorRow),
                size.cursorPos(Math.min(size.getRows() - 1, firstItemRow + items.size()), 0));
    }

    /**
     * Build display lines for list prompts with column layout support.
     */
    private List<AttributedString> buildListDisplayLines(
            List<AttributedString> header, String message, List<ListItem> items, int cursorRow) {
        List<AttributedString> out = new ArrayList<>(header != null ? header : new ArrayList<>());

        // Add message line
        AttributedStringBuilder asb = new AttributedStringBuilder();
        asb.append(message);
        out.add(asb.toAttributedString());

        if (columns == 1) {
            // Single column layout - use original logic with pagination
            computeListRange(cursorRow, items.size());
            for (int i = range.first; i < range.last; i++) {
                if (items.isEmpty() || i > items.size() - 1) {
                    break;
                }
                out.add(buildSingleItemLine(items.get(i), i + firstItemRow == cursorRow));
            }
        } else {
            // Multi-column layout
            out.addAll(buildMultiColumnLines(items, cursorRow));
        }

        return out;
    }

    /**
     * Build a single item line for display.
     */
    private AttributedString buildSingleItemLine(ListItem item, boolean isSelected) {
        AttributedStringBuilder asb = new AttributedStringBuilder();

        // Add selection indicator and key if available
        String key = item instanceof ChoiceItem ? ((ChoiceItem) item).getKey() + " - " : "";
        if (isSelected) {
            asb.styled(config.style(PrompterConfig.CURSOR), config.indicator())
                    .style(config.style(PrompterConfig.SE))
                    .append(" ")
                    .append(key)
                    .append(item.getText());
        } else if (item.isSelectable()) {
            fillIndicatorSpace(asb);
            asb.append(" ").append(key).append(item.getText());
        } else {
            // Disabled item - use proper styling
            fillIndicatorSpace(asb);
            asb.append(" ").append(key);
            if (item.isDisabled()) {
                asb.styled(config.style(PrompterConfig.BD), item.getDisabledText())
                        .append(" (")
                        .styled(config.style(PrompterConfig.BD), item.getDisabledText())
                        .append(")");
            } else {
                asb.styled(config.style(PrompterConfig.BD), item.getText());
            }
        }

        return asb.toAttributedString();
    }

    /**
     * Build multi-column layout lines.
     */
    private List<AttributedString> buildMultiColumnLines(List<ListItem> items, int cursorRow) {
        List<AttributedString> out = new ArrayList<>();

        // Calculate column width
        int terminalWidth = size.getColumns();
        int columnWidth = (terminalWidth - (columns - 1) * MARGIN_BETWEEN_COLUMNS) / columns;

        for (int row = 0; row < lines; row++) {
            AttributedStringBuilder lineBuilder = new AttributedStringBuilder();

            for (int col = 0; col < columns; col++) {
                int index = gridToIndex(row, col, items.size());
                if (index >= 0 && index < items.size()) {
                    ListItem item = items.get(index);
                    boolean isSelected = (index + firstItemRow) == cursorRow;

                    // Build item text
                    AttributedStringBuilder itemBuilder = new AttributedStringBuilder();
                    String key = item instanceof ChoiceItem ? ((ChoiceItem) item).getKey() + " - " : "";

                    if (isSelected) {
                        itemBuilder
                                .styled(config.style(PrompterConfig.CURSOR), config.indicator())
                                .style(AttributedStyle.DEFAULT.inverse())
                                .append(" ")
                                .append(key)
                                .append(item.getText());
                    } else if (item.isSelectable()) {
                        fillIndicatorSpace(itemBuilder);
                        itemBuilder.append(" ").append(key).append(item.getText());
                    } else {
                        // Disabled item
                        fillIndicatorSpace(itemBuilder);
                        itemBuilder.append(" ").append(key);
                        if (item.isDisabled()) {
                            itemBuilder.styled(config.style(PrompterConfig.BD), item.getDisabledText());
                        } else {
                            itemBuilder.append(item.getText());
                        }
                    }

                    // Pad to column width
                    String itemText = itemBuilder.toString();
                    int itemLength = display.wcwidth(itemText);
                    lineBuilder.append(itemText);

                    // Add padding to reach column width
                    for (int i = itemLength; i < columnWidth; i++) {
                        lineBuilder.append(' ');
                    }

                    // Add margin between columns (except for last column)
                    if (col < columns - 1) {
                        for (int i = 0; i < MARGIN_BETWEEN_COLUMNS; i++) {
                            lineBuilder.append(' ');
                        }
                    }
                }
            }

            out.add(lineBuilder.toAttributedString());
        }

        return out;
    }

    /**
     * Fill space for indicator alignment.
     */
    private AttributedStringBuilder fillIndicatorSpace(AttributedStringBuilder asb) {
        for (int i = 0; i < display.wcwidth(config.indicator()); i++) {
            asb.append(" ");
        }
        return asb;
    }

    /**
     * Bind keys for checkbox prompt operations.
     */
    private void bindCheckboxKeys(KeyMap<CheckboxOperation> map, boolean multiColumn) {
        // Bind navigation keys
        map.bind(CheckboxOperation.FORWARD_ONE_LINE, "e", ctrl('E'), key(terminal, key_down));
        map.bind(CheckboxOperation.BACKWARD_ONE_LINE, "y", ctrl('Y'), key(terminal, key_up));

        // Only bind column navigation for multi-column layouts (like console-ui behavior)
        if (multiColumn) {
            map.bind(CheckboxOperation.FORWARD_ONE_COLUMN, key(terminal, key_right));
            map.bind(CheckboxOperation.BACKWARD_ONE_COLUMN, key(terminal, key_left));
        }

        // Bind toggle key
        map.bind(CheckboxOperation.TOGGLE, " ");
        // Bind action keys
        map.bind(CheckboxOperation.EXIT, "\r");
        map.bind(CheckboxOperation.ESCAPE, esc()); // Escape goes back to previous prompt
        map.bind(CheckboxOperation.CANCEL, ctrl('C')); // Ctrl+C cancels

        // Set up fallback for unmatched keys (like console-ui behavior)
        map.setNomatch(CheckboxOperation.IGNORE);
        map.setAmbiguousTimeout(DEFAULT_TIMEOUT_WITH_ESC);
    }

    /**
     * Refresh the display for checkbox prompts using JLine's Display class.
     */
    private void refreshCheckboxDisplay(
            List<AttributedString> header,
            String message,
            List<CheckboxItem> items,
            int cursorRow,
            Set<String> selectedIds) {
        size.copy(terminal.getSize());
        display.resize(size.getRows(), size.getColumns());
        display.update(
                buildCheckboxDisplayLines(header, message, items, cursorRow, selectedIds),
                size.cursorPos(Math.min(size.getRows() - 1, firstItemRow + items.size()), 0));
    }

    /**
     * Build display lines for checkbox prompts with column layout support.
     */
    private List<AttributedString> buildCheckboxDisplayLines(
            List<AttributedString> header,
            String message,
            List<CheckboxItem> items,
            int cursorRow,
            Set<String> selectedIds) {
        List<AttributedString> out = new ArrayList<>(header != null ? header : new ArrayList<>());

        // Add message line
        AttributedStringBuilder asb = new AttributedStringBuilder();
        asb.append(message);
        out.add(asb.toAttributedString());

        if (columns == 1) {
            // Single column layout - use original logic with pagination
            computeListRange(cursorRow, items.size());
            for (int i = range.first; i < range.last; i++) {
                if (items.isEmpty() || i > items.size() - 1) {
                    break;
                }
                out.add(buildSingleCheckboxLine(items.get(i), i + firstItemRow == cursorRow, selectedIds));
            }
        } else {
            // Multi-column layout
            out.addAll(buildMultiColumnCheckboxLines(items, cursorRow, selectedIds));
        }

        return out;
    }

    /**
     * Build a single checkbox item line for display.
     */
    private AttributedString buildSingleCheckboxLine(CheckboxItem item, boolean isSelected, Set<String> selectedIds) {
        AttributedStringBuilder asb = new AttributedStringBuilder();

        if (item.isSelectable()) {
            // Selection indicator
            if (isSelected) {
                asb.styled(config.style(PrompterConfig.CURSOR), config.indicator());
            } else {
                fillIndicatorSpace(asb);
            }
            asb.append(" ");

            // Checkbox state
            if (selectedIds.contains(item.getName())) {
                asb.styled(config.style(PrompterConfig.BE), config.checkedBox());
            } else {
                asb.styled(config.style(PrompterConfig.BE), config.uncheckedBox());
            }
        } else {
            // Disabled item
            fillIndicatorSpace(asb);
            asb.append(" ");
            if (item.isDisabled()) {
                asb.styled(config.style(PrompterConfig.BD), config.unavailable());
            } else {
                fillCheckboxSpace(asb);
            }
        }

        // Item text
        asb.append(item.getText());
        if (item.isDisabled()) {
            asb.append(" (").append(item.getDisabledText()).append(")");
        }

        return asb.toAttributedString();
    }

    /**
     * Build multi-column checkbox layout lines.
     */
    private List<AttributedString> buildMultiColumnCheckboxLines(
            List<CheckboxItem> items, int cursorRow, Set<String> selectedIds) {
        List<AttributedString> out = new ArrayList<>();

        // Calculate column width
        int terminalWidth = size.getColumns();
        int columnWidth = (terminalWidth - (columns - 1) * MARGIN_BETWEEN_COLUMNS) / columns;

        for (int row = 0; row < lines; row++) {
            AttributedStringBuilder lineBuilder = new AttributedStringBuilder();

            for (int col = 0; col < columns; col++) {
                int index = gridToIndex(row, col, items.size());
                if (index >= 0 && index < items.size()) {
                    CheckboxItem item = items.get(index);
                    boolean isSelected = (index + firstItemRow) == cursorRow;

                    // Build item text
                    AttributedStringBuilder itemBuilder = new AttributedStringBuilder();

                    if (item.isSelectable()) {
                        // Selection indicator
                        if (isSelected) {
                            itemBuilder
                                    .styled(config.style(PrompterConfig.CURSOR), config.indicator())
                                    .style(AttributedStyle.DEFAULT)
                                    .append(" ");
                        } else {
                            fillIndicatorSpace(itemBuilder).append(" ");
                        }

                        // Checkbox state
                        if (selectedIds.contains(item.getName())) {
                            itemBuilder.styled(config.style(PrompterConfig.BE), config.checkedBox());
                        } else {
                            itemBuilder.styled(config.style(PrompterConfig.BE), config.uncheckedBox());
                        }
                    } else {
                        // Disabled item
                        fillIndicatorSpace(itemBuilder);
                        itemBuilder.append(" ");
                        if (item.isDisabled()) {
                            itemBuilder.styled(config.style(PrompterConfig.BD), config.unavailable());
                        } else {
                            fillCheckboxSpace(itemBuilder);
                        }
                    }

                    // Item text
                    itemBuilder.append(item.getText());
                    if (item.isDisabled()) {
                        itemBuilder.append(" (").append(item.getDisabledText()).append(")");
                    }

                    // Pad to column width
                    String itemText = itemBuilder.toString();
                    int itemLength = display.wcwidth(itemText);
                    lineBuilder.append(itemText);

                    // Add padding to reach column width
                    for (int i = itemLength; i < columnWidth; i++) {
                        lineBuilder.append(' ');
                    }

                    // Add margin between columns (except for last column)
                    if (col < columns - 1) {
                        for (int i = 0; i < MARGIN_BETWEEN_COLUMNS; i++) {
                            lineBuilder.append(' ');
                        }
                    }
                }
            }

            out.add(lineBuilder.toAttributedString());
        }

        return out;
    }

    /**
     * Fill space for checkbox alignment.
     */
    private void fillCheckboxSpace(AttributedStringBuilder asb) {
        for (int i = 0; i < display.wcwidth(config.checkedBox()); i++) {
            asb.append(" ");
        }
    }

    /**
     * Bind keys for choice prompt operations.
     */
    private void bindChoiceKeys(KeyMap<ChoiceOperation> map) {
        // Bind printable characters to INSERT operation
        for (char i = 32; i < KEYMAP_LENGTH; i++) {
            map.bind(ChoiceOperation.INSERT, Character.toString(i));
        }
        // Bind action keys
        map.bind(ChoiceOperation.EXIT, "\r");
        map.bind(ChoiceOperation.CANCEL, esc());
        map.setAmbiguousTimeout(DEFAULT_TIMEOUT_WITH_ESC);
    }

    /**
     * Build choice items display lines with proper styling.
     */
    private List<AttributedString> buildChoiceItemsDisplay(List<ChoiceItem> items) {
        List<AttributedString> out = new ArrayList<>();
        out.add(AttributedString.EMPTY); // Empty line before choices

        for (ChoiceItem item : items) {
            if (item.isSelectable()) {
                AttributedStringBuilder asb = new AttributedStringBuilder();
                asb.append("  ");
                if (item.getKey() != null && item.getKey() != ' ') {
                    asb.styled(config.style(PrompterConfig.CURSOR), item.getKey() + ") ");
                }
                asb.append(item.getText());
                if (item.isDefaultChoice()) {
                    asb.styled(config.style(PrompterConfig.AN), " (default)");
                }
                out.add(asb.toAttributedString());
            }
        }
        return out;
    }

    /**
     * Inner class for managing list pagination ranges.
     */
    private static class ListRange {
        final int first;
        final int last;

        public ListRange(int first, int last) {
            this.first = first;
            this.last = last;
        }
    }

    /**
     * Compute the visible range of items based on cursor position and terminal size.
     */
    private void computeListRange(int cursorRow, int itemsSize) {
        if (range != null && range.first <= cursorRow - firstItemRow && range.last - 1 > cursorRow - firstItemRow) {
            return;
        }
        range = new ListRange(0, itemsSize);
        if (size.getRows() < firstItemRow + itemsSize) {
            int itemId = cursorRow - firstItemRow;
            int forList = size.getRows() - firstItemRow;
            if (itemId < forList - 1) {
                range = new ListRange(0, forList);
            } else {
                range = new ListRange(itemId - forList + 2, itemId + 2);
            }
        }
    }

    /**
     * Get the next selectable row in the list.
     */
    private static <T extends PromptItem> int nextRow(int row, int firstItemRow, List<T> items) {
        int itemsSize = items.size();
        int next;
        for (next = row + 1;
                next - firstItemRow < itemsSize
                        && !items.get(next - firstItemRow).isSelectable();
                next++) {}
        if (next - firstItemRow >= itemsSize) {
            for (next = firstItemRow;
                    next - firstItemRow < itemsSize
                            && !items.get(next - firstItemRow).isSelectable();
                    next++) {}
        }
        return next;
    }

    /**
     * Get the previous selectable row in the list.
     */
    private static <T extends PromptItem> int prevRow(int row, int firstItemRow, List<T> items) {
        int itemsSize = items.size();
        int prev;
        for (prev = row - 1;
                prev - firstItemRow >= 0 && !items.get(prev - firstItemRow).isSelectable();
                prev--) {}
        if (prev - firstItemRow < 0) {
            for (prev = firstItemRow + itemsSize - 1;
                    prev - firstItemRow >= 0 && !items.get(prev - firstItemRow).isSelectable();
                    prev--) {}
        }
        return prev;
    }

    /**
     * Reset display size tracking.
     */
    private void resetDisplay() {
        size.copy(terminal.getSize());
    }

    /**
     * Calculate column layout for items based on terminal width.
     */
    private void calculateColumnLayout(List<? extends PromptItem> items) {
        if (items.isEmpty()) {
            columns = 1;
            lines = 1;
            return;
        }

        // Use single column for small number of items (like console-ui behavior)
        if (items.size() < MIN_ITEMS_FOR_MULTICOLUMN) {
            columns = 1;
            lines = items.size();
            return;
        }

        // Calculate maximum item width
        int maxWidth = 0;
        for (PromptItem item : items) {
            String text = item.getText();
            if (item instanceof ChoiceItem) {
                ChoiceItem choice = (ChoiceItem) item;
                if (choice.getKey() != null) {
                    text = choice.getKey() + " - " + text;
                }
            }
            maxWidth = Math.max(maxWidth, display.wcwidth(text));
        }

        // Add space for indicator and checkbox symbols
        maxWidth += display.wcwidth(config.indicator()) + 1; // indicator + space
        if (items.get(0) instanceof CheckboxItem) {
            maxWidth += Math.max(display.wcwidth(config.checkedBox()), display.wcwidth(config.uncheckedBox()));
        }

        // Calculate how many columns fit
        int terminalWidth = size.getColumns();
        columns = Math.max(1, terminalWidth / (maxWidth + MARGIN_BETWEEN_COLUMNS));

        // Adjust if we have fewer items than columns
        columns = Math.min(columns, items.size());

        // Calculate lines needed
        lines = (items.size() + columns - 1) / columns;

        // Ensure we don't exceed available terminal height
        int availableRows = size.getRows() - firstItemRow;
        if (lines > availableRows && availableRows > 0) {
            lines = availableRows;
            columns = (items.size() + lines - 1) / lines;
        }
    }

    /**
     * Convert 2D grid position to linear item index.
     */
    private int gridToIndex(int row, int col, int totalItems) {
        int index;
        if (rowsFirst) {
            index = row * columns + col;
        } else {
            index = col * lines + row;
        }
        return index < totalItems ? index : -1;
    }

    /**
     * Convert linear item index to 2D grid position.
     */
    private int[] indexToGrid(int index) {
        if (rowsFirst) {
            return new int[] {index / columns, index % columns};
        } else {
            return new int[] {index % lines, index / lines};
        }
    }

    /**
     * Navigate to next column in grid layout.
     */
    private static <T extends PromptItem> int nextColumn(
            int currentRow, int firstItemRow, List<T> items, int columns, int lines, boolean rowsFirst) {
        int currentIndex = currentRow - firstItemRow;
        int[] grid = indexToGrid(currentIndex, columns, lines, rowsFirst);
        int row = grid[0];
        int col = grid[1];

        // Move right
        col = (col + 1) % columns;

        int newIndex = gridToIndex(row, col, items.size(), columns, lines, rowsFirst);
        if (newIndex >= 0 && newIndex < items.size() && items.get(newIndex).isSelectable()) {
            return firstItemRow + newIndex;
        }

        // If target is not selectable, find next selectable item
        return nextRow(currentRow, firstItemRow, items);
    }

    /**
     * Navigate to previous column in grid layout.
     */
    private static <T extends PromptItem> int prevColumn(
            int currentRow, int firstItemRow, List<T> items, int columns, int lines, boolean rowsFirst) {
        int currentIndex = currentRow - firstItemRow;
        int[] grid = indexToGrid(currentIndex, columns, lines, rowsFirst);
        int row = grid[0];
        int col = grid[1];

        // Move left
        col = (col - 1 + columns) % columns;

        int newIndex = gridToIndex(row, col, items.size(), columns, lines, rowsFirst);
        if (newIndex >= 0 && newIndex < items.size() && items.get(newIndex).isSelectable()) {
            return firstItemRow + newIndex;
        }

        // If target is not selectable, find previous selectable item
        return prevRow(currentRow, firstItemRow, items);
    }

    /**
     * Helper methods for grid calculations.
     */
    private static int gridToIndex(int row, int col, int totalItems, int columns, int lines, boolean rowsFirst) {
        int index;
        if (rowsFirst) {
            index = row * columns + col;
        } else {
            index = col * lines + row;
        }
        return index < totalItems ? index : -1;
    }

    private static int[] indexToGrid(int index, int columns, int lines, boolean rowsFirst) {
        if (rowsFirst) {
            return new int[] {index / columns, index % columns};
        } else {
            return new int[] {index % lines, index / lines};
        }
    }
}
