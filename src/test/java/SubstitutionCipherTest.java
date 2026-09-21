import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


class SubstitutionCipherTest {

    private static final String PLAIN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
    private static final String CODED = "bcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890a";


    /* Stand-In for file handler */
    private static class StubHandler implements FileHandler {
        private final Map<String, String> keys = new HashMap<>();
        String lastKeyRequested;

        StubHandler withKey(String name, String content) {
            keys.put(name, content);
            return this;
        }

        @Override public String getFileList() {return "";}
        @Override public String readFile(int fileNumber) {return null;}
        @Override public String readKey(String keyFileName) {
            lastKeyRequested = keyFileName;
            return keys.get(keyFileName);
        }
    }

    private String run(String keyContent, String text) {
        Cipher c = new SubstitutionCipher(new StubHandler().withKey("key.txt", keyContent));
        return c.decipher(text, "key.txt");
    }


    // Mapping
    @Test void decipherSimpleMapping() {
        assertEquals("cab", run("abc\nxyz", "zxy"));
    }

    @Test void unlistedCharactersPassThrough() {
        assertEquals("a1 b!\n", run("abc\nxyz", "x1 y!\n"));
    }

    @Test void swapMappingIsSinglePass() {
        assertEquals("ab", run("ab\nba", "ba"));
    }

    @Test void emptyTextReturnsEmpty() {
        assertEquals("", run("abc\nxyz", ""));
    }

    @Test void keyWithSpaceCharacterWorks() {
        assertEquals("a a", run("a \nx_", "x_x"));
    }

    // Key file formatting
    @Test void trailingNewlineInKeyIsIgnored() {
        assertEquals("abc", run("abc\nxyz\n", "xyz"));
    }

    @Test void windowLineEndingsAreIgnored() {
        assertEquals("abc", run("abc\r\nxyz\r\n", "xyz"));
    }

// Validation

    @Test void mismatchedLengthsRejected() {
        assertThrows(InvalidCipherException.class, () -> run("abc\nxy", "abc"));
    }

    @Test void duplicateInCipherLineRejected() {
        assertThrows(InvalidCipherException.class, () -> run("abc\nxxz", "abc"));
    }

    @Test void duplicateInPlainLineRejected() {
        assertThrows(InvalidCipherException.class, () -> run("aab\nxyz", "abc"));
    }

    @Test void tooFewLinesRejected() {
        assertThrows(InvalidCipherException.class, () -> run("abc", "abc"));
    }

    @Test void tooManyLinesRejected() {
        assertThrows(InvalidCipherException.class, () -> run("abc\nxyz\nextra", "abc"));
    }

    @Test void emptyKeyFileRejected() {
        assertThrows(InvalidCipherException.class, () -> run("", "hello"));
    }

    @Test void twoBlankLinesKeyRejected() {
        assertThrows(InvalidCipherException.class, () -> run("\n\n", "hello"));
    }

    // Error handling

    @Test void missingKeyFileThrows() {
        Cipher c = new SubstitutionCipher(new StubHandler());
        assertThrows(InvalidCipherException.class, () -> c.decipher("abc", "nope.txt"));
    }

    @Test void nullTextThrows() {
        Cipher c = new SubstitutionCipher(new StubHandler().withKey("key.txt", "abc\nxyz"));
        assertThrows(IllegalArgumentException.class, () -> c.decipher(null, "key.txt"));
    }

    @Test void nullHandlerRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SubstitutionCipher(null));
    }

    @Test void requestsTheKeyFileItWasGiven() {
        StubHandler handler = new StubHandler().withKey("alt.txt", "abc\nxyz");
        new SubstitutionCipher(handler).decipher("xyz", "alt.txt");
        assertEquals("alt.txt", handler.lastKeyRequested);
    }

    // Real sample key

    @Test void sampleKeyDeciphersWords() {
        assertEquals("hello world", run(PLAIN + "\n" + CODED + "\n", "ifmmp xpsme"));
    }

    @Test void sampleKeyWrapsAroundAtEnds() {
        String key = PLAIN + "\n" + CODED;
        assertEquals("0", run(key, "a"));
        assertEquals("Z", run(key, "1"));
    }

    @Test void sampleKeyLeavesPunctuationAlone() {
        assertEquals("hi, there!\n", run(PLAIN + "\n" + CODED, "ij, uifsf!\n"));
    }



}
