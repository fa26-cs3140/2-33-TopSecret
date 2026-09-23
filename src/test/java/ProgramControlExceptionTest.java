import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ProgramControlExceptionTest {

    @Test
    void carriesMessage() {
        ProgramControlException error = new ProgramControlException("missing file");

        assertEquals("missing file", error.getMessage());
    }

    @Test
    void carriesCause() {
        RuntimeException cause = new RuntimeException("bad key");
        ProgramControlException error = new ProgramControlException("decipher failed", cause);

        assertEquals("decipher failed", error.getMessage());
        assertSame(cause, error.getCause());
    }
}
