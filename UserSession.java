/** Holds the user currently signed in to this console application. */
public final class UserSession {
    private static User currentUser;

    private UserSession() {
        // Session state is accessed through the static methods below.
    }

    static void setCurrentUser(User user) {
        currentUser = user;
    }

    /** Clears the current application login without changing stored user data. */
    public static void logout() {
        currentUser = null;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static String getCurrentUserRole() {
        return currentUser == null ? null : currentUser.getRole();
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }
}
