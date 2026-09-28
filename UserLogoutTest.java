import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Database-backed checks for Phase 4 logout behavior. */
public class UserLogoutTest {
    public static void main(String[] args) throws SQLException {
        UserSession.setCurrentUser(new Buyer("TEST-BUYER", "Logout Test Buyer"));
        UserSession.logout();
        verifyLoggedOut();
        System.out.println("In-memory logout cleared a populated session.");

        UserDAO userDAO = new UserDAO();
        int userId = 100_000_000 + (int) (System.currentTimeMillis() % 900_000_000L);
        String email = "phase4-buyer-" + userId + "@example.invalid";
        char[] password = "phase4-buyer-test-password".toCharArray();

        userDAO.registerUser(userId, "Phase 4 Test Buyer", email, password, "BUYER");
        String[] savedUser = readUserRecord(userId);

        User firstLogin = userDAO.login(email, password);
        verifyBuyerSession(firstLogin, userId);
        UserSession.logout();
        verifyLoggedOut();
        verifyUserRecordUnchanged(userId, savedUser);

        User secondLogin = userDAO.login(email, password);
        verifyBuyerSession(secondLogin, userId);
        UserSession.logout();
        verifyLoggedOut();
        verifyUserRecordUnchanged(userId, savedUser);

        System.out.println("Buyer login, logout, repeat login, cleared session, and "
                + "unchanged database user record were verified.");
    }

    private static void verifyBuyerSession(User user, int userId) {
        if (!(user instanceof Buyer) || !String.valueOf(userId).equals(user.getUserId())
                || UserSession.getCurrentUser() != user || !UserSession.isLoggedIn()) {
            throw new AssertionError("Buyer login did not set the expected current user.");
        }
    }

    private static void verifyLoggedOut() {
        if (UserSession.getCurrentUser() != null || UserSession.isLoggedIn()
                || UserSession.getCurrentUserRole() != null) {
            throw new AssertionError("Logout did not clear the current-user session.");
        }
    }

    private static void verifyUserRecordUnchanged(int userId, String[] savedUser)
            throws SQLException {
        String[] currentUser = readUserRecord(userId);
        for (int index = 0; index < savedUser.length; index++) {
            if (!savedUser[index].equals(currentUser[index])) {
                throw new AssertionError("Logout changed the user's database record.");
            }
        }
    }

    private static String[] readUserRecord(int userId) throws SQLException {
        String sql = "SELECT user_id, name, email, password_hash, role FROM users WHERE user_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError("The test user was not found in the database.");
                }
                return new String[] {
                    resultSet.getString("user_id"),
                    resultSet.getString("name"),
                    resultSet.getString("email"),
                    resultSet.getString("password_hash"),
                    resultSet.getString("role")
                };
            }
        }
    }
}
