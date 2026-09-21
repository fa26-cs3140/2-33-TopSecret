import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ProgramControlTest {

    @Test
    void testListAvailableFilesReturnsFormattedList() {
        ProgramControl pc = new ProgramControl(new FakeFileHandler(), new FakeCipher());
        assertEquals("01 filea.txt\n02 fileb.txt", pc.listAvailableFiles());
    }

    @Test
    void testGetFileContentsValidFileReturnsDecipheredText() {
        ProgramControl pc = new ProgramControl(new FakeFileHandler(), new FakeCipher());
        String result = pc.getFileContents(1, null);
        assertEquals("DECIPHERED: ENCODED SAMPLE TEXT", result);
    }

    @Test
    void testGetFileContentsInvalidFileReturnsErrorNotCrash() {
        ProgramControl pc = new ProgramControl(new FakeFileHandler(), new FakeCipher());
        String result = pc.getFileContents(99, null);
        assertEquals("Error: file not found.", result);
    }

    @Test
    void testGetFileContentsWithAlternateKeyFile() {
        ProgramControl pc = new ProgramControl(new FakeFileHandler(), new FakeCipher());
        String result = pc.getFileContents(1, "altkey.txt");
        assertNotNull(result);
    }
}
