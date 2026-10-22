import java.util.Objects;

public class Credential {
    private final String username;
    private final String password;
    private final Role role;

    /** Standard user. Kept so the older two-argument callers still work. */
    public Credential(String username, String password) {
        this(username, password, Role.STANDARD);
    }

    public Credential(String username, String password, Role role) {
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.password = Objects.requireNonNull(password, "password cannot be null");
        this.role = Objects.requireNonNull(role, "role cannot be null");
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }

    public boolean hasPassword() {return !password.isEmpty();}

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Same account with a different password, role kept. */
    public Credential withPassword(String newPassword) {
        return new Credential(username, newPassword, role);
    }

    /** Same account with a different role, password kept. */
    public Credential withRole(Role newRole) {
        return new Credential(username, password, newRole);
    }
}
