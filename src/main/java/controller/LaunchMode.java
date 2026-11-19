package controller;

public enum LaunchMode {
    TERMINAL,
    WEB,
    CHANGE_PASSWORD,
    HELP;

    public static final String USAGE = "Usage: TopSecret [-web | --change-password | -h]";

    public static LaunchMode fromArguments(String[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return TERMINAL;
        }
        if (arguments.length == 1) {
            if ("-web".equals(arguments[0])) {
                return WEB;
            }
            if ("--change-password".equals(arguments[0])) {
                return CHANGE_PASSWORD;
            }
            if ("-h".equals(arguments[0]) || "--help".equals(arguments[0])) {
                return HELP;
            }
        }
        throw new IllegalArgumentException(USAGE);
    }
}
