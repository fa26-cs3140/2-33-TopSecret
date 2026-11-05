package model;

import model.*;
import view.*;
import controller.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The kinds of content an audit entry can point at. */
class AuditContentTypeTest {

    @Test
    void theKindsCoverEveryEntity() {
        assertArrayEquals(
                new AuditContentType[]{AuditContentType.BRIEF, AuditContentType.AGENT,
                        AuditContentType.FACILITY, AuditContentType.MISSION},
                AuditContentType.values());
    }

    @Test
    void eachNameRoundTripsThroughValueOf() {
        for (AuditContentType kind : AuditContentType.values()) {
            assertEquals(kind, AuditContentType.valueOf(kind.name()));
        }
    }

    @Test
    void everyContentTypeHasAMatchingAuditKind() {
        for (ContentType type : ContentType.values()) {
            assertEquals(type.name(), AuditContentType.valueOf(type.name()).name());
        }
    }

    @Test
    void anUnknownNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AuditContentType.valueOf("MESSAGE"));
    }
}
