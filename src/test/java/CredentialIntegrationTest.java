import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialIntegrationTest {
    private static final String KEY = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890\n"
            + "bcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890a";

    @TempDir
    Path temporaryDirectory;

    @Test
    void entryPointCreatesCipheredCredentialFileAndChangesPassword() throws Exception {
        Path credentialFile = temporaryDirectory.resolve("credentials.cip");
        AuthenticationService auth = authFor(credentialFile);
        UserInterface ui = testUi();

        String setupOutput = runWith(auth, ui, "agent\nSecret1\n", new String[]{});

        assertTrue(setupOutput.contains("Account created."));        assertTrue(Files.isRegularFile(credentialFile));
        String rawCredentialFile = Files.readString(credentialFile, StandardCharsets.UTF_8);
        assertFalse(rawCredentialFile.contains("agent"));
        assertFalse(rawCredentialFile.contains("Secret1"));

        String changeOutput = runWith(auth, ui, "agent\nSecret1\nNewpass1\nNewpass1\n",
                new String[]{"--change-password"});

        assertTrue(changeOutput.contains("Password changed."));
        assertTrue(auth.authenticate("agent", "Newpass1"));
        assertFalse(auth.authenticate("agent", "Secret1"));
    }

    private AuthenticationService authFor(Path credentialFile) {
        FileHandler fileHandler = new StaticKeyFileHandler();
        Cipher cipher = new SubstitutionCipher(fileHandler);
        CredentialStore store = new FileCredentialStore(credentialFile, fileHandler, cipher, "key.txt");
        return new AuthenticationService(store);
    }

    private UserInterface testUi() {
        ProgramControl control = new ProgramControlImpl(
                new StaticKeyFileHandler(),
                (text, key) -> text,
                query -> List.of(),
                List::of);
        return new UserInterface(control);
    }

    private String runWith(AuthenticationService auth, UserInterface ui, String input, String[] args) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        TopSecret.run(args, new Scanner(input), new PrintStream(buffer, true, StandardCharsets.UTF_8), auth, ui);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static class StaticKeyFileHandler implements FileHandler {
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
            return KEY;
        }
    }
}
