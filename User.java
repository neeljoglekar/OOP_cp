public class User {
    private final String userId;
    private String name;
    private String email;

    public User(String userId, String name) {
        this(userId, name, null);
    }

    public User(String userId, String name, String email) {
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return "User";
    }

    @Override
    public String toString() {
        return name + " (" + getRole() + ")";
    }
}
