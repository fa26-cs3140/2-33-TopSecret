import java.util.List;

/** Search behavior used by the control layer. */
public interface MissionSearch {

    List<Mission> search(String query) throws MissionSearchException;
}
