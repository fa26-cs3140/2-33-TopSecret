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
    static final String COUNTRIES_TXT = "countries.txt";

    private static final DateTimeFormatter SEED_DATE = DateTimeFormatter
            .ofPattern("MMMM dd, uuuu", Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT);

    private static final String CREATE_COUNTRIES =
            "CREATE TABLE IF NOT EXISTS countries ("
                    + "id INTEGER PRIMARY KEY, name TEXT NOT NULL)";
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
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + type.table + " WHERE id = ?")) {
                statement.setInt(1, id);
                if (statement.executeUpdate() == 0) {
                    throw new ProgramControlException("No entry with ID " + id + ".");
                }
            }
            // the audit row and the delete commit together or not at all
            record(connection, type, id, session, AuditEventType.DELETE);
            connection.commit();
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
        return related(ContentType.BRIEF,
                "SELECT * FROM briefs WHERE mission_id = ? ORDER BY id",
                missionId, "Briefs for the mission could not be listed.", session);
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
        return first(related(ContentType.FACILITY,
                "SELECT facilities.* FROM facilities "
                        + "JOIN mission_facilities ON mission_facilities.facility_id = facilities.id "
                        + "WHERE mission_facilities.mission_id = ?",
                missionId, "The facility for the mission could not be read.", session));
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
        return related(ContentType.AGENT,
                "SELECT agents.* FROM agents "
                        + "JOIN mission_agents ON mission_agents.agent_id = agents.id "
                        + "WHERE mission_agents.mission_id = ? ORDER BY agents.id",
                missionId, "The agents on the mission could not be listed.", session);
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
        require(access.canCreateContent(session));
        validate(ContentType.MISSION, values);
        String placeholders = String.join(",", Collections.nCopies(values.size(), "?"));
        try (Connection connection = connect()) {
            // One transaction. A mission requires a facility and an agent, and
            // the triggers that protect those rules only fire on delete, so a
            // mission must never be committed without both. Leaving this block
            // without reaching commit rolls the whole thing back.
            connection.setAutoCommit(false);
            if (!rowExists(connection, "facilities", facilityId)) {
                throw new ProgramControlException("That facility does not exist.");
            }
            if (!rowExists(connection, "agents", agentId)) {
                throw new ProgramControlException("That agent does not exist.");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO missions (" + String.join(",", ContentType.MISSION.fields)
                            + ") VALUES (" + placeholders + ")")) {
                bind(statement, values);
                statement.executeUpdate();
            }
            int missionId;
            try (Statement statement = connection.createStatement();
                 ResultSet results = statement.executeQuery("SELECT last_insert_rowid()")) {
                results.next();
                missionId = results.getInt(1);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO mission_facilities (mission_id, facility_id) VALUES (?, ?)")) {
                statement.setInt(1, missionId);
                statement.setInt(2, facilityId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO mission_agents (mission_id, agent_id) VALUES (?, ?)")) {
                statement.setInt(1, missionId);
                statement.setInt(2, agentId);
                statement.executeUpdate();
            }
            record(connection, ContentType.MISSION, missionId, session, AuditEventType.CREATE);
            connection.commit();
            return missionId;
        } catch (SQLException exception) {
            throw new ProgramControlException("The mission could not be saved.", exception);
        }
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
            statement.executeUpdate(CREATE_COUNTRIES);
            statement.executeUpdate(CREATE_FACILITIES);
            statement.executeUpdate(CREATE_AGENTS);
            statement.executeUpdate(SqliteMissionRepository.CREATE_MISSIONS);
            statement.executeUpdate(CREATE_BRIEFS);
            statement.executeUpdate(CREATE_MISSION_FACILITIES);
            statement.executeUpdate(CREATE_MISSION_AGENTS);
            statement.executeUpdate(PROTECT_MISSION_FACILITY);
            statement.executeUpdate(PROTECT_LAST_MISSION_AGENT);
            // CREATE TABLE IF NOT EXISTS cannot add a column to a database that
            // already exists, so the facility's country arrives by ALTER TABLE.
            // SQLite permits a REFERENCES clause here because the column is
            // nullable and has no default.
            if (!columnExists(connection, "facilities", "country_id")) {
                statement.executeUpdate("ALTER TABLE facilities ADD COLUMN country_id INTEGER NULL "
                        + "REFERENCES countries(id) ON DELETE RESTRICT");
            }
            if (!columnExists(connection, "briefs", "mission_id")) {
                statement.executeUpdate("ALTER TABLE briefs ADD COLUMN mission_id INTEGER NULL "
                        + "REFERENCES missions(id) ON DELETE SET NULL");
            }
        } catch (SQLException exception) {
            throw new ProgramControlException("Content database could not be initialized.", exception);
        }
    }

    private void importSeedDataAndRelationships() throws ProgramControlException {
        String countryText = fileHandler.readText(COUNTRIES_TXT);
        String facilityText = fileHandler.readText(FACILITIES_TSV);
        String agentText = fileHandler.readText(AGENTS_TSV);
        String briefText = fileHandler.readText(BRIEFS_TSV);
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                if (rowCount(connection, "countries") == 0 && countryText != null) {
                    importCountries(connection, countryText);
                }
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

    private void importCountries(Connection connection, String contents)
            throws SQLException, ProgramControlException {
        List<List<String>> rows = tabRows(contents, 1, "Country");
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO countries (name) VALUES (?)")) {
            for (List<String> row : rows) {
                statement.setString(1, row.get(0));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void importFacilities(Connection connection, String contents)
            throws SQLException, ProgramControlException {
        // Two or three columns: the third names the country, which files
        // written before countries existed do not carry.
        List<List<String>> rows = tabRows(contents, 2, 3, "Facility");
        // Every seed file is optional. With no country list loaded there is
        // nothing to resolve a name against, so the facilities are imported
        // unassigned rather than refusing to start. A name missing from a list
        // that WAS loaded stays a fault in the sample data.
        boolean haveCountries = rowCount(connection, "countries") > 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO facilities (name, abbreviation, country_id) VALUES (?, ?, ?)")) {
            for (List<String> row : rows) {
                if (row.get(0).isBlank() || row.get(1).isBlank()) {
                    throw new ProgramControlException("Facility sample data contains a blank field.");
                }
                statement.setString(1, row.get(0));
                statement.setString(2, row.get(1));
                if (row.get(2).isBlank() || !haveCountries) {
                    statement.setNull(3, java.sql.Types.INTEGER);
                } else {
                    statement.setInt(3, countryNamed(connection, row.get(2)));
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * The identifier of the country with that name. The supplied list is the
     * authority, so a sample facility naming a country outside it is a fault
     * in the sample data rather than a reason to invent a country.
     */
    private static int countryNamed(Connection connection, String name)
            throws SQLException, ProgramControlException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM countries WHERE name = ?")) {
            statement.setString(1, name);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new ProgramControlException(
                            "Facility sample data names an unknown country: " + name + ".");
                }
                return results.getInt(1);
            }
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

    /**
     * Rows whose width may vary between two bounds, for a file that gained a
     * column. Narrow rows are padded with blanks so the caller can read every
     * position without checking the width again.
     */
    private static List<List<String>> tabRows(String contents, int fewest, int most, String kind)
            throws ProgramControlException {
        List<List<String>> rows = new ArrayList<>();
        for (List<String> row : tabRows(contents, -1, kind)) {
            if (row.size() < fewest || row.size() > most) {
                throw new ProgramControlException(
                        kind + " sample row has the wrong number of fields.");
            }
            List<String> padded = new ArrayList<>(row);
            while (padded.size() < most) {
                padded.add("");
            }
            rows.add(padded);
        }
        return rows;
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
            if (fields >= 0 && cells.length != fields) {
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
                validateField(type.fields.get(index), values.get(index));
            }
            if (type == ContentType.AGENT && !values.get(2).isBlank()
                    && LocalDate.parse(values.get(2)).isBefore(LocalDate.parse(values.get(1)))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new ProgramControlException(
                    "Enter required fields, valid ISO dates (YYYY-MM-DD), and a positive record ID.",
                    exception);
        }
    }

    /**
     * One field, by the shape of its name. Any field ending in _id holds a
     * record reference, so a new relationship column is checked by the rule
     * that already covers facility_id.
     */
    private static void validateField(String field, String value) {
        if (value == null || (value.isBlank()
                && !field.equals("date_of_death") && !field.equals("notes"))) {
            throw new IllegalArgumentException();
        }
        if (field.contains("date") && !value.isBlank()) {
            LocalDate.parse(value);
        }
        if (field.endsWith("_id") && Integer.parseInt(value.trim()) < 1) {
            throw new IllegalArgumentException();
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
    @Override
    public int addFacility(List<String> values, int countryId, Session session)
            throws ProgramControlException {
        require(access.canCreateContent(session));
        validate(ContentType.FACILITY, values);
        String placeholders = String.join(",", Collections.nCopies(values.size(), "?"));
        try (Connection connection = connect()) {
            // One transaction. A facility is assigned to a country, and the
            // caller cannot know the new row's identifier beforehand, so the
            // country is written here rather than in a second call that could
            // fail and leave the facility unassigned.
            connection.setAutoCommit(false);
            if (!rowExists(connection, "countries", countryId)) {
                throw new ProgramControlException("That country does not exist.");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO facilities (" + String.join(",", ContentType.FACILITY.fields)
                            + ",country_id) VALUES (" + placeholders + ",?)")) {
                bind(statement, values);
                statement.setInt(values.size() + 1, countryId);
                statement.executeUpdate();
            }
            int facilityId;
            try (Statement statement = connection.createStatement();
                 ResultSet results = statement.executeQuery("SELECT last_insert_rowid()")) {
                results.next();
                facilityId = results.getInt(1);
            }
            record(connection, ContentType.FACILITY, facilityId, session, AuditEventType.CREATE);
            connection.commit();
            return facilityId;
        } catch (SQLException exception) {
            throw new ProgramControlException("The facility could not be saved.", exception);
        }
    }

    @Override
    public void updateFacility(int facilityId, List<String> values, int countryId, Session session)
            throws ProgramControlException {
        require(access.canCreateContent(session));
        validate(ContentType.FACILITY, values);
        List<String> assignments = new ArrayList<>();
        for (String field : ContentType.FACILITY.fields) {
            assignments.add(field + " = ?");
        }
        assignments.add("country_id = ?");
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            if (!rowExists(connection, "countries", countryId)) {
                throw new ProgramControlException("That country does not exist.");
            }
            // The fields and the country are columns of the same row, so one
            // statement changes them together or changes neither.
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE facilities SET " + String.join(",", assignments) + " WHERE id = ?")) {
                bind(statement, values);
                statement.setInt(values.size() + 1, countryId);
                statement.setInt(values.size() + 2, facilityId);
                if (statement.executeUpdate() == 0) {
                    throw new ProgramControlException("No entry with ID " + facilityId + ".");
                }
            }
            record(connection, ContentType.FACILITY, facilityId, session, AuditEventType.UPDATE);
            connection.commit();
        } catch (SQLException exception) {
            throw new ProgramControlException("The facility could not be updated.", exception);
        }
    }

    @Override
    public Optional<ContentEntry> countryFor(int facilityId, Session session)
            throws ProgramControlException {
        return first(related(ContentType.COUNTRY,
                "SELECT countries.* FROM countries "
                        + "JOIN facilities ON facilities.country_id = countries.id "
                        + "WHERE facilities.id = ?",
                facilityId, "The country for the facility could not be read.", session));
    }

    @Override
    public void setCountry(int facilityId, int countryId, Session session)
            throws ProgramControlException {
        require(access.canCreateContent(session));
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            if (!rowExists(connection, "facilities", facilityId)) {
                throw new ProgramControlException("That facility does not exist.");
            }
            if (!rowExists(connection, "countries", countryId)) {
                throw new ProgramControlException("That country does not exist.");
            }
            // The country is a column on the facility, so reassigning is an
            // update in place; there is no relationship row to replace.
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE facilities SET country_id = ? WHERE id = ?")) {
                statement.setInt(1, countryId);
                statement.setInt(2, facilityId);
                statement.executeUpdate();
            }
            record(connection, ContentType.FACILITY, facilityId, session, AuditEventType.UPDATE);
            connection.commit();
        } catch (SQLException exception) {
            throw new ProgramControlException(
                    "The country for the facility could not be saved.", exception);
        }
    }

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

    @Override
    public List<ContentEntry> agentsAt(int facilityId, Session session) throws ProgramControlException {
        return related(ContentType.AGENT,
                "SELECT * FROM agents WHERE facility_id = ? ORDER BY id",
                facilityId, "The agents at the facility could not be listed.", session);
    }

    @Override
    public List<ContentEntry> missionsAt(int facilityId, Session session) throws ProgramControlException {
        return related(ContentType.MISSION,
                "SELECT missions.* FROM missions "
                        + "JOIN mission_facilities ON mission_facilities.mission_id = missions.id "
                        + "WHERE mission_facilities.facility_id = ? ORDER BY missions.id",
                facilityId, "The missions at the facility could not be listed.", session);
    }

    @Override
    public List<ContentEntry> missionsFor(int agentId, Session session) throws ProgramControlException {
        return related(ContentType.MISSION,
                "SELECT missions.* FROM missions "
                        + "JOIN mission_agents ON mission_agents.mission_id = missions.id "
                        + "WHERE mission_agents.agent_id = ? ORDER BY missions.id",
                agentId, "The missions for the agent could not be listed.", session);
    }

    @Override
    public Optional<ContentEntry> facilityOf(int agentId, Session session) throws ProgramControlException {
        return first(related(ContentType.FACILITY,
                "SELECT facilities.* FROM facilities "
                        + "JOIN agents ON agents.facility_id = facilities.id WHERE agents.id = ?",
                agentId, "The facility of the agent could not be read.", session));
    }

    @Override
    public List<ContentEntry> facilitiesIn(int countryId, Session session) throws ProgramControlException {
        return related(ContentType.FACILITY,
                "SELECT * FROM facilities WHERE country_id = ? ORDER BY id",
                countryId, "The facilities in the country could not be listed.", session);
    }

    @Override
    public Optional<ContentEntry> missionOf(int briefId, Session session) throws ProgramControlException {
        return first(related(ContentType.MISSION,
                "SELECT missions.* FROM missions "
                        + "JOIN briefs ON briefs.mission_id = missions.id WHERE briefs.id = ?",
                briefId, "The mission of the brief could not be read.", session));
    }

    @Override
    public List<ContentHit> search(String query, Session session) throws ProgramControlException {
        require(access.canSearch(session));
        String needle = query == null ? "" : query.trim();
        if (needle.isEmpty()) {
            throw new ProgramControlException("Search phrase cannot be empty.");
        }
        // LIKE treats % and _ as wildcards, so escape them along with the escape character itself
        String pattern = "%" + needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            List<ContentHit> hits = new ArrayList<>();
            for (ContentType type : ContentType.values()) {
                List<String> tests = new ArrayList<>();
                for (String field : type.fields) {
                    if (!field.endsWith("_id")) {
                        tests.add(field + " LIKE ? ESCAPE '\\'");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM "
                        + type.table + " WHERE " + String.join(" OR ", tests) + " ORDER BY id")) {
                    for (int index = 1; index <= tests.size(); index++) {
                        statement.setString(index, pattern);
                    }
                    try (ResultSet results = statement.executeQuery()) {
                        while (results.next()) {
                            hits.add(new ContentHit(type, entry(type, results)));
                        }
                    }
                }
            }
            for (ContentHit hit : hits) {
                record(connection, hit.type(), hit.entry().id(), session, AuditEventType.READ);
            }
            connection.commit();
            return hits;
        } catch (SQLException exception) {
            throw new ProgramControlException("The search could not be completed.", exception);
        }
    }

    /**
     * Read every record a one-parameter query finds, audit each as a READ in the
     * same transaction, and answer them. The query selects one table's columns.
     */
    private List<ContentEntry> related(ContentType type, String sql, int key, String failure,
                                       Session session) throws ProgramControlException {
        require(access.canReadContent(session));
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            List<ContentEntry> found = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, key);
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        found.add(entry(type, results));
                    }
                }
            }
            for (ContentEntry item : found) {
                record(connection, type, item.id(), session, AuditEventType.READ);
            }
            connection.commit();
            return found;
        } catch (SQLException exception) {
            throw new ProgramControlException(failure, exception);
        }
    }

    private static Optional<ContentEntry> first(List<ContentEntry> entries) {
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.get(0));
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            // two programs share this file, so wait for the other one instead of failing at once
            statement.execute("PRAGMA busy_timeout = 5000");
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
