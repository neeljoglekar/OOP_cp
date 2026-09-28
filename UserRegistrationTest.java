import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserRegistrationTest {
    public static void main(String[] args) throws SQLException {
        UserDAO userDAO = new UserDAO();
        int buyerId = 100_000_000 + (int) (System.currentTimeMillis() % 900_000_000L);
        int sellerId = buyerId + 1;
        String buyerEmail = "phase2-buyer-" + buyerId + "@example.invalid";
        String sellerEmail = "phase2-seller-" + sellerId + "@example.invalid";
        String buyerPassword = "phase2-buyer-test-password";
        String sellerPassword = "phase2-seller-test-password";

        User buyer = userDAO.registerUser(buyerId, "Phase 2 Test Buyer", buyerEmail,
                buyerPassword.toCharArray(), "BUYER");
        User seller = userDAO.registerUser(sellerId, "Phase 2 Test Seller", sellerEmail,
                sellerPassword.toCharArray(), "SELLER");

        if (!(buyer instanceof Buyer) || !(seller instanceof Seller)) {
            throw new AssertionError("Registration did not create the correct user subclasses.");
        }
        verifyStoredRecord(buyerId, buyerEmail, "BUYER", buyerPassword);
        verifyStoredRecord(sellerId, sellerEmail, "SELLER", sellerPassword);
        expectDuplicate(buyerId + 2, "Another Name", buyerEmail, "another-password", "BUYER");
        expectDuplicate(buyerId, "Another Name", "duplicate-id-" + buyerId + "@example.invalid",
                "another-password", "SELLER");

        System.out.println("Buyer and seller registration, database records, password hashes, "
                + "duplicate email, and duplicate user ID were verified.");
        System.out.println("Test records remain in the users table with generated IDs "
                + buyerId + " and " + sellerId + ".");
    }

    private static void verifyStoredRecord(int userId, String email, String expectedRole,
            String plainTextPassword) throws SQLException {
        String sql = "SELECT email, password_hash, role FROM users WHERE user_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError("Registered user was not found in the database.");
                }
                String storedHash = resultSet.getString("password_hash");
                if (!email.equals(resultSet.getString("email"))
                        || !expectedRole.equals(resultSet.getString("role"))
                        || storedHash.equals(plainTextPassword)
                        || !storedHash.startsWith("pbkdf2-sha256$")) {
                    throw new AssertionError("Stored user data or password hash was incorrect.");
                }
            }
        }
    }

    private static void expectDuplicate(int userId, String name, String email,
            String password, String role) throws SQLException {
        try {
            new UserDAO().registerUser(userId, name, email, password.toCharArray(), role);
            throw new AssertionError("Expected duplicate user ID or email to be rejected.");
        } catch (SQLException exception) {
            if (!"23000".equals(exception.getSQLState())) {
                throw exception;
            }
        }
    }
}
