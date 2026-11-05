package model;

import controller.ProgramControlException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Assigning agents to missions. Until now mission_agents was written only by
 * the seed importer, so no user could put an agent on a mission.
 */
class SqliteContentServiceAgentTest {

    @TempDir Path temporary;

    private final Session admin = new Session("admin", Role.ADMIN);
    private final Session reader = new Session("reader", Role.STANDARD);

    /**
     * Two facilities, one agent in each, and two missions. Nothing is related
     * yet, so each test states its own relationships.
     */
    private SqliteContentService service() throws Exception {
        Path database = temporary.resolve("agents.db");
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        SqliteContentService service =
                new SqliteContentService(database, audit, mock(FileHandler.class));
        service.initialize();
        service.add(ContentType.FACILITY, List.of("Langley Annex", "LGA"), admin);
        service.add(ContentType.FACILITY, List.of("Berlin Station", "BER"), admin);
        service.add(ContentType.AGENT,
                List.of("Cardinal", "1931-02-14", "1979-04-02", "Langley.", "1"), admin);
        service.add(ContentType.AGENT,
                List.of("Nightjar", "1940-06-01", "1988-12-19", "Berlin.", "2"), admin);
        service.add(ContentType.MISSION,
                List.of("Operation Nightfall", "Watch the north gate.", "2026-11-05"), admin);
        service.add(ContentType.MISSION,
                List.of("Operation Sandtrap", "Bug the pouch.", "2026-11-06"), admin);
        return service;
    }

    @Test
    void aMissionStartsWithNoAgents() throws Exception {
        SqliteContentService service = service();

        assertEquals(List.of(), service.agentsFor(1, reader));
    }

    @Test
    void anAssignedAgentIsListedForTheMission() throws Exception {
        SqliteContentService service = service();

        service.assignAgent(1, 1, admin);

        List<ContentEntry> agents = service.agentsFor(1, reader);
        assertEquals(1, agents.size());
        assertEquals("Cardinal", agents.get(0).values().get(0));
    }

    @Test
    void oneMissionMayHaveSeveralAgents() throws Exception {
        SqliteContentService service = service();

        service.assignAgent(1, 1, admin);
        service.assignAgent(1, 2, admin);

        assertEquals(2, service.agentsFor(1, reader).size());
    }

    @Test
    void assigningTheSameAgentTwiceChangesNothing() throws Exception {
        SqliteContentService service = service();
        service.assignAgent(1, 1, admin);

        service.assignAgent(1, 1, admin);

        assertEquals(1, service.agentsFor(1, reader).size());
    }

    @Test
    void aMissionThatDoesNotExistHasNoAgents() throws Exception {
        SqliteContentService service = service();

        assertEquals(List.of(), service.agentsFor(404, reader));
    }

    @Test
    void theMissionAndTheAgentMustBothExist() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class, () -> service.assignAgent(404, 1, admin));
        assertThrows(ProgramControlException.class, () -> service.assignAgent(1, 404, admin));
    }

    @Test
    void aStandardUserMayReadTheAgentsButNotAssignThem() throws Exception {
        SqliteContentService service = service();
        service.assignAgent(1, 1, admin);

        assertEquals(1, service.agentsFor(1, reader).size());
        assertThrows(ProgramControlException.class, () -> service.assignAgent(1, 2, reader));
    }

    @Test
    void assigningAnAgentIsAuditedAgainstTheMission() throws Exception {
        SqliteContentService service = service();

        service.assignAgent(1, 1, admin);

        assertTrue(service.audit(admin).stream().anyMatch(entry ->
                entry.getContentType() == AuditContentType.MISSION
                        && entry.getEventType() == AuditEventType.UPDATE
                        && entry.getUsername().equals("admin")));
    }
}
