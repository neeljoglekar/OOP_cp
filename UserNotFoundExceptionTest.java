import java.sql.SQLException;

/** Database-backed checks for user lookup and missing-user reporting. */
public class UserNotFoundExceptionTest {
    public static void main(String[] args) throws SQLException, UserNotFoundException {
        UserDAO userDAO = new UserDAO();
        int userId = 100_000_000 + (int) (System.currentTimeMillis() % 900_000_000L);
        String email = "user-not-found-test-" + userId + "@example.invalid";
        char[] password = "user-not-found-test-password".toCharArray();

        User registered = userDAO.registerUser(userId, "Lookup Test User", email,
                password, "BUYER");
        User found = userDAO.findUserById(userId);
        check(found instanceof Buyer, "ID lookup did not create the Buyer subtype.");
        check(found.getUserId().equals(registered.getUserId())
                        && found.getName().equals("Lookup Test User")
                        && found.getEmail().equals(email),
                "ID lookup returned incorrect user information.");

        try {
            userDAO.findUserById(userId + 1);
            throw new AssertionError("A nonexistent user ID did not throw UserNotFoundException.");
        } catch (UserNotFoundException expected) {
            // Expected for an explicitly requested missing user ID.
        }

        check(userDAO.login("unknown-" + userId + "@example.invalid", password) == null,
                "Unknown-email login behavior changed.");
        User unchanged = userDAO.findUserById(userId);
        check(unchanged.getName().equals("Lookup Test User") && unchanged.getEmail().equals(email),
                "Lookup unexpectedly changed the stored user record.");

        System.out.println("UserNotFoundExceptionTest passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
