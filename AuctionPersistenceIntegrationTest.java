import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MySQL and REST integration checks for persisted auction startup and seller creation. */
public class AuctionPersistenceIntegrationTest {
    private static HttpClient client;

    public static void main(String[] args) throws Exception {
        if (!databaseEnvironmentConfigured()) {
            System.out.println("SKIPPED: Set AUCTION_DB_URL, AUCTION_DB_USERNAME, and "
                    + "AUCTION_DB_PASSWORD to run auction persistence integration tests.");
            return;
        }
        new AuctionPersistenceIntegrationTest().run();
    }

    private void run() throws Exception {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        String unique = UUID.randomUUID().toString().replace("-", "");
        String sellerEmail = "phase3-seller-" + unique + "@example.test";
        String buyerEmail = "phase3-buyer-" + unique + "@example.test";
        String seedProductId = "p3-seed-product-" + unique;
        String seedAuctionId = "p3-seed-auction-" + unique;
        String createdProductId = "p3-created-product-" + unique;
        String createdAuctionId = "p3-created-auction-" + unique;
        String duplicateProductId = "p3-duplicate-product-" + unique;
        String rollbackProductId = "p3-rollback-product-" + unique;
        String rollbackAuctionId = "p3-rollback-auction-" + unique;
        int[] userIds = findUnusedUserIds();
        String password = "phase3-test-" + unique;
        UserDAO userDAO = new UserDAO();
        ProductDAO productDAO = new ProductDAO();
        BidDAO bidDAO = new BidDAO(userDAO);
        AuctionDAO auctionDAO = new AuctionDAO(productDAO, userDAO, bidDAO);
        boolean sellerCreated = false;
        boolean buyerCreated = false;
        AuctionServer server = null;
        try {
            char[] sellerPassword = password.toCharArray();
            try {
                userDAO.registerUser(userIds[0], "Phase 3 Seller", sellerEmail,
                        sellerPassword, "SELLER");
                sellerCreated = true;
            } finally {
                Arrays.fill(sellerPassword, '\0');
            }
            char[] buyerPassword = password.toCharArray();
            try {
                userDAO.registerUser(userIds[1], "Phase 3 Buyer", buyerEmail,
                        buyerPassword, "BUYER");
                buyerCreated = true;
            } finally {
                Arrays.fill(buyerPassword, '\0');
            }

            Seller seller = (Seller) userDAO.findUserById(userIds[0]);
            Buyer buyer = (Buyer) userDAO.findUserById(userIds[1]);
            Product seededProduct = new Product(seedProductId, "Persisted camera",
                    "Seed auction loaded on API startup.", "Good");
            Auction seededAuction = new Auction(seedAuctionId, seededProduct, seller,
                    LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.MICROS), 5.0);
            productDAO.insertProduct(seededProduct);
            auctionDAO.insertAuction(seededAuction);
            bidDAO.insertBid(seedAuctionId, new Bid(buyer, 125.0,
                    LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MICROS)));
            check(auctionDAO.closeAuction(seedAuctionId), "Could not seed the closed auction state.");

            AuctionSystem system = new AuctionSystem();
            server = new AuctionServer(system, userDAO, productDAO, auctionDAO, bidDAO, 0);
            URI base = URI.create("http://127.0.0.1:" + server.getPort());
            verifyNotAcceptingBeforeStart(base);
            server.start();

            HttpResponse<String> list = get(base, "/api/auctions", null);
            check(list.statusCode() == 200 && list.body().contains(seedAuctionId),
                    "Persisted auction was not exposed by GET /api/auctions after startup.");
            HttpResponse<String> details = get(base, "/api/auctions/" + seedAuctionId, null);
            int expectedProductCount = productDAO.getAllProducts().size();
            int expectedAuctionCount = auctionDAO.getAllAuctions().size();
            check(details.statusCode() == 200
                            && details.body().contains("Persisted camera")
                            && details.body().contains("Seed auction loaded on API startup.")
                            && details.body().contains("\"buyerName\":\"Phase 3 Buyer\"")
                            && details.body().contains("\"amount\":125.0")
                            && details.body().contains("\"minimumBidIncrement\":5.0")
                            && details.body().contains("\"endTime\":\"" + seededAuction.getEndTime())
                            && details.body().contains("\"bids\":[")
                            && details.body().contains("\"status\":\"manually closed\"")
                            && details.body().contains("\"winner\":{\"userId\":\"" + userIds[1]),
                    "Restored auction details, bid history, or winner were incorrect.");
            check(system.getAuctions().size() == expectedAuctionCount
                            && system.getProducts().size() == expectedProductCount,
                    "Startup load did not match the persisted products and auctions exactly.");

            String sellerToken = login(base, sellerEmail, password);
            LocalDateTime endTime = LocalDateTime.now().plusDays(3)
                    .truncatedTo(ChronoUnit.MICROS);
            String createRequest = creationRequest(createdAuctionId, createdProductId,
                    "Seller-created item", "Created through the persistent API.", "Like New",
                    endTime, "2.5");
            HttpResponse<String> created = post(base, "/api/auctions", createRequest, sellerToken);
            check(created.statusCode() == 201 && created.body().contains(createdAuctionId),
                    "Seller auction creation through the persistent server failed.");
            check(productDAO.findProductById(createdProductId) != null
                            && auctionDAO.findAuctionById(createdAuctionId) != null,
                    "Seller-created product and auction were not persisted.");
            check(system.containsProductId(createdProductId)
                            && system.containsAuctionId(createdAuctionId),
                    "Committed seller-created data was not added to memory.");
            HttpResponse<String> dashboard = get(base, "/api/my-auctions", sellerToken);
            check(dashboard.statusCode() == 200 && dashboard.body().contains(createdAuctionId),
                    "The persisted auction was missing from the existing seller dashboard.");

