package view;

import model.ContentEntry;
import java.util.List;
import java.util.StringJoiner;

public final class MissionBriefJson {
    public String encode(int missionId, List<ContentEntry> briefs) {
        StringJoiner records = new StringJoiner(",", "[", "]\n");
        for (ContentEntry brief : briefs) {
            records.add("{\"id\":" + brief.id() + ",\"mission_id\":" + missionId
                    + ",\"title\":" + quote(brief.values().get(0))
                    + ",\"date\":" + quote(brief.values().get(1))
                    + ",\"text\":" + quote(brief.values().get(2)) + "}");
        }
        return records.toString();
    }

    private static String quote(String value) {
        StringBuilder encoded = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') {
                encoded.append('\\').append(character);
            } else if (character < 32) {
                encoded.append(String.format("\\u%04x", (int) character));
            } else {
                encoded.append(character);
            }
        }
        return encoded.append('"').toString();
    }
}
