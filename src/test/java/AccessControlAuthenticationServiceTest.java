import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roles going through a real ciphered credential file and back out again. */
class AccessControlAuthenticationServiceTest {

    private static final String KEY =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890\n"
            + "bcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890a";

    private final AccessControl access = new AccessControl();

    @TempDir
    Path temporaryDirectory;

    private Path credentialFile() {
        return temporaryDirectory.resolve("credentials.cip");
    }

    private AuthenticationService authFor(Path file) {
        FileHandler fileHandler = new StaticKeyFileHandler();
        Cipher cipher = new SubstitutionCipher(fileHandler);
        return new AuthenticationService(new FileCredentialStore(file, fileHandler, cipher, "key.txt"));
    }

    /* roles surviving a trip to disk */

    @Test
    void theFirstAccountIsStillAnAdminAfterReopening() throws Exception {
        Path file = credentialFile();
        authFor(file).createCredentials("boss", "Secret1");

        assertEquals(Role.ADMIN, authFor(file).roleOf("boss"));
    }

    @Test
    void aCreatedUserIsStillStandardAfterReopening() throws Exception {
        Path file = credentialFile();
        AuthenticationService auth = authFor(file);
        auth.createCredentials("boss", "Secret1");
        auth.createUser("agent");

        assertEquals(Role.STANDARD, authFor(file).roleOf("agent"));
    }

    @Test
    void promotionPersists() throws Exception {
        Path file = credentialFile();
        AuthenticationService auth = authFor(file);
        auth.createCredentials("boss", "Secret1");
        auth.createUser("agent");

        auth.promote("boss", "agent");

        assertEquals(Role.ADMIN, authFor(file).roleOf("agent"));
    }

    @Test
    void demotionPersists() throws Exception {
        Path file = credentialFile();
        AuthenticationService auth = authFor(file);
        auth.createCredentials("boss", "Secret1");
        auth.createUser("deputy");
        auth.promote("boss", "deputy");

        auth.demote("boss", "deputy");

        assertEquals(Role.STANDARD, authFor(file).roleOf("deputy"));
    }

    @Test
    void aPasswordChangeLeavesTheRoleAlone() throws Exception {
        Path file = credentialFile();
        AuthenticationService auth = authFor(file);
        auth.createCredentials("boss", "Secret1");

        auth.changePassword("boss", "Secret1", "Newpass1", "Newpass1");

        assertEquals(Role.ADMIN, authFor(file).roleOf("boss"));
        assertTrue(authFor(file).authenticate("boss", "Newpass1"));
    }

    @Test
    void aNewUserSettingTheirOwnPasswordStaysStandard() throws Exception {
        Path file = credentialFile();
        AuthenticationService auth = authFor(file);
        auth.createCredentials("boss", "Secret1");
        auth.createUser("agent");

        auth.setInitialPassword("agent", "Agentpw1", "Agentpw1");

        assertEquals(Role.STANDARD, authFor(file).roleOf("agent"));
        assertTrue(authFor(file).authenticate("agent", "Agentpw1"));
    }

    /* the access rules agreeing with what is on disk */

    @Test
    void theStoredAdminGetsTheAdminOptions() throws Exception {
        Path file = credentialFile();
        AuthenticationService auth = authFor(file);
        auth.createCredentials("boss", "Secret1");

        Session session = auth.sessionFor("boss");

        assertTrue(access.canViewAuditLog(session));
        assertTrue(access.canCreateUsers(session));
        assertTrue(access.canCreateContent(session));
        assertTrue(access.canManageRoles(session));
    }

    @Test
    void theStoredStandardUserOnlyGetsToRead() throws Exception {
        Path file = credentialFile();
        AuthenticationService auth = authFor(file);
        auth.createCredentials("boss", "Secret1");
        auth.createUser("agent");

        Session session = auth.sessionFor("agent");

        assertTrue(access.canReadContent(session));
        assertFalse(access.canViewAuditLog(session));
        assertFalse(access.canCreateUsers(session));
        assertFalse(access.canCreateContent(session));
        assertFalse(access.canManageRoles(session));
    }

    @Test
    void aPromotedUserGainsTheAdminOptions() throws Exception {
        Path file = credentialFile();
        AuthenticationService auth = authFor(file);
        auth.createCredentials("boss", "Secret1");
        auth.createUser("agent");
        assertFalse(access.canViewAuditLog(auth.sessionFor("agent")));

        auth.promote("boss", "agent");

        assertTrue(access.canViewAuditLog(auth.sessionFor("agent")));
    }

    /* the file format */

    @Test
    void theRoleIsNotReadableInTheSavedFile() throws Exception {
        Path file = credentialFile();
        authFor(file).createCredentials("boss", "Secret1");

        String raw = Files.readString(file, StandardCharsets.UTF_8);

        assertFalse(raw.contains("ADMIN"));
        assertFalse(raw.contains("boss"));
    }

    @Test
    void aFileWrittenBeforeRolesExistedReadsAsStandard() throws Exception {
        Path file = credentialFile();
        writeCiphered(file, "agent\tSecret1\n");

        assertEquals(Role.STANDARD, authFor(file).roleOf("agent"));
    }

    @Test
    void anOldFileStillLetsTheUserLogIn() throws Exception {
        Path file = credentialFile();
        writeCiphered(file, "agent\tSecret1\n");

        assertTrue(authFor(file).authenticate("agent", "Secret1"));
    }

    @Test
    void aJunkRoleIsRejected() throws Exception {
        Path file = credentialFile();
        writeCiphered(file, "agent\tSecret1\tWIZARD\n");

        assertThrows(CredentialException.class, () -> authFor(file).roleOf("agent"));
    }

    @Test
    void tooManyFieldsAreRejected() throws Exception {
        Path file = credentialFile();
        writeCiphered(file, "agent\tSecret1\tADMIN\textra\n");

        assertThrows(CredentialException.class, () -> authFor(file).roleOf("agent"));
    }

    // the key maps each character in line one to the same position in line two,
    // so this is the same substitution the store writes with
    private void writeCiphered(Path file, String plain) throws Exception {
        String[] lines = KEY.split("\n");
        StringBuilder out = new StringBuilder(plain.length());
        for (char character : plain.toCharArray()) {
            int index = lines[0].indexOf(character);
            out.append(index < 0 ? character : lines[1].charAt(index));
        }
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
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
