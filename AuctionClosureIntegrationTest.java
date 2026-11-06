import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MySQL integration checks for ownership-checked, persistent auction closure. */
public class AuctionClosureIntegrationTest {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        if (blank(System.getenv("AUCTION_DB_URL"))
                || blank(System.getenv("AUCTION_DB_USERNAME"))
                || System.getenv("AUCTION_DB_PASSWORD") == null) {
            System.out.println("SKIPPED: Set AUCTION_DB_URL, AUCTION_DB_USERNAME, and AUCTION_DB_PASSWORD to run auction closure integration tests.");
            return;
        }
        new AuctionClosureIntegrationTest().run();
    }

    private void run() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        String sellerAEmail = "closure-a-" + unique + "@example.test";
        String sellerBEmail = "closure-b-" + unique + "@example.test";
        String buyerEmail = "closure-buyer-" + unique + "@example.test";
        int[] ids = allocateUserIds(3);
        String productA = "closure-product-a-" + unique;
        String productB = "closure-product-b-" + unique;
        String auctionA = "closure-auction-a-" + unique;
        String auctionB = "closure-auction-b-" + unique;
        String password = "closure-test-" + unique;
        UserDAO userDAO = new UserDAO();
        ProductDAO productDAO = new ProductDAO();
        BidDAO bidDAO = new BidDAO(userDAO);
        AuctionDAO auctionDAO = new AuctionDAO(productDAO, userDAO, bidDAO);
        boolean[] usersCreated = new boolean[3];
        AuctionServer server = null;
        try {
            register(userDAO, ids[0], "Closure Seller A", sellerAEmail, password, "SELLER");
            usersCreated[0] = true;
            register(userDAO, ids[1], "Closure Seller B", sellerBEmail, password, "SELLER");
            usersCreated[1] = true;
            register(userDAO, ids[2], "Closure Buyer", buyerEmail, password, "BUYER");
            usersCreated[2] = true;
            Seller sellerA = (Seller) userDAO.findUserById(ids[0]);
            Seller sellerB = (Seller) userDAO.findUserById(ids[1]);
            Product firstProduct = new Product(productA, "Closure item A", "Test item.", "Good");
            Product secondProduct = new Product(productB, "Closure item B", "Test item.", "Average");
            productDAO.insertProduct(firstProduct);
            productDAO.insertProduct(secondProduct);
            LocalDateTime end = LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.MICROS);
            auctionDAO.insertAuction(new Auction(auctionA, firstProduct, sellerA, end, 1.0));
            auctionDAO.insertAuction(new Auction(auctionB, secondProduct, sellerB, end, 1.0));

            server = startServer(new AuctionSystem(), userDAO, productDAO, auctionDAO, bidDAO);
            URI base = base(server);
            String sellerAToken = login(base, sellerAEmail, password);
            String sellerBToken = login(base, sellerBEmail, password);
            String buyerToken = login(base, buyerEmail, password);
            check(close(base, auctionA, sellerBToken).statusCode() == 403,
                    "A Seller was allowed to end another Seller's auction.");
            check(close(base, auctionA, buyerToken).statusCode() == 403,
                    "A Buyer was allowed to end an auction.");
            HttpResponse<String> ended = close(base, auctionA, sellerAToken);
            check(ended.statusCode() == 200 && ended.body().contains("manually closed"),
                    "The owning Seller could not end their active auction.");
            check("manually closed".equals(auctionDAO.findAuctionById(auctionA).getStatus()),
                    "Manual closure was not stored in MySQL.");
            check(close(base, auctionA, sellerAToken).statusCode() == 409,
                    "An already-ended auction was not rejected.");
            check(bid(base, auctionA, buyerToken).statusCode() == 409,
                    "Bidding remained possible after persistent manual closure.");

            server.stop();
            server = null;
            AuctionSystem restartedSystem = new AuctionSystem();
            server = startServer(restartedSystem, userDAO, productDAO, auctionDAO, bidDAO);
            check("manually closed".equals(restartedSystem.getAuction(auctionA).getStatus()),
                    "Manual closure did not remain after restarting AuctionServer.");
            System.out.println("AuctionClosureIntegrationTest passed: seller ownership, buyer denial, database closure, post-close bid rejection, and restart restoration.");
        } finally {
            if (server != null) server.stop();
            cleanup(auctionA, auctionB, productA, productB, ids,
                    sellerAEmail, sellerBEmail, buyerEmail, usersCreated);
        }
    }

    private static AuctionServer startServer(AuctionSystem system, UserDAO userDAO,
            ProductDAO productDAO, AuctionDAO auctionDAO, BidDAO bidDAO) throws Exception {
        AuctionServer server = new AuctionServer(system, userDAO, productDAO, auctionDAO, bidDAO, 0);
        server.start();
        return server;
    }

    private static URI base(AuctionServer server) {
        return URI.create("http://127.0.0.1:" + server.getPort());
    }

    private static HttpResponse<String> close(URI base, String auctionId, String token)
            throws Exception {
        return send(base.resolve("/api/auctions/" + auctionId + "/close"), token, "POST", "");
    }

    private static HttpResponse<String> bid(URI base, String auctionId, String token)
            throws Exception {
        return send(base.resolve("/api/auctions/" + auctionId + "/bids"), token,
                "POST", "{\"amount\":5.0}");
    }

    private static HttpResponse<String> send(URI uri, String token, String method, String body)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String login(URI base, String email, String password) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email
                        + "\",\"password\":\"" + password + "\"}"))
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == 200, "Test login failed for " + email + ".");
        Matcher matcher = Pattern.compile("\\\"sessionToken\\\":\\\"([^\\\"]+)\\\"")
                .matcher(response.body());
        check(matcher.find(), "Login response did not return a session token.");
        return matcher.group(1);
    }

    private static void register(UserDAO dao, int id, String name, String email,
            String password, String role) throws SQLException {
        char[] passwordChars = password.toCharArray();
        try {
            dao.registerUser(id, name, email, passwordChars, role);
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    private static int[] allocateUserIds(int count) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            for (int attempt = 0; attempt < 100; attempt++) {
                int first = ThreadLocalRandom.current().nextInt(100_000_000, 1_900_000_000);
                int[] ids = new int[count];
                boolean available = true;
                for (int index = 0; index < count; index++) {
                    ids[index] = first + index;
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT 1 FROM users WHERE user_id = ?")) {
                        statement.setInt(1, ids[index]);
                        try (ResultSet results = statement.executeQuery()) {
                            if (results.next()) available = false;
                        }
                    }
                }
                if (available) return ids;
            }
        }
        throw new SQLException("Could not allocate test user IDs.");
    }

    private static void cleanup(String auctionA, String auctionB, String productA,
            String productB, int[] ids, String sellerA, String sellerB, String buyer,
            boolean[] usersCreated) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                delete(connection, "DELETE FROM bids WHERE auction_id IN (?, ?)", auctionA, auctionB);
                delete(connection, "DELETE FROM auctions WHERE auction_id IN (?, ?)", auctionA, auctionB);
                delete(connection, "DELETE FROM products WHERE product_id IN (?, ?)", productA, productB);
                if (usersCreated[0]) deleteUser(connection, ids[0], sellerA);
                if (usersCreated[1]) deleteUser(connection, ids[1], sellerB);
                if (usersCreated[2]) deleteUser(connection, ids[2], buyer);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void delete(Connection connection, String sql, String first, String second)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, first);
            statement.setString(2, second);
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

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
