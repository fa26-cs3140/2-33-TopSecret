import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileHandlerImpl implements FileHandler {

    private static final String DEFAULT_KEY_FILE = "key.txt";

    private final Path dataDirectory;
    private final Path cipherDirectory;

    // Uses the project folders when the program runs normally.
    public FileHandlerImpl() {
        this(Paths.get("data"), Paths.get("ciphers"));
    }

    // Accepts custom folders for isolated testing and rejects null paths.
    FileHandlerImpl(Path dataDirectory, Path cipherDirectory) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory cannot be null");
        this.cipherDirectory = Objects.requireNonNull(cipherDirectory, "cipherDirectory cannot be null");
    }

    // Returns available mission filenames in order, or an empty string if none can be found.
    @Override
    public String getFileList() {
        return missionFiles().stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.joining("\n"));
    }

    // Reads a numbered mission file, returning null for invalid numbers or unreadable files.
    @Override
    public String readFile(int fileNumber) {
        if (fileNumber <= 0) {
            return null;
        }

        List<Path> files = missionFiles();
        if (fileNumber > files.size()) {
            return null;
        }

        return read(files.get(fileNumber - 1));
    }

    // Reads the requested key, uses key.txt by default, and blocks paths outside ciphers/.
    @Override
    public String readKey(String keyFileName) {
        String requestedName = keyFileName == null ? DEFAULT_KEY_FILE : keyFileName;
        Path allowedDirectory = cipherDirectory.toAbsolutePath().normalize();
        Path requestedPath = allowedDirectory.resolve(requestedName).normalize();

        if (!requestedPath.startsWith(allowedDirectory)) {
            return null;
        }

        return read(requestedPath);
    }

    // Finds regular .cip files alphabetically, returning an empty list for directory errors.
    private List<Path> missionFiles() {
        if (!Files.isDirectory(dataDirectory)) {
            return List.of();
        }

        // Try-with-resources closes the directory stream automatically.
        try (Stream<Path> paths = Files.list(dataDirectory)) {
            return paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".cip"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
        } catch (IOException | SecurityException exception) {
            // Missing permissions or filesystem failures are handled without crashing.
            return List.of();
        }
    }

    // Reads a regular file as UTF-8, returning null if it is missing or cannot be read.
    private String read(Path path) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }

        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException exception) {
            // Read and permission failures are reported to callers as null.
            return null;
        }
    }
}
