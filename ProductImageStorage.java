import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Stores validated product image files under uploads/products. */
public class ProductImageStorage {
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 12_000_000L;
    private static final String STORED_NAME_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)";

    private final Path directory;

    public ProductImageStorage() {
        this(Path.of("uploads", "products"));
    }

    public ProductImageStorage(Path directory) {
        if (directory == null) throw new IllegalArgumentException("Image directory is required.");
        this.directory = directory.toAbsolutePath().normalize();
    }

    /** Validates image bytes, assigns a generated filename, and saves the file. */
    public String save(byte[] imageBytes) throws IOException {
        String extension = detectAndValidateFormat(imageBytes);
        Files.createDirectories(directory);
        String filename = UUID.randomUUID().toString() + "." + extension;
        Path target = resolveStoredName(filename);
        Files.write(target, imageBytes);
        return filename;
    }

    public Path resolveStoredName(String filename) {
        if (filename == null || !filename.matches(STORED_NAME_PATTERN)) {
            throw new IllegalArgumentException("Product image name is invalid.");
        }
        Path resolved = directory.resolve(filename).normalize();
        if (!resolved.getParent().equals(directory)) {
            throw new IllegalArgumentException("Product image path is invalid.");
        }
        return resolved;
    }

    public boolean exists(String filename) {
        try {
            return Files.isRegularFile(resolveStoredName(filename), LinkOption.NOFOLLOW_LINKS);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public byte[] read(String filename) throws IOException {
        Path image = resolveStoredName(filename);
        if (!Files.isRegularFile(image, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Product image was not found.");
        }
        try (InputStream input = Files.newInputStream(image, LinkOption.NOFOLLOW_LINKS)) {
            return input.readAllBytes();
        }
    }

    public void delete(String filename) throws IOException {
        if (filename != null) Files.deleteIfExists(resolveStoredName(filename));
    }

    public static String contentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    private static String detectAndValidateFormat(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Choose a product image no larger than 5 MB.");
        }
        String format;
        if (isPng(bytes)) format = "png";
        else if (isJpeg(bytes)) format = "jpg";
        else if (isWebp(bytes)) format = "webp";
        else throw new IllegalArgumentException("Use a valid JPG, JPEG, PNG, or WEBP image.");

        if (!"webp".equals(format)) validateDecodableImage(bytes);
        return format;
    }

    private static boolean isPng(byte[] bytes) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        if (bytes.length < 33) return false;
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) return false;
        }
        return true;
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 4 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
    }

    private static boolean isWebp(byte[] bytes) {
        if (bytes.length < 20 || !asciiAt(bytes, 0, "RIFF")
                || !asciiAt(bytes, 8, "WEBP")) return false;
        long declaredRiffSize = littleEndianUInt32(bytes, 4);
        if (declaredRiffSize != bytes.length - 8L) return false;
        int position = 12;
        boolean foundImageFrame = false;
        while (position + 8 <= bytes.length) {
            String chunk = new String(bytes, position, 4,
                    java.nio.charset.StandardCharsets.US_ASCII);
            long chunkLength = littleEndianUInt32(bytes, position + 4);
            long dataStartLong = position + 8L;
            long dataEndLong = dataStartLong + chunkLength;
            if (dataEndLong > bytes.length || dataEndLong < dataStartLong) return false;
            int dataStart = (int) dataStartLong;
            if ("VP8 ".equals(chunk)) {
                if (chunkLength < 10 || (bytes[dataStart + 3] & 0xff) != 0x9d
                        || (bytes[dataStart + 4] & 0xff) != 0x01
                        || (bytes[dataStart + 5] & 0xff) != 0x2a
                        || !validDimensions(littleEndianUInt16(bytes, dataStart + 6) & 0x3fff,
                                littleEndianUInt16(bytes, dataStart + 8) & 0x3fff)) return false;
                foundImageFrame = true;
            } else if ("VP8L".equals(chunk)) {
                if (chunkLength < 5 || (bytes[dataStart] & 0xff) != 0x2f) return false;
                int b1 = bytes[dataStart + 1] & 0xff;
                int b2 = bytes[dataStart + 2] & 0xff;
                int b3 = bytes[dataStart + 3] & 0xff;
                int b4 = bytes[dataStart + 4] & 0xff;
                int width = 1 + b1 + ((b2 & 0x3f) << 8);
                int height = 1 + ((b2 & 0xc0) >> 6) + (b3 << 2) + ((b4 & 0x0f) << 10);
                if (!validDimensions(width, height)) return false;
                foundImageFrame = true;
            } else if ("VP8X".equals(chunk) && chunkLength >= 10) {
                int width = 1 + littleEndianUInt24(bytes, dataStart + 4);
                int height = 1 + littleEndianUInt24(bytes, dataStart + 7);
                if (!validDimensions(width, height)) return false;
            }
            long paddedEnd = dataEndLong + (chunkLength & 1L);
            if (paddedEnd > bytes.length) return false;
            position = (int) paddedEnd;
        }
        return position == bytes.length && foundImageFrame;
    }

    private static boolean validDimensions(int width, int height) {
        return width > 0 && height > 0 && (long) width * height <= MAX_IMAGE_PIXELS;
    }

    private static int littleEndianUInt16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static int littleEndianUInt24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16);
    }

    private static void validateDecodableImage(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new IllegalArgumentException("The selected file is not a valid image.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("The selected file is not a valid image.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IllegalArgumentException("Product image dimensions are too large.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw new IllegalArgumentException("The selected file is not a valid image.");
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("The selected file is not a valid image.", exception);
        }
    }

    private static boolean asciiAt(byte[] bytes, int offset, String value) {
        for (int index = 0; index < value.length(); index++) {
            if (bytes[offset + index] != (byte) value.charAt(index)) return false;
        }
        return true;
    }

    private static long littleEndianUInt32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL) | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16) | ((bytes[offset + 3] & 0xffL) << 24);
    }
}
