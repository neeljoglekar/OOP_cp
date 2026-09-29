import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** In-memory HTTP checks for login validation and token-scoped logout authorization. */
public class RestAuthenticationTest {
    public static void main(String[] args) throws Exception {
        AuctionServer server = new AuctionServer(new AuctionSystem(), new UserDAO(), 0);
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            URI baseUri = URI.create("http://127.0.0.1:" + server.getPort());

            HttpResponse<String> emptyCredentials = post(client,
                    baseUri.resolve("/api/login"), "{\"email\":\"\",\"password\":\"x\"}");
            check(emptyCredentials.statusCode() == 400,
                    "Empty login credentials should return HTTP 400.");
            check(!emptyCredentials.body().contains("password_hash")
                            && !emptyCredentials.body().contains("stack"),
                    "Login validation exposed sensitive data or a stack trace.");

            HttpResponse<String> malformed = post(client,
                    baseUri.resolve("/api/login"), "not-json");
            check(malformed.statusCode() == 400,
                    "Malformed login JSON should return HTTP 400.");

            UserSession.setCurrentUser(new Buyer("901", "Session Test Buyer"));
            HttpResponse<String> logout = post(client,
                    baseUri.resolve("/api/logout"), "{}");
            check(logout.statusCode() == 401,
                    "Logout without a REST session token should return HTTP 401.");
            check(UserSession.getCurrentUser() != null && UserSession.isLoggedIn(),
                    "An unauthenticated REST logout changed the console UserSession.");

            System.out.println("RestAuthenticationTest passed: required/malformed login requests, "
                    + "and rejection of logout without a REST session token.");
        } finally {
            UserSession.logout();
            server.stop();
        }
    }

    private static HttpResponse<String> post(HttpClient client, URI uri, String body)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
