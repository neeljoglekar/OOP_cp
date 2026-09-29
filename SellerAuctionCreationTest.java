import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** In-memory HTTP checks for seller-only auction creation and authenticated bidding. */
public class SellerAuctionCreationTest {
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
            String spoofedRequest = validRequest.substring(0, validRequest.length() - 1)
                    + ",\"sellerId\":9999}";
            check(post(client, baseUri, "/api/auctions", spoofedRequest, sellerToken).statusCode() == 400,
                    "Auction creation accepted a seller ID from the request.");

            System.out.println("SellerAuctionCreationTest passed: session authorization, "
                    + "creation, validation, list/detail, and session-bound bidding.");
        } finally {
            server.stop();
        }
    }

    private static String creationRequest(String auctionId, String productId, String name,
            String description, String condition, Object endTime, String increment) {
        return "{\"auctionId\":\"" + auctionId + "\",\"productId\":\"" + productId
                + "\",\"productName\":\"" + name + "\",\"description\":\"" + description
                + "\",\"condition\":\"" + condition + "\",\"endTime\":\"" + endTime
                + "\",\"minimumBidIncrement\":" + increment + "}";
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
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
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
