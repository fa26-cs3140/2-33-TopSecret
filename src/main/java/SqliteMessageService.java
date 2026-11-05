import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Messages for agents, stored in the project database next to the agents table. */
public class SqliteMessageService implements MessageService {

    static final String CREATE_MESSAGES =
            "CREATE TABLE IF NOT EXISTS messages ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "agent_id INTEGER NOT NULL REFERENCES agents(id) ON DELETE CASCADE, "
                    + "sender TEXT NOT NULL, body TEXT NOT NULL, created_at TEXT NOT NULL)";

    // the inbox filter is the session's username, never anything the user typed
    private static final String SELECT_INBOX =
            "SELECT m.id, m.agent_id, a.name, m.sender, m.body, m.created_at "
                    + "FROM messages m JOIN agents a ON a.id = m.agent_id "
                    + "WHERE LOWER(a.name) = LOWER(?) ORDER BY m.id DESC";

    private final Path database;
    private final AccessControl access = new AccessControl();

    public SqliteMessageService(Path database) {
        this.database = Objects.requireNonNull(database, "database cannot be null");
    }

    /** Makes the messages table if it is missing. Run after SqliteContentService.initialize(). */
    public void initialize() throws ProgramControlException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_MESSAGES);
        } catch (SQLException exception) {
            throw new ProgramControlException("Message database could not be initialized.", exception);
        }
    }

    @Override
    public Map<Integer, String> recipients(Session session) throws ProgramControlException {
        require(access.canSendMessage(session));
        Map<Integer, String> agents = new LinkedHashMap<>();
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT id, name FROM agents ORDER BY id")) {
            while (results.next()) {
                agents.put(results.getInt("id"), results.getString("name"));
            }
            return agents;
        } catch (SQLException exception) {
            throw new ProgramControlException("Agents could not be listed.", exception);
        }
    }

    @Override
    public void send(int agentId, String body, Session session) throws ProgramControlException {
        require(access.canSendMessage(session));
        if (body == null || body.isBlank()) {
            throw new ProgramControlException("A message cannot be empty.");
        }
        try (Connection connection = connect()) {
            if (!agentExists(connection, agentId)) {
                throw new ProgramControlException("No agent with ID " + agentId + ".");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO messages (agent_id, sender, body, created_at) VALUES (?, ?, ?, ?)")) {
                statement.setInt(1, agentId);
                statement.setString(2, session.getUsername());
                statement.setString(3, body.trim());
                statement.setString(4, LocalDateTime.now().withNano(0).toString());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new ProgramControlException("Message could not be sent.", exception);
        }
    }

    @Override
    public List<Message> inbox(Session session) throws ProgramControlException {
        require(access.canReadMessages(session));
        List<Message> messages = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(SELECT_INBOX)) {
            statement.setString(1, session.getUsername());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    messages.add(new Message(results.getInt("id"), results.getInt("agent_id"),
                            results.getString("name"), results.getString("sender"),
                            results.getString("body"),
                            LocalDateTime.parse(results.getString("created_at"))));
                }
            }
            return messages;
        } catch (SQLException exception) {
            throw new ProgramControlException("Messages could not be read.", exception);
        }
    }

    private static boolean agentExists(Connection connection, int agentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM agents WHERE id = ?")) {
            statement.setInt(1, agentId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private static void require(boolean allowed) throws ProgramControlException {
        if (!allowed) {
            throw new ProgramControlException("Access denied.");
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }
}