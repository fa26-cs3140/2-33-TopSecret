import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AuditLogExceptionTest {

    @Test
    void messageConstructorKeepsMessage() {
        assertEquals("boom", new AuditLogException("boom").getMessage());
    }

    @Test
    void causeConstructorKeepsMessageAndCause() {
        Throwable cause = new RuntimeException("root");

        AuditLogException exception = new AuditLogException("boom", cause);

        assertEquals("boom", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}