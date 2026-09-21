import java.util.List;

/*
 * Reads the args, asks the control layer for what it needs, builds the text to show.
 * Doesn't print, doesn't exit, doesn't touch files.
 *
 * run() hands back a String and TopSecret prints it - makes this testable easily
 *
 * Spec is in docs/userinterface.txt.
 */
public class UserInterface {

    static final String USAGE = "Usage: TopSecret [number] [keyname]";
    static final String NO_FILES = "No files are available.";
    static final String LIST_HINT = "Run with no arguments to see the list.";

    private final ProgramControl control;

    public UserInterface(ProgramControl control) {
        this.control = control;
    }

    /** One run of the program. Returns everything the user should see. */
    public String run(String[] args) {
        if (args == null || args.length == 0) {
            return listFiles();
        }
        if (args.length == 1) {
            return showFile(args[0], null);
        }
        if (args.length == 2) {
            return showFile(args[0], args[1]);
        }
        return line(USAGE);
    }

    /** Numbered from 01, in whatever order the control layer gives them. */
    private String listFiles() {
        List<String> files = control.listFiles();
        if (files == null || files.isEmpty()) {
            return line(NO_FILES);
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < files.size(); i++) {
            out.append(String.format("%02d", i + 1))
               .append(' ')
               .append(files.get(i))
               .append('\n');
        }
        return out.toString();
    }

    /** One file. null keyName means use the default key. */
    private String showFile(String numberArg, String keyName) {
        String trimmed = numberArg == null ? "" : numberArg.trim();

        int number;
        try {
            number = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return error("'" + trimmed + "' is not a file number.");
        }

        // anything that parsed goes straight through. deciding if it's a real
        // file is the control layer's problem, not mine
        try {
            String contents = keyName == null
                    ? control.getFileContents(number)
                    : control.getFileContents(number, keyName);
            return endWithNewline(contents == null ? "" : contents);
        } catch (ProgramControlException e) {
            return error(e.getMessage());
        }
    }

    private String error(String message) {
        return line("Error: " + message) + line(LIST_HINT);
    }

    private String line(String text) {
        return text + "\n";
    }

    private String endWithNewline(String text) {
        return text.endsWith("\n") ? text : text + "\n";
    }
}
