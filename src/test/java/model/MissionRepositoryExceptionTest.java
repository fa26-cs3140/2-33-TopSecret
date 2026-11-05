package model;

import model.*;
import view.*;
import controller.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MissionRepositoryExceptionTest {

    @Test
    void constructorsPreserveMessageAndCause() {
        RuntimeException cause = new RuntimeException("database locked");

        MissionRepositoryException messageOnly =
                new MissionRepositoryException("Mission table could not be created.");
        MissionRepositoryException withCause =
                new MissionRepositoryException("Mission records could not be read.", cause);

        assertEquals("Mission table could not be created.", messageOnly.getMessage());
        assertNull(messageOnly.getCause());
        assertEquals("Mission records could not be read.", withCause.getMessage());
        assertSame(cause, withCause.getCause());
    }
}
