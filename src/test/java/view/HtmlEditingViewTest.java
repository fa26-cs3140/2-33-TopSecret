package view;

import controller.WebEditForm;
import model.ContentType;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HtmlEditingViewTest {
    private final HtmlView view = new HtmlView();

    @Test
    void blankAndPopulatedFormsEscapeValuesAndUseCorrectTargets() {
        String blank = view.editForm(WebEditForm.newRecord(ContentType.FACILITY), "token", Map.of(), Map.of(), null);
        assertTrue(blank.contains("action=\"/create\""));
        assertTrue(blank.contains("value=\"\""));
        assertTrue(blank.contains("href=\"/content?type=facilities\""));
        String edit = view.editForm(new WebEditForm(ContentType.FACILITY, 7, List.of("\"><script>", "NO")),
                "<&", Map.of(), Map.of(), "<invalid>");
        assertTrue(edit.contains("action=\"/update\""));
        assertTrue(edit.contains("name=\"id\" value=\"7\""));
        assertTrue(edit.contains("&quot;&gt;&lt;script&gt;"));
        assertTrue(edit.contains("&lt;invalid&gt;"));
        assertTrue(edit.contains("/record?type=facilities&amp;id=7"));
        assertFalse(edit.contains("<script>"));
    }

    @Test
    void relationshipChoicesKeepCurrentAndSubmittedSelections() {
        Map<String, List<HtmlView.Link>> choices = Map.of("facility_id", List.of(new HtmlView.Link("<North>", "2")));
        String agent = view.editForm(new WebEditForm(ContentType.AGENT, 4,
                List.of("Ghost", "1990-01-01", "", "", "2")), "token", choices, Map.of(), null);
        assertTrue(agent.contains("value=\"2\" selected>&lt;North&gt;"));
        String mission = view.editForm(WebEditForm.newRecord(ContentType.MISSION), "token", choices,
                Map.of("facility_id", "2"), "Choose an agent");
        assertTrue(mission.contains("<select required"));
        assertTrue(mission.contains("value=\"2\" selected"));
    }

    @Test
    void multilineContentPreservesLineBreaksAndCannotCloseTheTextarea() {
        String text = "\nFirst line\n</textarea><script>";
        String html = view.editForm(new WebEditForm(ContentType.BRIEF, 1,
                List.of("Signal", "2026-11-19", text)), "token", Map.of(), Map.of(), null);
        assertTrue(html.contains("name=\"text\">\n\nFirst line\n&lt;/textarea&gt;&lt;script&gt;</textarea>"));
        assertTrue(html.contains("type=\"date\""));
        assertFalse(html.contains("<script>"));
    }
}
