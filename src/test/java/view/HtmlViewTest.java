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
        assertTrue(lower.contains("<style"), "homework 7 requires every page to be styled");
        assertFalse(lower.contains("<script"));
        assertFalse(lower.contains("<img"));
        assertFalse(lower.contains("javascript:"));
        assertFalse(lower.contains("onclick"));
        assertFalse(lower.contains("<link rel=\"stylesheet\""),
                "the stylesheet is served in the page shell, not as a separate route");
    }

    @Test
    void aFieldWithSeveralLinksRendersEachOneAsAnAnchor() {
        String html = view.record("Facility #1", List.of(new HtmlView.Field("agents", List.of(
                new HtmlView.Link("1. Cardinal", "/record?type=agents&id=1"),
                new HtmlView.Link("2. <Nightjar>", "/record?type=agents&id=2")))));

        assertTrue(html.contains("<a href=\"/record?type=agents&amp;id=1\">1. Cardinal</a>"));
        assertTrue(html.contains("<a href=\"/record?type=agents&amp;id=2\">2. &lt;Nightjar&gt;</a>"));
    }

    @Test
    void aFieldWithNoLinksSaysNone() {
        String html = view.record("Country #1", List.of(new HtmlView.Field("facilities", List.of())));

        assertTrue(html.contains("<td>None</td>"));
    }

    @Test
    void searchPageHasAGetFormAndListsResults() {
        String html = view.search("gate <x>", List.of(
                new HtmlView.Link("missions: 1. Operation Nightfall", "/record?type=missions&id=1")));

        assertTrue(html.contains("action=\"/search\""));
        assertTrue(html.contains("method=\"get\""));
        assertTrue(html.contains("name=\"q\""));
        assertTrue(html.contains("gate &lt;x&gt;"));
        assertFalse(html.contains("gate <x>"));
        assertTrue(html.contains("href=\"/record?type=missions&amp;id=1\""));
    }

    @Test
    void searchPageSaysWhenNothingMatchedButNotBeforeASearch() {
        assertTrue(view.search("zzz", List.of()).contains("No matches."));
        assertFalse(view.search("", List.of()).contains("No matches."));
    }
}
