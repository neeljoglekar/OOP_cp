import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.security.MessageDigest;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class UserDAO {
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BITS = 256;
    private static final int ITERATIONS = 600_000;
    private static final String HASH_PREFIX = "pbkdf2-sha256";
    private static final SecureRandom RANDOM = new SecureRandom();

    public User registerUser(int userId, String name, String email, char[] password, String role)
            throws SQLException {
        validateRequiredFields(userId, name, email, password, role);

        String cleanName = name.trim();
        String cleanEmail = email.trim();
        String cleanRole = role.trim().toUpperCase(Locale.ROOT);
        User user = createUser(userId, cleanName, cleanEmail, cleanRole);

        try (Connection connection = DatabaseConnection.getConnection()) {
            if (userAlreadyExists(connection, userId, cleanEmail)) {
                throw new SQLException("A user with this ID or email already exists.", "23000");
            }

            String passwordHash = hashPassword(password);
            String sql = "INSERT INTO users (user_id, name, email, password_hash, role) "
                    + "VALUES (?, ?, ?, ?, ?)";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, userId);
                statement.setString(2, cleanName);
                statement.setString(3, cleanEmail);
                statement.setString(4, passwordHash);
                statement.setString(5, cleanRole);
                statement.executeUpdate();
            } catch (SQLException exception) {
                if (isDuplicateKey(exception)) {
                    throw new SQLException("A user with this ID or email already exists.",
                            exception.getSQLState(), exception.getErrorCode(), exception);
                }
                throw exception;
            }
        }

        return user;
    }

    /**
     * Logs in a user by email. Returns null for an unknown email or incorrect password.
     * A successful login becomes the application's current user.
     */
    public User login(String email, char[] password) throws SQLException {
        if (isBlank(email) || password == null || password.length == 0) {
            return null;
        }

        String sql = "SELECT user_id, name, email, password_hash, role FROM users WHERE email = ?";
        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()
                        || !verifyPassword(password, resultSet.getString("password_hash"))) {
                    return null;
                }

                User user = createUser(resultSet.getInt("user_id"),
                        resultSet.getString("name"), resultSet.getString("email"),
                        resultSet.getString("role")
                                .trim().toUpperCase(Locale.ROOT));
                UserSession.setCurrentUser(user);
                return user;
            }
        }
    }

    /** Loads the profile for the currently logged-in user and refreshes its session data. */
    public User viewCurrentProfile() throws SQLException {
        User currentUser = requireLoggedInUser();
        String sql = "SELECT name, email FROM users WHERE user_id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Integer.parseInt(currentUser.getUserId()));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("The current user's profile was not found.");
                }
                currentUser.setName(resultSet.getString("name"));
                currentUser.setEmail(resultSet.getString("email"));
                return currentUser;
            }
        }
    }

    /** Updates only the logged-in user's name and email. Returns false for an existing email. */
    public boolean updateCurrentProfile(String name, String email) throws SQLException {
        User currentUser = requireLoggedInUser();
        if (isBlank(name)) {
            throw new IllegalArgumentException("Name is required.");
        }
        if (isBlank(email)) {
            throw new IllegalArgumentException("Email is required.");
        }

        String cleanName = name.trim();
        String cleanEmail = email.trim();
        String sql = "UPDATE users SET name = ?, email = ? WHERE user_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, cleanName);
            statement.setString(2, cleanEmail);
            statement.setInt(3, Integer.parseInt(currentUser.getUserId()));
            int rowsUpdated;
            try {
                rowsUpdated = statement.executeUpdate();
            } catch (SQLException exception) {
                if (isDuplicateKey(exception)) {
                    return false;
                }
                throw exception;
            }

            if (rowsUpdated == 1) {
                currentUser.setName(cleanName);
                currentUser.setEmail(cleanEmail);
                return true;
            }
            return false;
        }
    }

    /** Changes the logged-in user's password using the same salted PBKDF2 hash as registration. */
    public boolean changeCurrentPassword(char[] newPassword) throws SQLException {
        User currentUser = requireLoggedInUser();
        if (newPassword == null || newPassword.length == 0) {
            throw new IllegalArgumentException("New password is required.");
        }

        String newPasswordHash = hashPassword(newPassword);
        String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newPasswordHash);
            statement.setInt(2, Integer.parseInt(currentUser.getUserId()));
            return statement.executeUpdate() == 1;
        }
    }

    private User requireLoggedInUser() {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            throw new IllegalStateException("You must be logged in to manage a profile.");
        }
        return currentUser;
    }

    private void validateRequiredFields(int userId, String name, String email,
            char[] password, String role) {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be a positive integer.");
        }
        if (isBlank(name)) {
            throw new IllegalArgumentException("Name is required.");
        }
        if (isBlank(email)) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (isBlank(role)) {
            throw new IllegalArgumentException("Role is required.");
        }
    }

    private User createUser(int userId, String name, String email, String role) {
        String stringId = String.valueOf(userId);
        switch (role) {
            case "BUYER":
                return new Buyer(stringId, name, email);
            case "SELLER":
                return new Seller(stringId, name, email);
            case "ADMIN":
                return new Admin(stringId, name, email);
            default:
                throw new IllegalArgumentException("Role must be BUYER, SELLER, or ADMIN.");
        }
    }

    private boolean userAlreadyExists(Connection connection, int userId, String email)
            throws SQLException {
        String sql = "SELECT 1 FROM users WHERE user_id = ? OR email = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setString(2, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private String hashPassword(char[] password) throws SQLException {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        RANDOM.nextBytes(salt);

        PBEKeySpec keySpec = new PBEKeySpec(password, salt, ITERATIONS, HASH_LENGTH_BITS);
        byte[] hash = null;
        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            hash = keyFactory.generateSecret(keySpec).getEncoded();
            Base64.Encoder encoder = Base64.getEncoder();
            return HASH_PREFIX + "$" + ITERATIONS + "$"
                    + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash);
        } catch (Exception exception) {
            throw new SQLException("Unable to securely hash the password.", exception);
        } finally {
            keySpec.clearPassword();
            Arrays.fill(salt, (byte) 0);
            if (hash != null) {
                Arrays.fill(hash, (byte) 0);
            }
        }
    }

    private boolean verifyPassword(char[] password, String storedHash) {
        if (storedHash == null) {
            return false;
        }

        String[] parts = storedHash.split("\\$", -1);
        if (parts.length != 4 || !HASH_PREFIX.equals(parts[0])) {
            return false;
        }

        PBEKeySpec keySpec = null;
        byte[] salt = null;
        byte[] expectedHash = null;
        byte[] actualHash = null;
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations <= 0) {
                return false;
            }
            Base64.Decoder decoder = Base64.getDecoder();
            salt = decoder.decode(parts[2]);
            expectedHash = decoder.decode(parts[3]);
            if (iterations != ITERATIONS || salt.length != SALT_LENGTH_BYTES
                    || expectedHash.length != HASH_LENGTH_BITS / 8) {
                return false;
            }
            keySpec = new PBEKeySpec(password, salt, iterations, expectedHash.length * 8);
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            actualHash = keyFactory.generateSecret(keySpec).getEncoded();
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception exception) {
            return false;
        } finally {
            if (keySpec != null) {
                keySpec.clearPassword();
            }
            if (salt != null) {
                Arrays.fill(salt, (byte) 0);
            }
            if (expectedHash != null) {
                Arrays.fill(expectedHash, (byte) 0);
            }
            if (actualHash != null) {
                Arrays.fill(actualHash, (byte) 0);
            }
        }
    }

    private boolean isDuplicateKey(SQLException exception) {
        return "23000".equals(exception.getSQLState()) && exception.getErrorCode() == 1062;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
