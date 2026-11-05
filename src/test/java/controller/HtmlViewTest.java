package controller;

import model.*;
import view.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HtmlViewTest {
    @Test
    void plainHtmlUsesSharedTextAndEscapesEveryDynamicField() {
        assertNotNull(new HtmlView());
        assertNotNull(new ContentText());
        assertEquals("", HtmlView.escape(null));
        assertEquals("&lt;&amp;&gt;&quot;&#39;", HtmlView.escape("<&>\"'"));
        ContentEntry entry = new ContentEntry(1, List.of("<title>", "date", "body"));
        assertEquals("1. <title>", ContentText.title(entry));
        assertTrue(ContentText.record(ContentType.BRIEF, entry).contains("title: <title>"));
        assertTrue(HtmlView.record(ContentType.BRIEF, entry).contains("title: &lt;title&gt;"));
        assertTrue(HtmlView.list(ContentType.BRIEF, List.of(entry)).contains("1. &lt;title&gt;"));
        assertTrue(HtmlView.list(ContentType.BRIEF, List.of()).contains("No entries available."));
        AuditLogEntry audit = new AuditLogEntry(AuditEventType.READ, AuditContentType.BRIEF, 1, "<agent>", LocalDateTime.of(2026, 11, 5, 1, 2));
        assertTrue(ContentText.audit(List.of(audit)).contains("<agent> | READ | BRIEF #1"));
        assertTrue(HtmlView.audit(List.of(audit)).contains("&lt;agent&gt;"));
        assertTrue(HtmlView.audit(List.of()).contains("Audit log is empty."));
        assertTrue(HtmlView.menu(true).contains("/audit"));
        assertFalse(HtmlView.menu(false).contains("/audit"));
        assertTrue(HtmlView.menu(false).contains("type=missions"));
        assertFalse(HtmlView.login("<error>").contains("<error>"));
        assertTrue(HtmlView.page("<title>", "<p>safe</p>").contains("<title>&lt;title&gt;</title>"));
        for (String page : List.of(HtmlView.menu(true), HtmlView.login(""), HtmlView.record(ContentType.BRIEF, entry))) {
            assertFalse(page.contains("<script"));
            assertFalse(page.contains("<style"));
        }
    }
}
