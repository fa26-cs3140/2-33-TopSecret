import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FileHandlerTest {

    @TempDir
    Path temporaryDirectory;

    private Path dataDirectory;
    private Path cipherDirectory;

    @BeforeEach
    void createProjectFolders() throws IOException {
        dataDirectory = Files.createDirectory(temporaryDirectory.resolve("data"));
        cipherDirectory = Files.createDirectory(temporaryDirectory.resolve("ciphers"));
    }

    @Test
    void getFileListReturnsCipherFilesInAlphabeticalOrder() throws IOException {
        write(dataDirectory.resolve("zebra.cip"), "second");
        write(dataDirectory.resolve("alpha.cip"), "first");
        write(dataDirectory.resolve("plaintext.txt"), "reference copy");
        Files.createDirectory(dataDirectory.resolve("directory.cip"));

        FileHandler handler = handlerFor(dataDirectory, cipherDirectory);

        assertEquals("alpha.cip\nzebra.cip", handler.getFileList());
    }

    @Test
    void getFileListReturnsEmptyStringForEmptyDataDirectory() {
        FileHandler handler = handlerFor(dataDirectory, cipherDirectory);

        assertEquals("", handler.getFileList());
    }

    @Test
    void getFileListReturnsEmptyStringWhenDataDirectoryDoesNotExist() {
        Path missingDataDirectory = temporaryDirectory.resolve("missing-data");
        FileHandler handler = handlerFor(missingDataDirectory, cipherDirectory);

        assertEquals("", handler.getFileList());
    }

    @Test
    void readFileUsesTheSameAlphabeticalOrderAsGetFileList() throws IOException {
        write(dataDirectory.resolve("zebra.cip"), "second file");
        write(dataDirectory.resolve("alpha.cip"), "first file");

        FileHandler handler = handlerFor(dataDirectory, cipherDirectory);

        assertEquals("first file", handler.readFile(1));
        assertEquals("second file", handler.readFile(2));
    }

    @Test
    void readFilePreservesWhitespaceAndLineBreaks() throws IOException {
        String contents = "  classified heading  \nline two\n";
        write(dataDirectory.resolve("mission.cip"), contents);

        FileHandler handler = handlerFor(dataDirectory, cipherDirectory);

        assertEquals(contents, handler.readFile(1));
    }

    @Test
    void readFileReturnsNullForInvalidOrMissingNumber() throws IOException {
        write(dataDirectory.resolve("mission.cip"), "contents");
        FileHandler handler = handlerFor(dataDirectory, cipherDirectory);

        assertNull(handler.readFile(0));
        assertNull(handler.readFile(-1));
        assertNull(handler.readFile(2));
    }

    @Test
    void readFileReturnsNullWhenDataDirectoryDoesNotExist() {
        Path missingDataDirectory = temporaryDirectory.resolve("missing-data");
        FileHandler handler = handlerFor(missingDataDirectory, cipherDirectory);

        assertNull(handler.readFile(1));
    }

    @Test
    void readKeyUsesKeyTxtWhenNameIsNull() throws IOException {
        String keyContents = "abc\nxyz\n";
        write(cipherDirectory.resolve("key.txt"), keyContents);

        FileHandler handler = handlerFor(dataDirectory, cipherDirectory);

        assertEquals(keyContents, handler.readKey(null));
    }

    @Test
    void readKeyUsesTheRequestedAlternateKey() throws IOException {
        write(cipherDirectory.resolve("key.txt"), "abc\nxyz");
        write(cipherDirectory.resolve("alternate.txt"), "def\nuvw");

        FileHandler handler = handlerFor(dataDirectory, cipherDirectory);

        assertEquals("def\nuvw", handler.readKey("alternate.txt"));
    }

    @Test
    void readKeyReturnsNullWhenKeyDoesNotExist() {
        FileHandler handler = handlerFor(dataDirectory, cipherDirectory);

        assertNull(handler.readKey("missing.txt"));
    }

    @Test
    void readKeyDoesNotAllowPathsOutsideCipherDirectory() throws IOException {
        write(temporaryDirectory.resolve("outside.txt"), "secret outside key");
        FileHandler handler = handlerFor(dataDirectory, cipherDirectory);

        assertNull(handler.readKey("../outside.txt"));
    }

    private FileHandler handlerFor(Path dataPath, Path cipherPath) {
        return new FileHandlerImpl(dataPath, cipherPath);
    }

    private void write(Path path, String contents) throws IOException {
        Files.writeString(path, contents, StandardCharsets.UTF_8);
    }
}
