import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * Stand-in for B's control layer so the UI can be tested on its own.
 * A test sets up what this should return, then checks afterwards what the UI
 * actually asked for.
 */
public class FakeProgramControl implements ProgramControl {

    private List<String> files = new ArrayList<>();
    private String contents = "";
    private String errorMessage = null;

    private int lastNumber = -1;
    private String lastKeyName = null;
    private boolean defaultKeyUsed = false;

    /* set up what it returns */

    public void setFiles(String... names) {
        this.files = new ArrayList<>(Arrays.asList(names));
    }

    public void setContents(String text) {
        this.contents = text;
    }

    /** Make the next call blow up, like the real thing would. */
    public void failWith(String message) {
        this.errorMessage = message;
    }

    /* check what the UI asked for */

    public int getLastNumber() {
        return lastNumber;
    }

    public String getLastKeyName() {
        return lastKeyName;
    }

    public boolean wasDefaultKeyUsed() {
        return defaultKeyUsed;
    }

    /* the actual interface */

    @Override
    public List<String> listFiles() {
        return files;
    }

    @Override
    public String getFileContents(int number) throws ProgramControlException {
        lastNumber = number;
        defaultKeyUsed = true;
        if (errorMessage != null) {
            throw new ProgramControlException(errorMessage);
        }
        return contents;
    }

    @Override
    public String getFileContents(int number, String keyName) throws ProgramControlException {
        lastNumber = number;
        lastKeyName = keyName;
        defaultKeyUsed = false;
        if (errorMessage != null) {
            throw new ProgramControlException(errorMessage);
        }
        return contents;
    }
}
