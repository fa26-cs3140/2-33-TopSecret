/** What a user is allowed to be. Admins manage, standard users read. */
public enum Role {
    ADMIN,
    STANDARD;

    /** Reads a role name from the credential file. */
    public static Role fromFileValue(String value) {
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(value)) {
                return role;
            }
        }
        return null;
    }

    /** What gets written to the credential file. */
    public String toFileValue() {
        return name();
    }
}
