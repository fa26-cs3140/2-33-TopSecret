import java.util.Objects;

public class Credential {
    private final String username;
    private final String password;

    public Credential(String username, String password) {
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.password = Objects.requireNonNull(password, "password cannot be null");
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
