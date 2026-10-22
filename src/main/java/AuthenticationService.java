import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AuthenticationService {
    private static final String USERNAME_PATTERN = "[A-Za-z0-9_-]{3,}";
    private static final int MIN_PASSWORD_LENGTH = 5;

    private final CredentialStore credentialStore;

    public AuthenticationService(CredentialStore credentialStore) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore cannot be null");
    }

    public boolean hasCredentialFile() {
        return credentialStore.exists();
    }

    // true only if the file exists AND holds at least one account
    public boolean hasAccounts() throws CredentialException {
        return credentialStore.exists() && !credentialStore.readAll().isEmpty();
    }

    // first-ever account only, and it is the admin; later users come from createUser
    public void createCredentials(String username, String password) throws CredentialException {
        if (hasAccounts()) {
            throw new CredentialException("Accounts already exist.");
        }
        validateUsername(username);
        validatePassword(password);
        credentialStore.writeAll(List.of(new Credential(username, password, Role.ADMIN)));
    }

    // adds an account with no password; the owner sets it at first login
    public void createUser(String username) throws CredentialException {
        validateUsername(username);
        List<Credential> accounts = readAccounts();
        if (indexOf(accounts, username) >= 0) {
            throw new CredentialException("That username is already taken.");
        }
        accounts.add(new Credential(username, "", Role.STANDARD));
        credentialStore.writeAll(accounts);
    }

    public void verifyStoredCredentialsAreValid() throws CredentialException {
        List<Credential> accounts = credentialStore.readAll();
        List<String> seen = new ArrayList<>();
        for (Credential account : accounts) {
            validateUsername(account.getUsername());
            if (seen.contains(account.getUsername())) {
                throw new CredentialException("Credential file is invalid.");
            }
            seen.add(account.getUsername());
        }
    }

    public boolean authenticate(String username, String password) throws CredentialException {
        List<Credential> accounts = credentialStore.readAll();
        int index = indexOf(accounts, username);
        if (index < 0) {
            return false;
        }
        Credential account = accounts.get(index);
        return account.hasPassword() && account.getPassword().equals(password);
    }

    // true when the account exists but its owner has not chosen a password yet
    public boolean needsPassword(String username) throws CredentialException {
        List<Credential> accounts = credentialStore.readAll();
        int index = indexOf(accounts, username);
        return index >= 0 && !accounts.get(index).hasPassword();
    }

    public void setInitialPassword(String username, String password, String confirmation)
            throws CredentialException {
        List<Credential> accounts = new ArrayList<>(credentialStore.readAll());
        int index = indexOf(accounts, username);
        if (index < 0 || accounts.get(index).hasPassword()) {
            throw new CredentialException("A password cannot be set for this account.");
        }
        if (!Objects.equals(password, confirmation)) {
            throw new CredentialException("Passwords do not match.");
        }
        validatePassword(password);
        accounts.set(index, accounts.get(index).withPassword(password));
        credentialStore.writeAll(accounts);
    }

    public void changePassword(String username, String currentPassword, String newPassword, String confirmation)
            throws CredentialException {
        if (!authenticate(username, currentPassword)) {
            throw new CredentialException("Username or password is incorrect.");
        }
        if (!Objects.equals(newPassword, confirmation)) {
            throw new CredentialException("New passwords do not match.");
        }
        validatePassword(newPassword);
        List<Credential> accounts = new ArrayList<>(credentialStore.readAll());
        int index = indexOf(accounts, username);
        accounts.set(index, accounts.get(index).withPassword(newPassword));
        credentialStore.writeAll(accounts);
    }

    public Role roleOf(String username) throws CredentialException {
        List<Credential> accounts = credentialStore.readAll();
        int index = indexOf(accounts, username);
        if (index < 0) {
            throw new CredentialException("No account named " + username + ".");
        }
        return accounts.get(index).getRole();
    }

    /** What the menu gets once a login has succeeded. */
    public Session sessionFor(String username) throws CredentialException {
        return new Session(username, roleOf(username));
    }

    public List<String> listUsernames() throws CredentialException {
        List<String> names = new ArrayList<>();
        for (Credential account : credentialStore.readAll()) {
            names.add(account.getUsername());
        }
        return names;
    }

    public void promote(String actingUsername, String targetUsername) throws CredentialException {
        changeRole(actingUsername, targetUsername, Role.ADMIN);
    }

    public void demote(String actingUsername, String targetUsername) throws CredentialException {
        changeRole(actingUsername, targetUsername, Role.STANDARD);
    }

    // an admin can change anyone's role but their own, so the last admin stays an admin
    private void changeRole(String actingUsername, String targetUsername, Role newRole)
            throws CredentialException {
        List<Credential> accounts = new ArrayList<>(credentialStore.readAll());

        int actingIndex = indexOf(accounts, actingUsername);
        if (actingIndex < 0 || !accounts.get(actingIndex).isAdmin()) {
            throw new CredentialException("Only an admin can change roles.");
        }
        if (Objects.equals(actingUsername, targetUsername)) {
            throw new CredentialException("You cannot change your own role.");
        }

        int targetIndex = indexOf(accounts, targetUsername);
        if (targetIndex < 0) {
            throw new CredentialException("No account named " + targetUsername + ".");
        }
        if (accounts.get(targetIndex).getRole() == newRole) {
            throw new CredentialException(targetUsername + " is already "
                    + (newRole == Role.ADMIN ? "an admin." : "a standard user."));
        }

        accounts.set(targetIndex, accounts.get(targetIndex).withRole(newRole));
        credentialStore.writeAll(accounts);
    }

    private List<Credential> readAccounts() throws CredentialException {
        if (!credentialStore.exists()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(credentialStore.readAll());
    }

    private static int indexOf(List<Credential> accounts, String username) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getUsername().equals(username)) {
                return i;
            }
        }
        return -1;
    }

    private static void validateUsername(String username) throws CredentialException {
        if (username == null || !username.matches(USERNAME_PATTERN)) {
            throw new CredentialException(
                    "Username must be at least 3 characters: letters, numbers, hyphens and underscores only.");
        }
    }

    private static void validatePassword(String password) throws CredentialException {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH
                || password.contains("\t")
                || !password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*")) {
            throw new CredentialException(
                    "Password must be at least 5 characters with upper and lower case letters.");
        }
    }
}