            HttpResponse<String> duplicate = post(base, "/api/auctions",
                    creationRequest(seedAuctionId, duplicateProductId,
                            "Duplicate item", "Must not be inserted.", "Good", endTime, "1.0"),
                    sellerToken);
            check(duplicate.statusCode() == 409
                            && productDAO.findProductById(duplicateProductId) == null,
                    "A duplicate auction ID was not rejected before inserting its product.");

            String rollbackRequest = creationRequest(rollbackAuctionId, rollbackProductId,
                    "Rollback item", "This product insert must roll back.", "Used",
                    endTime, "0.00001");
            HttpResponse<String> rejected = post(base, "/api/auctions", rollbackRequest, sellerToken);
            check(rejected.statusCode() == 400,
                    "Invalid database precision did not fail auction creation cleanly.");
            check(productDAO.findProductById(rollbackProductId) == null
                            && auctionDAO.findAuctionById(rollbackAuctionId) == null
                            && !system.containsProductId(rollbackProductId)
                            && !system.containsAuctionId(rollbackAuctionId),
                    "Failed auction insertion did not roll back the product or changed memory.");

            String buyerToken = login(base, buyerEmail, password);
            check(post(base, "/api/auctions",
                    creationRequest("p3-buyer-auction-" + unique,
                            "p3-buyer-product-" + unique, "Buyer item", "Denied.", "Good",
                            endTime, "1.0"), buyerToken).statusCode() == 403,
                    "A Buyer was allowed to create an auction.");

            System.out.println("AuctionPersistenceIntegrationTest passed: startup loading, "
                    + "REST visibility, seller persistence, rollback, dashboard, and role checks.");
        } finally {
            if (server != null) server.stop();
            cleanup(unique, duplicateProductId, sellerEmail, buyerEmail, userIds,
                    sellerCreated, buyerCreated);
        }
    }

    private static void verifyNotAcceptingBeforeStart(URI base) throws Exception {
        try {
            get(base, "/api/health", null);
            throw new AssertionError("Server accepted a request before database initialization.");
        } catch (IOException expected) {
            // HttpServer is bound but not started until persistent initialization succeeds.
        }
    }

    private static String creationRequest(String auctionId, String productId, String name,
            String description, String condition, LocalDateTime endTime, String increment) {
        return "{\"auctionId\":\"" + auctionId + "\",\"productId\":\"" + productId
                + "\",\"productName\":\"" + name + "\",\"description\":\"" + description
                + "\",\"condition\":\"" + condition + "\",\"endTime\":\"" + endTime
                + "\",\"minimumBidIncrement\":" + increment + "}";
    }

    private static String login(URI base, String email, String password) throws Exception {
        HttpResponse<String> response = post(base, "/api/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        check(response.statusCode() == 200, "Test login failed for " + email + ".");
        Matcher matcher = Pattern.compile("\\\"sessionToken\\\":\\\"([^\\\"]+)\\\"")
                .matcher(response.body());
        check(matcher.find(), "Login response did not include a session token.");
        return matcher.group(1);
    }

    private static HttpResponse<String> get(URI base, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(4));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(URI base, String path, String body, String token)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(4)).header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static int[] findUnusedUserIds() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            for (int attempt = 0; attempt < 100; attempt++) {
                int first = ThreadLocalRandom.current().nextInt(100_000_000, 1_900_000_000);
                if (!userIdExists(connection, first) && !userIdExists(connection, first + 1)) {
                    return new int[] { first, first + 1 };
                }
            }
        }
        throw new SQLException("Could not allocate integration-test user IDs.");
    }

    private static boolean userIdExists(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM users WHERE user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private static void cleanup(String unique, String duplicateProductId, String sellerEmail,
            String buyerEmail, int[] userIds, boolean sellerCreated, boolean buyerCreated)
            throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                deleteBids(connection, "p3-seed-auction-" + unique);
                deleteAuction(connection, "p3-seed-auction-" + unique);
                deleteProduct(connection, "p3-seed-product-" + unique);
                deleteAuction(connection, "p3-created-auction-" + unique);
                deleteProduct(connection, "p3-created-product-" + unique);
                deleteProduct(connection, duplicateProductId);
                deleteAuction(connection, "p3-rollback-auction-" + unique);
                deleteProduct(connection, "p3-rollback-product-" + unique);
                if (sellerCreated) deleteUser(connection, userIds[0], sellerEmail);
                if (buyerCreated) deleteUser(connection, userIds[1], buyerEmail);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void deleteBids(Connection connection, String auctionId) throws SQLException {
        deleteByAuctionId(connection, "DELETE FROM bids WHERE auction_id = ?", auctionId);
    }

    private static void deleteAuction(Connection connection, String auctionId) throws SQLException {
        deleteByAuctionId(connection, "DELETE FROM auctions WHERE auction_id = ?", auctionId);
    }

    private static void deleteProduct(Connection connection, String productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM products WHERE product_id = ?")) {
            statement.setString(1, productId);
            statement.executeUpdate();
        }
    }

    private static void deleteByAuctionId(Connection connection, String sql, String auctionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, auctionId);
            statement.executeUpdate();
        }
    }

    private static void deleteUser(Connection connection, int userId, String email)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM users WHERE user_id = ? AND email = ?")) {
            statement.setInt(1, userId);
            statement.setString(2, email);
            statement.executeUpdate();
        }
    }

    private static boolean databaseEnvironmentConfigured() {
        return !isBlank(System.getenv("AUCTION_DB_URL"))
                && !isBlank(System.getenv("AUCTION_DB_USERNAME"))
                && System.getenv("AUCTION_DB_PASSWORD") != null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
