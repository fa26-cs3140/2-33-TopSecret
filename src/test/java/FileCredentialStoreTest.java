import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileCredentialStoreTest {
    private static final String KEY = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890\n"
            + "bcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890a";

    @TempDir
    Path temporaryDirectory;

    @Test
    void writeAllStoresCipheredCredentialFile() throws Exception {
        Path credentialFile = temporaryDirectory.resolve("credentials.cip");
        CredentialStore store = storeAt(credentialFile, KEY);

        store.writeAll(List.of(new Credential("agent", "secret")));

        String raw = Files.readString(credentialFile, StandardCharsets.UTF_8);
        assertFalse(raw.contains("agent"));
        assertFalse(raw.contains("secret"));
        assertTrue(raw.contains("bhfou"));
        assertTrue(raw.contains("tfdsfu"));
    }

    @Test
    void readAllReturnsAllAccountsInOrder() throws Exception {
        CredentialStore store = storeAt(temporaryDirectory.resolve("credentials.cip"), KEY);
        store.writeAll(List.of(new Credential("agent", "Secret1"), new Credential("bob-2", "Other22")));

        List<Credential> credentials = store.readAll();

        assertEquals(2, credentials.size());
        assertEquals("agent", credentials.get(0).getUsername());
        assertEquals("Secret1", credentials.get(0).getPassword());
        assertEquals("bob-2", credentials.get(1).getUsername());
        assertEquals("Other22", credentials.get(1).getPassword());
    }

    @Test
    void accountWithoutPasswordSurvivesRoundTrip() throws Exception {
        CredentialStore store = storeAt(temporaryDirectory.resolve("credentials.cip"), KEY);
        store.writeAll(List.of(new Credential("newuser", "")));

        List<Credential> credentials = store.readAll();

        assertEquals(1, credentials.size());
        assertFalse(credentials.get(0).hasPassword());
    }

    @Test
    void emptyListReadsBackAsEmptyList() throws Exception {
        CredentialStore store = storeAt(temporaryDirectory.resolve("credentials.cip"), KEY);
        store.writeAll(List.of());

        assertTrue(store.exists());
        assertTrue(store.readAll().isEmpty());
    }

    @Test
    void existsReturnsFalseWhenCredentialFileIsMissing() {
        CredentialStore store = storeAt(temporaryDirectory.resolve("missing.cip"), KEY);

        assertFalse(store.exists());
    }

    @Test
    void productionConstructorUsesConfiguredCredentialPath() {
        Path credentialFile = temporaryDirectory.resolve("configured.cip");
        String previous = System.getProperty("topsecret.credentials");
        try {
            System.setProperty("topsecret.credentials", credentialFile.toString());
            CredentialStore store = new FileCredentialStore(
                    new StaticKeyFileHandler(KEY), (text, keyName) -> text);

            assertFalse(store.exists());
        } finally {
            restoreProperty("topsecret.credentials", previous);
        }
    }

    @Test
    void readAllThrowsWhenFileIsMissing() {
        CredentialStore store = storeAt(temporaryDirectory.resolve("missing.cip"), KEY);

        assertThrows(CredentialException.class, store::readAll);
    }

    @Test
    void readAllThrowsWhenLineIsMalformed() throws Exception {
        Path credentialFile = temporaryDirectory.resolve("credentials.cip");
        // no tab, so the line has one field instead of two
        Files.writeString(credentialFile, "justoneword\n", StandardCharsets.UTF_8);
        CredentialStore store = storeAt(credentialFile, KEY);

        assertThrows(CredentialException.class, store::readAll);
    }

    @Test
    void readAllThrowsWhenUsernameIsEmpty() throws Exception {
        Path credentialFile = temporaryDirectory.resolve("credentials.cip");
        Files.writeString(credentialFile, "\tpassword\n", StandardCharsets.UTF_8);
        CredentialStore store = storeAt(credentialFile, KEY);

        assertThrows(CredentialException.class, store::readAll);
    }

    @Test
    void writeAllThrowsWhenKeyIsInvalid() {
        CredentialStore store = storeAt(temporaryDirectory.resolve("credentials.cip"), "onlyonekeyline");

        assertThrows(CredentialException.class, () -> store.writeAll(List.of(new Credential("a", "b"))));
    }

    @Test
    void writeAllRejectsNullList() {
        CredentialStore store = storeAt(temporaryDirectory.resolve("credentials.cip"), KEY);

        assertThrows(NullPointerException.class, () -> store.writeAll(null));
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

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
