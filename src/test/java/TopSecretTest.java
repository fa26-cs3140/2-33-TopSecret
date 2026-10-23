import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopSecretTest {

    @Test
    void mainUsesIsolatedStorageAndRoutesArgumentsThroughTheApplication(@TempDir Path temporary) {
        String previousDatabase = System.getProperty("topsecret.database");
        String previousCredentials = System.getProperty("topsecret.credentials");
        PrintStream previousOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setProperty("topsecret.database", temporary.resolve("missions.db").toString());
            System.setProperty("topsecret.credentials", temporary.resolve("credentials.cip").toString());
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));

            assertNotNull(new TopSecret());
            TopSecret.main(new String[]{"unexpected"});

            assertTrue(buffer.toString(StandardCharsets.UTF_8)
                    .contains("Usage: TopSecret [--change-password]"));
        } finally {
            System.setOut(previousOut);
            restoreProperty("topsecret.database", previousDatabase);
            restoreProperty("topsecret.credentials", previousCredentials);
        }
    }

    @Test
    void firstRunCreatesAccountAndLogsIn() {
        MemoryCredentialStore store = new MemoryCredentialStore();

        String output = runWith(store, "agent\nSecret1\n4\n", new String[]{});

        assertTrue(output.contains("No accounts found."));
        assertTrue(output.contains("Account created. You are logged in as agent."));
        assertTrue(output.contains("1) List missions"));
        assertTrue(output.contains("Goodbye."));
        assertEquals("agent", store.accounts.get(0).getUsername());
        assertEquals("Secret1", store.accounts.get(0).getPassword());
    }

    @Test
    void firstRunInvalidCredentialShowsErrorAndDoesNotCreateFile() {
        MemoryCredentialStore store = new MemoryCredentialStore();

        String output = runWith(store, "ab\nSecret1\n", new String[]{});

        assertTrue(output.contains("Error: Username must be at least 3 characters"));
        assertFalse(store.exists());
    }

    @Test
    void loginFailureDoesNotShowMenu() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "Secret1"));

        String output = runWith(store, "agent\nwrong\n", new String[]{});

        assertTrue(output.contains("Login failed."));
        assertFalse(output.contains("1) List missions"));
    }

    @Test
    void loginSuccessStartsInteractiveMenu() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "Secret1"));

        String output = runWith(store, "agent\nSecret1\n4\n", new String[]{});

        assertTrue(output.contains("1) List missions"));
        assertTrue(output.contains("Goodbye."));
    }

    @Test
    void changePasswordOverwritesCredentialAfterConfirmation() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "Secret1"));

        String output = runWith(store, "agent\nSecret1\nNewpass1\nNewpass1\n", new String[]{"--change-password"});

        assertTrue(output.contains("Password changed."));
        assertEquals("Newpass1", store.accounts.get(0).getPassword());
    }

    @Test
    void unexpectedArgumentsShowUsage() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "Secret1"));

        String output = runWith(store, "", new String[]{"01"});

        assertEquals("Usage: TopSecret [--change-password]\n", output.replace("\r\n", "\n"));
    }

    private String runWith(MemoryCredentialStore store, String input, String[] args) {
        AuthenticationService auth = new AuthenticationService(store);
        ProgramControl control = new ProgramControlImpl(
                new UnusedFileHandler(),
                (text, key) -> text,
                query -> List.of(),
                List::of);
        UserInterface ui = new UserInterface(control);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        TopSecret.run(args, new Scanner(input), new PrintStream(buffer, true, StandardCharsets.UTF_8), auth, ui);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static class MemoryCredentialStore implements CredentialStore {
        private List<Credential> accounts; // null means "no credential file"

        MemoryCredentialStore() {
        }

        MemoryCredentialStore(Credential credential) {
            this.accounts = new ArrayList<>(List.of(credential));
        }

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

    private static class UnusedFileHandler implements FileHandler {
        @Override
        public String getFileList() {
            return "";
        }

        @Override
        public String readFile(int fileNumber) {
            return null;
        }

        @Override
        public String readKey(String keyFileName) {
            return null;
        }
    }

    @Test
    void firstRunIsCancelledWhenInputEnds() {
        MemoryCredentialStore store = new MemoryCredentialStore();

        String output = runWith(store, "", new String[]{});

        assertTrue(output.contains("Account setup cancelled."));
        assertFalse(store.exists());
    }

    @Test
    void unknownUserCannotLogIn() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "Secret1"));

        String output = runWith(store, "nobody\nSecret1\n", new String[]{});

        assertTrue(output.contains("Login failed."));
        assertFalse(output.contains("1) List missions"));
    }

    @Test
    void loginIsCancelledWhenUsernameInputEnds() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "Secret1"));

        String output = runWith(store, "", new String[]{});

        assertTrue(output.contains("Login cancelled."));
    }

    @Test
    void loginIsCancelledWhenPasswordInputEnds() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "Secret1"));

        String output = runWith(store, "agent\n", new String[]{});

        assertTrue(output.contains("Login cancelled."));
    }

    @Test
    void newUserCreatesPasswordOnFirstLogin() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "Secret1"));
        store.accounts.add(new Credential("bob", ""));

        String output = runWith(store, "bob\nBobpass1\nBobpass1\n4\n", new String[]{});

        assertTrue(output.contains("Create a password"));
        assertTrue(output.contains("1) List missions"));
        assertEquals("Bobpass1", store.accounts.get(1).getPassword());
    }

    @Test
    void newUserWithMismatchedPasswordsIsNotLoggedIn() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "Secret1"));
        store.accounts.add(new Credential("bob", ""));

        String output = runWith(store, "bob\nBobpass1\nOther\n", new String[]{});

        assertTrue(output.contains("Error: Passwords do not match."));
        assertFalse(output.contains("1) List missions"));
        assertEquals("", store.accounts.get(1).getPassword());
    }

    @Test
    void newUserLoginIsCancelledWhenPasswordInputEnds() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "Secret1"));
        store.accounts.add(new Credential("bob", ""));

        String output = runWith(store, "bob\n", new String[]{});

        assertTrue(output.contains("Login cancelled."));
    }

    @Test
    void corruptStoredAccountsShowErrorAndDoNotLogIn() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("ab", "Secret1"));

        String output = runWith(store, "ab\nSecret1\n", new String[]{});

        assertTrue(output.contains("Error: Username must be at least 3 characters"));
        assertFalse(output.contains("1) List missions"));
    }

    @Test
    void changePasswordWithNoAccountsCreatesFirstAccount() {
        MemoryCredentialStore store = new MemoryCredentialStore();

        String output = runWith(store, "agent\nSecret1\n", new String[]{"--change-password"});

        assertTrue(output.contains("Account created."));
        assertFalse(output.contains("1) List missions"));
        assertTrue(store.exists());
    }

    @Test
    void changePasswordWithWrongCurrentPasswordShowsError() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "Secret1"));

        String output = runWith(store, "agent\nwrong\nNewpass1\nNewpass1\n", new String[]{"--change-password"});

        assertTrue(output.contains("Error: Username or password is incorrect."));
        assertEquals("Secret1", store.accounts.get(0).getPassword());
    }

    @Test
    void changePasswordIsCancelledWhenInputEnds() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "Secret1"));

        String output = runWith(store, "agent\n", new String[]{"--change-password"});

        assertTrue(output.contains("Password change cancelled."));
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

}
