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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MySQL integration checks for transactional REST bidding and bid restoration. */
public class PersistentBidIntegrationTest {
    private static HttpClient client;

    public static void main(String[] args) throws Exception {
        if (!databaseEnvironmentConfigured()) {
            System.out.println("SKIPPED: Set AUCTION_DB_URL, AUCTION_DB_USERNAME, and "
                    + "AUCTION_DB_PASSWORD to run persistent bid integration tests.");
            return;
        }
        new PersistentBidIntegrationTest().run();
    }

    private void run() throws Exception {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        String unique = UUID.randomUUID().toString().replace("-", "");
        String sellerEmail = "phase4-seller-" + unique + "@example.test";
        String[] buyerEmails = {
            "phase4-buyer1-" + unique + "@example.test",
            "phase4-buyer2-" + unique + "@example.test",
            "phase4-buyer3-" + unique + "@example.test"
        };
        int[] userIds = findUnusedUserIds(4);
        String password = "phase4-test-" + unique;
        String[] productIds = new String[6];
        String[] auctionIds = new String[6];
        for (int index = 0; index < productIds.length; index++) {
            productIds[index] = "p4-product-" + index + "-" + unique;
            auctionIds[index] = "p4-auction-" + index + "-" + unique;
        }

        UserDAO userDAO = new UserDAO();
        ProductDAO productDAO = new ProductDAO();
        BidDAO bidDAO = new BidDAO(userDAO);
        AuctionDAO auctionDAO = new AuctionDAO(productDAO, userDAO, bidDAO);
        boolean[] usersCreated = new boolean[4];
        boolean[] productsCreated = new boolean[productIds.length];
        AuctionServer server = null;
        try {
            register(userDAO, userIds[0], "Phase 4 Seller", sellerEmail, password, "SELLER");
            usersCreated[0] = true;
            for (int index = 0; index < buyerEmails.length; index++) {
                register(userDAO, userIds[index + 1], "Phase 4 Buyer " + index,
                        buyerEmails[index], password, "BUYER");
                usersCreated[index + 1] = true;
            }
            Seller seller = (Seller) userDAO.findUserById(userIds[0]);
            Buyer initialBuyer = (Buyer) userDAO.findUserById(userIds[1]);

            createAuction(productDAO, auctionDAO, seller, productsCreated, 0,
                    productIds[0], auctionIds[0], "Persistent bid auction",
                    LocalDateTime.now().plusDays(3), 10.0);
            bidDAO.insertBid(auctionIds[0], new Bid(initialBuyer, 100.0,
                    LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MICROS)));

            createAuction(productDAO, auctionDAO, seller, productsCreated, 1,
                    productIds[1], auctionIds[1],
                    "Expired auction", LocalDateTime.now().minusMinutes(2), 10.0);
            createAuction(productDAO, auctionDAO, seller, productsCreated, 2,
                    productIds[2], auctionIds[2], "Closed auction",
                    LocalDateTime.now().plusDays(3), 10.0);
            check(auctionDAO.closeAuction(auctionIds[2]), "Could not close the test auction.");

            createAuction(productDAO, auctionDAO, seller, productsCreated, 3,
                    productIds[3], auctionIds[3],
                    "Insert failure auction", LocalDateTime.now().plusDays(3), 10.0);
            createAuction(productDAO, auctionDAO, seller, productsCreated, 4,
                    productIds[4], auctionIds[4],
                    "Concurrent auction", LocalDateTime.now().plusDays(3), 10.0);
            createAuction(productDAO, auctionDAO, seller, productsCreated, 5,
                    productIds[5], auctionIds[5],
                    "Independent auction", LocalDateTime.now().plusDays(3), 10.0);

            AuctionSystem system = new AuctionSystem();
            server = newServer(system, userDAO, productDAO, auctionDAO, bidDAO);
            server.start();
            URI base = base(server);
            String[] buyerTokens = new String[buyerEmails.length];
            for (int index = 0; index < buyerTokens.length; index++) {
                buyerTokens[index] = login(base, buyerEmails[index], password);
            }
            String sellerToken = login(base, sellerEmail, password);

            check(postBid(base, auctionIds[0], "109.0", buyerTokens[1]).statusCode() == 400,
                    "A bid below the persisted minimum increment was not rejected.");
            check(bidCount(auctionIds[0]) == 1,
                    "Rejected insufficient bid was written to MySQL.");
            HttpResponse<String> accepted = postBid(base, auctionIds[0], "110.0", buyerTokens[1]);
            check(accepted.statusCode() == 201 && bidCount(auctionIds[0]) == 2,
                    "A valid bid was not committed to MySQL.");
            check(postBid(base, auctionIds[0], "110.0", buyerTokens[2]).statusCode() == 400,
                    "A bid equal to the highest amount was not rejected.");
            check(bidCount(auctionIds[0]) == 2,
                    "An invalid bid changed the persisted bid history.");

            check(postBid(base, auctionIds[0], "120.0", "invalid-token").statusCode() == 401,
                    "An invalid bearer session was allowed to bid.");
            check(postBid(base, auctionIds[0], "120.0", sellerToken).statusCode() == 403,
                    "A Seller was allowed to bid.");
            check(postBid(base, auctionIds[1], "50.0", buyerTokens[0]).statusCode() == 409,
                    "An expired auction accepted a bid.");
            check(postBid(base, auctionIds[2], "50.0", buyerTokens[0]).statusCode() == 409,
                    "A manually closed auction accepted a bid.");

            HttpResponse<String> failedInsert = postBid(base, auctionIds[3], "1e16", buyerTokens[0]);
            check(failedInsert.statusCode() == 400 && bidCount(auctionIds[3]) == 0,
                    "A DECIMAL-range failure was not rolled back.");
            check(system.getAuction(auctionIds[3]).getBids().isEmpty(),
                    "A bid whose database insert failed was published in memory.");

            verifyConcurrentBids(base, auctionIds[0], buyerTokens, system, 100.0);
            verifyDifferentAuctionsDoNotShareAnInMemoryLock(base, auctionIds[4],
                    auctionIds[5], buyerTokens[0], buyerTokens[1]);

            Auction current = system.getAuction(auctionIds[0]);
            List<Bid> stored = bidDAO.getBidsForAuction(auctionIds[0]);
            check(current.getBids().size() == stored.size(),
                    "In-memory and persisted bid history sizes differ after concurrent bids.");
            Bid persistedHighest = bidDAO.getHighestBidForAuction(auctionIds[0]);
            check(persistedHighest != null
                            && current.getHighestValidBid().getAmount() == persistedHighest.getAmount(),
                    "In-memory highest bid differs from the database.");

            server.stop();
            server = null;
            AuctionSystem restartedSystem = new AuctionSystem();
            server = newServer(restartedSystem, userDAO, productDAO, auctionDAO, bidDAO);
            server.start();
            URI restartedBase = base(server);
            HttpResponse<String> restored = get(restartedBase,
                    "/api/auctions/" + auctionIds[0], null);
            check(restored.statusCode() == 200
                            && restored.body().contains("\"amount\":"
                                    + Double.toString(persistedHighest.getAmount()))
                            && restored.body().contains("\"buyerName\":\""
                                    + persistedHighest.getBidder().getName() + "\""),
                    "Restart did not restore the highest bid and bidder.");
            String buyerTokenAfterRestart = login(restartedBase, buyerEmails[1], password);
            HttpResponse<String> dashboard = get(restartedBase, "/api/my-bids", buyerTokenAfterRestart);
            check(dashboard.statusCode() == 200 && dashboard.body().contains(auctionIds[0])
                            && dashboard.body().contains("Phase 4 Buyer 1"),
                    "Buyer dashboard did not show persisted bidding history after restart.");

            System.out.println("PersistentBidIntegrationTest passed: transactional bid writes, "
                    + "validation, concurrency, in-memory consistency, and restart restoration.");
        } finally {
            if (server != null) server.stop();
            cleanup(sellerEmail, buyerEmails, userIds, usersCreated,
                    productIds, auctionIds, productsCreated);
        }
    }

    private static void verifyConcurrentBids(URI base, String auctionId, String[] tokens,
            AuctionSystem system, double startingAmount) throws Exception {
        double[] amounts = { startingAmount + 10.0, startingAmount + 20.0,
                startingAmount + 30.0 };
        int initialBidCount = new BidDAO().getBidsForAuction(auctionId).size();
        CountDownLatch ready = new CountDownLatch(amounts.length);
        CountDownLatch begin = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(amounts.length);
        List<Future<HttpResponse<String>>> results = new ArrayList<>();
        try {
            for (int index = 0; index < amounts.length; index++) {
                final int requestIndex = index;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    begin.await();
                    return postBid(base, auctionId, Double.toString(amounts[requestIndex]),
                            tokens[requestIndex]);
                }));
            }
            ready.await();
            begin.countDown();
            int acceptedCount = 0;
            for (Future<HttpResponse<String>> result : results) {
                HttpResponse<String> response = result.get();
                check(response.statusCode() == 201 || response.statusCode() == 400,
                        "Concurrent bid returned an unexpected status " + response.statusCode());
                if (response.statusCode() == 201) acceptedCount++;
            }
            check(acceptedCount > 0, "All concurrent bids were rejected.");

            List<Bid> persisted = new BidDAO().getBidsForAuction(auctionId);
            check(persisted.size() == initialBidCount + acceptedCount,
                    "The number of persisted bids does not match the accepted concurrent requests.");
            List<Double> acceptedAmounts = new ArrayList<>();
            for (Bid bid : persisted) acceptedAmounts.add(bid.getAmount());
            acceptedAmounts.sort(Comparator.naturalOrder());
            check(acceptedAmounts.get(0) == 100.0,
                    "The starting persisted bid was lost during concurrent bidding.");
            for (int index = 1; index < acceptedAmounts.size(); index++) {
                check(acceptedAmounts.get(index) >= acceptedAmounts.get(index - 1) + 10.0,
                        "Concurrent accepted bids violate the minimum increment.");
            }
            Auction inMemory = system.getAuction(auctionId);
            check(inMemory.getBids().size() == persisted.size()
                            && inMemory.getHighestValidBid().getAmount()
                                    == acceptedAmounts.get(acceptedAmounts.size() - 1),
                    "Concurrent database and in-memory auction state disagree.");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void verifyDifferentAuctionsDoNotShareAnInMemoryLock(URI base,
            String firstAuctionId, String secondAuctionId, String firstToken, String secondToken)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstRequestStarted = new CountDownLatch(1);
        try (Connection blocker = DatabaseConnection.getConnection()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lock = blocker.prepareStatement(
                    "SELECT auction_id FROM auctions WHERE auction_id = ? FOR UPDATE")) {
                lock.setString(1, firstAuctionId);
                try (ResultSet result = lock.executeQuery()) {
                    check(result.next(), "The concurrency test auction was not found.");
                }
            }

            Future<HttpResponse<String>> first = executor.submit(() -> {
                firstRequestStarted.countDown();
                return postBid(base, firstAuctionId, "50.0", firstToken);
            });
            firstRequestStarted.await();
            Thread.sleep(250);
            check(!first.isDone(), "The first bid did not wait for its auction row lock.");

            HttpResponse<String> independentAuctionBid = postBid(base, secondAuctionId,
                    "50.0", secondToken);
            check(independentAuctionBid.statusCode() == 201,
                    "A bid on another auction waited for an unrelated auction row lock.");
            blocker.commit();
            check(first.get().statusCode() == 201,
                    "The bid waiting for its auction row lock did not complete after release.");
        } finally {
            executor.shutdownNow();
        }
    }

    private static AuctionServer newServer(AuctionSystem system, UserDAO userDAO,
            ProductDAO productDAO, AuctionDAO auctionDAO, BidDAO bidDAO) throws Exception {
        return new AuctionServer(system, userDAO, productDAO, auctionDAO, bidDAO, 0);
    }

    private static Auction createAuction(ProductDAO productDAO, AuctionDAO auctionDAO,
            Seller seller, boolean[] productsCreated, int productIndex,
            String productId, String auctionId, String name,
            LocalDateTime endTime, double increment) throws SQLException {
        Product product = new Product(productId, name, "Persistent bid integration item.", "Good");
        productDAO.insertProduct(product);
        productsCreated[productIndex] = true;
        Auction auction = new Auction(auctionId, product, seller,
                endTime.truncatedTo(ChronoUnit.MICROS), increment);
        auctionDAO.insertAuction(auction);
        return auction;
    }

    private static void register(UserDAO userDAO, int id, String name, String email,
            String password, String role) throws SQLException {
        char[] chars = password.toCharArray();
        try {
            userDAO.registerUser(id, name, email, chars, role);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    private static URI base(AuctionServer server) {
        return URI.create("http://127.0.0.1:" + server.getPort());
    }

    private static String login(URI base, String email, String password) throws Exception {
        HttpResponse<String> response = post(base, "/api/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        check(response.statusCode() == 200, "Test login failed for " + email + ".");
        Matcher matcher = Pattern.compile("\\\"sessionToken\\\":\\\"([^\\\"]+)\\\"")
                .matcher(response.body());
        check(matcher.find(), "Login response did not return a session token.");
        return matcher.group(1);
    }

    private static HttpResponse<String> postBid(URI base, String auctionId,
            String amount, String token) throws Exception {
        return post(base, "/api/auctions/" + auctionId + "/bids",
                "{\"amount\":" + amount + "}", token);
    }

    private static HttpResponse<String> post(URI base, String path, String body, String token)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(URI base, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static long bidCount(String auctionId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM bids WHERE auction_id = ?")) {
            statement.setString(1, auctionId);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getLong(1);
            }
        }
    }

    private static int[] findUnusedUserIds(int count) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            for (int attempt = 0; attempt < 100; attempt++) {
                int first = ThreadLocalRandom.current().nextInt(100_000_000, 1_800_000_000);
                boolean available = true;
                for (int offset = 0; offset < count; offset++) {
                    if (userIdExists(connection, first + offset)) available = false;
                }
                if (available) {
                    int[] ids = new int[count];
                    for (int offset = 0; offset < count; offset++) ids[offset] = first + offset;
                    return ids;
                }
            }
        }
        throw new SQLException("Could not allocate integration-test user IDs.");
    }

    private static boolean userIdExists(Connection connection, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM users WHERE user_id = ?")) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private static void cleanup(String sellerEmail, String[] buyerEmails,
            int[] userIds, boolean[] usersCreated, String[] productIds, String[] auctionIds,
            boolean[] productsCreated) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (String auctionId : auctionIds) {
                    delete(connection, "DELETE FROM bids WHERE auction_id = ?", auctionId);
                    delete(connection, "DELETE FROM auctions WHERE auction_id = ?", auctionId);
                }
                for (int index = 0; index < productIds.length; index++) {
                    if (productsCreated[index]) {
                        delete(connection, "DELETE FROM products WHERE product_id = ?", productIds[index]);
                    }
                }
                if (usersCreated[0]) deleteUser(connection, userIds[0], sellerEmail);
                for (int index = 0; index < buyerEmails.length; index++) {
                    if (usersCreated[index + 1]) {
                        deleteUser(connection, userIds[index + 1], buyerEmails[index]);
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void delete(Connection connection, String sql, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }

    private static void deleteUser(Connection connection, int id, String email) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM users WHERE user_id = ? AND email = ?")) {
            statement.setInt(1, id);
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
