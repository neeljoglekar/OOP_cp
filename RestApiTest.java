import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** In-memory REST checks using only the JDK HTTP client. */
public class RestApiTest {
    public static void main(String[] args) throws Exception {
        AuctionSystem system = new AuctionSystem();
        Seller seller = new Seller("REST-SELLER", "REST Test Seller");
        Buyer buyer = new Buyer("2001", "REST Test Buyer");
        Seller otherSeller = new Seller("3001", "Not a Buyer");
        system.addUser(buyer);
        system.addUser(otherSeller);

        Product product = new Product("REST-PRODUCT", "REST Test Item",
                "A test item with \"quoted\" text.", "Good");
        seller.listProduct(system, product);
        Auction activeAuction = seller.createAuction(system, "REST-ACTIVE", product,
                LocalDateTime.now().plusHours(1), 10.00);

        Product closedProduct = new Product("REST-CLOSED-PRODUCT", "Closed Item",
                "An item for closed-auction request testing.", "Used");
        Auction closedAuction = seller.createAuction(system, "REST-CLOSED", closedProduct,
                LocalDateTime.now().plusHours(1), 10.00);
        closedAuction.closeAuction();

        Product expiredProduct = new Product("REST-EXPIRED-PRODUCT", "Expired Item",
                "An item for expired-auction request testing.", "Good");
        seller.createAuction(system, "REST-EXPIRED", expiredProduct,
                LocalDateTime.now().minusMinutes(1), 10.00);

        AuctionServer server = new AuctionServer(system, new RestTestUserDAO(), 0);
        server.start();
        try {
            URI baseUri = URI.create("http://127.0.0.1:" + server.getPort());
            HttpClient browserA = HttpClient.newHttpClient();
            HttpClient browserB = HttpClient.newHttpClient();
            String buyerToken = login(browserA, baseUri, "buyer@rest.test");
            String sellerToken = login(browserA, baseUri, "seller@rest.test");

            HttpResponse<String> health = get(browserA, baseUri.resolve("/api/health"));
            check(health.statusCode() == 200 && health.body().contains("Auction API is running"),
                    "Health endpoint did not return success.");
            check("*".equals(health.headers().firstValue("Access-Control-Allow-Origin").orElse(null)),
                    "CORS response header was not present.");

            HttpResponse<String> options = send(browserA, HttpRequest.newBuilder(
                    baseUri.resolve("/api/auctions")).method("OPTIONS",
                            HttpRequest.BodyPublishers.noBody()).build());
            check(options.statusCode() == 204, "CORS preflight did not return 204.");

            HttpResponse<String> list = get(browserA, baseUri.resolve("/api/auctions"));
            check(list.statusCode() == 200 && list.body().contains("REST-ACTIVE")
                            && list.body().contains("REST Test Item")
                            && list.body().contains("quoted"),
                    "Auction listing did not expose the current auction/product details.");

            HttpResponse<String> details = get(browserA,
                    baseUri.resolve("/api/auctions/REST-ACTIVE"));
            check(details.statusCode() == 200 && details.body().contains("REST Test Item")
                            && details.body().contains("minimumBidIncrement")
                            && details.body().contains("endTime")
                            && details.body().contains("\"status\":\"open\""),
                    "Single-auction endpoint did not return its current details.");

            HttpResponse<String> missing = get(browserA,
                    baseUri.resolve("/api/auctions/DOES-NOT-EXIST"));
            check(missing.statusCode() == 404 && missing.body().contains("success\":false"),
                    "Missing auction did not return 404.");

            HttpResponse<String> accepted = postBid(browserA, baseUri, "REST-ACTIVE",
                    "{\"buyerId\":3001,\"amount\":100.0}", buyerToken);
            check(accepted.statusCode() == 201 && accepted.body().contains("Bid placed")
                            && accepted.body().contains("\"amount\":100.0")
                            && accepted.body().contains("\"buyerId\":\"2001\""),
                    "Valid bid did not use the authenticated Buyer instead of request buyerId.");

            HttpResponse<String> sharedState = get(browserB,
                    baseUri.resolve("/api/auctions/REST-ACTIVE"));
            check(sharedState.statusCode() == 200
                            && sharedState.body().contains("\"amount\":100.0")
                            && sharedState.body().contains("REST Test Buyer"),
                    "A second client did not observe the same shared auction state.");

            HttpResponse<String> insufficient = postBid(browserB, baseUri, "REST-ACTIVE",
                    "{\"buyerId\":2001,\"amount\":105.0}", buyerToken);
            check(insufficient.statusCode() == 400
                            && insufficient.body().contains("minimum next bid"),
                    "Insufficient bid did not map to HTTP 400.");

            HttpResponse<String> invalid = postBid(browserA, baseUri, "REST-ACTIVE",
                    "{\"buyerId\":2001,\"amount\":0.0}", buyerToken);
            check(invalid.statusCode() == 400 && invalid.body().contains("positive finite amount"),
                    "Invalid bid did not map to HTTP 400.");

            HttpResponse<String> malformed = postBid(browserA, baseUri, "REST-ACTIVE", "not-json", buyerToken);
            check(malformed.statusCode() == 400 && malformed.body().contains("amount"),
                    "Malformed bid request did not map to HTTP 400.");

            HttpResponse<String> unauthorized = postBid(browserA, baseUri, "REST-ACTIVE",
                    "{\"buyerId\":2001,\"amount\":120.0}", sellerToken);
            check(unauthorized.statusCode() == 403,
                    "A non-buyer was not rejected with HTTP 403.");

            activeAuction.closeAuction();
            HttpResponse<String> closed = postBid(browserB, baseUri, "REST-ACTIVE",
                    "{\"buyerId\":2001,\"amount\":120.0}", buyerToken);
            check(closed.statusCode() == 409, "Manually closed auction did not return HTTP 409.");

            HttpResponse<String> expired = postBid(browserB, baseUri, "REST-EXPIRED",
                    "{\"buyerId\":2001,\"amount\":120.0}", buyerToken);
            check(expired.statusCode() == 409, "Expired auction did not return HTTP 409.");

            HttpResponse<String> closedDetails = get(browserB,
                    baseUri.resolve("/api/auctions/REST-ACTIVE"));
            check(closedDetails.body().contains("\"winner\":{")
                            && closedDetails.body().contains("REST Test Buyer"),
                    "Closed-auction winner was not returned.");

            System.out.println("RestApiTest passed: health, CORS, list/detail, 404, shared state, "
                    + "bid acceptance/errors, closed/expired auctions, and winner data.");
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<String> get(HttpClient client, URI uri) throws Exception {
        return send(client, HttpRequest.newBuilder(uri).GET().build());
    }

    private static HttpResponse<String> postBid(HttpClient client, URI baseUri,
            String auctionId, String requestBody, String token) throws Exception {
        URI uri = baseUri.resolve("/api/auctions/" + auctionId + "/bids");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        return send(client, request);
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest request)
            throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String login(HttpClient client, URI baseUri, String email) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + email + "\",\"password\":\"rest-test\"}"))
                .build();
        HttpResponse<String> response = send(client, request);
        check(response.statusCode() == 200, "REST test account login failed for " + email + ".");
        Matcher matcher = Pattern.compile("\\\"sessionToken\\\":\\\"([^\\\"]+)\\\"")
                .matcher(response.body());
        if (!matcher.find()) throw new AssertionError("Successful login returned no session token.");
        return matcher.group(1);
    }

    private static final class RestTestUserDAO extends UserDAO {
        @Override
        public User authenticate(String email, char[] password) {
            if (!"rest-test".equals(new String(password))) return null;
            if ("buyer@rest.test".equals(email)) return new Buyer("2001", "REST Test Buyer");
            if ("seller@rest.test".equals(email)) return new Seller("3001", "Not a Buyer");
            return null;
        }
    }
}
