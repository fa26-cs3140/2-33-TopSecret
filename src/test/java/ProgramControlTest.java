import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProgramControlTest {

    @Test
    void listFilesReturnsFileNamesInDisplayOrder() {
        ProgramControlImpl pc = new ProgramControlImpl(
                new StubFileHandler("filea.txt\nfileb.txt", "ENCODED SAMPLE TEXT"),
                new RecordingCipher());

        List<String> result = pc.listFiles();

        assertEquals(2, result.size());
        assertEquals("filea.txt", result.get(0));
        assertEquals("fileb.txt", result.get(1));
    }

    @Test
    void listFilesReturnsEmptyListWhenHandlerHasNoFiles() {
        ProgramControlImpl pc = new ProgramControlImpl(
                new StubFileHandler("", "ENCODED SAMPLE TEXT"),
                new RecordingCipher());

        List<String> result = pc.listFiles();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void listFilesReturnsEmptyListWhenHandlerReturnsNull() {
        ProgramControlImpl pc = new ProgramControlImpl(
                new StubFileHandler(null, "ENCODED SAMPLE TEXT"),
                new RecordingCipher());

        List<String> result = pc.listFiles();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getFileContentsValidFileReturnsDecipheredText() throws ProgramControlException {
        ProgramControlImpl pc = new ProgramControlImpl(
                new StubFileHandler("filea.txt", "ENCODED SAMPLE TEXT"),
                new RecordingCipher());

        String result = pc.getFileContents(1);

        assertEquals("DECIPHERED: ENCODED SAMPLE TEXT", result);
    }

    @Test
    void getFileContentsInvalidFileThrowsException() {
        ProgramControlImpl pc = new ProgramControlImpl(
                new StubFileHandler("filea.txt", null),
                new RecordingCipher());

        assertThrows(ProgramControlException.class, () -> pc.getFileContents(99));
    }

    @Test
    void getFileContentsZeroThrowsException() {
        ProgramControlImpl pc = new ProgramControlImpl(
                new StubFileHandler("filea.txt", "ENCODED SAMPLE TEXT"),
                new RecordingCipher());

        assertThrows(ProgramControlException.class, () -> pc.getFileContents(0));
    }

    @Test
    void getFileContentsNegativeNumberThrowsException() {
        ProgramControlImpl pc = new ProgramControlImpl(
                new StubFileHandler("filea.txt", "ENCODED SAMPLE TEXT"),
                new RecordingCipher());

        assertThrows(ProgramControlException.class, () -> pc.getFileContents(-5));
    }

    @Test
    void getFileContentsDefaultKeyPassesNullKeyToCipher() throws ProgramControlException {
        RecordingCipher cipher = new RecordingCipher();
        ProgramControlImpl pc = new ProgramControlImpl(
                new StubFileHandler("filea.txt", "ENCODED SAMPLE TEXT"),
                cipher);

        pc.getFileContents(1);

        assertNull(cipher.lastKeyName);
    }

    @Test
    void getFileContentsWithAlternateKeyPassesKeyNameToCipher() throws ProgramControlException {
        RecordingCipher cipher = new RecordingCipher();
        ProgramControlImpl pc = new ProgramControlImpl(
                new StubFileHandler("filea.txt", "ENCODED SAMPLE TEXT"),
                cipher);

        String result = pc.getFileContents(1, "altkey.txt");

        assertEquals("altkey.txt", cipher.lastKeyName);
        assertEquals("DECIPHERED: ENCODED SAMPLE TEXT", result);
    }

    @Test
    void getFileContentsWrapsCipherFailureInProgramControlException() {
        Cipher failingCipher = (text, keyFileName) -> {
            throw new RuntimeException("bad key");
        };
        ProgramControlImpl pc = new ProgramControlImpl(
                new StubFileHandler("filea.txt", "ENCODED SAMPLE TEXT"),
                failingCipher);

        ProgramControlException error =
                assertThrows(ProgramControlException.class, () -> pc.getFileContents(1, "badkey.txt"));
        assertEquals("Failed to decipher file: 1", error.getMessage());
        assertNotNull(error.getCause());
    }

    @Test
    void getFileContentsCatchesInvalidCipherExceptionWithUsefulMessage() {
        Cipher failingCipher = (text, keyFileName) -> {
            throw new InvalidCipherException("Key file could not be read: missing.txt");
        };
        ProgramControlImpl pc = new ProgramControlImpl(
                new StubFileHandler("filea.txt", "ENCODED SAMPLE TEXT"),
                failingCipher);

        ProgramControlException error =
                assertThrows(ProgramControlException.class, () -> pc.getFileContents(1, "missing.txt"));
        assertEquals("Key file could not be read: missing.txt", error.getMessage());
        assertTrue(error.getCause() instanceof InvalidCipherException);
    }

    @Test
    void searchMissionsDelegatesToSearchFeature() throws ProgramControlException {
        Mission mission = new Mission(1, "Title", "matching brief",
                java.time.LocalDate.of(2000, 1, 1));
        MissionSearch search = query -> List.of(mission);
        ProgramControl control = new ProgramControlImpl(
                new StubFileHandler("", ""), new RecordingCipher(), search);

        List<Mission> result = control.searchMissions("matching");

        assertEquals(List.of(mission), result);
    }

    @Test
    void searchMissionsWrapsSearchFailure() {
        MissionSearch search = query -> {
            throw new MissionSearchException("Search phrase cannot be empty.");
        };
        ProgramControl control = new ProgramControlImpl(
                new StubFileHandler("", ""), new RecordingCipher(), search);

        ProgramControlException error = assertThrows(
                ProgramControlException.class, () -> control.searchMissions(" "));

        assertEquals("Search phrase cannot be empty.", error.getMessage());
        assertTrue(error.getCause() instanceof MissionSearchException);
    }

    @Test
    void searchMissionsReportsWhenDatabaseHasNotBeenConnected() {
        ProgramControl control = new ProgramControlImpl(
                new StubFileHandler("", ""), new RecordingCipher());

        ProgramControlException error = assertThrows(
                ProgramControlException.class, () -> control.searchMissions("agent"));

        assertEquals("Mission database is not connected.", error.getMessage());
    }

    private static class StubFileHandler implements FileHandler {
        private final String fileList;
        private final String fileContents;

        StubFileHandler(String fileList, String fileContents) {
            this.fileList = fileList;
            this.fileContents = fileContents;
        }

        @Override
        public String getFileList() {
            return fileList;
        }

        @Override
        public String readFile(int fileNumber) {
            return fileNumber == 1 ? fileContents : null;
        }

        @Override
        public String readKey(String keyFileName) {
            return "abc\nbcd";
        }
    }

    private static class RecordingCipher implements Cipher {
        private String lastKeyName;

        @Override
        public String decipher(String text, String keyFileName) {
            lastKeyName = keyFileName;
            return "DECIPHERED: " + text;
        }
    }

    private ProgramControlImpl controlWith(MissionRepository repo) {
        return new ProgramControlImpl(new StubFileHandler("", null),
                new RecordingCipher(), query -> List.of(), repo);
    }

    @Test
    void listMissionsReturnsRepositoryMissionsInOrder() throws ProgramControlException {
        Mission first = new Mission(1, "Alpha", "brief a", LocalDate.of(2026, 1, 1));
        Mission second = new Mission(2, "Bravo", "brief b", LocalDate.of(2026, 1, 2));

        List<Mission> result = controlWith(() -> List.of(first, second)).listMissions();

        assertEquals(List.of(first, second), result);
    }

    @Test
    void listMissionsReturnsEmptyListWhenRepositoryIsEmpty() throws ProgramControlException {
        assertTrue(controlWith(() -> List.of()).listMissions().isEmpty());
    }

    @Test
    void listMissionsReturnsEmptyListWhenRepositoryReturnsNull() throws ProgramControlException {
        assertNotNull(controlWith(() -> null).listMissions());
    }

    @Test
    void listMissionsWrapsRepositoryFailure() {
        ProgramControlImpl pc = controlWith(() -> {
            throw new MissionRepositoryException("db down");
        });

        ProgramControlException e = assertThrows(ProgramControlException.class, pc::listMissions);

        assertEquals("db down", e.getMessage());
        assertTrue(e.getCause() instanceof MissionRepositoryException);
    }
}
