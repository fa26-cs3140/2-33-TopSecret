package model;

import controller.ProgramControlException;

public final class ContentNotFoundException extends ProgramControlException {
    public ContentNotFoundException(int id) {
        super("No entry with ID " + id + ".");
    }
}
