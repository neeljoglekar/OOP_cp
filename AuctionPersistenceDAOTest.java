import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** MySQL integration checks for ProductDAO, AuctionDAO, and BidDAO. */
public class AuctionPersistenceDAOTest {
    private final ProductDAO productDAO = new ProductDAO();
    private final BidDAO bidDAO = new BidDAO();
    private final AuctionDAO auctionDAO = new AuctionDAO(productDAO, new UserDAO(), bidDAO);

    public static void main(String[] args) throws Exception {
        if (!databaseEnvironmentConfigured()) {
            System.out.println("SKIPPED: Set AUCTION_DB_URL, AUCTION_DB_USERNAME, and "
                    + "AUCTION_DB_PASSWORD to run DAO integration tests.");
            return;
        }
        new AuctionPersistenceDAOTest().runAll();
        System.out.println("AuctionPersistenceDAOTest passed.");
    }

    private void runAll() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        String productId = "dao-product-" + unique;
        String auctionId = "dao-auction-" + unique;
        String noExpiryAuctionId = "dao-no-expiry-" + unique;
        String missingProductId = "dao-missing-product-" + unique;
        String missingAuctionId = "dao-missing-auction-" + unique;
        String[] testAuctionIds = { auctionId, noExpiryAuctionId, missingAuctionId,
                "dao-orphan-product-" + unique, "dao-orphan-seller-" + unique };
        int[] userIds = findUnusedUserIds();
        String password = "DAO-test-" + unique;
        UserDAO userDAO = new UserDAO();
        boolean buyerCreated = false;
        boolean sellerCreated = false;
        boolean productCreated = false;

        try {
            userDAO.registerUser(userIds[0], "DAO Test Buyer", "buyer-" + unique + "@example.test",
                    password.toCharArray(), "BUYER");
            buyerCreated = true;
            userDAO.registerUser(userIds[1], "DAO Test Seller", "seller-" + unique + "@example.test",
                    password.toCharArray(), "SELLER");
            sellerCreated = true;

            Buyer buyer = (Buyer) userDAO.findUserById(userIds[0]);
            Seller seller = (Seller) userDAO.findUserById(userIds[1]);
            Product product = new Product(productId, "DAO Test Product",
                    "Product inserted by the DAO integration test.", "Good");

            productDAO.insertProduct(product);
            productCreated = true;
            assertProductEquals(product, productDAO.findProductById(productId));
            check(productDAO.findProductById(missingProductId) == null,
                    "Missing product lookup should return null.");
            check(productDAO.getAllProducts().stream()
                    .anyMatch(found -> productId.equals(found.getProductId())),
                    "Product should appear in the all-products result.");
            expectConstraint(() -> productDAO.insertProduct(product), "Duplicate product ID");

            LocalDateTime endTime = LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.MICROS);
            Auction auction = new Auction(auctionId, product, seller, endTime, 2.5);

            Auction invalidScaleAuction = new Auction("dao-invalid-scale-" + unique, product,
                    seller, endTime, 0.00001);
            expectIllegalArgument(() -> auctionDAO.insertAuction(invalidScaleAuction),
                    "Minimum increment with more than four fractional digits");
            expectConstraint(() -> auctionDAO.insertAuction(new Auction(
                    "dao-orphan-product-" + unique,
                    new Product(missingProductId, "Missing", "Not inserted", "Good"),
                    seller, endTime, 1.0)), "Auction with missing product");

            int absentUserId = findUnusedUserId();
            expectConstraint(() -> auctionDAO.insertAuction(new Auction(
                    "dao-orphan-seller-" + unique, product,
                    new Seller(String.valueOf(absentUserId), "Missing Seller"), endTime, 1.0)),
                    "Auction with missing seller");

            auctionDAO.insertAuction(auction);
            expectConstraint(() -> auctionDAO.insertAuction(auction), "Duplicate auction ID");
            check(auctionDAO.findAuctionById(missingAuctionId) == null,
                    "Missing auction lookup should return null.");

