import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionSearchServiceTest {

    private final Mission alpha = mission(1, "Alpha", "Meet the agent at the station.");
    private final Mission bravo = mission(2, "Bravo", "Monitor the AGENT during the exchange.");
    private final Mission charlie = mission(3, "Agent in the Title", "Inspect the safehouse.");

    @Test
    void searchIgnoresCase() throws MissionSearchException {
        MissionSearch search = searchWith(alpha, bravo, charlie);

        List<Mission> matches = search.search("aGeNt");

        assertEquals(List.of(alpha, bravo), matches);
    }

    @Test
    void searchMatchesAWholePhraseInsideBrief() throws MissionSearchException {
        MissionSearch search = searchWith(alpha, bravo);

        List<Mission> matches = search.search("agent at the station");

        assertEquals(List.of(alpha), matches);
    }

    @Test
    void searchDoesNotInspectTitles() throws MissionSearchException {
        MissionSearch search = searchWith(charlie);

        List<Mission> matches = search.search("agent");

        assertTrue(matches.isEmpty());
    }

    @Test
    void searchTrimsSurroundingWhitespace() throws MissionSearchException {
        MissionSearch search = searchWith(alpha);

        List<Mission> matches = search.search("  station  ");

        assertEquals(List.of(alpha), matches);
    }

    @Test
    void searchReturnsEmptyListWhenNothingMatches() throws MissionSearchException {
        MissionSearch search = searchWith(alpha, bravo);

        List<Mission> matches = search.search("submarine");

        assertTrue(matches.isEmpty());
    }

    @Test
    void searchRejectsNullOrBlankQuery() {
        MissionSearch search = searchWith(alpha);

        MissionSearchException nullError =
                assertThrows(MissionSearchException.class, () -> search.search(null));
        MissionSearchException blankError =
                assertThrows(MissionSearchException.class, () -> search.search("   "));

        assertEquals("Search phrase cannot be empty.", nullError.getMessage());
        assertEquals("Search phrase cannot be empty.", blankError.getMessage());
    }

    @Test
    void searchReturnsEmptyListWhenRepositoryHasNoMissions() throws MissionSearchException {
        MissionSearch search = searchWith();

        List<Mission> matches = search.search("anything");

        assertTrue(matches.isEmpty());
    }

    @Test
    void repositoryFailureBecomesSearchException() {
        MissionRepository repository = () -> {
            throw new MissionRepositoryException("database unavailable");
        };
        MissionSearch search = new MissionSearchService(repository);

        MissionSearchException error =
                assertThrows(MissionSearchException.class, () -> search.search("agent"));

        assertEquals("Mission search could not be completed.", error.getMessage());
        assertTrue(error.getCause() instanceof MissionRepositoryException);
    }

    private MissionSearch searchWith(Mission... missions) {
        MissionRepository repository = () -> List.of(missions);
        return new MissionSearchService(repository);
    }

    private Mission mission(int id, String title, String brief) {
        return new Mission(id, title, brief, LocalDate.of(2000, 1, id));
    }
}
