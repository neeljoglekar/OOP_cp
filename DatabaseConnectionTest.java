import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnectionTest {
    public static void main(String[] args) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            System.out.println("MySQL JDBC connection succeeded.");
        } catch (SQLException exception) {
            System.err.println("MySQL JDBC connection failed: " + exception.getMessage());
            System.exit(1);
        }
    }
}
