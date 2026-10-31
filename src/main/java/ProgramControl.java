import java.util.List;

/*
 * What the UI needs from the control layer.
 *
 * The control layer coordinates mission access, but it does not print, exit the
 * program, read files directly, or implement cipher rules.
 */
public interface ProgramControl {

    /** Missions whose briefs contain the supplied word or phrase, ignoring case. */
    List<Mission> searchMissions(String query) throws ProgramControlException;

    /** UI Needs a way to get missions **/
    List<Mission> listMissions() throws ProgramControlException;
}
