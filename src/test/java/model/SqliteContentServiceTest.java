package model;

import model.*;
import view.*;
import controller.*;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The parts that need no database: what the constructor rejects, and the access
 * checks that run before a connection is ever opened. Behaviour against real
 * SQLite is covered by SqliteContentServiceSqliteAuditLogRepositoryTest.
 */
class SqliteContentServiceTest {

    private static final Path DATABASE = Path.of("unused.db");

    private final AuditLogRepository auditLog = mock(AuditLogRepository.class);

    private SqliteContentService service() {
        return new SqliteContentService(DATABASE, auditLog, new FakeFileHandler());
    }

    /* what the constructor insists on */

    @Test
    void theDatabasePathIsRequired() {
        assertThrows(NullPointerException.class,
                () -> new SqliteContentService(null, auditLog, new FakeFileHandler()));
    }

    @Test
    void theAuditLogIsRequired() {
        assertThrows(NullPointerException.class,
                () -> new SqliteContentService(DATABASE, null, new FakeFileHandler()));
    }

    @Test
    void theFileHandlerIsRequired() {
        assertThrows(NullPointerException.class,
                () -> new SqliteContentService(DATABASE, auditLog, null));
    }

    @Test
    void theTwoArgumentConstructorSuppliesItsOwnFileHandler() {
        assertEquals(SqliteContentService.class,
                new SqliteContentService(DATABASE, auditLog).getClass());
    }

    /* no session means no access, and nothing is opened */

    @Test
    void readingWithoutASessionIsDenied() {
        SqliteContentService service = service();

        assertEquals("Access denied.", assertThrows(ProgramControlException.class,
                () -> service.list(ContentType.BRIEF, null)).getMessage());
        assertEquals("Access denied.", assertThrows(ProgramControlException.class,
                () -> service.read(ContentType.BRIEF, 1, null)).getMessage());
        verifyNoInteractions(auditLog);
    }

    @Test
    void writingWithoutASessionIsDenied() {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class,
                () -> service.add(ContentType.BRIEF, List.of("t", "d", "x"), null));
        assertThrows(ProgramControlException.class,
                () -> service.update(ContentType.BRIEF, 1, List.of("t", "d", "x"), null));
        assertThrows(ProgramControlException.class,
                () -> service.delete(ContentType.BRIEF, 1, null));
        verifyNoInteractions(auditLog);
    }

    @Test
    void theAuditLogIsAdminOnly() {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class, () -> service.audit(null));
        assertThrows(ProgramControlException.class,
                () -> service.audit(new Session("agent", Role.STANDARD)));
        verifyNoInteractions(auditLog);
    }

    /* a standard user may read but not change anything */

    @Test
    void aStandardUserCannotCreateUpdateOrDelete() {
        SqliteContentService service = service();
        Session agent = new Session("agent", Role.STANDARD);

        assertThrows(ProgramControlException.class,
                () -> service.add(ContentType.AGENT, List.of("n", "d", "", "x", "1"), agent));
        assertThrows(ProgramControlException.class,
                () -> service.update(ContentType.AGENT, 1, List.of("n", "d", "", "x", "1"), agent));
        assertThrows(ProgramControlException.class,
                () -> service.delete(ContentType.AGENT, 1, agent));
        verifyNoInteractions(auditLog);
    }
}
