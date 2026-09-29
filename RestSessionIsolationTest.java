import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** In-memory REST checks for independent browser tokens and authenticated identity. */
public class RestSessionIsolationTest {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        AuctionSystem system = new AuctionSystem();
        Seller auctionSeller = new Seller("8001", "Auction Owner");
        Product product = new Product("SESSION-PRODUCT", "Session test item",
                "Used to verify authenticated bidding.", "Good");
        Auction auction = auctionSeller.createAuction(system, "SESSION-AUCTION", product,
                LocalDateTime.now().plusHours(2), 10.0);
        AuctionServer server = new AuctionServer(system, new SessionTestDAO(), 0);
        server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getPort());
        try {
            check(UserSession.getCurrentUser() == null,
                    "Test precondition failed: console UserSession should begin empty.");
            String sellerToken = login(base, "seller@session.test", "seller-role");
            String buyerToken = login(base, "buyer@session.test", "buyer-role");
            String adminToken = login(base, "admin@session.test", "admin-role");
            check(!sellerToken.equals(buyerToken) && !sellerToken.equals(adminToken)
                            && !buyerToken.equals(adminToken),
                    "Successful logins did not receive distinct session tokens.");
            check(sellerToken.matches("[A-Za-z0-9_-]{40,}")
                            && UserSession.getCurrentUser() == null,
                    "REST token format was predictable or REST login changed UserSession.");

            check(get(base, "/api/profile", sellerToken).body().contains("Seller User"),
                    "Seller token did not retrieve the Seller profile.");
            check(get(base, "/api/profile", buyerToken).body().contains("Buyer User"),
                    "Buyer token did not retrieve the Buyer profile.");
            check(get(base, "/api/profile", adminToken).body().contains("Admin User"),
                    "Admin token did not retrieve the Admin profile.");

            check(get(base, "/api/profile", null).statusCode() == 401
                            && get(base, "/api/profile", "not-a-valid-token").statusCode() == 401
                            && put(base, "/api/profile", profileJson("X", "x@session.test", ""), null)
                                    .statusCode() == 401
                            && post(base, "/api/logout", "{}", null).statusCode() == 401,
                    "Missing/invalid tokens were not rejected for protected endpoints.");

            HttpResponse<String> sellerUpdate = put(base, "/api/profile",
                    profileJson("Seller Updated", "seller-new@session.test", ""), sellerToken);
            check(sellerUpdate.statusCode() == 200
                            && get(base, "/api/profile", sellerToken).body().contains("Seller Updated")
                            && get(base, "/api/profile", buyerToken).body().contains("Buyer User"),
                    "Seller profile update leaked into the Buyer profile.");
            HttpResponse<String> buyerUpdate = put(base, "/api/profile",
                    profileJson("Buyer Updated", "buyer-new@session.test", ""), buyerToken);
            check(buyerUpdate.statusCode() == 200
                            && get(base, "/api/profile", buyerToken).body().contains("Buyer Updated")
                            && get(base, "/api/profile", sellerToken).body().contains("Seller Updated"),
                    "Buyer profile update leaked into the Seller profile.");
            HttpResponse<String> attemptedIdChange = put(base, "/api/profile",
                    "{\"name\":\"Hijack\",\"email\":\"hijack@session.test\","
                            + "\"password\":\"\",\"userId\":8202}", sellerToken);
            check(attemptedIdChange.statusCode() == 400,
                    "Profile endpoint accepted a request-selected user ID.");

            String createBody = creationJson("SESSION-CREATED", "SESSION-PRODUCT-2");
            check(post(base, "/api/auctions", createBody, sellerToken).statusCode() == 201,
                    "Seller token could not create an auction.");
            check(post(base, "/api/auctions", createBody.replace("SESSION-CREATED", "BUYER-CREATED"),
                            buyerToken).statusCode() == 403,
                    "Buyer token could create an auction.");
            check(post(base, "/api/auctions", createBody.replace("SESSION-CREATED", "ADMIN-CREATED"),
                            adminToken).statusCode() == 403,
                    "Admin token could create an auction.");
            check(post(base, "/api/auctions/SESSION-AUCTION/bids",
                            "{\"buyerId\":8101,\"amount\":100.0}", buyerToken).statusCode() == 201
                            && get(base, "/api/auctions/SESSION-AUCTION", null).body()
                                    .contains("\"buyerId\":\"8202\""),
                    "Bidding trusted buyerId in the body rather than the Buyer session.");
            check(post(base, "/api/auctions/SESSION-AUCTION/bids",
                            "{\"amount\":120.0}", sellerToken).statusCode() == 403,
                    "Seller token could perform a Buyer-only bid.");

            check(post(base, "/api/logout", "{}", sellerToken).statusCode() == 200,
                    "Seller logout failed.");
            check(get(base, "/api/profile", sellerToken).statusCode() == 401,
                    "Logged-out Seller token remained valid.");
            check(get(base, "/api/profile", buyerToken).statusCode() == 200
                            && post(base, "/api/auctions/SESSION-AUCTION/bids",
                                    "{\"amount\":110.0}", buyerToken).statusCode() == 201,
                    "Logging out Seller invalidated or disrupted the Buyer session.");

            String sellerReLoginToken = login(base, "seller-new@session.test", "seller-role");
            check(!sellerToken.equals(sellerReLoginToken)
                            && get(base, "/api/profile", sellerReLoginToken).statusCode() == 200
                            && get(base, "/api/profile", buyerToken).statusCode() == 200,
                    "Seller re-login did not create a fresh isolated session.");
            check(get(base, "/api/profile", null).statusCode() == 401,
                    "A profile request without a token was accepted.");

            System.out.println("RestSessionIsolationTest passed: distinct tokens, role-bound "
                    + "profiles, profile isolation, seller/buyer authorization, spoof resistance, "
                    + "and token-specific logout/re-login.");
        } finally {
            UserSession.logout();
            server.stop();
        }
    }

    private static String login(URI base, String email, String password) throws Exception {
        HttpResponse<String> response = post(base, "/api/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        check(response.statusCode() == 200, "Login failed for " + email + ".");
        Matcher tokenMatcher = Pattern.compile("\\\"sessionToken\\\":\\\"([^\\\"]+)\\\"")
                .matcher(response.body());
        if (!tokenMatcher.find()) throw new AssertionError("Login response had no session token.");
        check(response.body().contains("\"role\":\"" + expectedRole(email) + "\"")
                        && !response.body().contains("password"),
                "Login did not return the expected safe role-specific account information.");
        return tokenMatcher.group(1);
    }

    private static String expectedRole(String email) {
        if (email.startsWith("seller")) return "Seller";
        if (email.startsWith("buyer")) return "Buyer";
        return "Admin";
    }

    private static HttpResponse<String> get(URI base, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return CLIENT.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(URI base, String path, String body, String token)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return CLIENT.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> put(URI base, String path, String body, String token)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return CLIENT.send(builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String profileJson(String name, String email, String password) {
        return "{\"name\":\"" + name + "\",\"email\":\"" + email
                + "\",\"password\":\"" + password + "\"}";
    }

    private static String creationJson(String auctionId, String productId) {
        return "{\"auctionId\":\"" + auctionId + "\",\"productId\":\"" + productId
                + "\",\"productName\":\"Test product\",\"description\":\"Test description\","
                + "\"condition\":\"Good\",\"endTime\":\""
                + LocalDateTime.now().plusHours(1).withNano(0)
                + "\",\"minimumBidIncrement\":5.0}";
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class SessionTestDAO extends UserDAO {
        private final Map<String, AccountRecord> accountsByEmail = new HashMap<>();
        private final Map<String, AccountRecord> accountsById = new HashMap<>();

        private SessionTestDAO() {
            add("8201", "Seller User", "seller@session.test", "Seller", "seller-role");
            add("8202", "Buyer User", "buyer@session.test", "Buyer", "buyer-role");
            add("8203", "Admin User", "admin@session.test", "Admin", "admin-role");
        }

        @Override
        public User authenticate(String email, char[] password) {
            AccountRecord account = accountsByEmail.get(email);
            if (account == null || !account.password.equals(new String(password))) return null;
            return account.createUser();
        }

        @Override
        public User viewProfile(User user) throws UserNotFoundException {
            AccountRecord account = accountsById.get(user.getUserId());
            if (account == null) throw new UserNotFoundException("Test profile not found.");
            user.setName(account.name);
            user.setEmail(account.email);
            return user;
        }

        @Override
        public boolean updateProfile(User user, String name, String email)
                throws UserNotFoundException {
            AccountRecord account = accountsById.get(user.getUserId());
            if (account == null) throw new UserNotFoundException("Test profile not found.");
            AccountRecord owner = accountsByEmail.get(email);
            if (owner != null && owner != account) return false;
            accountsByEmail.remove(account.email);
            account.name = name.trim();
            account.email = email.trim();
            user.setName(account.name);
            user.setEmail(account.email);
            accountsByEmail.put(account.email, account);
            return true;
        }

        @Override
        public boolean changePassword(User user, char[] password) throws UserNotFoundException {
            AccountRecord account = accountsById.get(user.getUserId());
            if (account == null) throw new UserNotFoundException("Test profile not found.");
            account.password = new String(password);
            return true;
        }

        private void add(String id, String name, String email, String role, String password) {
            AccountRecord account = new AccountRecord(id, name, email, role, password);
            accountsByEmail.put(email, account);
            accountsById.put(id, account);
        }
    }

    private static final class AccountRecord {
        private final String id;
        private String name;
        private String email;
        private final String role;
        private String password;

        private AccountRecord(String id, String name, String email, String role, String password) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.role = role;
            this.password = password;
        }

        private User createUser() {
            switch (role) {
                case "Seller": return new Seller(id, name, email);
                case "Buyer": return new Buyer(id, name, email);
                default: return new Admin(id, name, email);
            }
        }
    }
}
