public class FakeFileHandler implements FileHandler {

    @Override
    public String getFileList() {
        return "01 filea.txt\n02 fileb.txt";
    }

    @Override
    public String readFile(int fileNumber) {
        if (fileNumber == 1) return "ENCODED SAMPLE TEXT";
        return null; // simulates file not found
    }

    @Override
    public String readKey(String keyFileName) {
        return "abc\nbcd";
    }
}