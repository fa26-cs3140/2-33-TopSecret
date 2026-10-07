import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProgramControlImpl implements ProgramControl {

    private final FileHandler fileHandler;
    private final Cipher cipher;
    private final MissionSearch missionSearch;

    public ProgramControlImpl(FileHandler fileHandler, Cipher cipher) {
        this(fileHandler, cipher, query -> {
            throw new MissionSearchException("Mission database is not connected.");
        });
    }

    public ProgramControlImpl(FileHandler fileHandler, Cipher cipher, MissionSearch missionSearch) {
        this.fileHandler = fileHandler;
        this.cipher = cipher;
        this.missionSearch = Objects.requireNonNull(missionSearch, "missionSearch cannot be null");
    }

    @Override
    public List<String> listFiles() {
        String rawList = fileHandler.getFileList();
        List<String> files = new ArrayList<>();

        if (rawList == null || rawList.isEmpty()) {
            return files;
        }

        for (String line : rawList.split("\n")) {
            files.add(line);
        }
        return files;
    }

    @Override
    public String getFileContents(int number) throws ProgramControlException {
        return getFileContents(number, null);
    }

    @Override
    public String getFileContents(int number, String keyName) throws ProgramControlException {
        if (number <= 0) {
            throw new ProgramControlException("Invalid file number: " + number);
        }

        String rawContent = fileHandler.readFile(number);
        if (rawContent == null) {
            throw new ProgramControlException("File not found: " + number);
        }

        try {
            return cipher.decipher(rawContent, keyName);
        } catch (InvalidCipherException e) {
            throw new ProgramControlException(e.getMessage(), e);
        } catch (Exception e) {
            throw new ProgramControlException("Failed to decipher file: " + number, e);
        }
    }

    @Override
    public List<Mission> searchMissions(String query) throws ProgramControlException {
        try {
            return missionSearch.search(query);
        } catch (MissionSearchException exception) {
            throw new ProgramControlException(exception.getMessage(), exception);
        }
    }
}
