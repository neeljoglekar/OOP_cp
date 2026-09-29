import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** REST checks for public registration and token-scoped profile operations. */
public class RestAccountManagementTest {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        AuctionServer server = new AuctionServer(new AuctionSystem(), new UserDAO(), 0);
        server.start();
        try {
            URI baseUri = URI.create("http://127.0.0.1:" + server.getPort());
            verifyPublicRegistrationRestrictions(baseUri);
            verifyProfileSessionAndIsolation(baseUri);
            if (databaseConfigured()) {
                verifyDatabaseAccountFlow(baseUri);
                System.out.println("REST database-backed registration/profile checks passed.");
            } else {
                System.out.println("Database-backed registration/profile checks skipped: "
                        + "AUCTION_DB_URL, AUCTION_DB_USERNAME, and AUCTION_DB_PASSWORD are required.");
            }
            System.out.println("RestAccountManagementTest passed the available checks.");
        } finally {
            UserSession.logout();
            server.stop();
        }
    }

    private static void verifyPublicRegistrationRestrictions(URI baseUri) throws Exception {
        String adminRequest = registrationJson(999_991, "Public Admin", "public-admin@example.invalid",
                "not-a-real-password", "ADMIN");
        HttpResponse<String> admin = post(baseUri.resolve("/api/register"), adminRequest);
        check(admin.statusCode() == 400, "Public registration must reject ADMIN.");
        check(!admin.body().contains("password_hash"), "Registration error exposed a hash.");

        HttpResponse<String> unsupportedRole = post(baseUri.resolve("/api/register"),
                registrationJson(999_992, "Unsupported", "unsupported@example.invalid",
                        "not-a-real-password", "OWNER"));
        check(unsupportedRole.statusCode() == 400,
                "Public registration must reject unsupported roles.");

        HttpResponse<String> malformed = post(baseUri.resolve("/api/register"),
                "{\"userId\":1.5,\"name\":\"Name\",\"email\":\"e@example.invalid\","
                        + "\"password\":\"x\",\"role\":\"BUYER\"}");
        check(malformed.statusCode() == 400,
                "Registration must reject a non-integer user ID.");
    }

    private static void verifyProfileSessionAndIsolation(URI baseUri) throws Exception {
        UserSession.logout();
        HttpResponse<String> noProfileSession = get(baseUri.resolve("/api/profile"));
        check(noProfileSession.statusCode() == 401,
                "Profile retrieval without a current user must be rejected.");
        HttpResponse<String> noUpdateSession = put(baseUri.resolve("/api/profile"),
                "{\"name\":\"Someone\",\"email\":\"someone@example.invalid\"}");
        check(noUpdateSession.statusCode() == 401,
                "Profile update without a REST token must be rejected.");

        UserSession.setCurrentUser(new Buyer("901", "Session Test Buyer"));
        HttpResponse<String> globalOnlyProfile = get(baseUri.resolve("/api/profile"));
        check(globalOnlyProfile.statusCode() == 401,
                "REST profile access must not use the console UserSession.");
        HttpResponse<String> globalOnlyUpdate = put(baseUri.resolve("/api/profile"),
                "{\"name\":\"Changed\",\"email\":\"changed@example.invalid\"}");
        check(globalOnlyUpdate.statusCode() == 401,
                "REST profile updates must require a token even if UserSession is set.");
        UserSession.logout();
    }

    private static void verifyDatabaseAccountFlow(URI baseUri) throws Exception {
        int buyerId = 100_000_000 + (int) (System.currentTimeMillis() % 800_000_000L);
        int sellerId = buyerId + 1;
        String suffix = String.valueOf(buyerId);
        String buyerEmail = "rest-phase5-buyer-" + suffix + "@example.invalid";
        String updatedEmail = "rest-phase5-updated-" + suffix + "@example.invalid";
        String sellerEmail = "rest-phase5-seller-" + suffix + "@example.invalid";
        String oldPassword = "old-rest-password-" + suffix;
        String newPassword = "new-rest-password-" + suffix;

        try {
            HttpResponse<String> buyerRegistration = post(baseUri.resolve("/api/register"),
                    registrationJson(buyerId, "REST Phase Buyer", buyerEmail,
                            oldPassword, "BUYER"));
            check(buyerRegistration.statusCode() == 201
                            && buyerRegistration.body().contains("\"role\":\"Buyer\"")
                            && !containsPasswordData(buyerRegistration.body()),
                    "Buyer registration failed or returned password data.");

            HttpResponse<String> sellerRegistration = post(baseUri.resolve("/api/register"),
                    registrationJson(sellerId, "REST Phase Seller", sellerEmail,
                            "seller-password-" + suffix, "SELLER"));
            check(sellerRegistration.statusCode() == 201
                            && sellerRegistration.body().contains("\"role\":\"Seller\"")
                            && !containsPasswordData(sellerRegistration.body()),
                    "Seller registration failed or returned password data.");

            HttpResponse<String> duplicateId = post(baseUri.resolve("/api/register"),
                    registrationJson(buyerId, "Another", "another-" + suffix + "@example.invalid",
                            "other-password", "BUYER"));
            check(duplicateId.statusCode() == 409, "Duplicate user ID was not rejected.");
            HttpResponse<String> duplicateEmail = post(baseUri.resolve("/api/register"),
                    registrationJson(sellerId + 1, "Another", buyerEmail,
                            "other-password", "BUYER"));
            check(duplicateEmail.statusCode() == 409, "Duplicate email was not rejected.");

            HttpResponse<String> unknownEmail = post(baseUri.resolve("/api/login"),
                    loginJson("missing-" + suffix + "@example.invalid", oldPassword));
            check(unknownEmail.statusCode() == 401,
                    "An unknown email should receive a generic login rejection.");
            HttpResponse<String> wrongPassword = post(baseUri.resolve("/api/login"),
                    loginJson(buyerEmail, "incorrect-password"));
            check(wrongPassword.statusCode() == 401,
                    "An incorrect password should be rejected.");

            HttpResponse<String> buyerLogin = post(baseUri.resolve("/api/login"),
                    loginJson(buyerEmail, oldPassword));
            check(buyerLogin.statusCode() == 200
                            && buyerLogin.body().contains("\"userId\":\"" + buyerId + "\"")
                            && buyerLogin.body().contains("\"role\":\"Buyer\"")
                            && !containsPasswordData(buyerLogin.body()),
                    "Buyer login did not return basic account fields safely.");
            String buyerToken = extractSessionToken(buyerLogin.body());

            HttpResponse<String> profile = get(baseUri.resolve("/api/profile"), buyerToken);
            check(profile.statusCode() == 200 && profile.body().contains(buyerEmail)
                            && profile.body().contains("REST Phase Buyer")
                            && !containsPasswordData(profile.body()),
                    "Profile retrieval returned incorrect or sensitive data.");

            HttpResponse<String> duplicateProfileEmail = put(baseUri.resolve("/api/profile"),
                    profileJson("Should Not Save", sellerEmail, ""), buyerToken);
            check(duplicateProfileEmail.statusCode() == 409,
                    "Profile update accepted an email owned by another user.");

            HttpResponse<String> updated = put(baseUri.resolve("/api/profile"),
                    profileJson("REST Phase Buyer Updated", updatedEmail, newPassword), buyerToken);
            check(updated.statusCode() == 200 && updated.body().contains(updatedEmail)
                            && updated.body().contains("REST Phase Buyer Updated")
                            && !containsPasswordData(updated.body()),
                    "Profile name, email, and password update failed or returned sensitive data.");

            HttpResponse<String> oldCredentials = post(baseUri.resolve("/api/login"),
                    loginJson(buyerEmail, oldPassword));
            check(oldCredentials.statusCode() == 401,
                    "Old credentials remained valid after the account update.");
            HttpResponse<String> newCredentials = post(baseUri.resolve("/api/login"),
                    loginJson(updatedEmail, newPassword));
            check(newCredentials.statusCode() == 200,
                    "Login with the updated email and password failed.");
            String storedHash = readPasswordHash(buyerId);
            check(storedHash != null && storedHash.startsWith("pbkdf2-sha256$")
                            && !storedHash.equals(newPassword),
                    "The database did not store a password hash.");

            HttpResponse<String> userIdMutation = put(baseUri.resolve("/api/profile"),
                    "{\"name\":\"Changed\",\"email\":\"changed-" + suffix
                            + "@example.invalid\",\"password\":\"\",\"userId\":"
                            + (buyerId + 1) + "}", buyerToken);
            check(userIdMutation.statusCode() == 400,
                    "Profile API accepted an attempt to change user ID.");

            HttpResponse<String> logout = post(baseUri.resolve("/api/logout"), "{}", buyerToken);
            check(logout.statusCode() == 200,
                    "Logout did not invalidate the REST session after profile changes.");
            check(get(baseUri.resolve("/api/profile"), buyerToken).statusCode() == 401,
                    "Profile API remained available after token logout.");
        } finally {
            UserSession.logout();
            deleteTestUsers(buyerId, sellerId, buyerEmail, sellerEmail);
        }
    }

    private static boolean databaseConfigured() {
        return nonEmpty(System.getenv("AUCTION_DB_URL"))
                && nonEmpty(System.getenv("AUCTION_DB_USERNAME"))
                && nonEmpty(System.getenv("AUCTION_DB_PASSWORD"));
    }

    private static boolean nonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String registrationJson(int userId, String name, String email,
            String password, String role) {
        return "{\"userId\":" + userId + ",\"name\":" + json(name)
                + ",\"email\":" + json(email) + ",\"password\":" + json(password)
                + ",\"role\":" + json(role) + "}";
    }

    private static String loginJson(String email, String password) {
        return "{\"email\":" + json(email) + ",\"password\":" + json(password) + "}";
    }

    private static String extractSessionToken(String response) {
        Matcher matcher = Pattern.compile("\\\"sessionToken\\\":\\\"([^\\\"]+)\\\"")
                .matcher(response);
        if (!matcher.find()) throw new AssertionError("Successful login returned no session token.");
        return matcher.group(1);
    }

    private static String profileJson(String name, String email, String password) {
        return "{\"name\":" + json(name) + ",\"email\":" + json(email)
                + ",\"password\":" + json(password) + "}";
    }

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static boolean containsPasswordData(String response) {
        return response.toLowerCase().contains("password") || response.contains("pbkdf2-");
    }

    private static String readPasswordHash(int userId) throws Exception {
        String sql = "SELECT password_hash FROM users WHERE user_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private static void deleteTestUsers(int buyerId, int sellerId,
            String buyerEmail, String sellerEmail) throws Exception {
        String sql = "DELETE FROM users WHERE (user_id = ? AND email = ?) "
                + "OR (user_id = ? AND email = ?)";
        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, buyerId);
            statement.setString(2, buyerEmail);
            statement.setInt(3, sellerId);
            statement.setString(4, sellerEmail);
            statement.executeUpdate();
        }
    }

    private static HttpResponse<String> get(URI uri) throws Exception {
        return get(uri, null);
    }

    private static HttpResponse<String> get(URI uri, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpRequest request = builder.GET().build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(URI uri, String body) throws Exception {
        return post(uri, body, null);
    }

    private static HttpResponse<String> post(URI uri, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> put(URI uri, String body) throws Exception {
        return put(uri, body, null);
    }

    private static HttpResponse<String> put(URI uri, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpRequest request = builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
