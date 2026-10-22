import java.util.Objects;

/** Who is logged in and what they are. Built once at login and passed around. */
public final class Session {

    private final String username;
    private final Role role;

    public Session(String username, Role role) {
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.role = Objects.requireNonNull(role, "role cannot be null");
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Session)) {
            return false;
        }
        Session session = (Session) other;
        return username.equals(session.username) && role == session.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, role);
    }

    @Override
    public String toString() {
        return username + " (" + role + ")";
    }
}
