# Sixel Graphics and Double-Size Characters Support

This document describes the Sixel graphics and double-size character support added to JLine 3.

## Sixel Graphics

Sixel is a bitmap graphics format supported by some terminals that allows displaying raster graphics directly in the terminal. The name "Sixel" comes from "six pixels" because each character cell represents 6 pixels arranged vertically.

### Supported Terminals

The following terminals are known to support Sixel graphics:

- **XTerm** (with `--enable-sixel-graphics` or patch #359+)
- **MLTerm** (since version 3.1.9)
- **Mintty** (since version 2.6.0)
- **iTerm2** (since version 3.3.0)
- **Konsole** (since version 22.04)
- **foot** (since version 1.2.0)
- **WezTerm** (since version 20200620)
- **Contour** (all versions)
- **DomTerm** (since version 2.0)
- **XFCE Terminal** (recent versions)

### Basic Usage

```java
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.SixelGraphics;
import java.awt.image.BufferedImage;
import java.io.File;

// Create terminal
Terminal terminal = TerminalBuilder.builder().build();

// Check if terminal supports Sixel
if (SixelGraphics.isSixelSupported(terminal)) {
    // Display image from file
    SixelGraphics.displayImage(terminal, new File("image.png"));
    
    // Display BufferedImage
    BufferedImage image = createMyImage();
    SixelGraphics.displayImage(terminal, image);
    
    // Display image from InputStream
    try (InputStream is = getClass().getResourceAsStream("/logo.png")) {
        SixelGraphics.displayImage(terminal, is);
    }
}
```

### Advanced Features

#### Override Terminal Detection

```java
// Force enable Sixel support (useful for testing)
SixelGraphics.setSixelSupportOverride(true);

// Force disable Sixel support
SixelGraphics.setSixelSupportOverride(false);

// Reset to automatic detection
SixelGraphics.setSixelSupportOverride(null);
```

#### Convert Images to Sixel Format

```java
BufferedImage image = loadImage();
String sixelData = SixelGraphics.convertToSixel(image);
terminal.writer().print(sixelData);
```

### Image Processing

The Sixel implementation automatically:

- **Resizes large images** to fit reasonable terminal dimensions (800x480 by default)
- **Converts to indexed color** with up to 256 colors for optimal quality
- **Uses high-quality scaling** with bicubic interpolation
- **Optimizes color palette** by only defining colors that are actually used
- **Handles transparency** by using a white background

### Example Program

Run the demo with various options:

```bash
# Display test image
java -cp demo.jar org.jline.demo.examples.SixelExample --test-image

# Force enable Sixel support
java -cp demo.jar org.jline.demo.examples.SixelExample --force-enable

# Display specific image file
java -cp demo.jar org.jline.demo.examples.SixelExample /path/to/image.png

# Demonstrate override functionality
java -cp demo.jar org.jline.demo.examples.SixelExample --demo-override
```

## Double-Size Characters

Double-size characters are a VT100-compatible feature that allows displaying text at double width and/or double height. This is useful for creating banners, headers, or emphasizing important text.

### Supported Modes

- **Normal**: Single-width, single-height characters (default)
- **Double Width**: Double-width, single-height characters
- **Double Height**: Double-width, double-height characters (requires two lines)

### Basic Usage

```java
import org.jline.terminal.impl.DoubleSizeCharacters;

// Check if terminal supports double-size characters
if (DoubleSizeCharacters.isDoubleSizeSupported(terminal)) {
    // Print normal text
    DoubleSizeCharacters.printNormal(terminal, "Normal text");
    
    // Print double-width text
    DoubleSizeCharacters.printDoubleWidth(terminal, "Wide text");
    
    // Print double-height text (automatically handles both halves)
    DoubleSizeCharacters.printDoubleHeight(terminal, "Tall text");
    
    // Create a banner
    DoubleSizeCharacters.printBanner(terminal, "JLine 3", '*');
    
    // Reset to normal size
    DoubleSizeCharacters.reset(terminal);
}
```

### Manual Mode Control

```java
// Set specific modes
DoubleSizeCharacters.setMode(terminal, DoubleSizeCharacters.Mode.NORMAL);
DoubleSizeCharacters.setMode(terminal, DoubleSizeCharacters.Mode.DOUBLE_WIDTH);
DoubleSizeCharacters.setMode(terminal, DoubleSizeCharacters.Mode.DOUBLE_HEIGHT_TOP);
DoubleSizeCharacters.setMode(terminal, DoubleSizeCharacters.Mode.DOUBLE_HEIGHT_BOTTOM);
```

### Example Program

```bash
# Demonstrate double-size characters
java -cp demo.jar org.jline.demo.examples.SixelExample --double-size

# Create a custom banner
java -cp demo.jar org.jline.demo.examples.SixelExample --banner "My App"
```

## Implementation Details

### Sixel Format

The Sixel format uses the following structure:
- **DCS** (Device Control String): `\033P`
- **Parameters**: `0;1;q` (aspect ratio 1:1, black background, Sixel mode)
- **Color definitions**: `#<index>;2;<r>;<g>;<b>` (RGB values 0-100)
- **Sixel data**: Characters from `?` (0x3F) to `~` (0x7E)
- **Control characters**: 
  - `#<index>` - Select color
  - `$` - Graphics carriage return
  - `-` - Graphics new line
- **ST** (String Terminator): `\033\`

### Double-Size Character Sequences

- `ESC # 3` - Double-height, double-width line (top half)
- `ESC # 4` - Double-height, double-width line (bottom half)  
- `ESC # 5` - Single-width, single-height line (normal)
- `ESC # 6` - Double-width, single-height line

## Testing

Both features include comprehensive unit tests:

- `SixelGraphicsTest` - Tests Sixel functionality
- `DoubleSizeCharactersTest` - Tests double-size character functionality

## Compatibility

These features are designed to gracefully degrade on terminals that don't support them:

- **Sixel**: Throws `UnsupportedOperationException` if terminal doesn't support Sixel
- **Double-size**: Falls back to normal text with simple borders for banners

## Performance Considerations

- Large images are automatically resized to prevent excessive terminal load
- Color quantization is optimized to use only necessary colors
- Sixel data is generated efficiently with minimal memory usage
