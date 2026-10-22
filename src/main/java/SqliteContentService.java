import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SqliteContentService implements ContentService {
    private final Path database;
    private final AuditLogRepository auditLog;
    private final AccessControl access = new AccessControl();

    public SqliteContentService(Path database, AuditLogRepository auditLog) {
        this.database = database;
        this.auditLog = auditLog;
    }

    public void initialize() throws ProgramControlException {
        try {
            Files.createDirectories(database.toAbsolutePath().getParent());
            try (Connection connection = connect(); var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS facilities (id INTEGER PRIMARY KEY, name TEXT NOT NULL, abbreviation TEXT NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS agents (id INTEGER PRIMARY KEY, name TEXT NOT NULL, date_of_birth TEXT NOT NULL, date_of_death TEXT NOT NULL, notes TEXT NOT NULL, facility_id INTEGER NOT NULL REFERENCES facilities(id) ON DELETE RESTRICT)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS briefs (id INTEGER PRIMARY KEY, title TEXT NOT NULL, date TEXT NOT NULL, text TEXT NOT NULL)");
            }
        } catch (java.io.IOException | SQLException exception) {
            throw new ProgramControlException("Content database could not be initialized.", exception);
        }
    }

    @Override
    public List<ContentEntry> list(ContentType type, Session session) throws ProgramControlException {
        require(access.canReadContent(session));
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            List<ContentEntry> entries = new ArrayList<>();
            try (var statement = connection.createStatement();
                 ResultSet results = statement.executeQuery("SELECT * FROM " + type.table + " ORDER BY id")) {
                while (results.next()) {
                    List<String> values = new ArrayList<>();
                    for (String field : type.fields) {
                        values.add(results.getString(field));
                    }
                    entries.add(new ContentEntry(results.getInt("id"), values));
                }
            }
            for (ContentEntry entry : entries) {
                record(connection, type, entry.id(), session, AuditEventType.READ);
            }
            connection.commit();
            return entries;
        } catch (SQLException exception) {
            throw new ProgramControlException("Content could not be listed.", exception);
        }
    }

    @Override
    public ContentEntry read(ContentType type, int id, Session session) throws ProgramControlException {
        require(access.canReadContent(session));
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            ContentEntry entry;
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + type.table + " WHERE id = ?")) {
                statement.setInt(1, id);
                try (ResultSet results = statement.executeQuery()) {
                    if (!results.next()) {
                        throw new ProgramControlException("No entry with ID " + id + ".");
                    }
                    List<String> values = new ArrayList<>();
                    for (String field : type.fields) {
                        values.add(results.getString(field));
                    }
                    entry = new ContentEntry(id, values);
                }
            }
            record(connection, type, id, session, AuditEventType.READ);
            connection.commit();
            return entry;
        } catch (SQLException exception) {
            throw new ProgramControlException("Content could not be read.", exception);
        }
    }

    @Override
    public void add(ContentType type, List<String> values, Session session) throws ProgramControlException {
        require(access.canCreateContent(session));
        validate(type, values);
        String placeholders = String.join(",", Collections.nCopies(values.size(), "?"));
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + type.table
                    + " (" + String.join(",", type.fields) + ") VALUES (" + placeholders + ")")) {
                for (int index = 0; index < values.size(); index++) {
                    statement.setString(index + 1, values.get(index));
                }
                statement.executeUpdate();
            }
            try (var statement = connection.createStatement(); ResultSet results = statement.executeQuery("SELECT last_insert_rowid()")) {
                results.next();
                record(connection, type, results.getInt(1), session, AuditEventType.CREATE);
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new ProgramControlException("Entry could not be saved. Agents require an existing facility ID.", exception);
        }
    }

    @Override
    public List<AuditLogEntry> audit(Session session) throws ProgramControlException {
        require(access.canViewAuditLog(session));
        try {
            return auditLog.getAllEntries();
        } catch (AuditLogException exception) {
            throw new ProgramControlException(exception.getMessage(), exception);
        }
    }

    private void validate(ContentType type, List<String> values) throws ProgramControlException {
        if (values.size() != type.fields.size()) {
            throw new ProgramControlException("Incorrect number of fields.");
        }
        try {
            for (int index = 0; index < values.size(); index++) {
                String field = type.fields.get(index);
                String value = values.get(index);
                if (value == null || (value.isBlank() && !field.equals("date_of_death") && !field.equals("notes"))) {
                    throw new IllegalArgumentException();
                }
                if (field.contains("date") && !value.isBlank()) {
                    LocalDate.parse(value);
                }
                if (field.equals("facility_id") && Integer.parseInt(value) < 1) {
                    throw new IllegalArgumentException();
                }
            }
            if (type == ContentType.AGENT && !values.get(2).isBlank()
                    && LocalDate.parse(values.get(2)).isBefore(LocalDate.parse(values.get(1)))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new ProgramControlException("Enter required fields, valid ISO dates (YYYY-MM-DD), and a positive facility ID.", exception);
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private void require(boolean allowed) throws ProgramControlException {
        if (!allowed) {
            throw new ProgramControlException("Access denied.");
        }
    }

    private void record(Connection connection, ContentType type, int id, Session session, AuditEventType event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO audit_log (event_type, content_type, content_id, username, timestamp) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, event.name());
            statement.setString(2, type.name());
            statement.setInt(3, id);
            statement.setString(4, session.getUsername());
            statement.setString(5, LocalDateTime.now().toString());
            statement.executeUpdate();
        }
    }
}
