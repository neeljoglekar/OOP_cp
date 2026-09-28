import java.sql.SQLException;

/** Database-backed checks for Phase 3 login behavior. */
public class UserLoginTest {
    public static void main(String[] args) throws SQLException {
        UserDAO userDAO = new UserDAO();
        int userId = 100_000_000 + (int) (System.currentTimeMillis() % 900_000_000L);
        String buyerEmail = "phase3-buyer-" + userId + "@example.invalid";
        String sellerEmail = "phase3-seller-" + userId + "@example.invalid";
        char[] buyerPassword = "phase3-buyer-test-password".toCharArray();
        char[] sellerPassword = "phase3-seller-test-password".toCharArray();

        userDAO.registerUser(userId, "Phase 3 Test Buyer", buyerEmail, buyerPassword, "BUYER");
        userDAO.registerUser(userId + 1, "Phase 3 Test Seller", sellerEmail,
                sellerPassword, "SELLER");

        User buyer = userDAO.login(buyerEmail, buyerPassword);
        assertUserTypeAndSession(buyer, Buyer.class, "Buyer");

        User seller = userDAO.login(sellerEmail, sellerPassword);
        assertUserTypeAndSession(seller, Seller.class, "Seller");

        if (userDAO.login(sellerEmail, "incorrect-password".toCharArray()) != null) {
            throw new AssertionError("An incorrect password was accepted.");
        }
        if (userDAO.login("unknown-" + userId + "@example.invalid",
                "any-password".toCharArray()) != null) {
            throw new AssertionError("An unknown email was accepted.");
        }
        if (UserSession.getCurrentUser() != seller || !UserSession.isLoggedIn()) {
            throw new AssertionError("Failed login changed the current user session.");
        }

        System.out.println("Buyer and seller login, role/type, current-user session, "
                + "incorrect password, and unknown email were verified.");
    }

    private static void assertUserTypeAndSession(User user, Class<?> expectedType,
            String expectedRole) {
        if (user == null || !expectedType.isInstance(user)
                || !expectedRole.equals(user.getRole())
                || UserSession.getCurrentUser() != user
                || !expectedRole.equals(UserSession.getCurrentUserRole())) {
            throw new AssertionError("Login did not create the expected user and session.");
        }
    }
}
