import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    // ---- username and password rules ----

    @Test
    void createCredentialsRejectsShortUsername() {
        assertThrows(CredentialException.class, () -> auth.createCredentials("ab", "Secret1"));
    }

    @Test
    void createCredentialsRejectsUsernameWithSpaceOrSymbol() {
        assertThrows(CredentialException.class, () -> auth.createCredentials("bad name", "Secret1"));
        assertThrows(CredentialException.class, () -> auth.createCredentials("bad!name", "Secret1"));
    }

    @Test
    void createCredentialsRejectsNullUsername() {
        assertThrows(CredentialException.class, () -> auth.createCredentials(null, "Secret1"));
    }

    @Test
    void createCredentialsAcceptsLettersNumbersHyphensAndUnderscores() throws CredentialException {
        auth.createCredentials("Agent_7-x", "Secret1");

        assertTrue(auth.authenticate("Agent_7-x", "Secret1"));
    }

    @Test
    void createCredentialsRejectsShortPassword() {
        assertThrows(CredentialException.class, () -> auth.createCredentials("agent", "Ab1d"));
    }

    @Test
    void createCredentialsRejectsPasswordWithoutUppercase() {
        assertThrows(CredentialException.class, () -> auth.createCredentials("agent", "secret1"));
    }

    @Test
    void createCredentialsRejectsPasswordWithoutLowercase() {
        assertThrows(CredentialException.class, () -> auth.createCredentials("agent", "SECRET1"));
    }

    @Test
    void createCredentialsRejectsPasswordWithTab() {
        assertThrows(CredentialException.class, () -> auth.createCredentials("agent", "Sec\tret1"));
    }

    @Test
    void createCredentialsRejectsNullPassword() {
        assertThrows(CredentialException.class, () -> auth.createCredentials("agent", null));
    }

    @Test
    void createCredentialsRejectsWhenAccountsAlreadyExist() throws CredentialException {
        auth.createCredentials("agent", "Secret1");

        assertThrows(CredentialException.class, () -> auth.createCredentials("other", "Secret2"));
    }

    // ---- existence checks ----

    @Test
    void hasCredentialFileReflectsStore() throws CredentialException {
        assertFalse(auth.hasCredentialFile());

        auth.createCredentials("agent", "Secret1");

        assertTrue(auth.hasCredentialFile());
    }

    @Test
    void hasAccountsIsFalseWhenFileIsMissing() throws CredentialException {
        assertFalse(auth.hasAccounts());
    }

    @Test
    void hasAccountsIsFalseWhenFileIsEmpty() throws CredentialException {
        store.accounts = new ArrayList<>();

        assertFalse(auth.hasAccounts());
    }

    @Test
    void hasAccountsIsTrueAfterFirstAccount() throws CredentialException {
        auth.createCredentials("agent", "Secret1");

        assertTrue(auth.hasAccounts());
    }

    // ---- login ----

    @Test
    void authenticateReturnsTrueForStoredCredential() throws CredentialException {
        auth.createCredentials("agent", "Secret1");

        assertTrue(auth.authenticate("agent", "Secret1"));
    }

    @Test
    void authenticateReturnsFalseForWrongPassword() throws CredentialException {
        auth.createCredentials("agent", "Secret1");

        assertFalse(auth.authenticate("agent", "wrong"));
    }

    @Test
    void authenticateReturnsFalseForUnknownUser() throws CredentialException {
        auth.createCredentials("agent", "Secret1");

        assertFalse(auth.authenticate("nobody", "Secret1"));
    }

    // ---- adding users ----

    @Test
    void createUserAddsAccountWithoutPassword() throws CredentialException {
        auth.createCredentials("agent", "Secret1");

        auth.createUser("bob");

        assertEquals(2, store.accounts.size());
        assertTrue(auth.needsPassword("bob"));
        assertFalse(auth.authenticate("bob", ""));
    }

    @Test
    void createUserRejectsDuplicateUsername() throws CredentialException {
        auth.createCredentials("agent", "Secret1");

        assertThrows(CredentialException.class, () -> auth.createUser("agent"));
    }

    @Test
    void createUserRejectsInvalidUsername() throws CredentialException {
        auth.createCredentials("agent", "Secret1");

        assertThrows(CredentialException.class, () -> auth.createUser("x"));
    }

    @Test
    void createUserWorksWhenNoFileExistsYet() throws CredentialException {
        auth.createUser("bob");

        assertTrue(store.exists());
        assertTrue(auth.needsPassword("bob"));
    }

    // ---- first login of a new user ----

    @Test
    void needsPasswordIsFalseForUserWithPasswordAndForUnknownUser() throws CredentialException {
        auth.createCredentials("agent", "Secret1");

        assertFalse(auth.needsPassword("agent"));
        assertFalse(auth.needsPassword("nobody"));
    }

    @Test
    void setInitialPasswordLetsNewUserLogIn() throws CredentialException {
        auth.createCredentials("agent", "Secret1");
        auth.createUser("bob");

        auth.setInitialPassword("bob", "Bobpass1", "Bobpass1");

        assertFalse(auth.needsPassword("bob"));
        assertTrue(auth.authenticate("bob", "Bobpass1"));
    }

    @Test
    void setInitialPasswordRejectsMismatchedConfirmation() throws CredentialException {
        auth.createUser("bob");

        assertThrows(CredentialException.class, () -> auth.setInitialPassword("bob", "Bobpass1", "Other"));
        assertTrue(auth.needsPassword("bob"));
    }

    @Test
    void setInitialPasswordRejectsWeakPassword() throws CredentialException {
        auth.createUser("bob");

        assertThrows(CredentialException.class, () -> auth.setInitialPassword("bob", "weak", "weak"));
    }

    @Test
    void setInitialPasswordRejectsUnknownUser() throws CredentialException {
        auth.createCredentials("agent", "Secret1");

        assertThrows(CredentialException.class, () -> auth.setInitialPassword("nobody", "Bobpass1", "Bobpass1"));
    }

    @Test
    void setInitialPasswordRejectsAccountThatAlreadyHasPassword() throws CredentialException {
        auth.createCredentials("agent", "Secret1");

        assertThrows(CredentialException.class, () -> auth.setInitialPassword("agent", "Newpass1", "Newpass1"));
        assertTrue(auth.authenticate("agent", "Secret1"));
    }

    // ---- stored data validation ----

    @Test
    void storedCredentialValidationAcceptsValidAccounts() {
        store.accounts = new ArrayList<>(List.of(
                new Credential("agent", "Secret1"), new Credential("bob", "")));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> auth.verifyStoredCredentialsAreValid());
    }

    @Test
    void storedCredentialValidationRejectsBadUsername() {
        store.accounts = new ArrayList<>(List.of(new Credential("ab", "Secret1")));

        assertThrows(CredentialException.class, () -> auth.verifyStoredCredentialsAreValid());
    }

    @Test
    void storedCredentialValidationRejectsDuplicateUsernames() {
        store.accounts = new ArrayList<>(List.of(
                new Credential("agent", "Secret1"), new Credential("agent", "Other22")));

        assertThrows(CredentialException.class, () -> auth.verifyStoredCredentialsAreValid());
    }

    // ---- changing a password ----

    @Test
    void changePasswordRequiresCorrectCurrentPassword() throws CredentialException {
        auth.createCredentials("agent", "Secret1");

        assertThrows(CredentialException.class,
                () -> auth.changePassword("agent", "wrong", "Newpass1", "Newpass1"));
    }

    @Test
    void changePasswordRequiresMatchingConfirmation() throws CredentialException {
        auth.createCredentials("agent", "Secret1");

        assertThrows(CredentialException.class,
                () -> auth.changePassword("agent", "Secret1", "Newpass1", "Other"));
    }

    @Test
    void changePasswordRejectsWeakNewPassword() throws CredentialException {
        auth.createCredentials("agent", "Secret1");

        assertThrows(CredentialException.class,
                () -> auth.changePassword("agent", "Secret1", "weak", "weak"));
    }

    @Test
    void changePasswordOverwritesOnlyThatUsersPassword() throws CredentialException {
        auth.createCredentials("agent", "Secret1");
        auth.createUser("bob");
        auth.setInitialPassword("bob", "Bobpass1", "Bobpass1");

        auth.changePassword("agent", "Secret1", "Newpass1", "Newpass1");

        assertTrue(auth.authenticate("agent", "Newpass1"));
        assertFalse(auth.authenticate("agent", "Secret1"));
        assertTrue(auth.authenticate("bob", "Bobpass1"));
    }

    @Test
    void constructorRejectsNullStore() {
        assertThrows(NullPointerException.class, () -> new AuthenticationService(null));
    }

    private static class MemoryCredentialStore implements CredentialStore {
        private List<Credential> accounts; // null means "no credential file"

        @Override
        public boolean exists() {
            return accounts != null;
        }

        @Override
        public List<Credential> readAll() throws CredentialException {
            if (accounts == null) {
                throw new CredentialException("missing");
            }
            return new ArrayList<>(accounts);
        }

        @Override
        public void writeAll(List<Credential> credentials) {
            this.accounts = new ArrayList<>(credentials);
        }
    }
}