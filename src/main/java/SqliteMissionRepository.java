import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mission storage in SQLite. The only class that touches the database. */
public class SqliteMissionRepository implements MissionRepository {

    static final String DATABASE_FILE = "missions.db";
    static final String DEFAULT_TSV = "mission_briefs.tsv";

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS missions ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "title TEXT NOT NULL, "
            + "brief TEXT NOT NULL, "
            + "date TEXT NOT NULL)";

    private static final String COUNT_MISSIONS = "SELECT COUNT(*) FROM missions";
    private static final String INSERT_MISSION =
            "INSERT INTO missions (title, brief, date) VALUES (?, ?, ?)";
    private static final String SELECT_ALL =
            "SELECT id, title, brief, date FROM missions ORDER BY id";

    private final FileHandler fileHandler;
    private final Path databasePath;
    private final String tsvRelativePath;

    /** Normal use: the project database and the shipped TSV. */
    public SqliteMissionRepository(FileHandler fileHandler) {
        this(fileHandler, defaultDatabasePath(), DEFAULT_TSV);
    }

    /** Test use: point the database and the import file anywhere. */
    SqliteMissionRepository(FileHandler fileHandler, Path databasePath, String tsvRelativePath) {
        this.fileHandler = Objects.requireNonNull(fileHandler, "fileHandler cannot be null");
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath cannot be null");
        this.tsvRelativePath = Objects.requireNonNull(tsvRelativePath, "tsvRelativePath cannot be null");
    }

    /** Makes the table if it is missing, then imports only if it is empty. */
    public void initialize() throws MissionRepositoryException {
        createTableIfMissing();
        if (missionCount() == 0) {
            importMissions();
        }
    }

    @Override
    public List<Mission> getAllMissions() throws MissionRepositoryException {
        List<Mission> missions = new ArrayList<>();

        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(SELECT_ALL)) {

            while (results.next()) {
                missions.add(new Mission(
                        results.getInt("id"),
                        results.getString("title"),
                        results.getString("brief"),
                        parseDate(results.getString("date"))));
            }
        } catch (SQLException exception) {
            throw new MissionRepositoryException("Mission records could not be read.", exception);
        }

        return missions;
    }

    /** How many rows the table holds. Decides whether the import runs. */
    int missionCount() throws MissionRepositoryException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(COUNT_MISSIONS)) {

            return results.next() ? results.getInt(1) : 0;
        } catch (SQLException exception) {
            throw new MissionRepositoryException("Mission records could not be counted.", exception);
        }
    }

    private void createTableIfMissing() throws MissionRepositoryException {
        ensureParentDirectory();

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(CREATE_TABLE);
        } catch (SQLException exception) {
            throw new MissionRepositoryException("Mission table could not be created.", exception);
        }
    }

    /** Loads the TSV in file order, all in one transaction so a bad row undoes the lot. */
    private void importMissions() throws MissionRepositoryException {
        String contents = fileHandler.readText(tsvRelativePath);
        if (contents == null) {
            throw new MissionRepositoryException(
                    "Mission import file could not be read: " + tsvRelativePath);
        }

        List<Mission> parsed = parseTsv(contents);
        if (parsed.isEmpty()) {
            return;
        }

        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_MISSION)) {
                for (Mission mission : parsed) {
                    statement.setString(1, mission.getTitle());
                    statement.setString(2, mission.getBrief());
                    statement.setString(3, mission.getDate().toString());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new MissionRepositoryException("Mission records could not be imported.", exception);
        }
    }

    /** Splits the TSV. Ids here are placeholders, the database hands out the real ones. */
    List<Mission> parseTsv(String contents) throws MissionRepositoryException {
        if (contents == null || contents.isBlank()) {
            return List.of();
        }

        String[] lines = contents.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        int titleColumn = -1;
        int briefColumn = -1;
        int dateColumn = -1;
        List<Mission> missions = new ArrayList<>();

        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }

            String[] cells = line.split("\t", -1);

            // columns are found by header name, so the file can reorder them
            if (titleColumn < 0) {
                for (int column = 0; column < cells.length; column++) {
                    String header = cells[column].trim().toLowerCase();
                    if (header.equals("title")) {
                        titleColumn = column;
                    } else if (header.equals("text") || header.equals("brief")) {
                        briefColumn = column;
                    } else if (header.equals("date")) {
                        dateColumn = column;
                    }
                }
                if (titleColumn < 0 || briefColumn < 0 || dateColumn < 0) {
                    throw new MissionRepositoryException(
                            "Mission import file needs Title, Date and Text columns.");
                }
                continue;
            }

            int widest = Math.max(titleColumn, Math.max(briefColumn, dateColumn));
            if (cells.length <= widest) {
                throw new MissionRepositoryException(
                        "Mission import row is missing columns: " + line);
            }

            missions.add(new Mission(
                    0,
                    cells[titleColumn].trim(),
                    cells[briefColumn].trim(),
                    parseDate(cells[dateColumn].trim())));
        }

        return missions;
    }

    /** Dates go in and out as ISO text, like 1970-11-03. */
    private static LocalDate parseDate(String value) throws MissionRepositoryException {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new MissionRepositoryException("Mission date is not a valid date: " + value);
        }
    }

    // opened and closed per call, so the file is never left locked
    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private void ensureParentDirectory() throws MissionRepositoryException {
        Path parent = databasePath.toAbsolutePath().normalize().getParent();
        if (parent == null || Files.isDirectory(parent)) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException | SecurityException exception) {
            throw new MissionRepositoryException(
                    "Database folder could not be created: " + parent, exception);
        }
    }

    /** data/missions.db, found the same way FileHandlerImpl finds the project. */
    public static Path defaultDatabasePath() {
        Path current = Paths.get("").toAbsolutePath().normalize();

        while (current != null) {
            if (Files.isDirectory(current.resolve("data"))
                    && Files.isDirectory(current.resolve("ciphers"))) {
                return current.resolve("data").resolve(DATABASE_FILE);
            }
            current = current.getParent();
        }

        return Paths.get("data").resolve(DATABASE_FILE).toAbsolutePath().normalize();
    }
}
