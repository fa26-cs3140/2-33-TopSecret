package controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The failure a rejected form carries. It stays a ProgramControlException so
 * the handlers that already catch that keep catching it, while being distinct
 * enough for the editing handler to answer 400 rather than 500.
 */
class WebValidationExceptionTest {

    @Test
    void theMessageIsCarriedThrough() {
        assertEquals("Choose a record for country_id.",
                new WebValidationException("Choose a record for country_id.").getMessage());
    }

    @Test
    void itIsStillAProgramControlException() {
        assertTrue(new WebValidationException("x") instanceof ProgramControlException);
    }

    @Test
    void itIsDistinguishableFromAnOrdinaryControlFailure() {
        ProgramControlException ordinary = new ProgramControlException("storage failed");

        assertTrue(new WebValidationException("bad field") instanceof WebValidationException);
        assertEquals(false, ordinary instanceof WebValidationException);
    }
}
