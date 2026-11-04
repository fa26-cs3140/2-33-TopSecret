package view;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract tests for the read-only HTML presentation layer. */
class HtmlViewTest {
    private final HtmlView view = new HtmlView();

    @Test
    void loginPageHasAPlainHtmlFormAndEscapesAnError() {
        String html = view.login("Invalid <user> & password");

        assertDocument(html);
        assertTrue(html.contains("<form"));
        assertTrue(html.contains("action=\"/login\""));
        assertTrue(html.contains("method=\"post\""));
        assertTrue(html.contains("name=\"username\""));
        assertTrue(html.contains("name=\"password\""));
        assertTrue(html.contains("type=\"password\""));
        assertTrue(html.contains("Invalid &lt;user&gt; &amp; password"));
        assertFalse(html.contains("Invalid <user>"));
    }

    @Test
    void menuShowsOnlyLinksSuppliedByTheController() {
        String html = view.menu("Ghost <agent>", List.of(
                new HtmlView.Link("Briefs", "/content?type=briefs"),
                new HtmlView.Link("Facilities", "/content?type=facilities")));

        assertDocument(html);
        assertTrue(html.contains("Ghost &lt;agent&gt;"));
        assertTrue(html.contains("href=\"/content?type=briefs\""));
        assertTrue(html.contains("href=\"/content?type=facilities\""));
        assertFalse(html.contains("href=\"/audit\""));
    }

    @Test
    void listPageUsesRecordLinksAndEscapesLabelsAndUrls() {
        String html = view.list("Briefs", List.of(new HtmlView.Link(
                "Operation <Ghost> & Co", "/record?type=briefs&id=7")));

        assertDocument(html);
        assertTrue(html.contains("<ul"));
        assertTrue(html.contains("Operation &lt;Ghost&gt; &amp; Co"));
        assertTrue(html.contains("href=\"/record?type=briefs&amp;id=7\""));
        assertFalse(html.contains("Operation <Ghost>"));
        assertFalse(html.contains("<form"));
    }

    @Test
    void emptyListHasAReadableMessage() {
        String html = view.list("Agents", List.of());

        assertDocument(html);
        assertTrue(html.toLowerCase(Locale.ROOT).contains("no entries"));
    }

    @Test
    void recordPageShowsEscapedFieldsWithoutEditingControls() {
        String html = view.record("Agent 7", List.of(
                new HtmlView.Field("name", "Ghost"),
                new HtmlView.Field("notes", "<script>alert(1)</script> & classified")));

        assertDocument(html);
        assertTrue(html.contains("<table"));
        assertTrue(html.contains("Ghost"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; classified"));
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("<form"));
        assertFalse(html.contains("<input"));
        assertFalse(html.contains("contenteditable"));
    }

    @Test
    void auditPageShowsEscapedRowsInATable() {
        String html = view.audit(List.of(new HtmlView.AuditRow(
                "2026-11-04 12:00", "Ghost & Co", "READ", "BRIEF", "7")));

        assertDocument(html);
        assertTrue(html.contains("<table"));
        assertTrue(html.contains("Ghost &amp; Co"));
        assertTrue(html.contains("READ"));
        assertTrue(html.contains("BRIEF"));
        assertFalse(html.contains("<form"));
    }

    @Test
    void errorPageEscapesItsMessageAndOffersAWayBack() {
        String html = view.error("Record <missing> & unavailable");

        assertDocument(html);
        assertTrue(html.contains("Record &lt;missing&gt; &amp; unavailable"));
        assertTrue(html.contains("href=\"/\""));
        assertFalse(html.contains("Record <missing>"));
    }

    private static void assertDocument(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        assertTrue(lower.contains("<!doctype html>"));
        assertTrue(lower.contains("<html"));
        assertTrue(lower.contains("<title>"));
        assertTrue(lower.contains("charset=\"utf-8\""));
        assertFalse(lower.contains("<style"));
        assertFalse(lower.contains("<script"));
        assertFalse(lower.contains("<link rel=\"stylesheet\""));
    }
}
