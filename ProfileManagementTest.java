import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/** Database-backed checks for Phase 6 profile management. */
public class ProfileManagementTest {
    public static void main(String[] args) throws SQLException {
        UserDAO userDAO = new UserDAO();
        verifyProfileOperationsRequireLogin(userDAO);
        System.out.println("Profile operations correctly require a logged-in user.");

        int buyerId = 100_000_000 + (int) (System.currentTimeMillis() % 900_000_000L);
        int sellerId = buyerId + 1;
        int adminId = buyerId + 2;
        String buyerEmail = "phase6-buyer-" + buyerId + "@example.invalid";
        String updatedBuyerEmail = "phase6-buyer-updated-" + buyerId + "@example.invalid";
        String sellerEmail = "phase6-seller-" + sellerId + "@example.invalid";
        String adminEmail = "phase6-admin-" + adminId + "@example.invalid";
        char[] buyerPassword = "phase6-buyer-old-password".toCharArray();
        char[] sellerPassword = "phase6-seller-password".toCharArray();
        char[] adminPassword = "phase6-admin-password".toCharArray();

        userDAO.registerUser(buyerId, "Phase 6 Buyer", buyerEmail, buyerPassword, "BUYER");
        userDAO.registerUser(sellerId, "Phase 6 Seller", sellerEmail, sellerPassword, "SELLER");
        userDAO.registerUser(adminId, "Phase 6 Admin", adminEmail, adminPassword, "ADMIN");

        String[] sellerBeforeBuyerProfile = readRecord(sellerId);
        verifyBuyerProfileFlow(userDAO, buyerId, buyerEmail, updatedBuyerEmail, sellerEmail,
                buyerPassword);
        verifyRecordUnchanged(sellerId, sellerBeforeBuyerProfile);
        verifyOtherRoleProfile(userDAO, sellerEmail, sellerPassword, Seller.class,
                "Phase 6 Seller Updated");
        String[] sellerBeforeAdminProfile = readRecord(sellerId);
        verifyOtherRoleProfile(userDAO, adminEmail, adminPassword, Admin.class,
                "Phase 6 Admin Updated");
        verifyRecordUnchanged(sellerId, sellerBeforeAdminProfile);
        verifyProfileOperationsRequireLogin(userDAO);

        System.out.println("Profile view, name/email/password updates, duplicate email rejection, "
                + "user isolation, Buyer/Seller/Admin profiles, and login checks were verified.");
    }

    private static void verifyProfileOperationsRequireLogin(UserDAO userDAO) throws SQLException {
        UserSession.logout();
        expectNotLoggedIn(userDAO::viewCurrentProfile);
        expectNotLoggedIn(() -> userDAO.updateCurrentProfile("No Session", "no-session@example.invalid"));
        expectNotLoggedIn(() -> userDAO.changeCurrentPassword("new-password".toCharArray()));
    }

    private static void expectNotLoggedIn(SqlAction action) throws SQLException {
        try {
            action.run();
            throw new AssertionError("A profile operation succeeded without a logged-in user.");
        } catch (IllegalStateException expected) {
            // A profile operation must require an active session.
        }
    }

