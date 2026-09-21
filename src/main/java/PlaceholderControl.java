import java.util.ArrayList;
import java.util.List;

/*
 * TEMPORARY, delete it once B's control layer exists.
 * Only here so the program compiles. Says there are no files and refuses
 * everything else.
 */
public class PlaceholderControl implements ProgramControl {

    @Override
    public List<String> listFiles() {
        return new ArrayList<>();
    }

    @Override
    public String getFileContents(int number) throws ProgramControlException {
        throw new ProgramControlException("The program control layer is not connected yet.");
    }

    @Override
    public String getFileContents(int number, String keyName) throws ProgramControlException {
        throw new ProgramControlException("The program control layer is not connected yet.");
    }
}
