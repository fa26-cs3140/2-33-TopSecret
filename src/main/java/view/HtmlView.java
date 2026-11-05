package view;

import model.*;
import java.util.List;

public final class HtmlView {
    public static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static String page(String title, String body) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>"
                + escape(title) + "</title></head><body><h1>" + escape(title) + "</h1>" + body + "</body></html>";
    }

    public static String login(String message) {
        return page("Top Secret login", "<p>" + escape(message) + "</p>"
                + "<form method=\"post\" action=\"/login\"><label>Username <input name=\"username\" required autocomplete=\"username\"></label>"
                + "<label>Password <input name=\"password\" type=\"password\" required autocomplete=\"current-password\"></label>"
                + "<button type=\"submit\">Log in</button></form><p>New accounts and initial passwords are set up in the terminal.</p>");
    }

    public static String navigation() {
        return "<p><a href=\"/menu\">Main menu</a></p><form method=\"post\" action=\"/logout\"><button>Log out</button></form>";
    }

    public static String menu(boolean audit) {
        StringBuilder body = new StringBuilder("<ul>");
        for (ContentType type : ContentType.values()) {
            body.append("<li><a href=\"/content?type=").append(type.table).append("\">")
                    .append(escape(type.table)).append("</a></li>");
        }
        if (audit) {
            body.append("<li><a href=\"/audit\">Review audit log</a></li>");
        }
        return page("Main menu", body.append("</ul>").append(navigation()).toString());
    }

    public static String list(ContentType type, List<ContentEntry> entries) {
        StringBuilder body = new StringBuilder("<ul>");
        for (ContentEntry entry : entries) {
            body.append("<li><a href=\"/record?type=").append(type.table).append("&amp;id=")
                    .append(entry.id()).append("\">").append(escape(ContentText.title(entry))).append("</a></li>");
        }
        body.append("</ul>");
        if (entries.isEmpty()) {
            body.append("<p>No entries available.</p>");
        }
        return page(type.table, body + navigation());
    }

    public static String record(ContentType type, ContentEntry entry) {
        return page(type.table + " #" + entry.id(), "<pre>" + escape(ContentText.record(type, entry))
                + "</pre><p><a href=\"/content?type=" + type.table + "\">Back to list</a></p>" + navigation());
    }

    public static String audit(List<AuditLogEntry> entries) {
        return page("Audit log", "<pre>" + escape(ContentText.audit(entries)) + "</pre>" + navigation());
    }
}
