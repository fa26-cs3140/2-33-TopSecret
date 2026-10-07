import java.util.List;

/**
 * The mission data that the rest of the program may request.
 * The SQLite implementation belongs behind this interface.
 */
public interface MissionRepository {

    /** All missions in their normal display order. Never returns null. */
    List<Mission> getAllMissions() throws MissionRepositoryException;
}
