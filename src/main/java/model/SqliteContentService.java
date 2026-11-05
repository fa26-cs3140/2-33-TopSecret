package model;

import controller.ProgramControlException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** SQLite-backed boundary used by the authenticated content menu. */
public class SqliteContentService implements ContentService {
    static final String FACILITIES_TSV = "facilities.tsv";
    static final String AGENTS_TSV = "agents.tsv";
    static final String BRIEFS_TSV = "mission_briefs.tsv";

    private static final DateTimeFormatter SEED_DATE = DateTimeFormatter
            .ofPattern("MMMM dd, uuuu", Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT);

    private static final String CREATE_FACILITIES =
            "CREATE TABLE IF NOT EXISTS facilities ("
                    + "id INTEGER PRIMARY KEY, name TEXT NOT NULL, abbreviation TEXT NOT NULL)";
    private static final String CREATE_AGENTS =
            "CREATE TABLE IF NOT EXISTS agents ("
                    + "id INTEGER PRIMARY KEY, name TEXT NOT NULL, date_of_birth TEXT NOT NULL, "
                    + "date_of_death TEXT NOT NULL, notes TEXT NOT NULL, "
                    + "facility_id INTEGER NOT NULL REFERENCES facilities(id) ON DELETE RESTRICT)";
    private static final String CREATE_BRIEFS =
            "CREATE TABLE IF NOT EXISTS briefs ("
                    + "id INTEGER PRIMARY KEY, title TEXT NOT NULL, date TEXT NOT NULL, text TEXT NOT NULL, "
                    + "mission_id INTEGER NULL REFERENCES missions(id) ON DELETE SET NULL)";
    private static final String CREATE_MISSION_FACILITIES =
            "CREATE TABLE IF NOT EXISTS mission_facilities ("
                    + "mission_id INTEGER PRIMARY KEY REFERENCES missions(id) ON DELETE CASCADE, "
                    + "facility_id INTEGER NOT NULL REFERENCES facilities(id) ON DELETE RESTRICT)";
    private static final String CREATE_MISSION_AGENTS =
            "CREATE TABLE IF NOT EXISTS mission_agents ("
                    + "mission_id INTEGER NOT NULL REFERENCES missions(id) ON DELETE CASCADE, "
                    + "agent_id INTEGER NOT NULL REFERENCES agents(id) ON DELETE RESTRICT, "
                    + "PRIMARY KEY (mission_id, agent_id))";
    private static final String PROTECT_MISSION_FACILITY =
            "CREATE TRIGGER IF NOT EXISTS protect_mission_facility_delete "
                    + "BEFORE DELETE ON mission_facilities "
                    + "WHEN EXISTS (SELECT 1 FROM missions WHERE id = OLD.mission_id) "
                    + "BEGIN SELECT RAISE(ABORT, 'a mission requires one facility'); END";
    private static final String PROTECT_LAST_MISSION_AGENT =
            "CREATE TRIGGER IF NOT EXISTS protect_last_mission_agent_delete "
                    + "BEFORE DELETE ON mission_agents "
                    + "WHEN EXISTS (SELECT 1 FROM missions WHERE id = OLD.mission_id) "
                    + "AND (SELECT COUNT(*) FROM mission_agents WHERE mission_id = OLD.mission_id) <= 1 "
                    + "BEGIN SELECT RAISE(ABORT, 'a mission requires at least one agent'); END";

    private final Path database;
    private final AuditLogRepository auditLog;
    private final FileHandler fileHandler;
    private final AccessControl access = new AccessControl();

    public SqliteContentService(Path database, AuditLogRepository auditLog) {
        this(database, auditLog, new FileHandlerImpl());
    }

    /** Testable constructor: seed data still enters through the project's file boundary. */
    public SqliteContentService(Path database, AuditLogRepository auditLog, FileHandler fileHandler) {
        this.database = Objects.requireNonNull(database, "database cannot be null");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog cannot be null");
        this.fileHandler = Objects.requireNonNull(fileHandler, "fileHandler cannot be null");
    }

    /** Creates the schema, imports supplied samples once, then fills mission relationships. */
    public void initialize() throws ProgramControlException {
        ensureParentDirectory();
        createSchema();
        importSeedDataAndRelationships();
    }

