package view;

import model.ContentEntry;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MissionBriefJsonTest {
    private final MissionBriefJson json = new MissionBriefJson();

    @Test
    void representsAnEmptyMissionAndPreservesBriefOrderAndIdentifiers() {
        assertEquals("[]\n", json.encode(1, List.of()));
        assertEquals("[{\"id\":7,\"mission_id\":3,\"title\":\"First\",\"date\":\"2026-11-23\",\"text\":\"Body\"},"
                        + "{\"id\":9,\"mission_id\":3,\"title\":\"Second\",\"date\":\"2026-11-24\",\"text\":\"Next\"}]\n",
                json.encode(3, List.of(new ContentEntry(7, List.of("First", "2026-11-23", "Body")),
                        new ContentEntry(9, List.of("Second", "2026-11-24", "Next")))));
    }

    @Test
    void escapesEveryControlCharacterQuotesAndBackslashesAndPreservesUnicode() {
        StringBuilder controls = new StringBuilder();
        StringBuilder escaped = new StringBuilder();
        for (int code = 0; code < 32; code++) {
            controls.append((char) code);
            escaped.append(String.format("\\u%04x", code));
        }
        String result = json.encode(2, List.of(new ContentEntry(1,
                List.of("\"\\", "2026-11-23", controls + " café 東京 🚀 <script>"))));
        assertEquals("[{\"id\":1,\"mission_id\":2,\"title\":\"\\\"\\\\\",\"date\":\"2026-11-23\",\"text\":\""
                + escaped + " café 東京 🚀 <script>\"}]\n", result);
    }
}
