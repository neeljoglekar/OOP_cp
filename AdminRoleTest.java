import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/** Database and authorization checks for Phase 5 admin and role behavior. */
public class AdminRoleTest {
    public static void main(String[] args) throws SQLException {
        verifyUnsupportedRoleRejected();
        verifyAdminOperationAuthorization();
        System.out.println("Unsupported-role validation and in-memory admin authorization passed.");

        UserDAO userDAO = new UserDAO();
        int userId = 100_000_000 + (int) (System.currentTimeMillis() % 900_000_000L);
        String email = "phase5-admin-" + userId + "@example.invalid";
        char[] password = "phase5-admin-test-password".toCharArray();
        userDAO.registerUser(userId, "Phase 5 Test Admin", email, password, "ADMIN");

        verifyStoredAdminRole(userId);
        User loggedInUser = userDAO.login(email, password);
        if (!(loggedInUser instanceof Admin)
                || UserSession.getCurrentUser() != loggedInUser
                || !"ADMIN".equals(UserSession.getCurrentUserRole().toUpperCase(Locale.ROOT))) {
            throw new AssertionError("Admin login did not create the correct session role.");
        }

        ((Admin) loggedInUser).monitorAuctions(new AuctionSystem());
        verifyAdminOperationRejected(new Buyer("TEST-BUYER", "Test Buyer"));
        verifyAdminOperationRejected(new Seller("TEST-SELLER", "Test Seller"));
        UserSession.logout();

        System.out.println("ADMIN registration, stored role, login type/session, admin-only "
                + "operation, buyer/seller denial, and unsupported-role rejection were verified.");
    }

    private static void verifyUnsupportedRoleRejected() throws SQLException {
        try {
            new UserDAO().registerUser(1, "Invalid Role Test", "invalid-role@example.invalid",
                    "test-password".toCharArray(), "OBSERVER");
            throw new AssertionError("An unsupported role was accepted.");
        } catch (IllegalArgumentException expected) {
            // Role validation happens before a database connection is opened.
        }
    }

    private static void verifyAdminOperationAuthorization() {
        AuctionSystem system = new AuctionSystem();
        Admin admin = new Admin("TEST-ADMIN", "Test Admin");
        UserSession.setCurrentUser(admin);
        admin.monitorAuctions(system);
        verifyAdminOperationRejected(new Buyer("TEST-BUYER", "Test Buyer"));
        verifyAdminOperationRejected(new Seller("TEST-SELLER", "Test Seller"));
        UserSession.logout();
    }

    private static void verifyAdminOperationRejected(User user) {
        UserSession.setCurrentUser(user);
        try {
            new Admin("TEST-ADMIN", "Test Admin").monitorAuctions(new AuctionSystem());
            throw new AssertionError("An unauthorized user performed an admin-only operation.");
        } catch (SecurityException expected) {
            // Expected: the active session is not the Admin instance making the call.
        } finally {
            UserSession.logout();
        }
    }

    private static void verifyStoredAdminRole(int userId) throws SQLException {
        String sql = "SELECT role, password_hash FROM users WHERE user_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || !"ADMIN".equals(resultSet.getString("role"))
                        || !resultSet.getString("password_hash").startsWith("pbkdf2-sha256$")) {
                    throw new AssertionError("Admin role or password hash was not stored correctly.");
                }
            }
        }
    }
}