            Auction loaded = auctionDAO.findAuctionById(auctionId);
            assertAuctionEquals(auction, loaded);
            check(auctionDAO.getAllAuctions().stream()
                    .anyMatch(found -> auctionId.equals(found.getAuctionId())),
                    "Auction should appear in the all-auctions result.");
            check(auctionDAO.getAuctionsBySellerId(seller.getUserId()).stream()
                    .anyMatch(found -> auctionId.equals(found.getAuctionId())),
                    "Seller auction lookup should include the seller's auction.");

            Auction noExpiryAuction = new Auction(noExpiryAuctionId, product, seller,
                    LocalDateTime.MAX, 1.25);
            auctionDAO.insertAuction(noExpiryAuction);
            check(auctionDAO.findAuctionById(noExpiryAuctionId).getEndTime().equals(LocalDateTime.MAX),
                    "SQL NULL end time should restore as LocalDateTime.MAX.");

            expectConstraint(() -> bidDAO.insertBid(missingAuctionId,
                    new Bid(buyer, 50.0)), "Bid with missing auction");
            expectConstraint(() -> bidDAO.insertBid(auctionId,
                    new Bid(new Buyer(String.valueOf(absentUserId), "Missing Buyer"), 50.0)),
                    "Bid with missing buyer");
            expectIllegalArgument(() -> bidDAO.insertBid(auctionId,
                    new Bid(buyer, Double.NaN)), "NaN bid amount");
            expectIllegalArgument(() -> bidDAO.insertBid(auctionId,
                    new Bid(buyer, 10.00001)), "Bid amount with more than four fractional digits");

            LocalDateTime firstTime = LocalDateTime.now().minusMinutes(3)
                    .truncatedTo(ChronoUnit.MICROS);
            LocalDateTime secondTime = firstTime.plusMinutes(1);
            Bid firstBid = new Bid(buyer, 100.25, firstTime);
            Bid secondBid = new Bid(buyer, 110.75, secondTime);
            long firstBidId = bidDAO.insertBid(auctionId, firstBid);
            long secondBidId = bidDAO.insertBid(auctionId, secondBid);
            check(firstBidId > 0 && secondBidId > firstBidId,
                    "Bid rows should receive increasing generated IDs.");

            List<Bid> history = bidDAO.getBidsForAuction(auctionId);
            check(history.size() == 2, "Auction bid history should contain both bids.");
            check(history.get(0).getTimestamp().equals(secondTime),
                    "Bid history should be newest first and preserve microsecond timestamps.");
            check(history.get(1).getTimestamp().equals(firstTime),
                    "Older bid should follow newer bid in the history result.");
            check(bidDAO.getBidsForBuyer(buyer.getUserId()).size() == 2,
                    "Buyer lookup should return the buyer's bids.");
            Bid highestBid = bidDAO.getHighestBidForAuction(auctionId);
            check(highestBid != null && highestBid.getAmount() == 110.75,
                    "Highest-bid lookup should return the largest amount.");
            check(bidDAO.findBidById(secondBidId).getTimestamp().equals(secondTime),
                    "Bid lookup should preserve its stored timestamp.");
            check(bidDAO.findBidById(findUnusedBidId()) == null,
                    "Missing bid lookup should return null.");
            check(bidDAO.getHighestBidForAuction(missingAuctionId) == null,
                    "An auction with no stored bids should have no highest bid.");
            check(bidDAO.getBidsForAuction(missingAuctionId).isEmpty(),
                    "A missing auction's history query should return an empty list.");

            Auction reloaded = auctionDAO.findAuctionById(auctionId);
            check(reloaded.getBids().size() == 2,
                    "Reconstructed auction should contain its persisted bids.");
            check(reloaded.getBids().get(0).getTimestamp().equals(firstTime),
                    "Reconstructed auction should retain chronological in-auction bid order.");
            check(reloaded.getHighestValidBid().getAmount() == 110.75,
                    "Reconstructed auction should identify its highest stored bid.");

