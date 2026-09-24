import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileHandlerImpl implements FileHandler {

    private static final String DEFAULT_KEY_FILE = "key.txt";

    private final Path dataDirectory;
    private final Path cipherDirectory;

    // Finds the project folders even when launched from a build directory.
    public FileHandlerImpl() {
        this(findProjectRoot());
    }

    private FileHandlerImpl(Path projectRoot) {
        this(projectRoot.resolve("data"), projectRoot.resolve("ciphers"));
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

    // Searches the working directory and compiled-code location for the project root.
    private static Path findProjectRoot() {
        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
        Path projectRoot = findProjectRoot(workingDirectory);
        if (projectRoot != null) {
            return projectRoot;
        }

        try {
            CodeSource codeSource = FileHandlerImpl.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                Path codeLocation = Paths.get(codeSource.getLocation().toURI());
                projectRoot = findProjectRoot(codeLocation);
                if (projectRoot != null) {
                    return projectRoot;
                }
            }
        } catch (URISyntaxException | SecurityException exception) {
            // Fall back to the working directory so later reads fail gracefully.
        }

        return workingDirectory;
    }

    // Walks upward until a folder containing both data/ and ciphers/ is found.
    static Path findProjectRoot(Path startingPath) {
        Path current = Objects.requireNonNull(startingPath, "startingPath cannot be null")
                .toAbsolutePath().normalize();

        if (Files.isRegularFile(current)) {
            current = current.getParent();
        }

        while (current != null) {
            if (Files.isDirectory(current.resolve("data"))
                    && Files.isDirectory(current.resolve("ciphers"))) {
                return current;
            }
            current = current.getParent();
        }

        return null;
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
