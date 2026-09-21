public interface FileHandler {
    String getFileList();
    String readFile(int fileNumber);
    String readKey(String keyFileName);   // returns full key file text, or null if unreadable
}
