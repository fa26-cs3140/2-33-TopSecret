package model;

import model.*;
import view.*;
import controller.*;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The checks that run before a connection is ever opened. */
class SqliteMessageServiceTest {

    private final SqliteMessageService service = new SqliteMessageService(Path.of("unused.db"));
    private final Session reader = new Session("reader", Role.STANDARD);

    @Test
    void theDatabasePathIsRequired() {
        assertThrows(NullPointerException.class, () -> new SqliteMessageService(null));
    }

    @Test
    void everyOperationWithoutASessionIsDenied() {
        assertEquals("Access denied.", assertThrows(ProgramControlException.class,
                () -> service.recipients(null)).getMessage());
        assertEquals("Access denied.", assertThrows(ProgramControlException.class,
                () -> service.send(1, "hello", null)).getMessage());
        assertEquals("Access denied.", assertThrows(ProgramControlException.class,
                () -> service.inbox(null)).getMessage());
    }

    @Test
    void anEmptyMessageIsRefusedBeforeAnythingIsOpened() {
        assertEquals("A message cannot be empty.", assertThrows(ProgramControlException.class,
                () -> service.send(1, "   ", reader)).getMessage());
        assertEquals("A message cannot be empty.", assertThrows(ProgramControlException.class,
                () -> service.send(1, null, reader)).getMessage());
    }
}
