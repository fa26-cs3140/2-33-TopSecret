package model;

import controller.ProgramControlException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The facility that runs a mission. mission_facilities has its primary key on
 * mission_id, so one facility is the most a mission can have; these check the
 * service honours that and that a mission created through the menu does not
 * quietly end up with none.
 */
class SqliteContentServiceFacilityTest {

    @TempDir Path temporary;

    private final Session admin = new Session("admin", Role.ADMIN);
    private final Session reader = new Session("reader", Role.STANDARD);

    /** A service with two facilities and one mission, none of them related yet. */
    private SqliteContentService service() throws Exception {
        Path database = temporary.resolve("facilities.db");
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        SqliteContentService service =
                new SqliteContentService(database, audit, mock(FileHandler.class));
        service.initialize();
        service.add(ContentType.FACILITY, List.of("Langley Annex", "LGA"), admin);
        service.add(ContentType.FACILITY, List.of("Berlin Station", "BER"), admin);
        service.add(ContentType.MISSION,
                List.of("Operation Nightfall", "Watch the north gate.", "2026-11-05"), admin);
        return service;
    }

    @Test
    void aMissionCreatedThroughTheServiceHasNoFacilityYet() throws Exception {
        SqliteContentService service = service();

        assertEquals(Optional.empty(), service.facilityFor(1, reader));
    }

    @Test
    void theFacilityIsReadableOnceItIsSet() throws Exception {
        SqliteContentService service = service();

        service.setFacility(1, 2, admin);

        Optional<ContentEntry> facility = service.facilityFor(1, reader);
        assertTrue(facility.isPresent());
        assertEquals(2, facility.get().id());
        assertEquals(List.of("Berlin Station", "BER"), facility.get().values());
    }

    @Test
    void settingTheFacilityAgainReplacesItRatherThanAddingASecond() throws Exception {
        SqliteContentService service = service();
        service.setFacility(1, 1, admin);

        service.setFacility(1, 2, admin);

        assertEquals(2, service.facilityFor(1, reader).orElseThrow().id());
    }

    @Test
    void aMissionThatDoesNotExistHasNoFacility() throws Exception {
        SqliteContentService service = service();

        assertEquals(Optional.empty(), service.facilityFor(404, reader));
    }

    @Test
    void theMissionAndTheFacilityMustBothExist() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class, () -> service.setFacility(404, 1, admin));
        assertThrows(ProgramControlException.class, () -> service.setFacility(1, 404, admin));
    }

    @Test
    void aStandardUserMayReadTheFacilityButNotSetIt() throws Exception {
        SqliteContentService service = service();
        service.setFacility(1, 1, admin);

        assertTrue(service.facilityFor(1, reader).isPresent());
        assertThrows(ProgramControlException.class, () -> service.setFacility(1, 2, reader));
    }

    @Test
    void settingTheFacilityIsAuditedAgainstTheMission() throws Exception {
        SqliteContentService service = service();

        service.setFacility(1, 1, admin);

        assertTrue(service.audit(admin).stream().anyMatch(entry ->
                entry.getContentType() == AuditContentType.MISSION
                        && entry.getEventType() == AuditEventType.UPDATE
                        && entry.getUsername().equals("admin")));
    }
}
