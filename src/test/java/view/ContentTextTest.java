package view;

import model.AuditContentType;
import model.AuditEventType;
import model.AuditLogEntry;
import model.ContentEntry;
import model.ContentType;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The text the terminal menu and the HTML pages both render from. These pin the
 * shape of it, since two callers now depend on the same strings.
 */
class ContentTextTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 11, 5, 14, 30);

    @Test
    void aTitleIsTheIdThenTheFirstValue() {
        ContentEntry entry = new ContentEntry(7, List.of("Operation Sandtrap", "1970-11-03", "Text"));

        assertEquals("7. Operation Sandtrap", ContentText.title(entry));
    }

    @Test
    void aRecordLabelsEveryFieldOfItsType() {
        ContentEntry entry = new ContentEntry(1, List.of("Langley Annex", "LGA"));

        assertEquals("name: Langley Annex\nabbreviation: LGA\n",
                ContentText.record(ContentType.FACILITY, entry));
    }

    @Test
    void aRecordUsesTheFieldNamesOfTheTypeItIsGiven() {
        ContentEntry entry = new ContentEntry(1, List.of("Operation Dusk", "Brief text.", "2026-11-01"));

        String text = ContentText.record(ContentType.MISSION, entry);

        assertTrue(text.startsWith("title: Operation Dusk\n"));
        assertTrue(text.contains("brief: Brief text.\n"));
        assertTrue(text.endsWith("date: 2026-11-01\n"));
    }

    @Test
    void anEmptyAuditLogSaysSoRatherThanReturningNothing() {
        assertEquals("Audit log is empty.", ContentText.audit(List.of()));
    }

    @Test
    void anAuditLineCarriesTimeUserEventTypeAndId() {
        AuditLogEntry entry = new AuditLogEntry(
                AuditEventType.READ, AuditContentType.MISSION, 4, "reader", WHEN);

        assertEquals(WHEN + " | reader | READ | MISSION #4", ContentText.audit(List.of(entry)));
    }

    @Test
    void severalAuditLinesAreSeparatedByNewlines() {
        AuditLogEntry read = new AuditLogEntry(
                AuditEventType.READ, AuditContentType.BRIEF, 1, "reader", WHEN);
        AuditLogEntry create = new AuditLogEntry(
                AuditEventType.CREATE, AuditContentType.AGENT, 2, "admin", WHEN);

        String text = ContentText.audit(List.of(read, create));

        assertEquals(2, text.split("\n").length);
        assertTrue(text.contains("reader | READ | BRIEF #1"));
        assertTrue(text.contains("admin | CREATE | AGENT #2"));
    }
}
