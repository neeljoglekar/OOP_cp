import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** In-memory checks for authenticated seller auctions and buyer bid history endpoints. */
public class AuctionDashboardTest {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        AuctionSystem system = new AuctionSystem();
        Seller sellerOne = new Seller("9101", "Seller One");
        Seller sellerTwo = new Seller("9102", "Seller Two");
        Buyer buyerOne = new Buyer("9201", "Buyer One");
        Buyer buyerTwo = new Buyer("9202", "Buyer Two");
        Buyer otherBidder = new Buyer("9203", "Other Bidder");

        Auction firstAuction = createAuction(system, sellerOne, "DASH-A1", "First item");
        Auction secondAuction = createAuction(system, sellerTwo, "DASH-A2", "Second item");
        buyerOne.placeBid(firstAuction, 100.0);
        buyerOne.placeBid(firstAuction, 120.0);
        otherBidder.placeBid(firstAuction, 130.0);
        buyerTwo.placeBid(secondAuction, 200.0);
        firstAuction.closeAuction();

        AuctionServer server = new AuctionServer(system, new DashboardTestDAO(), 0);
        server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getPort());
        try {
            String sellerOneToken = login(base, "seller-one@dashboard.test");
            String sellerTwoToken = login(base, "seller-two@dashboard.test");
            String buyerOneToken = login(base, "buyer-one@dashboard.test");
            String buyerTwoToken = login(base, "buyer-two@dashboard.test");
            String adminToken = login(base, "admin@dashboard.test");

            check(get(base, "/api/my-auctions", null).statusCode() == 401,
                    "My Auctions accepted an unauthenticated request.");
            check(get(base, "/api/my-bids", null).statusCode() == 401,
                    "My Bids accepted an unauthenticated request.");
            check(get(base, "/api/my-auctions", "invalid-dashboard-token").statusCode() == 401
                            && get(base, "/api/my-bids", "invalid-dashboard-token").statusCode() == 401,
                    "My Auctions or My Bids accepted an invalid token.");

            HttpResponse<String> sellerOneAuctions = get(base, "/api/my-auctions", sellerOneToken);
            HttpResponse<String> sellerTwoAuctions = get(base, "/api/my-auctions", sellerTwoToken);
            check(sellerOneAuctions.statusCode() == 200
                            && sellerOneAuctions.body().contains("DASH-A1")
                            && !sellerOneAuctions.body().contains("DASH-A2"),
                    "Seller one received an auction owned by another seller or missed its own.");
            check(sellerTwoAuctions.statusCode() == 200
                            && sellerTwoAuctions.body().contains("DASH-A2")
                            && !sellerTwoAuctions.body().contains("DASH-A1"),
                    "Seller two received an auction owned by another seller or missed its own.");
            check(get(base, "/api/my-auctions?userId=9102", sellerOneToken).body()
                            .contains("DASH-A1")
                            && !get(base, "/api/my-auctions?userId=9102", sellerOneToken).body()
                                    .contains("DASH-A2"),
                    "My Auctions trusted a user ID supplied in the URL.");
            check(get(base, "/api/my-auctions", buyerOneToken).statusCode() == 403
                            && get(base, "/api/my-auctions", adminToken).statusCode() == 403,
                    "A non-seller accessed My Auctions.");

            HttpResponse<String> buyerOneBids = get(base, "/api/my-bids", buyerOneToken);
            HttpResponse<String> buyerTwoBids = get(base, "/api/my-bids", buyerTwoToken);
            check(buyerOneBids.statusCode() == 200
                            && buyerOneBids.body().contains("DASH-A1")
                            && !buyerOneBids.body().contains("DASH-A2")
                            && buyerOneBids.body().contains("\"amount\":120.0")
                            && buyerOneBids.body().contains("\"status\":\"manually closed\"")
                            && buyerOneBids.body().contains("\"winner\":{\"userId\":\"9203\"")
                            && !buyerOneBids.body().contains("Other Bidder"),
                    "Buyer one history did not include their highest bid and auction outcome only.");
            check(buyerTwoBids.statusCode() == 200
                            && buyerTwoBids.body().contains("DASH-A2")
                            && !buyerTwoBids.body().contains("DASH-A1"),
                    "Buyer two received another buyer's bidding history.");
            HttpResponse<String> details = get(base, "/api/auctions/DASH-A1", null);
            check(details.statusCode() == 200 && details.body().contains("\"bids\":[")
                            && details.body().contains("\"buyerName\":\"Buyer One\"")
                            && details.body().contains("\"timestamp\":\""),
                    "Auction details did not expose bid history with bidder and timestamp data.");
            check(get(base, "/api/my-bids", sellerOneToken).statusCode() == 403
                            && get(base, "/api/my-bids", adminToken).statusCode() == 403,
                    "A non-buyer accessed My Bids.");
            check(post(base, "/api/auctions/DASH-A1/bids", "{\"amount\":500.0}",
                            adminToken).statusCode() == 403,
                    "An Admin was allowed to bid through the REST auction routes.");

            System.out.println("AuctionDashboardTest passed: seller ownership, buyer history isolation, "
                    + "highest personal bid, winner data, missing sessions, and role denial.");
        } finally {
            server.stop();
        }
    }

    private static Auction createAuction(AuctionSystem system, Seller seller,
            String auctionId, String productName) {
        Product product = new Product("P-" + auctionId, productName,
                "Dashboard endpoint test item.", "Good");
        seller.listProduct(system, product);
        return seller.createAuction(system, auctionId, product, LocalDateTime.now().plusHours(1), 10.0);
    }

    private static String login(URI base, String email) throws Exception {
        String json = "{\"email\":\"" + email + "\",\"password\":\"test\"}";
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == 200, "Dashboard test login failed for " + email + ".");
        Matcher matcher = Pattern.compile("\\\"sessionToken\\\":\\\"([^\\\"]+)\\\"")
                .matcher(response.body());
        check(matcher.find(), "Dashboard test login response did not contain a token.");
        return matcher.group(1);
    }

    private static HttpResponse<String> get(URI base, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return CLIENT.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(URI base, String path, String body, String token)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class DashboardTestDAO extends UserDAO {
        private final Map<String, User> accounts = new HashMap<>();

        private DashboardTestDAO() {
            accounts.put("seller-one@dashboard.test", new Seller("9101", "Seller One"));
            accounts.put("seller-two@dashboard.test", new Seller("9102", "Seller Two"));
            accounts.put("buyer-one@dashboard.test", new Buyer("9201", "Buyer One"));
            accounts.put("buyer-two@dashboard.test", new Buyer("9202", "Buyer Two"));
            accounts.put("other-bidder@dashboard.test", new Buyer("9203", "Other Bidder"));
            accounts.put("admin@dashboard.test", new Admin("9301", "Admin"));
        }

        @Override
        public User authenticate(String email, char[] password) {
            if (!"test".equals(new String(password))) return null;
            User account = accounts.get(email);
            if (account == null) return null;
            if (account instanceof Seller) return new Seller(account.getUserId(), account.getName());
            if (account instanceof Buyer) return new Buyer(account.getUserId(), account.getName());
            return new Admin(account.getUserId(), account.getName());
        }
    }
}
