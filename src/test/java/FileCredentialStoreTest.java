import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileCredentialStoreTest {
    private static final String KEY = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890\n"
            + "bcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890a";

    @TempDir
    Path temporaryDirectory;

    @Test
    void writeStoresCipheredCredentialFile() throws Exception {
        Path credentialFile = temporaryDirectory.resolve("credentials.cip");
        CredentialStore store = storeAt(credentialFile, KEY);

        store.write(new Credential("agent", "secret"));

        String raw = Files.readString(credentialFile, StandardCharsets.UTF_8);
        assertFalse(raw.contains("agent"));
        assertFalse(raw.contains("secret"));
        assertTrue(raw.contains("bhfou"));
        assertTrue(raw.contains("tfdsfu"));
    }

    @Test
    void readDeciphersStoredCredentials() throws Exception {
        Path credentialFile = temporaryDirectory.resolve("credentials.cip");
        CredentialStore store = storeAt(credentialFile, KEY);
        store.write(new Credential("agent", "secret"));

        Credential credential = store.read();

        assertEquals("agent", credential.getUsername());
        assertEquals("secret", credential.getPassword());
    }

    @Test
    void existsReturnsFalseWhenCredentialFileIsMissing() {
        CredentialStore store = storeAt(temporaryDirectory.resolve("missing.cip"), KEY);

        assertFalse(store.exists());
    }

    private CredentialStore storeAt(Path credentialFile, String key) {
        FileHandler fileHandler = new StaticKeyFileHandler(key);
        Cipher cipher = new SubstitutionCipher(fileHandler);
        return new FileCredentialStore(credentialFile, fileHandler, cipher, "key.txt");
    }

    private static class StaticKeyFileHandler implements FileHandler {
        private final String key;

        StaticKeyFileHandler(String key) {
            this.key = key;
        }

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
            return key;
        }
    }
}
