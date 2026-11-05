package controller;

import model.ContentEntry;
import model.ContentType;
import org.junit.jupiter.api.Test;
import view.ContentText;
import view.HtmlView;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HtmlViewTest {
    @Test
    void sharedTerminalTextIncludesMissionDateAndHtmlUsesDisplayData() {
        assertNotNull(new ContentText());
        ContentEntry mission = new ContentEntry(7, List.of("Nightfall", "Brief text", "2026-11-05"));
        assertEquals("7. Nightfall (2026-11-05)", ContentText.title(ContentType.MISSION, mission));
        assertTrue(ContentText.record(ContentType.MISSION, mission).contains("brief: Brief text"));

        HtmlView view = new HtmlView();
        String html = view.list("missions", List.of(new HtmlView.Link(
                ContentText.title(ContentType.MISSION, mission), "/record?type=missions&id=7")));
        assertTrue(html.contains("7. Nightfall (2026-11-05)"));
        assertTrue(html.contains("/record?type=missions&amp;id=7"));
        assertFalse(html.contains("<script"));
    }
}
