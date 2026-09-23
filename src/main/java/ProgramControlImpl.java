import java.util.ArrayList;
import java.util.List;

public class ProgramControlImpl implements ProgramControl {

    private final FileHandler fileHandler;
    private final Cipher cipher;

    public ProgramControlImpl(FileHandler fileHandler, Cipher cipher) {
        this.fileHandler = fileHandler;
        this.cipher = cipher;
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
}
