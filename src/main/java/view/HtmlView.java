package view;

import java.util.List;

/** HTML rendering contract; page implementation follows the view tests. */
public final class HtmlView {
    public record Link(String label, String href) {}

    public record Field(String label, String value) {}

    public record AuditRow(String timestamp, String username, String event,
                           String contentType, String contentId) {}

    public String login(String errorMessage) {
        throw new UnsupportedOperationException("HTML login page is not implemented yet");
    }

    public String menu(String username, List<Link> links) {
        throw new UnsupportedOperationException("HTML menu page is not implemented yet");
    }

    public String list(String title, List<Link> records) {
        throw new UnsupportedOperationException("HTML list page is not implemented yet");
    }

    public String record(String title, List<Field> fields) {
        throw new UnsupportedOperationException("HTML record page is not implemented yet");
    }

    public String audit(List<AuditRow> entries) {
        throw new UnsupportedOperationException("HTML audit page is not implemented yet");
    }

    public String error(String message) {
        throw new UnsupportedOperationException("HTML error page is not implemented yet");
    }
}
