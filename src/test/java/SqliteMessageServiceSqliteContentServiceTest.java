import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteMessageServiceSqliteContentServiceTest {

    private static final int GHOST = 2;

    @TempDir Path temporary;
    private SqliteMessageService messages;
    private final Session admin = new Session("admin", Role.ADMIN);

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporary.resolve("messages.db");
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        new SqliteContentService(database, audit).initialize();
        messages = new SqliteMessageService(database);
        messages.initialize();
    }

    @Test
    void recipientsAreEverySeededAgentInIdOrder() throws Exception {
        Map<Integer, String> agents = messages.recipients(admin);

        assertEquals(10, agents.size());
        assertEquals("Vanguard", agents.get(1));
        assertEquals("Ghost", agents.get(GHOST));
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), List.copyOf(agents.keySet()));
    }

    @Test
    void aMessageForGhostIsReadByTheUserNamedGhost() throws Exception {
        messages.send(GHOST, "  Meet at dawn  ", admin);

        List<Message> inbox = messages.inbox(new Session("Ghost", Role.STANDARD));

        assertEquals(1, inbox.size());
        assertEquals("Ghost", inbox.get(0).getAgentName());
        assertEquals("admin", inbox.get(0).getSender());
        assertEquals("Meet at dawn", inbox.get(0).getBody());
        assertEquals(GHOST, inbox.get(0).getAgentId());
    }

    @Test
    void matchingIgnoresCase() throws Exception {
        messages.send(GHOST, "Meet at dawn", admin);

        assertEquals(1, messages.inbox(new Session("ghost", Role.STANDARD)).size());
        assertEquals(1, messages.inbox(new Session("GHOST", Role.STANDARD)).size());
    }

    @Test
    void nobodyElseSeesAMessageForGhost() throws Exception {
        messages.send(GHOST, "Meet at dawn", admin);

        assertTrue(messages.inbox(new Session("Mirage", Role.STANDARD)).isEmpty());
        assertTrue(messages.inbox(new Session("reader", Role.STANDARD)).isEmpty());
        // being an admin gives no access to other people's mail
        assertTrue(messages.inbox(admin).isEmpty());
    }

    @Test
    void theNewestMessageComesFirst() throws Exception {
        messages.send(GHOST, "first", admin);
        messages.send(GHOST, "second", admin);

        List<Message> inbox = messages.inbox(new Session("Ghost", Role.STANDARD));

        assertEquals("second", inbox.get(0).getBody());
        assertEquals("first", inbox.get(1).getBody());
    }

    @Test
    void anUnknownAgentIsRefused() {
        assertEquals("No agent with ID 999.", assertThrows(ProgramControlException.class,
                () -> messages.send(999, "hello", admin)).getMessage());
    }

    @Test
    void initializingAgainKeepsExistingMessages() throws Exception {
        messages.send(GHOST, "Meet at dawn", admin);

        messages.initialize();

        assertEquals(1, messages.inbox(new Session("Ghost", Role.STANDARD)).size());
    }
}