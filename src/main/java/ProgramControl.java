import java.util.List;

/*
 * What the UI needs from the control layer.
 *
 * B: implement this, then swap the line in TopSecret.java that says PlaceholderControl.
 *
 * Things we need (delete these notes after):
 *  - don't call System.exit()
 *  - listFiles() comes back in the order it should be numbered. [0] shows as 01.
 *    empty list if there's nothing there, not null
 *  - throw ProgramControlException if the number's no good or the file or key
 *    won't load. The code I wrote prints the message. don't hand back null
 *  - I don't range check. I check it's a number and pass it on, so 0 and -5
 *    will reach you and you decide they're bad
 */
public interface ProgramControl {

    /** File names to show, in numbering order. */
    List<String> listFiles();

    /** That file, deciphered with the default key. */
    String getFileContents(int number) throws ProgramControlException;

    /** Same, but using the named key file. */
    String getFileContents(int number, String keyName) throws ProgramControlException;
}
