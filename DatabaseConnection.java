import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private DatabaseConnection() {
        // Utility class; connections are created through getConnection().
    }

    public static Connection getConnection() throws SQLException {
        String url = System.getenv("AUCTION_DB_URL");
        String username = System.getenv("AUCTION_DB_USERNAME");
        String password = System.getenv("AUCTION_DB_PASSWORD");

        if (isBlank(url) || isBlank(username) || password == null) {
            throw new SQLException("Set AUCTION_DB_URL, AUCTION_DB_USERNAME, and "
                    + "AUCTION_DB_PASSWORD before connecting to MySQL.");
        }

        return DriverManager.getConnection(url, username, password);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