            check(auctionDAO.closeAuction(auctionId), "Manual auction close should update one row.");
            Auction closedAuction = auctionDAO.findAuctionById(auctionId);
            check("manually closed".equals(closedAuction.getStatus()),
                    "Manual closed status should be restored from the database.");
            check(closedAuction.getWinner() != null
                            && buyer.getUserId().equals(closedAuction.getWinner().getUserId()),
                    "Winner should be derived from the restored highest bid after closing.");

        } finally {
            cleanup(testAuctionIds, productId, userIds, buyerCreated, sellerCreated,
                    productCreated);
        }
    }

    private static void assertProductEquals(Product expected, Product actual) {
        check(actual != null, "Product lookup should return an object.");
        check(expected.getProductId().equals(actual.getProductId()), "Product ID should round-trip.");
        check(expected.getName().equals(actual.getName()), "Product name should round-trip.");
        check(expected.getDescription().equals(actual.getDescription()),
                "Product description should round-trip.");
        check(expected.getCondition().equals(actual.getCondition()),
                "Product condition should round-trip.");
    }

    private static void assertAuctionEquals(Auction expected, Auction actual) {
        check(actual != null, "Auction lookup should return an object.");
        check(expected.getAuctionId().equals(actual.getAuctionId()), "Auction ID should round-trip.");
        check(expected.getProduct().getProductId().equals(actual.getProduct().getProductId()),
                "Auction should be reconstructed with its associated product.");
        check(expected.getSeller().getUserId().equals(actual.getSeller().getUserId()),
                "Auction should be reconstructed with its associated seller.");
        check(expected.getEndTime().equals(actual.getEndTime()), "Auction end time should round-trip.");
        check(expected.getMinimumBidIncrement() == actual.getMinimumBidIncrement(),
                "Auction minimum increment should round-trip.");
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
        throw new SQLException("Could not allocate unused integration-test user IDs.");
    }

    private static int findUnusedUserId() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            for (int attempt = 0; attempt < 100; attempt++) {
                int candidate = ThreadLocalRandom.current().nextInt(100_000_000, 1_900_000_000);
                if (!userIdExists(connection, candidate)) return candidate;
            }
        }
        throw new SQLException("Could not allocate an unused foreign-key test ID.");
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

    private static long findUnusedBidId() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            for (int attempt = 0; attempt < 100; attempt++) {
                long candidate = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM bids WHERE bid_id = ?")) {
                    statement.setLong(1, candidate);
                    try (ResultSet results = statement.executeQuery()) {
                        if (!results.next()) return candidate;
                    }
                }
            }
        }
        throw new SQLException("Could not allocate an unused integration-test bid ID.");
    }

    private static void cleanup(String[] auctionIds, String productId, int[] userIds,
            boolean buyerCreated, boolean sellerCreated, boolean productCreated) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (int index = 0; index < auctionIds.length; index++) {
                    deleteByString(connection, "DELETE FROM bids WHERE auction_id = ?", auctionIds[index]);
                    deleteByString(connection, "DELETE FROM auctions WHERE auction_id = ?",
                            auctionIds[index]);
                }
                if (productCreated) {
                    deleteByString(connection, "DELETE FROM products WHERE product_id = ?", productId);
                }
                if (buyerCreated) deleteUser(connection, userIds[0]);
                if (sellerCreated) deleteUser(connection, userIds[1]);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void deleteByString(Connection connection, String sql, String value)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.executeUpdate();
        }
    }

    private static void deleteUser(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM users WHERE user_id = ?")) {
            statement.setInt(1, userId);
            statement.executeUpdate();
        }
    }

    private static void expectConstraint(SqlAction action, String label) throws Exception {
        try {
            action.run();
            throw new AssertionError(label + " should be rejected by a database constraint.");
        } catch (SQLException exception) {
            check(exception.getSQLState() != null && exception.getSQLState().startsWith("23"),
                    label + " failed for an unexpected SQL reason: " + exception.getMessage());
        }
    }

    private static void expectIllegalArgument(SqlAction action, String label) throws Exception {
        try {
            action.run();
            throw new AssertionError(label + " should be rejected.");
        } catch (IllegalArgumentException expected) {
            // Expected validation result.
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

    @FunctionalInterface
    private interface SqlAction {
        void run() throws Exception;
    }
}
