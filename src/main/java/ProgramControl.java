public class ProgramControl {

    private FileHandler fileHandler;
    private Cipher cipher; // remove this field if your team has only 3 members

    public ProgramControl(FileHandler fileHandler, Cipher cipher) {
        this.fileHandler = fileHandler;
        this.cipher = cipher;
    }

    // Called when the program runs with no arguments
    public String listAvailableFiles() {
        return fileHandler.getFileList();
    }

    // Called when the program runs with a file number (and optional key file)
    public String getFileContents(int fileNumber, String keyFileName) {
        String rawContent = fileHandler.readFile(fileNumber);

        if (rawContent == null) {
            return "Error: file not found.";
        }

        // 4-person teams: decipher it
        return cipher.decipher(rawContent, keyFileName);

        // 3-person teams: no cipher, just return the raw text instead:
        // return rawContent;
    }
}