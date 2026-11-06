import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** In-memory HTTP checks for seller-only auction creation and authenticated bidding. */
public class SellerAuctionCreationTest {
    private static final String BOUNDARY = "AuctionTestBoundary";
    public static void main(String[] args) throws Exception {
        AuctionSystem system = new AuctionSystem();
        AuctionServer server = new AuctionServer(system, new CreationUserDAO(), 0);
        server.start();
        HttpClient client = HttpClient.newHttpClient();
        URI baseUri = URI.create("http://127.0.0.1:" + server.getPort());
        try {
            String sellerToken = login(client, baseUri, "seller@example.test");
            String buyerToken = login(client, baseUri, "buyer@example.test");
            String adminToken = login(client, baseUri, "admin@example.test");
            LocalDateTime requestedEndTime = LocalDateTime.now().plusHours(2).withNano(0);
            String validRequest = creationRequest("CREATE-1", "PRODUCT-1", "Test camera",
                    "Camera with a case", "Good", requestedEndTime, "25.0");

            check(post(client, baseUri, "/api/auctions", validRequest, null).statusCode() == 401,
                    "An anonymous request was not denied.");
            check(post(client, baseUri, "/api/auctions", validRequest, buyerToken).statusCode() == 403,
                    "A Buyer was not denied auction creation.");
            check(post(client, baseUri, "/api/auctions", validRequest, adminToken).statusCode() == 403,
                    "An Admin was not denied auction creation.");

            HttpResponse<String> created = post(client, baseUri, "/api/auctions",
                    validRequest, sellerToken);
            check(created.statusCode() == 201 && created.body().contains("CREATE-1")
                            && created.body().contains("Camera with a case")
                            && created.body().contains("\"minimumBidIncrement\":25.0"),
                    "Seller auction creation did not return the created auction details.");
            Auction auction = system.getAuction("CREATE-1");
            check("5101".equals(auction.getSeller().getUserId())
                            && "Camera with a case".equals(auction.getProduct().getDescription())
                            && "Good".equals(auction.getProduct().getCondition())
                            && auction.getMinimumBidIncrement() == 25.0
                            && requestedEndTime.equals(auction.getEndTime()),
                    "Created objects did not preserve authenticated seller or submitted details.");
            Matcher imageMatcher = Pattern.compile("\\\"imageUrl\\\":\\\"([^\\\"]+)\\\"")
                    .matcher(created.body());
            check(auction.getProduct().getImagePath() != null && imageMatcher.find(),
                    "The uploaded image was not saved and returned in auction data.");
            HttpResponse<String> image = get(client, baseUri.resolve(imageMatcher.group(1)));
            check(image.statusCode() == 200
                            && "image/png".equals(image.headers().firstValue("Content-Type").orElse("")),
                    "The saved product image was not served with a safe image content type.");

            HttpResponse<String> duplicate = post(client, baseUri, "/api/auctions",
                    validRequest, sellerToken);
            check(duplicate.statusCode() == 409,
                    "A duplicate auction ID was not rejected with HTTP 409.");
            check(system.getAuctions().size() == 1 && system.getProducts().size() == 1,
                    "A rejected duplicate changed the in-memory product/auction lists.");

            HttpResponse<String> list = get(client, baseUri.resolve("/api/auctions"));
            HttpResponse<String> details = get(client, baseUri.resolve("/api/auctions/CREATE-1"));
            check(list.statusCode() == 200 && list.body().contains("CREATE-1"),
                    "The created auction was not available in the auction list.");
            check(details.statusCode() == 200 && details.body().contains("Camera with a case")
                            && details.body().contains("\"status\":\"open\""),
                    "The created auction was not available through the detail endpoint.");

            HttpResponse<String> bid = post(client, baseUri, "/api/auctions/CREATE-1/bids",
                    "{\"buyerId\":5101,\"amount\":100.0}", buyerToken);
            check(bid.statusCode() == 201 && bid.body().contains("Bid placed.")
                            && bid.body().contains("\"buyerId\":\"5102\""),
                    "Bidding trusted the request buyerId instead of the Buyer session.");
            HttpResponse<String> sellerBid = post(client, baseUri,
                    "/api/auctions/CREATE-1/bids", "{\"amount\":150.0}", sellerToken);
            check(sellerBid.statusCode() == 403,
                    "A Seller was allowed to place a Buyer-only bid.");

            check(post(client, baseUri, "/api/auctions",
                    creationRequest("", "PRODUCT-2", "Name", "Desc", "Good",
                            LocalDateTime.now().plusHours(1), "1.0"), sellerToken).statusCode() == 400,
                    "Blank required fields were not rejected.");
            check(post(client, baseUri, "/api/auctions",
                    creationRequest("CREATE-2", "PRODUCT-2", "Name", "Desc", "Good",
                            "not-a-date", "1.0"), sellerToken).statusCode() == 400,
                    "An invalid date was not rejected.");
            check(post(client, baseUri, "/api/auctions",
                    creationRequest("CREATE-2", "PRODUCT-2", "Name", "Desc", "Good",
                            LocalDateTime.now().minusMinutes(1), "1.0"), sellerToken).statusCode() == 400,
                    "A past auction end time was not rejected.");
            for (String increment : new String[] {"0", "-1", "NaN"}) {
                check(post(client, baseUri, "/api/auctions",
                        creationRequest("CREATE-2", "PRODUCT-2", "Name", "Desc", "Good",
                                LocalDateTime.now().plusHours(1), increment), sellerToken).statusCode() == 400,
                        "Invalid minimum bid increment was not rejected: " + increment);
            }
            check(post(client, baseUri, "/api/auctions", "{}", sellerToken).statusCode() == 400,
                    "A request with no uploaded image was accepted.");
            check(post(client, baseUri, "/api/auctions",
                    creationRequest("CREATE-2", "PRODUCT-2", "Name", "Desc", "Like New",
                            LocalDateTime.now().plusHours(1), "1.0"), sellerToken).statusCode() == 400,
                    "An unsupported product condition was accepted.");
            check(post(client, baseUri, "/api/auctions",
                    creationRequest("CREATE-2", "PRODUCT-2", "Name", "Desc", "Good",
                            LocalDateTime.now().plusHours(1), "1.0", "not an image"),
                    sellerToken).statusCode() == 400,
                    "An upload with invalid image content was accepted.");
            String truncatedPng = new String(java.util.Arrays.copyOf(testPng(), 33),
                    StandardCharsets.ISO_8859_1);
            HttpResponse<String> corruptImage = post(client, baseUri, "/api/auctions",
                    creationRequest("CREATE-2", "PRODUCT-2", "Name", "Desc", "Good",
                            LocalDateTime.now().plusHours(1), "1.0", truncatedPng), sellerToken);
            check(corruptImage.statusCode() == 400
                            && corruptImage.body().contains("not a valid image"),
                    "Corrupt image bytes were reported as a storage/server failure instead of a validation error.");
            String spoofedRequest = validRequest.replace("--" + BOUNDARY + "--",
                    multipartField("sellerId", "9999") + "--" + BOUNDARY + "--");
            check(post(client, baseUri, "/api/auctions", spoofedRequest, sellerToken).statusCode() == 400,
                    "Auction creation accepted a seller ID from the request.");

            System.out.println("SellerAuctionCreationTest passed: session authorization, "
                    + "creation, validation, list/detail, and session-bound bidding.");
        } finally {
            server.stop();
            for (Product product : system.getProducts()) {
                new ProductImageStorage().delete(product.getImagePath());
            }
        }
    }

