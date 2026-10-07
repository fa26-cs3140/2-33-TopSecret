import java.util.List;

/*
 * What the UI needs from the control layer.
 *
 * The control layer coordinates file access and deciphering, but it does not
 * print, exit the program, read files directly, or implement cipher rules.
 */
public interface ProgramControl {

    /** File names to show, in numbering order. Returns an empty list if none exist. */
    List<String> listFiles();

    /** That file, deciphered with the default key. */
    String getFileContents(int number) throws ProgramControlException;

    /** Same, but using the named key file. */
    String getFileContents(int number, String keyName) throws ProgramControlException;

    /** Missions whose briefs contain the supplied word or phrase, ignoring case. */
    List<Mission> searchMissions(String query) throws ProgramControlException;

    /** UI Needs a way to get missions **/
    List<Mission> listMissions() throws ProgramControlException;
}
