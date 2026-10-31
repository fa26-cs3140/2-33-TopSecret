import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MissionSearchExceptionTest {

    @Test
    void constructorsPreserveMessageAndCause() {
        RuntimeException cause = new RuntimeException("no index");

        MissionSearchException messageOnly =
                new MissionSearchException("Mission database is not connected.");
        MissionSearchException withCause =
                new MissionSearchException("Search failed.", cause);

        assertEquals("Mission database is not connected.", messageOnly.getMessage());
        assertNull(messageOnly.getCause());
        assertEquals("Search failed.", withCause.getMessage());
        assertSame(cause, withCause.getCause());
    }
}
