import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Audit log storage in SQLite, in the same database file as the content tables. */
public class SqliteAuditLogRepository implements AuditLogRepository {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS audit_log ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "event_type TEXT NOT NULL, "
                    + "content_type TEXT NOT NULL, "
                    + "content_id INTEGER NOT NULL, "
                    + "username TEXT NOT NULL, "
                    + "timestamp TEXT NOT NULL)";

    private static final String INSERT_ENTRY =
            "INSERT INTO audit_log (event_type, content_type, content_id, username, timestamp) "
                    + "VALUES (?, ?, ?, ?, ?)";

    private static final String SELECT_ALL =
            "SELECT id, event_type, content_type, content_id, username, timestamp "
                    + "FROM audit_log ORDER BY id DESC";

    private final Path databasePath;

    /** Normal use: the project database, shared with the mission table. */
    public SqliteAuditLogRepository() {
        this(SqliteMissionRepository.defaultDatabasePath());
    }

    /** Test use: point the database anywhere. */
    SqliteAuditLogRepository(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath cannot be null");
    }

    /** Makes the table if it is missing. Safe to call every start. */
    public void initialize() throws AuditLogException {
        ensureParentDirectory();

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(CREATE_TABLE);
        } catch (SQLException exception) {
            throw new AuditLogException("Audit log table could not be created.", exception);
        }
    }

    @Override
    public void record(AuditLogEntry entry) throws AuditLogException {
        Objects.requireNonNull(entry, "entry cannot be null");

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(INSERT_ENTRY)) {

            statement.setString(1, entry.getEventType().name());
            statement.setString(2, entry.getContentType().name());
            statement.setInt(3, entry.getContentId());
            statement.setString(4, entry.getUsername());
            statement.setString(5, entry.getTimestamp().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new AuditLogException("Audit log entry could not be saved.", exception);
        }
    }

    @Override
    public List<AuditLogEntry> getAllEntries() throws AuditLogException {
        List<AuditLogEntry> entries = new ArrayList<>();

        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(SELECT_ALL)) {

            while (results.next()) {
                entries.add(new AuditLogEntry(
                        results.getInt("id"),
                        AuditEventType.valueOf(results.getString("event_type")),
                        AuditContentType.valueOf(results.getString("content_type")),
                        results.getInt("content_id"),
                        results.getString("username"),
                        LocalDateTime.parse(results.getString("timestamp"))));
            }
        } catch (SQLException exception) {
            throw new AuditLogException("Audit log could not be read.", exception);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new AuditLogException("Audit log contains an invalid entry.", exception);
        }

        return entries;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private void ensureParentDirectory() throws AuditLogException {
        Path parent = databasePath.toAbsolutePath().normalize().getParent();
        if (parent == null || Files.isDirectory(parent)) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException | SecurityException exception) {
            throw new AuditLogException("Database folder could not be created: " + parent, exception);
        }
    }
}