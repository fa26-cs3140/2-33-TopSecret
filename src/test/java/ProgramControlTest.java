import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class ProgramControlTest {

    @Test
    void testListFilesReturnsFormattedList() {
        ProgramControlImpl pc = new ProgramControlImpl(new FakeFileHandler(), new FakeCipher());
        List<String> result = pc.listFiles();
        assertEquals(2, result.size());
        assertEquals("01 filea.txt", result.get(0));
    }

    @Test
    void testGetFileContentsValidFileReturnsDecipheredText() throws ProgramControlException {
        ProgramControlImpl pc = new ProgramControlImpl(new FakeFileHandler(), new FakeCipher());
        String result = pc.getFileContents(1);
        assertEquals("DECIPHERED: ENCODED SAMPLE TEXT", result);
    }

    @Test
    void testGetFileContentsInvalidFileThrowsException() {
        ProgramControlImpl pc = new ProgramControlImpl(new FakeFileHandler(), new FakeCipher());
        assertThrows(ProgramControlException.class, () -> pc.getFileContents(99));
    }

    @Test
    void testGetFileContentsZeroThrowsException() {
        ProgramControlImpl pc = new ProgramControlImpl(new FakeFileHandler(), new FakeCipher());
        assertThrows(ProgramControlException.class, () -> pc.getFileContents(0));
    }

    @Test
    void testGetFileContentsWithAlternateKeyFile() throws ProgramControlException {
        ProgramControlImpl pc = new ProgramControlImpl(new FakeFileHandler(), new FakeCipher());
        String result = pc.getFileContents(1, "altkey.txt");
        assertNotNull(result);
    }
}