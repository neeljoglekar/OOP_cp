import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** In-memory REST checks for seller-only manual auction closure. */
public class AuctionClosureTest {
    public static void main(String[] args) throws Exception {
        AuctionSystem system = new AuctionSystem();
        Seller firstSeller = new Seller("6101", "First Seller");
        Seller secondSeller = new Seller("6102", "Second Seller");
        Buyer buyer = new Buyer("6103", "Test Buyer");
        Auction openAuction = new Auction("CLOSE-OWN", new Product("CLOSE-PRODUCT-1",
                "Test product", "Used to test closure.", "Good"), firstSeller,
                LocalDateTime.now().plusHours(2), 1.0);
        Auction secondOpenAuction = new Auction("CLOSE-OTHER", new Product("CLOSE-PRODUCT-2",
                "Other seller product", "Used to test ownership.", "Average"), secondSeller,
                LocalDateTime.now().plusHours(2), 1.0);
        Auction expiredAuction = new Auction("CLOSE-EXPIRED", new Product("CLOSE-PRODUCT-3",
                "Expired product", "Used to test expired closure.", "Poor"), firstSeller,
                LocalDateTime.now().minusMinutes(1), 1.0);
        system.loadPersistedData(java.util.List.of(openAuction.getProduct(),
                secondOpenAuction.getProduct(), expiredAuction.getProduct()),
                java.util.List.of(openAuction, secondOpenAuction, expiredAuction));

        AuctionServer server = new AuctionServer(system, new ClosureUserDAO(), 0);
        server.start();
        HttpClient client = HttpClient.newHttpClient();
        URI base = URI.create("http://127.0.0.1:" + server.getPort());
        try {
            String firstToken = login(client, base, "first@example.test");
            String secondToken = login(client, base, "second@example.test");
            String buyerToken = login(client, base, "buyer@example.test");
            check(post(client, base, "/api/auctions/CLOSE-OWN/close", null).statusCode() == 401,
                    "An anonymous caller was allowed to end an auction.");
            check(post(client, base, "/api/auctions/CLOSE-OWN/close", buyerToken).statusCode() == 403,
                    "A Buyer was allowed to end an auction.");
            check(post(client, base, "/api/auctions/CLOSE-OWN/close", secondToken).statusCode() == 403,
                    "A different Seller was allowed to end another seller's auction.");
            HttpResponse<String> closed = post(client, base, "/api/auctions/CLOSE-OWN/close", firstToken);
            check(closed.statusCode() == 200 && "manually closed".equals(openAuction.getStatus()),
                    "The owner Seller could not end their active auction.");
            check(post(client, base, "/api/auctions/CLOSE-OWN/close", firstToken).statusCode() == 409,
                    "An already ended auction was accepted as active.");
            check(post(client, base, "/api/auctions/CLOSE-EXPIRED/close", firstToken).statusCode() == 409,
                    "An expired auction was accepted as active.");
            HttpResponse<String> bidAfterClose = post(client, base,
                    "/api/auctions/CLOSE-OWN/bids", buyerToken, "{\"amount\":5.0}");
            check(bidAfterClose.statusCode() == 409,
                    "Bidding remained possible after the Seller ended the auction.");
            System.out.println("AuctionClosureTest passed: authentication, seller ownership, active-state checks, and bid rejection after closure.");
        } finally {
            server.stop();
        }
    }

    private static String login(HttpClient client, URI base, String email) throws Exception {
        HttpResponse<String> response = send(client, base, "/api/login", "POST",
                "{\"email\":\"" + email + "\",\"password\":\"test-password\"}", null);
        check(response.statusCode() == 200, "Test login failed for " + email + ".");
        Matcher matcher = Pattern.compile("\\\"sessionToken\\\":\\\"([^\\\"]+)\\\"")
                .matcher(response.body());
        check(matcher.find(), "Login response did not include a session token.");
        return matcher.group(1);
    }

    private static HttpResponse<String> post(HttpClient client, URI base, String path, String token)
            throws Exception {
        return send(client, base, path, "POST", "", token);
    }

    private static HttpResponse<String> post(HttpClient client, URI base, String path,
            String token, String body) throws Exception {
        return send(client, base, path, "POST", body, token);
    }

    private static HttpResponse<String> send(HttpClient client, URI base, String path,
            String method, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path))
                .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class ClosureUserDAO extends UserDAO {
        @Override
        public User authenticate(String email, char[] password) {
            if (!"test-password".equals(new String(password))) return null;
            if ("first@example.test".equals(email)) return new Seller("6101", "First Seller");
            if ("second@example.test".equals(email)) return new Seller("6102", "Second Seller");
            if ("buyer@example.test".equals(email)) return new Buyer("6103", "Test Buyer");
            return null;
        }
    }
}
