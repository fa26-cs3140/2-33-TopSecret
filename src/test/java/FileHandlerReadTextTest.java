import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Tests for readText. Separate from FileHandlerTest since the method is mine. */
class FileHandlerReadTextTest {

    private FileHandlerImpl handlerFor(Path projectRoot) throws IOException {
        Path data = projectRoot.resolve("data");
        Path ciphers = projectRoot.resolve("ciphers");
        Files.createDirectories(data);
        Files.createDirectories(ciphers);
        return new FileHandlerImpl(data, ciphers, projectRoot);
    }

    @Test
    void readsAFileAtTheProjectRoot(@TempDir Path projectRoot) throws IOException {
        Files.writeString(projectRoot.resolve("notes.tsv"), "a\tb\n", StandardCharsets.UTF_8);

        assertEquals("a\tb\n", handlerFor(projectRoot).readText("notes.tsv"));
    }

    @Test
    void readsAFileInASubfolder(@TempDir Path projectRoot) throws IOException {
        FileHandlerImpl handler = handlerFor(projectRoot);
        Files.writeString(projectRoot.resolve("data").resolve("inner.txt"), "inner",
                StandardCharsets.UTF_8);

        assertEquals("inner", handler.readText("data/inner.txt"));
    }

    @Test
    void readsUtf8Content(@TempDir Path projectRoot) throws IOException {
        Files.writeString(projectRoot.resolve("utf8.txt"), "Fuerstenfeldbruck ü",
                StandardCharsets.UTF_8);

        assertEquals("Fuerstenfeldbruck ü", handlerFor(projectRoot).readText("utf8.txt"));
    }

    @Test
    void missingFileComesBackAsNull(@TempDir Path projectRoot) throws IOException {
        assertNull(handlerFor(projectRoot).readText("nope.tsv"));
    }

    @Test
    void nullPathComesBackAsNull(@TempDir Path projectRoot) throws IOException {
        assertNull(handlerFor(projectRoot).readText(null));
    }

    @Test
    void blankPathComesBackAsNull(@TempDir Path projectRoot) throws IOException {
        assertNull(handlerFor(projectRoot).readText("   "));
    }

    @Test
    void aDirectoryComesBackAsNull(@TempDir Path projectRoot) throws IOException {
        assertNull(handlerFor(projectRoot).readText("data"));
    }

    @Test
    void pathsOutsideTheProjectAreBlocked(@TempDir Path parent) throws IOException {
        Path projectRoot = parent.resolve("project");
        Files.createDirectories(projectRoot);
        FileHandlerImpl handler = handlerFor(projectRoot);
        Files.writeString(parent.resolve("secret.txt"), "should not be reachable",
                StandardCharsets.UTF_8);

        assertNull(handler.readText("../secret.txt"));
    }

    @Test
    void absolutePathsOutsideTheProjectAreBlocked(@TempDir Path parent) throws IOException {
        Path projectRoot = parent.resolve("project");
        Files.createDirectories(projectRoot);
        FileHandlerImpl handler = handlerFor(projectRoot);
        Path outside = parent.resolve("outside.txt");
        Files.writeString(outside, "should not be reachable", StandardCharsets.UTF_8);

        assertNull(handler.readText(outside.toAbsolutePath().toString()));
    }

    @Test
    void theDefaultInterfaceImplementationReturnsNull() {
        FileHandler minimal = new FileHandler() {
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
        };

        assertNull(minimal.readText("anything.txt"));
    }
}
