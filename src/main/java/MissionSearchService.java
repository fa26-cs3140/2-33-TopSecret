import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Searches mission briefs without knowing how or where missions are stored. */
public class MissionSearchService implements MissionSearch {

    private static final String EMPTY_QUERY_MESSAGE = "Search phrase cannot be empty.";
    private static final String SEARCH_FAILURE_MESSAGE = "Mission search could not be completed.";

    private final MissionRepository repository;

    public MissionSearchService(MissionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    @Override
    public List<Mission> search(String query) throws MissionSearchException {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            throw new MissionSearchException(EMPTY_QUERY_MESSAGE);
        }

        List<Mission> missions;
        try {
            missions = repository.getAllMissions();
        } catch (MissionRepositoryException exception) {
            throw new MissionSearchException(SEARCH_FAILURE_MESSAGE, exception);
        }

        if (missions == null || missions.isEmpty()) {
            return List.of();
        }

        String searchText = normalizedQuery.toLowerCase(Locale.ROOT);
        List<Mission> matches = new ArrayList<>();
        for (Mission mission : missions) {
            if (mission != null
                    && mission.getBrief().toLowerCase(Locale.ROOT).contains(searchText)) {
                matches.add(mission);
            }
        }
        return List.copyOf(matches);
    }
}
