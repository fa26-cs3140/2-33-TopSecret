import java.util.Objects;

public class AuthenticationService {
    private static final String USERNAME_PATTERN = "[a-z]+";

    private final CredentialStore credentialStore;

    public AuthenticationService(CredentialStore credentialStore) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore cannot be null");
    }

    public boolean hasCredentialFile() {
        return credentialStore.exists();
    }

    public void createCredentials(String username, String password) throws CredentialException {
        validateCredential(username, password);
        credentialStore.write(new Credential(username, password));
    }

    public void verifyStoredCredentialsAreValid() throws CredentialException {
        Credential credential = credentialStore.read();
        validateCredential(credential.getUsername(), credential.getPassword());
    }

    public boolean authenticate(String username, String password) throws CredentialException {
        Credential credential = credentialStore.read();
        return credential.getUsername().equals(username) && credential.getPassword().equals(password);
    }

    public void changePassword(String username, String currentPassword, String newPassword, String confirmation)
            throws CredentialException {
        if (!authenticate(username, currentPassword)) {
            throw new CredentialException("Username or password is incorrect.");
        }
        if (!Objects.equals(newPassword, confirmation)) {
            throw new CredentialException("New passwords do not match.");
        }
        validateCredential(username, newPassword);
        credentialStore.write(new Credential(username, newPassword));
    }

    private void validateCredential(String username, String password) throws CredentialException {
        if (username == null || !username.matches(USERNAME_PATTERN)) {
            throw new CredentialException("Username must contain only lower-case letters.");
        }
        if (password == null || password.length() < 5) {
            throw new CredentialException("Password must be at least 5 characters long.");
        }
    }
}
