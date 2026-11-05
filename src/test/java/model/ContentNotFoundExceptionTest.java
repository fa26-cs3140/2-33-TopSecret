package model;

import controller.ProgramControlException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The message carries the ID the caller asked for, and the type stays a
 * ProgramControlException so existing handlers keep catching it.
 */
class ContentNotFoundExceptionTest {

    @Test
    void theMessageNamesTheMissingId() {
        assertEquals("No entry with ID 7.", new ContentNotFoundException(7).getMessage());
    }

    @Test
    void itIsStillAProgramControlException() {
        assertTrue(new ContentNotFoundException(1) instanceof ProgramControlException);
    }

    @Test
    void anUnusualIdIsReportedAsGiven() {
        assertEquals("No entry with ID -1.", new ContentNotFoundException(-1).getMessage());
        assertEquals("No entry with ID 0.", new ContentNotFoundException(0).getMessage());
    }
}
