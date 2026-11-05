import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** JDBC data access for products. */
public class ProductDAO {
    private static final String INSERT_SQL =
            "INSERT INTO products (product_id, name, description, `condition`) VALUES (?, ?, ?, ?)";
    private static final String SELECT_COLUMNS =
            "SELECT product_id, name, description, `condition` FROM products ";

    public void insertProduct(Product product) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            insertProduct(connection, product);
        }
    }

    /** Uses but does not close or commit the caller-managed connection. */
    public void insertProduct(Connection connection, Product product) throws SQLException {
        requireConnection(connection);
        validateProduct(product);
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, product.getProductId());
            statement.setString(2, product.getName());
            statement.setString(3, product.getDescription());
            statement.setString(4, product.getCondition());
            statement.executeUpdate();
        }
    }

    public Product findProductById(String productId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return findProductById(connection, productId);
        }
    }

    /** Returns null if there is no matching product. */
    public Product findProductById(Connection connection, String productId) throws SQLException {
        requireConnection(connection);
        requireId(productId, "Product ID");
        String sql = SELECT_COLUMNS + "WHERE product_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, productId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? readProduct(results) : null;
            }
        }
    }

    public List<Product> getAllProducts() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return getAllProducts(connection);
        }
    }

    /** Returns products ordered by product ID, using the caller-managed connection. */
    public List<Product> getAllProducts(Connection connection) throws SQLException {
        requireConnection(connection);
        String sql = SELECT_COLUMNS + "ORDER BY product_id";
        List<Product> products = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet results = statement.executeQuery()) {
            while (results.next()) products.add(readProduct(results));
        }
        return products;
    }

    private static Product readProduct(ResultSet results) throws SQLException {
        return new Product(results.getString("product_id"), results.getString("name"),
                results.getString("description"), results.getString("condition"));
    }

    private static void validateProduct(Product product) {
        if (product == null) throw new IllegalArgumentException("Product is required.");
        requireId(product.getProductId(), "Product ID");
        requireText(product.getName(), "Product name", 255);
        if (product.getDescription() == null) {
            throw new IllegalArgumentException("Product description is required.");
        }
        requireText(product.getCondition(), "Product condition", 100);
    }

    private static void requireText(String value, String label, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (value.codePointCount(0, value.length()) > maximumLength) {
            throw new IllegalArgumentException(label + " exceeds " + maximumLength + " characters.");
        }
    }

    private static void requireId(String id, String label) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException(label + " is required.");
        if (id.codePointCount(0, id.length()) > 100) {
            throw new IllegalArgumentException(label + " exceeds 100 characters.");
        }
    }

    private static void requireConnection(Connection connection) {
        if (connection == null) throw new IllegalArgumentException("Database connection is required.");
    }
}
