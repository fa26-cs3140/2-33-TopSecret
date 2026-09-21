/*
 * B throws this when it can't do what the UI asked: no such number, file won't
 * read, key is missing or broken.
 */
public class ProgramControlException extends Exception {

    public ProgramControlException(String message) {
        super(message);
    }

    public ProgramControlException(String message, Throwable cause) {
        super(message, cause);
    }
}
