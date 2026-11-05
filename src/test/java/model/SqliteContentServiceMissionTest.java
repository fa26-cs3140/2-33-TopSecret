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
 * Creating a mission. A mission is run by one facility and has at least one
 * agent, and both triggers that protect those rules only fire on delete, so
 * creation has to supply them or the mission is born breaking its own rules.
 * That is why this is one call and one transaction rather than an add followed
 * by two relationship writes.
 */
class SqliteContentServiceMissionTest {

    private static final List<String> NIGHTFALL =
            List.of("Operation Nightfall", "Watch the north gate.", "2026-11-05");

    @TempDir Path temporary;

    private final Session admin = new Session("admin", Role.ADMIN);
    private final Session reader = new Session("reader", Role.STANDARD);

    /** Two facilities with one agent each, and no missions at all. */
    private SqliteContentService service() throws Exception {
        Path database = temporary.resolve("missions.db");
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
        return service;
    }

    @Test
    void aMissionIsCreatedWithItsFacilityAndItsFirstAgent() throws Exception {
        SqliteContentService service = service();

        int id = service.addMission(NIGHTFALL, 1, 1, admin);

        assertEquals(NIGHTFALL, service.read(ContentType.MISSION, id, reader).values());
        assertEquals(1, service.facilityFor(id, reader).orElseThrow().id());
        assertEquals(1, service.agentsFor(id, reader).size());
    }

    @Test
    void theIdentifierReturnedIsTheNewMission() throws Exception {
        SqliteContentService service = service();

        int first = service.addMission(NIGHTFALL, 1, 1, admin);
        int second = service.addMission(
                List.of("Operation Sandtrap", "Bug the pouch.", "2026-11-06"), 2, 2, admin);

        assertEquals(2, service.list(ContentType.MISSION, reader).size());
        assertTrue(second > first);
        assertEquals("Operation Sandtrap",
                service.read(ContentType.MISSION, second, reader).values().get(0));
    }

    @Test
    void creatingAMissionIsAuditedAsACreate() throws Exception {
        SqliteContentService service = service();

        service.addMission(NIGHTFALL, 1, 1, admin);

        assertTrue(service.audit(admin).stream().anyMatch(entry ->
                entry.getContentType() == AuditContentType.MISSION
                        && entry.getEventType() == AuditEventType.CREATE
                        && entry.getUsername().equals("admin")));
    }

    @Test
    void anUnknownFacilityLeavesNoMissionBehind() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class,
                () -> service.addMission(NIGHTFALL, 404, 1, admin));

        assertEquals(List.of(), service.list(ContentType.MISSION, reader));
    }

    @Test
    void anUnknownAgentLeavesNoMissionBehind() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class,
                () -> service.addMission(NIGHTFALL, 1, 404, admin));

        assertEquals(List.of(), service.list(ContentType.MISSION, reader));
    }

    @Test
    void theWrongNumberOfFieldsLeavesNoMissionBehind() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class,
                () -> service.addMission(List.of("Only a title"), 1, 1, admin));

        assertEquals(List.of(), service.list(ContentType.MISSION, reader));
    }

    @Test
    void aStandardUserMayNotCreateAMission() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class,
                () -> service.addMission(NIGHTFALL, 1, 1, reader));

        assertEquals(List.of(), service.list(ContentType.MISSION, reader));
    }

    @Test
    void theFirstAgentNeedNotBelongToTheFacilityRunningTheMission() throws Exception {
        SqliteContentService service = service();

        int id = service.addMission(NIGHTFALL, 1, 2, admin);

        assertEquals(1, service.facilityFor(id, reader).orElseThrow().id());
        assertEquals("Nightjar", service.agentsFor(id, reader).get(0).values().get(0));
    }

    @Test
    void aCreatedMissionSatisfiesTheRulesItsTriggersProtect() throws Exception {
        SqliteContentService service = service();

        int id = service.addMission(NIGHTFALL, 1, 1, admin);

        // Both triggers refuse to leave a mission without a facility or without
        // its last agent. A mission created here already has one of each, so
        // deleting the mission itself is the only legal way to remove them.
        assertTrue(service.facilityFor(id, reader).isPresent());
        assertEquals(1, service.agentsFor(id, reader).size());
        service.delete(ContentType.MISSION, id, admin);
        assertEquals(List.of(), service.list(ContentType.MISSION, reader));
    }
}