    private static void verifyBuyerProfileFlow(UserDAO userDAO, int userId, String oldEmail,
            String newEmail, String otherUserEmail, char[] oldPassword) throws SQLException {
        User loggedIn = userDAO.login(oldEmail, oldPassword);
        if (!(loggedIn instanceof Buyer)) {
            throw new AssertionError("Buyer login failed before profile testing.");
        }

        User profile = userDAO.viewCurrentProfile();
        assertProfile(profile, userId, "Phase 6 Buyer", oldEmail, Buyer.class);

        if (!userDAO.updateCurrentProfile("Phase 6 Buyer Updated", newEmail)
                || UserSession.getCurrentUser() != loggedIn) {
            throw new AssertionError("Buyer profile update failed or replaced the current session.");
        }
        assertProfile(loggedIn, userId, "Phase 6 Buyer Updated", newEmail, Buyer.class);
        assertRecord(userId, "Phase 6 Buyer Updated", newEmail, "BUYER");

        if (userDAO.login(oldEmail, oldPassword) != null) {
            throw new AssertionError("The old email still allowed login after an email update.");
        }
        User updatedEmailLogin = userDAO.login(newEmail, oldPassword);
        if (!(updatedEmailLogin instanceof Buyer)) {
            throw new AssertionError("Login with the updated email failed.");
        }

        String[] beforeDuplicate = readRecord(userId);
        if (userDAO.updateCurrentProfile("Should Not Save", otherUserEmail)) {
            throw new AssertionError("An email owned by another user was accepted.");
        }
        verifyRecordUnchanged(userId, beforeDuplicate);
        assertProfile(updatedEmailLogin, userId, "Phase 6 Buyer Updated", newEmail, Buyer.class);

        String oldHash = readRecord(userId)[3];
        char[] newPassword = "phase6-buyer-new-password".toCharArray();
        if (!userDAO.changeCurrentPassword(newPassword)) {
            throw new AssertionError("The Buyer password was not updated.");
        }
        String newHash = readRecord(userId)[3];
        if (oldHash.equals(newHash) || !newHash.startsWith("pbkdf2-sha256$")) {
            throw new AssertionError("The new password hash was not stored correctly.");
        }
        if (userDAO.login(newEmail, oldPassword) != null) {
            throw new AssertionError("The old password still worked after password change.");
        }
        User newPasswordLogin = userDAO.login(newEmail, newPassword);
        if (!(newPasswordLogin instanceof Buyer)) {
            throw new AssertionError("Login with the new password failed.");
        }
    }

    private static void verifyOtherRoleProfile(UserDAO userDAO, String email, char[] password,
            Class<?> expectedType, String updatedName) throws SQLException {
        User user = userDAO.login(email, password);
        if (user == null || !expectedType.isInstance(user)) {
            throw new AssertionError("Login failed for profile role " + expectedType.getSimpleName());
        }

        User profile = userDAO.viewCurrentProfile();
        if (profile != user || !expectedType.isInstance(profile)
                || !email.equals(profile.getEmail())) {
            throw new AssertionError("Profile view returned incorrect role or email.");
        }
        if (!userDAO.updateCurrentProfile(updatedName, email)
                || UserSession.getCurrentUser() != user || !updatedName.equals(user.getName())) {
            throw new AssertionError("Profile update failed for " + expectedType.getSimpleName());
        }
        assertRecord(Integer.parseInt(user.getUserId()), updatedName, email,
                user.getRole().toUpperCase(Locale.ROOT));
    }

    private static void assertProfile(User user, int userId, String name, String email,
            Class<?> expectedType) {
        if (user == null || !expectedType.isInstance(user)
                || !String.valueOf(userId).equals(user.getUserId())
                || !name.equals(user.getName()) || !email.equals(user.getEmail())) {
            throw new AssertionError("The profile fields or role were incorrect.");
        }
    }

    private static void assertRecord(int userId, String name, String email, String role)
            throws SQLException {
        String[] record = readRecord(userId);
        if (!String.valueOf(userId).equals(record[0]) || !name.equals(record[1])
                || !email.equals(record[2]) || !role.equals(record[4])) {
            throw new AssertionError("The database profile fields were incorrect.");
        }
    }

    private static void verifyRecordUnchanged(int userId, String[] expected) throws SQLException {
        String[] actual = readRecord(userId);
        for (int index = 0; index < expected.length; index++) {
            if (!expected[index].equals(actual[index])) {
                throw new AssertionError("A profile operation changed another user's record.");
            }
        }
    }

    private static String[] readRecord(int userId) throws SQLException {
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

    @FunctionalInterface
    private interface SqlAction {
        Object run() throws SQLException;
    }
}
