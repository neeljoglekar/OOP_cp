/** Local checks for admin authorization and logout behavior. */
public class UnauthorizedActionExceptionTest {
    public static void main(String[] args) throws UnauthorizedActionException {
        AuctionSystem system = new AuctionSystem();
        Admin loggedInAdmin = new Admin("AUTH-ADMIN", "Authorized Admin");
        UserSession.setCurrentUser(loggedInAdmin);
        loggedInAdmin.monitorAuctions(system);

        expectUnauthorized(new Buyer("AUTH-BUYER", "Unauthorized Buyer"));
        expectUnauthorized(new Seller("AUTH-SELLER", "Unauthorized Seller"));

        UserSession.setCurrentUser(loggedInAdmin);
        expectUnauthorized(new Admin("OTHER-ADMIN", "Different Admin"));

        UserSession.setCurrentUser(new Buyer("AUTH-BUYER", "Logged In Buyer"));
        UserSession.logout();
        if (UserSession.isLoggedIn() || UserSession.getCurrentUser() != null) {
            throw new AssertionError("Logout did not clear the user session.");
        }

        System.out.println("UnauthorizedActionExceptionTest passed.");
    }

    private static void expectUnauthorized(User user) throws UnauthorizedActionException {
        UserSession.setCurrentUser(user);
        try {
            new Admin("AUTH-ADMIN", "Authorized Admin").monitorAuctions(new AuctionSystem());
            throw new AssertionError(user.getRole() + " performed an admin-only operation.");
        } catch (UnauthorizedActionException expected) {
            // Expected when the active user is not the Admin performing the action.
        } finally {
            UserSession.logout();
        }
    }
}
