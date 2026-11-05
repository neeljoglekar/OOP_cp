import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JDBC data access for accepted bids. Auction rules are enforced before this DAO is called. */
public class BidDAO {
    private static final int MONEY_SCALE = 4;
    private static final int MONEY_PRECISION = 19;
    private static final String SELECT_COLUMNS =
            "SELECT bid_id, auction_id, buyer_id, bid_amount, bid_timestamp FROM bids ";

    private final UserDAO userDAO;

    public BidDAO() {
        this(new UserDAO());
    }

    public BidDAO(UserDAO userDAO) {
        if (userDAO == null) throw new IllegalArgumentException("User DAO is required.");
        this.userDAO = userDAO;
    }

    public long insertBid(String auctionId, Bid bid) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return insertBid(connection, auctionId, bid);
        }
    }

    /** Inserts an already-accepted bid using a caller-managed connection. */
    public long insertBid(Connection connection, String auctionId, Bid bid) throws SQLException {
        requireConnection(connection);
        requireId(auctionId, "Auction ID");
        if (bid == null) throw new IllegalArgumentException("Bid is required.");
        if (bid.getBidder() == null) throw new IllegalArgumentException("Bidder is required.");
        int buyerId = AuctionDAO.parseUserId(bid.getBidder().getUserId(), "Buyer ID");
        BigDecimal amount = toDatabaseMoney(bid.getAmount());
        LocalDateTime timestamp = bid.getTimestamp().truncatedTo(ChronoUnit.MICROS);

        String sql = "INSERT INTO bids (auction_id, buyer_id, bid_amount, bid_timestamp) "
                + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql,
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, auctionId);
            statement.setInt(2, buyerId);
            statement.setBigDecimal(3, amount);
            statement.setObject(4, timestamp);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) throw new SQLException("The database did not return a bid ID.");
                return generatedKeys.getLong(1);
            }
        }
    }

    /** Returns the auction's bid history newest first; a missing/empty history returns an empty list. */
    public List<Bid> getBidsForAuction(String auctionId)
            throws SQLException, UserNotFoundException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return getBidsForAuction(connection, auctionId);
        }
    }

    public List<Bid> getBidsForAuction(Connection connection, String auctionId)
            throws SQLException, UserNotFoundException {
        return getBidsForAuction(connection, auctionId, new HashMap<>());
    }

    /** Allows a caller loading several auctions to reuse resolved Buyer objects. */
    public List<Bid> getBidsForAuction(Connection connection, String auctionId,
            Map<Integer, Buyer> buyers)
            throws SQLException, UserNotFoundException {
        return getBidsForAuction(connection, auctionId, buyers, false);
    }

    /** Reads and locks an auction's current bid rows after its auction row has been locked. */
    public List<Bid> getBidsForAuctionForUpdate(Connection connection, String auctionId,
            Map<Integer, Buyer> buyers)
            throws SQLException, UserNotFoundException {
        return getBidsForAuction(connection, auctionId, buyers, true);
    }

    private List<Bid> getBidsForAuction(Connection connection, String auctionId,
            Map<Integer, Buyer> buyers, boolean forUpdate)
            throws SQLException, UserNotFoundException {
        requireConnection(connection);
        requireId(auctionId, "Auction ID");
        if (buyers == null) throw new IllegalArgumentException("Buyer cache is required.");
        String sql = SELECT_COLUMNS
                + "WHERE auction_id = ? ORDER BY bid_timestamp DESC, bid_id DESC"
                + (forUpdate ? " FOR UPDATE" : "");
        List<Bid> bids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, auctionId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) bids.add(readBid(connection, results, buyers));
            }
        }
        return bids;
    }

    public List<Bid> getBidsForBuyer(String buyerId)
            throws SQLException, UserNotFoundException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return getBidsForBuyer(connection, buyerId);
        }
    }

    public List<Bid> getBidsForBuyer(Connection connection, String buyerId)
            throws SQLException, UserNotFoundException {
        requireConnection(connection);
        int numericBuyerId = AuctionDAO.parseUserId(buyerId, "Buyer ID");
        String sql = SELECT_COLUMNS
                + "WHERE buyer_id = ? ORDER BY bid_timestamp DESC, bid_id DESC";
        List<Bid> bids = new ArrayList<>();
        Map<Integer, Buyer> buyers = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, numericBuyerId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) bids.add(readBid(connection, results, buyers));
            }
        }
        return bids;
    }

    /** Returns the highest bid, or null if no bid exists for the auction. */
    public Bid getHighestBidForAuction(String auctionId)
            throws SQLException, UserNotFoundException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return getHighestBidForAuction(connection, auctionId);
        }
    }

    public Bid getHighestBidForAuction(Connection connection, String auctionId)
            throws SQLException, UserNotFoundException {
        requireConnection(connection);
        requireId(auctionId, "Auction ID");
        String sql = SELECT_COLUMNS
                + "WHERE auction_id = ? ORDER BY bid_amount DESC, bid_timestamp DESC, bid_id DESC LIMIT 1";
        Map<Integer, Buyer> buyers = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, auctionId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? readBid(connection, results, buyers) : null;
            }
        }
    }

    /** Finds a persisted bid by its database-generated row ID, or returns null. */
    public Bid findBidById(long bidId) throws SQLException, UserNotFoundException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return findBidById(connection, bidId);
        }
    }

    public Bid findBidById(Connection connection, long bidId)
            throws SQLException, UserNotFoundException {
        requireConnection(connection);
        if (bidId <= 0) throw new IllegalArgumentException("Bid ID must be positive.");
        String sql = SELECT_COLUMNS + "WHERE bid_id = ?";
        Map<Integer, Buyer> buyers = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, bidId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? readBid(connection, results, buyers) : null;
            }
        }
    }

    private Bid readBid(Connection connection, ResultSet results, Map<Integer, Buyer> buyers)
            throws SQLException, UserNotFoundException {
        int buyerId = results.getInt("buyer_id");
        Buyer buyer = buyers.get(buyerId);
        if (buyer == null) {
            User user = userDAO.findUserById(connection, buyerId);
            if (!(user instanceof Buyer)) {
                throw new SQLException("Bid " + results.getLong("bid_id")
                        + " references a user who is not a buyer.");
            }
            buyer = (Buyer) user;
            buyers.put(buyerId, buyer);
        }

        double amount = fromDatabaseMoney(results.getBigDecimal("bid_amount"));
        LocalDateTime timestamp = results.getObject("bid_timestamp", LocalDateTime.class);
        if (timestamp == null) throw new SQLException("Bid timestamp is missing in the database.");
        return new Bid(buyer, amount, timestamp);
    }

    private static BigDecimal toDatabaseMoney(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Bid amount must be positive and finite.");
        }
        try {
            BigDecimal decimal = BigDecimal.valueOf(amount)
                    .setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
            if (decimal.precision() > MONEY_PRECISION) {
                throw new IllegalArgumentException("Bid amount exceeds the DECIMAL(19,4) database range.");
            }
            return decimal;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Bid amount must have no more than four fractional digits.",
                    exception);
        }
    }

    private static double fromDatabaseMoney(BigDecimal decimal) throws SQLException {
        if (decimal == null) throw new SQLException("Bid amount is missing in the database.");
        double value = decimal.doubleValue();
        if (!Double.isFinite(value)) throw new SQLException("Bid amount is outside Java double range.");
        try {
            BigDecimal roundTrip = BigDecimal.valueOf(value).setScale(MONEY_SCALE,
                    RoundingMode.UNNECESSARY);
            if (roundTrip.compareTo(decimal) != 0) {
                throw new SQLException("Bid amount cannot be represented without precision loss.");
            }
        } catch (ArithmeticException exception) {
            throw new SQLException("Bid amount cannot be represented without precision loss.", exception);
        }
        return value;
    }

    private static void requireId(String id, String label) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException(label + " is required.");
        if (id.codePointCount(0, id.length()) > 100) {
            throw new IllegalArgumentException(label + " exceeds 100 characters.");
        }
    }

    private static void requireConnection(Connection connection) {
        if (connection == null) throw new IllegalArgumentException("Database connection is required.");
    }
}
