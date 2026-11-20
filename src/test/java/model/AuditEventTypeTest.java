package model;

import model.*;
import view.*;
import controller.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The events the audit log is allowed to record. */
class AuditEventTypeTest {

    @Test
    void theEventsAreReadCreateUpdateAndDelete() {
        assertArrayEquals(
                new AuditEventType[]{AuditEventType.READ, AuditEventType.CREATE,
                        AuditEventType.UPDATE, AuditEventType.DELETE},
                AuditEventType.values());
    }

    @Test
    void eachNameRoundTripsThroughValueOf() {
        for (AuditEventType event : AuditEventType.values()) {
            assertEquals(event, AuditEventType.valueOf(event.name()));
        }
    }

    @Test
    void anUnknownNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AuditEventType.valueOf("ARCHIVE"));
    }
}
