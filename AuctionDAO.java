import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JDBC data access for auctions. Products must be inserted before their auctions. */
public class AuctionDAO {
    private static final int MONEY_SCALE = 4;
    private static final int MONEY_PRECISION = 19;
    private static final String SELECT_COLUMNS =
            "SELECT auction_id, product_id, seller_id, end_time, "
                    + "minimum_bid_increment, closed FROM auctions ";

    private final ProductDAO productDAO;
    private final UserDAO userDAO;
    private final BidDAO bidDAO;

    public AuctionDAO() {
        this(new ProductDAO(), new UserDAO(), new BidDAO());
    }

    public AuctionDAO(ProductDAO productDAO, UserDAO userDAO, BidDAO bidDAO) {
        if (productDAO == null || userDAO == null || bidDAO == null) {
            throw new IllegalArgumentException("Auction DAO dependencies are required.");
        }
        this.productDAO = productDAO;
        this.userDAO = userDAO;
        this.bidDAO = bidDAO;
    }

    public void insertAuction(Auction auction) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            insertAuction(connection, auction);
        }
    }

    /** Uses but does not close or commit the caller-managed connection. */
    public void insertAuction(Connection connection, Auction auction) throws SQLException {
        requireConnection(connection);
        validateAuction(auction);
        int sellerId = parseUserId(auction.getSeller().getUserId(), "Seller ID");
        BigDecimal increment = toDatabaseMoney(auction.getMinimumBidIncrement(),
                "Minimum bid increment");

        String sql = "INSERT INTO auctions (auction_id, product_id, seller_id, end_time, "
                + "minimum_bid_increment, closed) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, auction.getAuctionId());
            statement.setString(2, auction.getProduct().getProductId());
            statement.setInt(3, sellerId);
            setEndTime(statement, 4, auction.getEndTime());
            statement.setBigDecimal(5, increment);
            statement.setBoolean(6, "manually closed".equalsIgnoreCase(auction.getStatus()));
            statement.executeUpdate();
        }
    }

    public Auction findAuctionById(String auctionId)
            throws SQLException, UserNotFoundException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return findAuctionById(connection, auctionId);
        }
    }

    /** Returns null if there is no matching auction. */
    public Auction findAuctionById(Connection connection, String auctionId)
            throws SQLException, UserNotFoundException {
        requireConnection(connection);
        requireId(auctionId, "Auction ID");
        String sql = SELECT_COLUMNS + "WHERE auction_id = ?";
        Map<String, Product> products = new HashMap<>();
        Map<Integer, Seller> sellers = new HashMap<>();
        Map<Integer, Buyer> buyers = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, auctionId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? readAuction(connection, results, products, sellers, buyers) : null;
            }
        }
    }

    public List<Auction> getAllAuctions() throws SQLException, UserNotFoundException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return getAllAuctions(connection);
        }
    }

    public List<Auction> getAllAuctions(Connection connection)
            throws SQLException, UserNotFoundException {
        requireConnection(connection);
        String sql = SELECT_COLUMNS + "ORDER BY auction_id";
        List<Auction> auctions = new ArrayList<>();
        Map<String, Product> products = new HashMap<>();
        Map<Integer, Seller> sellers = new HashMap<>();
        Map<Integer, Buyer> buyers = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                auctions.add(readAuction(connection, results, products, sellers, buyers));
            }
        }
        return auctions;
    }

    public List<Auction> getAuctionsBySellerId(String sellerId)
            throws SQLException, UserNotFoundException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return getAuctionsBySellerId(connection, sellerId);
        }
    }

    public List<Auction> getAuctionsBySellerId(Connection connection, String sellerId)
            throws SQLException, UserNotFoundException {
        requireConnection(connection);
        int numericSellerId = parseUserId(sellerId, "Seller ID");
        String sql = SELECT_COLUMNS + "WHERE seller_id = ? ORDER BY auction_id";
        List<Auction> auctions = new ArrayList<>();
        Map<String, Product> products = new HashMap<>();
        Map<Integer, Seller> sellers = new HashMap<>();
        Map<Integer, Buyer> buyers = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, numericSellerId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    auctions.add(readAuction(connection, results, products, sellers, buyers));
                }
            }
        }
        return auctions;
    }

    /** Returns false if the row was missing or was already manually closed. */
    public boolean closeAuction(String auctionId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return closeAuction(connection, auctionId);
        }
    }

    /** Persists manual closure using a caller-managed connection. */
    public boolean closeAuction(Connection connection, String auctionId) throws SQLException {
        requireConnection(connection);
        requireId(auctionId, "Auction ID");
        String sql = "UPDATE auctions SET closed = TRUE WHERE auction_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, auctionId);
            return statement.executeUpdate() == 1;
        }
    }

    private Auction readAuction(Connection connection, ResultSet results,
            Map<String, Product> products, Map<Integer, Seller> sellers,
            Map<Integer, Buyer> buyers)
            throws SQLException, UserNotFoundException {
        String auctionId = results.getString("auction_id");
        String productId = results.getString("product_id");
        Product product = products.get(productId);
        if (product == null) {
            product = productDAO.findProductById(connection, productId);
            if (product != null) products.put(productId, product);
        }
        if (product == null) {
            throw new SQLException("Auction " + auctionId + " references a missing product.");
        }

        int sellerId = results.getInt("seller_id");
        Seller seller = sellers.get(sellerId);
        if (seller == null) {
            User sellerUser = userDAO.findUserById(connection, sellerId);
            if (!(sellerUser instanceof Seller)) {
                throw new SQLException("Auction " + auctionId
                        + " references a user who is not a seller.");
            }
            seller = (Seller) sellerUser;
            sellers.put(sellerId, seller);
        }

        LocalDateTime endTimeValue = results.getObject("end_time", LocalDateTime.class);
        LocalDateTime endTime = endTimeValue == null
                ? LocalDateTime.MAX : endTimeValue;
        double increment = fromDatabaseMoney(results.getBigDecimal("minimum_bid_increment"),
                "Minimum bid increment");
        boolean manuallyClosed = results.getBoolean("closed");

        List<Bid> chronologicalBids = bidDAO.getBidsForAuction(connection, auctionId, buyers);
        Collections.reverse(chronologicalBids);
        return new Auction(auctionId, product, seller, endTime, increment,
                manuallyClosed, chronologicalBids);
    }

    private static void setEndTime(PreparedStatement statement, int parameterIndex,
            LocalDateTime endTime) throws SQLException {
        if (endTime.equals(LocalDateTime.MAX)) {
            statement.setNull(parameterIndex, Types.TIMESTAMP);
            return;
        }
        LocalDateTime microsecondTime = endTime.truncatedTo(ChronoUnit.MICROS);
        statement.setObject(parameterIndex, microsecondTime);
    }

    private static BigDecimal toDatabaseMoney(double amount, String label) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException(label + " must be positive and finite.");
        }
        try {
            BigDecimal decimal = BigDecimal.valueOf(amount)
                    .setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
            if (decimal.precision() > MONEY_PRECISION) {
                throw new IllegalArgumentException(label
                        + " exceeds the DECIMAL(19,4) database range.");
            }
            return decimal;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(label
                    + " must have no more than four fractional digits.", exception);
        }
    }

    private static double fromDatabaseMoney(BigDecimal decimal, String label) throws SQLException {
        if (decimal == null) throw new SQLException(label + " is missing in the database.");
        double value = decimal.doubleValue();
        if (!Double.isFinite(value)) throw new SQLException(label + " is outside Java double range.");
        try {
            BigDecimal roundTrip = BigDecimal.valueOf(value).setScale(MONEY_SCALE,
                    RoundingMode.UNNECESSARY);
            if (roundTrip.compareTo(decimal) != 0) {
                throw new SQLException(label + " cannot be represented without precision loss.");
            }
        } catch (ArithmeticException exception) {
            throw new SQLException(label + " cannot be represented without precision loss.", exception);
        }
        return value;
    }

    private static void validateAuction(Auction auction) {
        if (auction == null) throw new IllegalArgumentException("Auction is required.");
        requireId(auction.getAuctionId(), "Auction ID");
        if (auction.getProduct() == null) throw new IllegalArgumentException("Auction product is required.");
        if (auction.getSeller() == null) throw new IllegalArgumentException("Auction seller is required.");
        requireId(auction.getProduct().getProductId(), "Product ID");
        parseUserId(auction.getSeller().getUserId(), "Seller ID");
    }

    static int parseUserId(String userId, String label) {
        try {
            int numericId = Integer.parseInt(userId);
            if (numericId <= 0) throw new NumberFormatException();
            return numericId;
        } catch (NumberFormatException | NullPointerException exception) {
            throw new IllegalArgumentException(label + " must be a positive numeric INT.", exception);
        }
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
