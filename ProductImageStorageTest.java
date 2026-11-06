import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Focused validation and safe-path checks for product image storage. */
public class ProductImageStorageTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("auction-product-images-");
        ProductImageStorage storage = new ProductImageStorage(directory);
        String filename = null;
        String webpFilename = null;
        try {
            byte[] png = createPng();
            filename = storage.save(png);
            check(filename.matches("[0-9a-f-]+\\.png") && storage.exists(filename),
                    "The valid PNG was not saved under a generated filename.");
            check("image/png".equals(ProductImageStorage.contentType(filename))
                            && java.util.Arrays.equals(png, storage.read(filename)),
                    "The stored image did not retain its image type and bytes.");
            byte[] webpFixture = java.util.Base64.getDecoder().decode(
                    "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA==");
            // The fixture contains four bytes beyond the RIFF and VP8 chunk lengths.
            // Keep the complete structurally valid RIFF image payload.
            byte[] webp = java.util.Arrays.copyOf(webpFixture, 42);
            webpFilename = storage.save(webp);
            check(webpFilename.endsWith(".webp")
                            && "image/webp".equals(ProductImageStorage.contentType(webpFilename)),
                    "A valid WEBP image was not accepted based on its content.");

            expectInvalid(storage, new byte[] {1, 2, 3, 4}, "Non-image bytes were accepted.");
            expectInvalid(storage, java.util.Arrays.copyOf(createPng(), 33),
                    "An image with a PNG header but invalid image data was accepted.");
            expectInvalid(storage, new byte[ProductImageStorage.MAX_IMAGE_BYTES + 1],
                    "An image larger than 5 MB was accepted.");
            try {
                storage.resolveStoredName("..\\secrets.png");
                throw new AssertionError("A path traversal filename was accepted.");
            } catch (IllegalArgumentException expected) {
                // The storage class accepts generated image names only.
            }
            System.out.println("ProductImageStorageTest passed: image-content validation, size limit, generated names, and path safety.");
        } finally {
            if (filename != null) storage.delete(filename);
            if (webpFilename != null) storage.delete(webpFilename);
            Files.deleteIfExists(directory);
        }
    }

    private static byte[] createPng() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    private static void expectInvalid(ProductImageStorage storage, byte[] bytes, String message)
            throws Exception {
        try {
            storage.save(bytes);
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // Invalid content and sizes fail before any file is written.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
