import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CredentialExceptionTest {

    @Test
    void constructorsPreserveMessageAndCause() {
        RuntimeException cause = new RuntimeException("disk failure");

        CredentialException messageOnly = new CredentialException("invalid credentials");
        CredentialException withCause = new CredentialException("storage failed", cause);

        assertEquals("invalid credentials", messageOnly.getMessage());
        assertEquals("storage failed", withCause.getMessage());
        assertSame(cause, withCause.getCause());
    }
}