    @Override
    public List<ContentEntry> list(ContentType type, Session session) throws ProgramControlException {
        require(access.canReadContent(session));
        Objects.requireNonNull(type, "type cannot be null");
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            List<ContentEntry> entries = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet results = statement.executeQuery(
                         "SELECT * FROM " + type.table + " ORDER BY id")) {
                while (results.next()) {
                    entries.add(entry(type, results));
                }
            }
            for (ContentEntry item : entries) {
                record(connection, type, item.id(), session, AuditEventType.READ);
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
        Objects.requireNonNull(type, "type cannot be null");
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            ContentEntry item;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM " + type.table + " WHERE id = ?")) {
                statement.setInt(1, id);
                try (ResultSet results = statement.executeQuery()) {
                    if (!results.next()) {
                        throw new ContentNotFoundException(id);
                    }
                    item = entry(type, results);
                }
            }
            record(connection, type, id, session, AuditEventType.READ);
            connection.commit();
            return item;
        } catch (SQLException exception) {
            throw new ProgramControlException("Content could not be read.", exception);
        }
    }

    @Override
    public void add(ContentType type, List<String> values, Session session)
            throws ProgramControlException {
        require(access.canCreateContent(session));
        validate(type, values);
        String placeholders = String.join(",", Collections.nCopies(values.size(), "?"));
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + type.table + " (" + String.join(",", type.fields)
                            + ") VALUES (" + placeholders + ")")) {
                bind(statement, values);
                statement.executeUpdate();
            }
            try (Statement statement = connection.createStatement();
                 ResultSet results = statement.executeQuery("SELECT last_insert_rowid()")) {
                results.next();
                record(connection, type, results.getInt(1), session, AuditEventType.CREATE);
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new ProgramControlException(
                    "Entry could not be saved. Agents require an existing facility ID.", exception);
        }
    }

    @Override
    public void update(ContentType type, int id, List<String> values, Session session)
            throws ProgramControlException {
        require(access.canCreateContent(session));
        validate(type, values);
        List<String> assignments = new ArrayList<>();
        for (String field : type.fields) {
            assignments.add(field + " = ?");
        }
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + type.table + " SET " + String.join(",", assignments)
                            + " WHERE id = ?")) {
                bind(statement, values);
                statement.setInt(values.size() + 1, id);
                if (statement.executeUpdate() == 0) {
                    throw new ProgramControlException("No entry with ID " + id + ".");
                }
            }
            record(connection, type, id, session, AuditEventType.UPDATE);
            connection.commit();
        } catch (SQLException exception) {
            throw new ProgramControlException(
                    "Entry could not be updated. Agents require an existing facility ID.", exception);
        }
    }

    @Override
    public void delete(ContentType type, int id, Session session) throws ProgramControlException {
        require(access.canCreateContent(session));
        Objects.requireNonNull(type, "type cannot be null");
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + type.table + " WHERE id = ?")) {
            statement.setInt(1, id);
            if (statement.executeUpdate() == 0) {
                throw new ProgramControlException("No entry with ID " + id + ".");
            }
        } catch (SQLException exception) {
            throw new ProgramControlException(
                    "Entry could not be deleted because other records depend on it.", exception);
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

    @Override
    public List<ContentEntry> briefsFor(int missionId, Session session) throws ProgramControlException {
        require(access.canReadContent(session));
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            List<ContentEntry> briefs = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM briefs WHERE mission_id = ? ORDER BY id")) {
                statement.setInt(1, missionId);
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        briefs.add(entry(ContentType.BRIEF, results));
                    }
                }
            }
            for (ContentEntry brief : briefs) {
                record(connection, ContentType.BRIEF, brief.id(), session, AuditEventType.READ);
            }
            connection.commit();
            return briefs;
        } catch (SQLException exception) {
            throw new ProgramControlException("Briefs for the mission could not be listed.", exception);
        }
    }

    @Override
    public void attachBrief(int missionId, int briefId, Session session) throws ProgramControlException {
        require(access.canCreateContent(session));
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            if (!rowExists(connection, "missions", missionId)) {
                throw new ProgramControlException("That mission does not exist.");
            }
            if (!rowExists(connection, "briefs", briefId)) {
                throw new ProgramControlException("That brief does not exist.");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE briefs SET mission_id = ? WHERE id = ?")) {
                statement.setInt(1, missionId);
                statement.setInt(2, briefId);
                statement.executeUpdate();
            }
            record(connection, ContentType.BRIEF, briefId, session, AuditEventType.UPDATE);
            connection.commit();
        } catch (SQLException exception) {
            throw new ProgramControlException("The brief could not be attached to the mission.", exception);
        }
    }

    @Override
    public Optional<ContentEntry> facilityFor(int missionId, Session session)
            throws ProgramControlException {
        require(access.canReadContent(session));
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            Optional<ContentEntry> facility = Optional.empty();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT facilities.* FROM facilities "
                            + "JOIN mission_facilities ON mission_facilities.facility_id = facilities.id "
                            + "WHERE mission_facilities.mission_id = ?")) {
                statement.setInt(1, missionId);
                try (ResultSet results = statement.executeQuery()) {
                    if (results.next()) {
                        facility = Optional.of(entry(ContentType.FACILITY, results));
                    }
                }
            }
            if (facility.isPresent()) {
                record(connection, ContentType.FACILITY, facility.get().id(), session,
                        AuditEventType.READ);
            }
            connection.commit();
            return facility;
        } catch (SQLException exception) {
            throw new ProgramControlException(
                    "The facility for the mission could not be read.", exception);
        }
    }

    @Override
    public void setFacility(int missionId, int facilityId, Session session)
            throws ProgramControlException {
        require(access.canCreateContent(session));
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            if (!rowExists(connection, "missions", missionId)) {
                throw new ProgramControlException("That mission does not exist.");
            }
            if (!rowExists(connection, "facilities", facilityId)) {
                throw new ProgramControlException("That facility does not exist.");
            }
            // An existing row is updated in place. Deleting it first would hit
            // protect_mission_facility_delete, which refuses to leave a mission
            // without a facility.
            String sql = hasFacility(connection, missionId)
                    ? "UPDATE mission_facilities SET facility_id = ? WHERE mission_id = ?"
                    : "INSERT INTO mission_facilities (facility_id, mission_id) VALUES (?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, facilityId);
                statement.setInt(2, missionId);
                statement.executeUpdate();
            }
            record(connection, ContentType.MISSION, missionId, session, AuditEventType.UPDATE);
            connection.commit();
        } catch (SQLException exception) {
            throw new ProgramControlException(
                    "The facility for the mission could not be saved.", exception);
        }
    }

    @Override
    public List<ContentEntry> agentsFor(int missionId, Session session)
            throws ProgramControlException {
        require(access.canReadContent(session));
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            List<ContentEntry> agents = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT agents.* FROM agents "
                            + "JOIN mission_agents ON mission_agents.agent_id = agents.id "
                            + "WHERE mission_agents.mission_id = ? ORDER BY agents.id")) {
                statement.setInt(1, missionId);
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        agents.add(entry(ContentType.AGENT, results));
                    }
                }
            }
            for (ContentEntry agent : agents) {
                record(connection, ContentType.AGENT, agent.id(), session, AuditEventType.READ);
            }
            connection.commit();
            return agents;
        } catch (SQLException exception) {
            throw new ProgramControlException(
                    "The agents on the mission could not be listed.", exception);
        }
    }

    @Override
    public void assignAgent(int missionId, int agentId, Session session)
            throws ProgramControlException {
        require(access.canCreateContent(session));
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            if (!rowExists(connection, "missions", missionId)) {
                throw new ProgramControlException("That mission does not exist.");
            }
            if (!rowExists(connection, "agents", agentId)) {
                throw new ProgramControlException("That agent does not exist.");
            }
            // The composite primary key makes a repeat assignment a no-op rather
            // than an error, which is what a menu that lists every agent needs.
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO mission_agents (mission_id, agent_id) VALUES (?, ?)")) {
                statement.setInt(1, missionId);
                statement.setInt(2, agentId);
                statement.executeUpdate();
            }
            record(connection, ContentType.MISSION, missionId, session, AuditEventType.UPDATE);
            connection.commit();
        } catch (SQLException exception) {
            throw new ProgramControlException(
                    "The agent could not be assigned to the mission.", exception);
        }
    }

    @Override
    public int addMission(List<String> values, int facilityId, int agentId, Session session)
            throws ProgramControlException {
        throw new UnsupportedOperationException("addMission is not implemented yet");
    }

    private static boolean hasFacility(Connection connection, int missionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM mission_facilities WHERE mission_id = ?")) {
            statement.setInt(1, missionId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private void createSchema() throws ProgramControlException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_FACILITIES);
            statement.executeUpdate(CREATE_AGENTS);
            statement.executeUpdate(SqliteMissionRepository.CREATE_MISSIONS);
            statement.executeUpdate(CREATE_BRIEFS);
            statement.executeUpdate(CREATE_MISSION_FACILITIES);
            statement.executeUpdate(CREATE_MISSION_AGENTS);
            statement.executeUpdate(PROTECT_MISSION_FACILITY);
            statement.executeUpdate(PROTECT_LAST_MISSION_AGENT);
            if (!columnExists(connection, "briefs", "mission_id")) {
                statement.executeUpdate("ALTER TABLE briefs ADD COLUMN mission_id INTEGER NULL "
                        + "REFERENCES missions(id) ON DELETE SET NULL");
            }
        } catch (SQLException exception) {
            throw new ProgramControlException("Content database could not be initialized.", exception);
        }
    }

    private void importSeedDataAndRelationships() throws ProgramControlException {
        String facilityText = fileHandler.readText(FACILITIES_TSV);
        String agentText = fileHandler.readText(AGENTS_TSV);
        String briefText = fileHandler.readText(BRIEFS_TSV);
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                if (rowCount(connection, "facilities") == 0 && facilityText != null) {
                    importFacilities(connection, facilityText);
                }
                if (rowCount(connection, "briefs") == 0 && briefText != null) {
                    importBriefs(connection, briefText);

                }
                List<Integer> facilityIds = ids(connection, "facilities");
                if (rowCount(connection, "agents") == 0 && agentText != null) {
                    if (facilityIds.isEmpty()) {
                        throw new ProgramControlException(
                                "Agent sample data requires at least one facility.");
                    }
                    importAgents(connection, agentText, facilityIds);
                }
                List<Integer> agentIds = ids(connection, "agents");
                if (tableExists(connection, "missions")
                        && !facilityIds.isEmpty() && !agentIds.isEmpty()) {
                    assignMissions(connection, facilityIds, agentIds);
                }
                connection.commit();
            } catch (SQLException | ProgramControlException exception) {
                connection.rollback();
                if (exception instanceof ProgramControlException) {
                    throw (ProgramControlException) exception;
                }
                throw new ProgramControlException("Sample content could not be imported.", exception);
            }
        } catch (SQLException exception) {
            throw new ProgramControlException("Sample content could not be imported.", exception);
        }
    }

    private void importFacilities(Connection connection, String contents)
            throws SQLException, ProgramControlException {
        List<List<String>> rows = tabRows(contents, 2, "Facility");
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO facilities (name, abbreviation) VALUES (?, ?)")) {
            for (List<String> row : rows) {
                if (row.get(0).isBlank() || row.get(1).isBlank()) {
                    throw new ProgramControlException("Facility sample data contains a blank field.");
                }
                statement.setString(1, row.get(0));
                statement.setString(2, row.get(1));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void importAgents(Connection connection, String contents, List<Integer> facilityIds)
            throws SQLException, ProgramControlException {
        List<List<String>> rows = tabRows(contents, 2, "Agent");
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO agents (name, date_of_birth, date_of_death, notes, facility_id) "
                        + "VALUES (?, ?, '', '', ?)")) {
            for (int index = 0; index < rows.size(); index++) {
                List<String> row = rows.get(index);
                if (row.get(0).isBlank() || row.get(1).isBlank()) {
                    throw new ProgramControlException("Agent sample data contains a blank field.");
                }
                statement.setString(1, row.get(0));
                statement.setString(2, parseSeedDate(row.get(1)).toString());
                statement.setInt(3, facilityIds.get(index % facilityIds.size()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void importBriefs(Connection connection, String contents)
            throws SQLException, ProgramControlException {
        List<List<String>> rows = tabRows(contents, 3, "Brief");
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO briefs (title, date, text) VALUES (?, ?, ?)")) {
            for (int index = 0; index < rows.size(); index++) {
                List<String> row = rows.get(index);
                if (index == 0 && row.get(0).equalsIgnoreCase("title")) {
                    continue;   // header row
                }
                if (row.get(0).isBlank() || row.get(1).isBlank() || row.get(2).isBlank()) {
                    throw new ProgramControlException("Brief sample data contains a blank field.");
                }
                try {
                    LocalDate.parse(row.get(1));
                } catch (DateTimeParseException exception) {
                    throw new ProgramControlException(
                            "Brief sample date is invalid: " + row.get(1), exception);
                }
                statement.setString(1, row.get(0));
                statement.setString(2, row.get(1));
                statement.setString(3, row.get(2));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void assignMissions(Connection connection, List<Integer> facilityIds,
                                List<Integer> agentIds) throws SQLException {
        List<Integer> missionIds = ids(connection, "missions");
        try (PreparedStatement facility = connection.prepareStatement(
                     "INSERT OR IGNORE INTO mission_facilities (mission_id, facility_id) VALUES (?, ?)");
             PreparedStatement agent = connection.prepareStatement(
                     "INSERT OR IGNORE INTO mission_agents (mission_id, agent_id) VALUES (?, ?)")) {
            for (int index = 0; index < missionIds.size(); index++) {
                facility.setInt(1, missionIds.get(index));
                facility.setInt(2, facilityIds.get(index % facilityIds.size()));
                facility.addBatch();
                agent.setInt(1, missionIds.get(index));
                agent.setInt(2, agentIds.get(index % agentIds.size()));
                agent.addBatch();
            }
            facility.executeBatch();
            agent.executeBatch();
        }
    }

    private static List<List<String>> tabRows(String contents, int fields, String kind)
            throws ProgramControlException {
        if (contents.isBlank()) {
            return List.of();
        }
        List<List<String>> rows = new ArrayList<>();
        String[] lines = contents.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] cells = line.split("\t", -1);
            if (cells.length != fields) {
                throw new ProgramControlException(kind + " sample row has the wrong number of fields.");
            }
            List<String> row = new ArrayList<>();
            for (String cell : cells) {
                row.add(cell.trim());
            }
            rows.add(row);
        }
        return rows;
    }

    private static LocalDate parseSeedDate(String value) throws ProgramControlException {
        try {
            return LocalDate.parse(value, SEED_DATE);
        } catch (DateTimeParseException exception) {
            throw new ProgramControlException("Agent sample date is invalid: " + value, exception);
        }
    }

    private static ContentEntry entry(ContentType type, ResultSet results) throws SQLException {
        List<String> values = new ArrayList<>();
        for (String field : type.fields) {
            values.add(results.getString(field));
        }
        return new ContentEntry(results.getInt("id"), values);
    }

    private void validate(ContentType type, List<String> values) throws ProgramControlException {
        Objects.requireNonNull(type, "type cannot be null");
        if (values == null || values.size() != type.fields.size()) {
            throw new ProgramControlException("Incorrect number of fields.");
        }
        try {
            for (int index = 0; index < values.size(); index++) {
                String field = type.fields.get(index);
                String value = values.get(index);
                if (value == null || (value.isBlank()
                        && !field.equals("date_of_death") && !field.equals("notes"))) {
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
            throw new ProgramControlException(
                    "Enter required fields, valid ISO dates (YYYY-MM-DD), and a positive facility ID.",
                    exception);
        }
    }

    private static void bind(PreparedStatement statement, List<String> values) throws SQLException {
        for (int index = 0; index < values.size(); index++) {
            statement.setString(index + 1, values.get(index));
        }
    }

    private static int rowCount(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return results.next() ? results.getInt(1) : 0;
        }
    }

    private static List<Integer> ids(Connection connection, String table) throws SQLException {
        List<Integer> values = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT id FROM " + table + " ORDER BY id")) {
            while (results.next()) {
                values.add(results.getInt(1));
            }
        }
        return values;
    }

    /** True when that table holds a row with that id. */
    private static boolean rowExists(Connection connection, String table, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM " + table + " WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column)
            throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, table, column)) {
            return columns.next();
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, table);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private void ensureParentDirectory() throws ProgramControlException {
        Path parent = database.toAbsolutePath().normalize().getParent();
        if (parent == null || Files.isDirectory(parent)) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException | SecurityException exception) {
            throw new ProgramControlException("Content database folder could not be created.", exception);
        }
    }

    private static void require(boolean allowed) throws ProgramControlException {
        if (!allowed) {
            throw new ProgramControlException("Access denied.");
        }
    }

    private static void record(Connection connection, ContentType type, int id,
                               Session session, AuditEventType event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO audit_log (event_type, content_type, content_id, username, timestamp) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, event.name());
            statement.setString(2, type.name());
            statement.setInt(3, id);
            statement.setString(4, session.getUsername());
            statement.setString(5, LocalDateTime.now().toString());
            statement.executeUpdate();
        }
    }
}