    private static String creationRequest(String auctionId, String productId, String name,
            String description, String condition, Object endTime, String increment) {
        return creationRequest(auctionId, productId, name, description, condition,
                endTime, increment, new String(testPng(), StandardCharsets.ISO_8859_1));
    }

    private static String creationRequest(String auctionId, String productId, String name,
            String description, String condition, Object endTime, String increment, String image) {
        return multipartField("auctionId", auctionId) + multipartField("productId", productId)
                + multipartField("productName", name) + multipartField("description", description)
                + multipartField("condition", condition) + multipartField("endTime", endTime.toString())
                + multipartField("minimumBidIncrement", increment)
                + "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"productImage\"; "
                + "filename=\"original-name-is-ignored.png\"\r\nContent-Type: text/plain\r\n\r\n"
                + image
                + "\r\n--" + BOUNDARY + "--\r\n";
    }

    private static String multipartField(String name, String value) {
        return "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"\r\n\r\n" + value + "\r\n";
    }

    private static byte[] testPng() {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", output);
            return output.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String login(HttpClient client, URI baseUri, String email) throws Exception {
        HttpResponse<String> response = post(client, baseUri, "/api/login",
                "{\"email\":\"" + email + "\",\"password\":\"test-password\"}", null);
        check(response.statusCode() == 200, "Test login failed for " + email + ".");
        Matcher matcher = Pattern.compile("\\\"sessionToken\\\":\\\"([^\\\"]+)\\\"")
                .matcher(response.body());
        if (!matcher.find()) throw new AssertionError("Login response did not return a session token.");
        return matcher.group(1);
    }

    private static HttpResponse<String> post(HttpClient client, URI baseUri, String path,
            String body, String token) throws Exception {
        boolean multipart = body.startsWith("--" + BOUNDARY);
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .header("Content-Type", multipart
                        ? "multipart/form-data; boundary=" + BOUNDARY : "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofByteArray(
                body.getBytes(StandardCharsets.ISO_8859_1))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(HttpClient client, URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class CreationUserDAO extends UserDAO {
        @Override
        public User authenticate(String email, char[] password) {
            if (!"test-password".equals(new String(password))) return null;
            if ("seller@example.test".equals(email)) return new Seller("5101", "Creation Seller");
            if ("buyer@example.test".equals(email)) return new Buyer("5102", "Creation Buyer");
            if ("admin@example.test".equals(email)) return new Admin("5103", "Creation Admin");
            return null;
        }
    }
}
