public interface FileHandler {
    String getFileList();
    String readFile(int fileNumber);
    String readKey(String keyFileName);   // returns full key file text, or null if unreadable

    // any project file by relative path, or null if it's missing or outside the
    // project. default returns null so the existing test stubs still compile
    default String readText(String relativePath) {
        return null;
    }
}
