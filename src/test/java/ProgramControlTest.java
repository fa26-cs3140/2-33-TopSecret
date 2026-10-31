import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProgramControlTest {

    @Test
    void searchMissionsDelegatesToSearchFeature() throws ProgramControlException {
        Mission mission = new Mission(1, "Title", "matching brief",
                java.time.LocalDate.of(2000, 1, 1));
        MissionSearch search = query -> List.of(mission);
        ProgramControl control = new ProgramControlImpl(search);

        List<Mission> result = control.searchMissions("matching");

        assertEquals(List.of(mission), result);
    }

    @Test
    void searchMissionsWrapsSearchFailure() {
        MissionSearch search = query -> {
            throw new MissionSearchException("Search phrase cannot be empty.");
        };
        ProgramControl control = new ProgramControlImpl(search);

        ProgramControlException error = assertThrows(
                ProgramControlException.class, () -> control.searchMissions(" "));

        assertEquals("Search phrase cannot be empty.", error.getMessage());
        assertTrue(error.getCause() instanceof MissionSearchException);
    }

    @Test
    void searchMissionsReportsWhenDatabaseHasNotBeenConnected() {
        ProgramControl control = new ProgramControlImpl();

        ProgramControlException error = assertThrows(
                ProgramControlException.class, () -> control.searchMissions("agent"));

        assertEquals("Mission database is not connected.", error.getMessage());
    }

    @Test
    void threeArgumentConstructorReportsWhenMissionRepositoryIsNotConnected() {
        ProgramControl control = new ProgramControlImpl(query -> List.of());

        ProgramControlException error = assertThrows(
                ProgramControlException.class, control::listMissions);

        assertEquals("Mission database is not connected.", error.getMessage());
    }

    private ProgramControlImpl controlWith(MissionRepository repo) {
        return new ProgramControlImpl(query -> List.of(), repo);
    }

    @Test
    void listMissionsReturnsRepositoryMissionsInOrder() throws ProgramControlException {
        Mission first = new Mission(1, "Alpha", "brief a", LocalDate.of(2026, 1, 1));
        Mission second = new Mission(2, "Bravo", "brief b", LocalDate.of(2026, 1, 2));

        List<Mission> result = controlWith(() -> List.of(first, second)).listMissions();

        assertEquals(List.of(first, second), result);
    }

    @Test
    void listMissionsReturnsEmptyListWhenRepositoryIsEmpty() throws ProgramControlException {
        assertTrue(controlWith(() -> List.of()).listMissions().isEmpty());
    }

    @Test
    void listMissionsReturnsEmptyListWhenRepositoryReturnsNull() throws ProgramControlException {
        assertNotNull(controlWith(() -> null).listMissions());
    }

    @Test
    void listMissionsWrapsRepositoryFailure() {
        ProgramControlImpl pc = controlWith(() -> {
            throw new MissionRepositoryException("db down");
        });

        ProgramControlException e = assertThrows(ProgramControlException.class, pc::listMissions);

        assertEquals("db down", e.getMessage());
        assertTrue(e.getCause() instanceof MissionRepositoryException);
    }
}
