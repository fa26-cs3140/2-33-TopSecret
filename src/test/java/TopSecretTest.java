import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopSecretTest {

    @Test
    void firstRunCreatesCredentialFileAndExits() {
        MemoryCredentialStore store = new MemoryCredentialStore();

        String output = runWith(store, "agent\nsecret\n", new String[]{});

        assertTrue(output.contains("No credential file found."));
        assertTrue(output.contains("Credential file created. Restart the program to log in."));
        assertTrue(store.exists());
        assertEquals("agent", store.credential.getUsername());
        assertEquals("secret", store.credential.getPassword());
    }

    @Test
    void firstRunInvalidCredentialShowsErrorAndDoesNotCreateFile() {
        MemoryCredentialStore store = new MemoryCredentialStore();

        String output = runWith(store, "Agent\n1234\n", new String[]{});

        assertTrue(output.contains("Error: Username must contain only lower-case letters."));
        assertFalse(store.exists());
    }

    @Test
    void loginFailureDoesNotShowMenu() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "secret"));

        String output = runWith(store, "agent\nwrong\n", new String[]{});

        assertTrue(output.contains("Login failed."));
        assertFalse(output.contains("1) List missions"));
    }

    @Test
    void loginSuccessStartsInteractiveMenu() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "secret"));

        String output = runWith(store, "agent\nsecret\n4\n", new String[]{});

        assertTrue(output.contains("1) List missions"));
        assertTrue(output.contains("Goodbye."));
    }

    @Test
    void changePasswordOverwritesCredentialAfterConfirmation() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "secret"));

        String output = runWith(store, "agent\nsecret\nnewpass\nnewpass\n", new String[]{"--change-password"});

        assertTrue(output.contains("Password changed."));
        assertEquals("newpass", store.credential.getPassword());
    }

    @Test
    void unexpectedArgumentsShowUsage() {
        MemoryCredentialStore store = new MemoryCredentialStore(new Credential("agent", "secret"));

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
        private Credential credential;

        MemoryCredentialStore() {
        }

        MemoryCredentialStore(Credential credential) {
            this.credential = credential;
        }

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
}
