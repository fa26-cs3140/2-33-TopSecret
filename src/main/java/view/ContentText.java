package view;

import model.*;
import java.util.List;
import java.util.stream.Collectors;

public final class ContentText {
    public static String title(ContentEntry entry) {
        return entry.id() + ". " + entry.values().get(0);
    }

    public static String record(ContentType type, ContentEntry entry) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < type.fields.size(); index++) {
            text.append(type.fields.get(index)).append(": ").append(entry.values().get(index)).append('\n');
        }
        return text.toString();
    }

    public static String audit(List<AuditLogEntry> entries) {
        if (entries.isEmpty()) {
            return "Audit log is empty.";
        }
        return entries.stream().map(entry -> entry.getTimestamp() + " | " + entry.getUsername() + " | "
                + entry.getEventType() + " | " + entry.getContentType() + " #" + entry.getContentId())
                .collect(Collectors.joining("\n"));
    }
}
