import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationServiceTest {
    private MemoryCredentialStore store;
    private AuthenticationService auth;

    @BeforeEach
    void setUp() {
        store = new MemoryCredentialStore();
        auth = new AuthenticationService(store);
    }

    @Test
    void createCredentialsRejectsUppercaseUsername() {
        assertThrows(CredentialException.class, () -> auth.createCredentials("Agent", "secret"));
    }

    @Test
    void createCredentialsRejectsUsernameWithNumbers() {
        assertThrows(CredentialException.class, () -> auth.createCredentials("agent7", "secret"));
    }

    @Test
    void createCredentialsRejectsShortPassword() {
        assertThrows(CredentialException.class, () -> auth.createCredentials("agent", "four"));
    }

    @Test
    void authenticateReturnsTrueForStoredCredential() throws CredentialException {
        auth.createCredentials("agent", "secret");

        assertTrue(auth.authenticate("agent", "secret"));
    }

    @Test
    void authenticateReturnsFalseForWrongPassword() throws CredentialException {
        auth.createCredentials("agent", "secret");

        assertFalse(auth.authenticate("agent", "wrong"));
    }

    @Test
    void storedCredentialValidationRejectsCorruptCredentialData() {
        store.credential = new Credential("Agent", "123");

        assertThrows(CredentialException.class, () -> auth.verifyStoredCredentialsAreValid());
    }

    @Test
    void changePasswordRequiresMatchingConfirmation() throws CredentialException {
        auth.createCredentials("agent", "secret");

        assertThrows(CredentialException.class,
                () -> auth.changePassword("agent", "secret", "newpass", "other"));
    }

    @Test
    void changePasswordOverwritesStoredPassword() throws CredentialException {
        auth.createCredentials("agent", "secret");

        auth.changePassword("agent", "secret", "newpass", "newpass");

        assertTrue(auth.authenticate("agent", "newpass"));
        assertFalse(auth.authenticate("agent", "secret"));
    }

    private static class MemoryCredentialStore implements CredentialStore {
        private Credential credential;

        @Override
        public boolean exists() {
            return credential != null;
        }

        @Override
        public Credential read() throws CredentialException {
            if (credential == null) {
                throw new CredentialException("missing");
            }
            return credential;
        }

        @Override
        public void write(Credential credential) {
            this.credential = credential;
        }
    }
}
